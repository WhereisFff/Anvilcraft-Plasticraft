package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.GeneratedPlasticTexture;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
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
    private ResourceInputs resources;

    private DynamicPlasticTextureManager() {
    }

    public synchronized ResourceLocation texture(MoldedPlasticData data) {
        List<PlasticSurface> surfaces = data.plasticSurfaces();
        String shapeHash = data.shapeHash();
        DyeColor color = PlasticMeltColor.get(data.material());
        ResourceInputs currentResources = this.loadResources();
        TextureKey key = new TextureKey(
            shapeHash,
            currentResources.baseHash,
            currentResources.paletteHash,
            color
        );
        return this.textures.computeIfAbsent(key, ignored -> this.createTexture(
            shapeHash,
            surfaces,
            color,
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
        this.resources = null;
    }

    private ResourceLocation createTexture(
        String shapeHash,
        List<PlasticSurface> surfaces,
        DyeColor color,
        ResourceInputs resourceInputs
    ) {
        PlasticTextureInput input = new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            shapeHash,
            surfaces,
            PlasticTextureSpriteSource.BASE_RESOURCE.toString(),
            resourceInputs.baseHash,
            PlasticTextureSpriteSource.PALETTE_RESOURCE.toString(),
            resourceInputs.paletteHash,
            color.getId()
        );
        GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
            input,
            () -> resourceInputs.available()
                ? PlasticTextureGenerator.generate(input, resourceInputs.base, resourceInputs.palette)
                : PlasticTextureGenerator.placeholder(input)
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
            "dynamic/molded_" + shapeHash + "_" + color.getName()
        );
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        return id;
    }

    private ResourceInputs loadResources() {
        if (this.resources != null) return this.resources;
        try {
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            byte[] baseBytes = PlasticTextureSpriteSource.readResource(
                manager,
                PlasticTextureSpriteSource.BASE_RESOURCE
            );
            byte[] paletteBytes = PlasticTextureSpriteSource.readResource(
                manager,
                PlasticTextureSpriteSource.PALETTE_RESOURCE
            );
            try (NativeImage baseImage = PlasticTextureSpriteSource.readImage(
                PlasticTextureSpriteSource.BASE_RESOURCE,
                baseBytes
            ); NativeImage paletteImage = PlasticTextureSpriteSource.readImage(
                PlasticTextureSpriteSource.PALETTE_RESOURCE,
                paletteBytes
            )) {
                this.resources = new ResourceInputs(
                    PlasticGrayscaleImage.fromArgb(
                        baseImage.getWidth(),
                        baseImage.getHeight(),
                        PlasticTextureSpriteSource.toArgbPixels(baseImage)
                    ),
                    PlasticColorPalette.fromArgb(
                        paletteImage.getHeight(),
                        paletteImage.getWidth(),
                        PlasticTextureSpriteSource.toArgbPixels(paletteImage)
                    ),
                    PlasticTextureInput.computeResourceHash(baseBytes),
                    PlasticTextureInput.computeResourceHash(paletteBytes)
                );
            }
        } catch (IOException | IllegalArgumentException exception) {
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to load dynamic plastic texture resources; using placeholders",
                exception
            );
            this.resources = ResourceInputs.unavailable();
        }
        return this.resources;
    }

    private record TextureKey(String shapeHash, String baseHash, String paletteHash, DyeColor color) {
    }

    private record ResourceInputs(
        PlasticGrayscaleImage base,
        PlasticColorPalette palette,
        String baseHash,
        String paletteHash
    ) {
        private static ResourceInputs unavailable() {
            return new ResourceInputs(null, null, "unavailable", "unavailable");
        }

        private boolean available() {
            return this.base != null && this.palette != null;
        }
    }
}
