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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 运行时制品共享的 CPU 生成结果与 GPU 动态纹理生命周期。 */
public final class DynamicPlasticTextureManager implements ResourceManagerReloadListener {
    public static final DynamicPlasticTextureManager INSTANCE = new DynamicPlasticTextureManager();

    private final Map<TextureKey, ResourceLocation> textures = new HashMap<>();
    private final Map<PlasticMaterial, ResourceInputs> resources = new HashMap<>();

    private DynamicPlasticTextureManager() {
    }

    public synchronized ResourceLocation texture(MoldedPlasticData data) {
        List<PlasticSurface> surfaces = data.plasticSurfaces();
        String shapeHash = data.shapeHash();
        DyeColor color = PlasticMeltColor.get(data.material());
        PlasticMaterial material = PlasticMaterial.fromMelt(data.material()).orElse(PlasticMaterial.UNIVERSAL);
        ResourceInputs currentResources = this.loadResources(material);
        TextureKey key = new TextureKey(
            shapeHash,
            material,
            currentResources.baseHash,
            currentResources.paletteHash,
            color
        );
        return this.textures.computeIfAbsent(key, ignored -> this.createTexture(
            shapeHash,
            surfaces,
            color,
            material,
            currentResources
        ));
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.clear();
    }

    public synchronized void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        for (ResourceLocation texture : this.textures.values()) {
            minecraft.getTextureManager().release(texture);
        }
        this.textures.clear();
        this.resources.clear();
    }

    private ResourceLocation createTexture(
        String shapeHash,
        List<PlasticSurface> surfaces,
        DyeColor color,
        PlasticMaterial material,
        ResourceInputs resourceInputs
    ) {
        PlasticTextureInput input = new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            shapeHash,
            surfaces,
            material.baseTexture(16).toString(),
            resourceInputs.baseHash,
            material.hasPalette() ? material.paletteTexture().toString() : "none",
            resourceInputs.paletteHash,
            material.hasPalette() ? CreativeColorVariantItem.paletteRow(color) : 0
        );
        GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
            input,
            () -> {
                if (!resourceInputs.available(material)) return PlasticTextureGenerator.placeholder(input);
                return material.isTransparent()
                    ? PlasticTextureGenerator.generateTransparent(input, resourceInputs.bases, resourceInputs.palette)
                    : PlasticTextureGenerator.generate(input, resourceInputs.bases, resourceInputs.palette);
            }
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

    private record TextureKey(
        String shapeHash,
        PlasticMaterial material,
        String baseHash,
        String paletteHash,
        DyeColor color
    ) {
    }

    private record ResourceInputs(
        PlasticBaseTextureSet bases,
        PlasticColorPalette palette,
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
