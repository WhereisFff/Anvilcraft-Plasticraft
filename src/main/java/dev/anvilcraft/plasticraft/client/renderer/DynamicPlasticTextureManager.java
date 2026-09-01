package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.GeneratedPlasticTexture;
import dev.anvilcraft.plasticraft.api.texture.PlasticBaseTextureSet;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.item.CreativeColorVariantItem;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.fluids.FluidStack;

import javax.annotation.Nullable;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 运行时制品共享的 CPU 生成结果与 GPU 动态纹理生命周期。
 *
 * <p>同一 (形状, 材质) 的十六种颜色纵向堆叠进同一张动态纹理，使十六色制品共用一个
 * {@code RenderType}。vanilla 的 {@code MultiBufferSource.BufferSource} 对非 {@code fixedBuffers}
 * 的 {@code RenderType} 只保留一个共享缓冲，切换即强制刷写，逐色独立纹理会把批次打散成颜色数量的倍数。</p>
 */
public final class DynamicPlasticTextureManager implements ResourceManagerReloadListener {
    public static final DynamicPlasticTextureManager INSTANCE = new DynamicPlasticTextureManager();

    private final Map<AtlasKey, ColorAtlas> atlases = new HashMap<>();
    private final Map<AtlasKey, Map<DyeColor, ResourceLocation>> oversizedTextures = new HashMap<>();
    private final Map<PlasticMaterial, ResourceInputs> resources = new HashMap<>();

    private DynamicPlasticTextureManager() {
    }

    /**
     * 取得制品纹理及其在堆叠图集中的颜色行。
     *
     * <p>只有真正被请求到的颜色才会生成并上传对应行，避免首次见到新形状时付出十六倍的生成开销。</p>
     */
    public synchronized MoldedTextureRef textureRef(MoldedPlasticData data) {
        String shapeHash = data.shapeHash();
        FluidStack melt = data.materialView();
        DyeColor color = PlasticMeltColor.get(melt);
        PlasticMaterial resolved = PlasticMaterial.fromMeltOrNull(melt);
        PlasticMaterial material = resolved == null ? PlasticMaterial.UNIVERSAL : resolved;
        ResourceInputs currentResources = this.loadResources(material);
        PlasticTextureLayout layout = data.textureLayout();
        AtlasKey key = new AtlasKey(
            shapeHash,
            material,
            currentResources.baseHash,
            currentResources.paletteHash
        );
        // 无色板的材质与颜色无关，只需单行；行数必须与 paletteRow 的取值域一致。
        int rowCount = material.hasPalette() ? CreativeColorVariantItem.CREATIVE_COLOR_ORDER.size() : 1;
        int paletteRow = material.hasPalette() ? CreativeColorVariantItem.paletteRow(color) : 0;

        // 安全阀：堆叠后的高度超过生成器上限时退回逐色单张纹理，复杂形状不会因为超限抛异常。
        if (layout.atlasHeight() * rowCount > PlasticTextureGenerator.MAX_ATLAS_SIZE) {
            ResourceLocation oversized = this.oversizedTextures
                .computeIfAbsent(key, ignored -> new HashMap<>())
                .computeIfAbsent(color, ignored -> this.createOversizedTexture(
                    shapeHash,
                    data.plasticSurfaces(),
                    color,
                    material,
                    currentResources,
                    paletteRow
                ));
            return new MoldedTextureRef(oversized, 0, 1);
        }

        ColorAtlas atlas = this.atlases.get(key);
        if (atlas == null) {
            atlas = createAtlas(shapeHash, material, layout, rowCount);
            this.atlases.put(key, atlas);
        }
        // 行已填充时不进入生成分支，避免逐帧为已就绪的颜色行构造捕获式回调。
        if (!atlas.hasRow(paletteRow)) {
            atlas.fillRow(paletteRow, () -> this.generate(
                shapeHash,
                data.plasticSurfaces(),
                material,
                currentResources,
                paletteRow
            ));
        }
        return new MoldedTextureRef(atlas.id, paletteRow, rowCount);
    }

    /** 只需要纹理标识、不关心颜色行的调用方入口（物品模型、实体渲染器纹理契约）。 */
    public ResourceLocation texture(MoldedPlasticData data) {
        return this.textureRef(data).texture();
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.clear();
    }

    public synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ColorAtlas atlas : this.atlases.values()) {
            minecraft.getTextureManager().release(atlas.id);
        }
        for (Map<DyeColor, ResourceLocation> byColor : this.oversizedTextures.values()) {
            for (ResourceLocation texture : byColor.values()) {
                minecraft.getTextureManager().release(texture);
            }
        }
        this.atlases.clear();
        this.oversizedTextures.clear();
        this.resources.clear();
    }

    private static ColorAtlas createAtlas(
        String shapeHash,
        PlasticMaterial material,
        PlasticTextureLayout layout,
        int rowCount
    ) {
        // 按满尺寸分配，未填充的行保持透明；calloc 保证未生成的行不会读到未初始化内存。
        NativeImage image = new NativeImage(
            layout.atlasWidth(),
            layout.atlasHeight() * rowCount,
            true
        );
        ResourceLocation id = AnvilcraftPlasticraft.of(
            "dynamic/molded_" + material.key() + "_" + shapeHash
        );
        DynamicTexture texture = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new ColorAtlas(id, texture, layout.atlasHeight(), rowCount);
    }

    private ResourceLocation createOversizedTexture(
        String shapeHash,
        List<PlasticSurface> surfaces,
        DyeColor color,
        PlasticMaterial material,
        ResourceInputs resourceInputs,
        int paletteRow
    ) {
        GeneratedPlasticTexture generated = this.generate(
            shapeHash,
            surfaces,
            material,
            resourceInputs,
            paletteRow
        );
        NativeImage image = new NativeImage(
            generated.layout().atlasWidth(),
            generated.layout().atlasHeight(),
            false
        );
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setPixelRGBA(x, y, PlasticTextureSpriteSource.swapRedBlue(generated.argbAt(x, y)));
            }
        }
        ResourceLocation id = AnvilcraftPlasticraft.of(
            "dynamic/molded_" + material.key() + "_" + shapeHash + "_" + color.getName()
        );
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    private GeneratedPlasticTexture generate(
        String shapeHash,
        List<PlasticSurface> surfaces,
        PlasticMaterial material,
        ResourceInputs resourceInputs,
        int paletteRow
    ) {
        PlasticTextureInput input = new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            shapeHash,
            surfaces,
            material.baseTexture(16).toString(),
            resourceInputs.baseHash,
            material.hasPalette() ? material.paletteTexture().toString() : "none",
            resourceInputs.paletteHash,
            paletteRow
        );
        return PlasticTextureCache.getOrGenerate(
            input,
            () -> {
                if (!resourceInputs.available(material)) return PlasticTextureGenerator.placeholder(input);
                return material.isTransparent()
                    ? PlasticTextureGenerator.generateTransparent(input, resourceInputs.bases, resourceInputs.palette)
                    : PlasticTextureGenerator.generate(input, resourceInputs.bases, resourceInputs.palette);
            }
        );
    }

    private ResourceInputs loadResources(PlasticMaterial material) {
        ResourceInputs cached = this.resources.get(material);
        if (cached != null) return cached;
        ResourceInputs loadedResources;
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            PlasticTextureResourceLoader.LoadedResources loaded = PlasticTextureResourceLoader.load(
                manager,
                material
            );
            loadedResources = new ResourceInputs(
                loaded.bases(),
                loaded.palette(),
                loaded.baseHash(),
                loaded.paletteHash()
            );
        } catch (IOException | IllegalArgumentException exception) {
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to load dynamic plastic texture resources; using placeholders",
                exception
            );
            loadedResources = ResourceInputs.unavailable();
        }
        this.resources.put(material, loadedResources);
        return loadedResources;
    }

    /**
     * 纹理引用：{@code colorRow} 是颜色在堆叠图集中的行号，{@code rowCount} 是堆叠总行数。
     *
     * <p>网格 UV 仍按单色图块的 0 至 1 计算，由渲染侧施加 {@code v → (v + colorRow) / rowCount}
     * 的行偏移，因此 {@code PreparedMesh} 缓存可以继续只按形状哈希索引。</p>
     */
    public record MoldedTextureRef(ResourceLocation texture, int colorRow, int rowCount) {
        public float mapV(float v) {
            return (v + this.colorRow) / this.rowCount;
        }
    }

    /** 一张 (形状, 材质) 图集，纵向堆叠全部颜色行，按需填充。 */
    private static final class ColorAtlas {
        private final ResourceLocation id;
        private final DynamicTexture texture;
        private final int rowHeight;
        private final boolean[] filledRows;

        private ColorAtlas(ResourceLocation id, DynamicTexture texture, int rowHeight, int rowCount) {
            this.id = id;
            this.texture = texture;
            this.rowHeight = rowHeight;
            this.filledRows = new boolean[rowCount];
        }

        private boolean hasRow(int row) {
            return this.filledRows[row];
        }

        private void fillRow(int row, Supplier<GeneratedPlasticTexture> generator) {
            if (this.filledRows[row]) return;
            NativeImage image = this.texture.getPixels();
            if (image == null) return;
            GeneratedPlasticTexture generated = generator.get();
            int baseY = row * this.rowHeight;
            for (int y = 0; y < this.rowHeight; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setPixelRGBA(
                        x,
                        baseY + y,
                        PlasticTextureSpriteSource.swapRedBlue(generated.argbAt(x, y))
                    );
                }
            }
            this.filledRows[row] = true;
            this.texture.upload();
        }
    }

    private record AtlasKey(
        String shapeHash,
        PlasticMaterial material,
        String baseHash,
        String paletteHash
    ) {
    }

    private record ResourceInputs(
        @Nullable PlasticBaseTextureSet bases,
        @Nullable PlasticColorPalette palette,
        String baseHash,
        String paletteHash
    ) {
        private static ResourceInputs unavailable() {
            return new ResourceInputs(null, null, "unavailable", "unavailable");
        }

        private boolean available(PlasticMaterial material) {
            return this.bases != null && (!material.hasPalette() || this.palette != null);
        }
    }
}
