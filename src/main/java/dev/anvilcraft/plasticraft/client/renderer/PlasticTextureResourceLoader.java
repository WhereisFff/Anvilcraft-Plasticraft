package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticBaseTextureSet;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureResourceException;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;

import java.io.IOException;

/** 集中加载并校验程序化塑料贴图使用的客户端资源。 */
final class PlasticTextureResourceLoader {
    static final ResourceLocation BASE_RESOURCE = PlasticMaterial.UNIVERSAL.baseTexture(16);
    /** 各行从上到下对应创造物品栏十六色顺序。 */
    static final ResourceLocation PALETTE_RESOURCE = PlasticMaterial.UNIVERSAL.paletteTexture();

    private PlasticTextureResourceLoader() {
    }

    static LoadedResources load(ResourceManager manager) throws IOException {
        return load(manager, PlasticMaterial.UNIVERSAL);
    }

    static LoadedResources load(ResourceManager manager, PlasticMaterial material) throws IOException {
        ResourceLocation base8 = material.baseTexture(8);
        ResourceLocation base16 = material.baseTexture(16);
        ResourceLocation base32 = material.baseTexture(32);
        ResourceLocation base48 = material.baseTexture(48);
        ResourceLocation paletteResource = material.paletteTexture();
        byte[] base8Bytes = PlasticTextureSpriteSource.readResource(manager, base8);
        byte[] base16Bytes = PlasticTextureSpriteSource.readResource(manager, base16);
        byte[] base32Bytes = PlasticTextureSpriteSource.readResource(manager, base32);
        byte[] base48Bytes = PlasticTextureSpriteSource.readResource(manager, base48);
        try (NativeImage base8Image = PlasticTextureSpriteSource.readImage(base8, base8Bytes);
             NativeImage base16Image = PlasticTextureSpriteSource.readImage(base16, base16Bytes);
             NativeImage base32Image = PlasticTextureSpriteSource.readImage(base32, base32Bytes);
             NativeImage base48Image = PlasticTextureSpriteSource.readImage(base48, base48Bytes)) {
            PlasticBaseTextureSet bases = PlasticBaseTextureSet.of(
                grayscale(base8Image),
                grayscale(base16Image),
                grayscale(base32Image),
                grayscale(base48Image)
            );
            PlasticColorPalette palette = null;
            byte[] paletteBytes = new byte[0];
            if (material.hasPalette()) {
                paletteBytes = PlasticTextureSpriteSource.readResource(manager, paletteResource);
                palette = palette(material, paletteResource, paletteBytes);
            }
            return new LoadedResources(
                bases,
                palette,
                PlasticTextureInput.computeResourceSetHash(
                    base8Bytes,
                    base16Bytes,
                    base32Bytes,
                    base48Bytes
                ),
                material.hasPalette() ? PlasticTextureInput.computeResourceHash(paletteBytes) : "none"
            );
        }
    }

    static PlasticColorPalette loadPalette(ResourceManager manager, PlasticMaterial material) throws IOException {
        ResourceLocation paletteResource = material.paletteTexture();
        return palette(
            material,
            paletteResource,
            PlasticTextureSpriteSource.readResource(manager, paletteResource)
        );
    }

    private static PlasticColorPalette palette(
        PlasticMaterial material,
        ResourceLocation paletteResource,
        byte[] paletteBytes
    ) throws IOException {
        try (NativeImage paletteImage = PlasticTextureSpriteSource.readImage(paletteResource, paletteBytes)) {
            if (paletteImage.getHeight() != DyeColor.values().length) {
                throw new PlasticTextureResourceException(
                    material.key() + " palette must contain one row for every DyeColor"
                );
            }
            return PlasticColorPalette.fromArgb(
                paletteImage.getHeight(),
                paletteImage.getWidth(),
                PlasticTextureSpriteSource.toArgbPixels(paletteImage)
            );
        }
    }

    private static PlasticGrayscaleImage grayscale(NativeImage image) {
        return PlasticGrayscaleImage.fromArgb(
            image.getWidth(),
            image.getHeight(),
            PlasticTextureSpriteSource.toArgbPixels(image)
        );
    }

    record LoadedResources(
        PlasticBaseTextureSet bases,
        PlasticColorPalette palette,
        String baseHash,
        String paletteHash
    ) {
    }
}
