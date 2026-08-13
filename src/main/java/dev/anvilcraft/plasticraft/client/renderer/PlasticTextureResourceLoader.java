package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.PlasticBaseTextureSet;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureResourceException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;

import java.io.IOException;

/** 集中加载并校验程序化塑料贴图使用的客户端资源。 */
final class PlasticTextureResourceLoader {
    static final ResourceLocation BASE_RESOURCE = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_base_16x16.png"
    );
    static final ResourceLocation PALETTE_RESOURCE = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_palette.png"
    );

    private static final ResourceLocation BASE_RESOURCE_8 = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_base_8x8.png"
    );
    private static final ResourceLocation BASE_RESOURCE_32 = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_base_32x32.png"
    );
    private static final ResourceLocation BASE_RESOURCE_48 = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_base_48x48.png"
    );

    private PlasticTextureResourceLoader() {
    }

    static LoadedResources load(ResourceManager manager) throws IOException {
        byte[] base8Bytes = PlasticTextureSpriteSource.readResource(manager, BASE_RESOURCE_8);
        byte[] base16Bytes = PlasticTextureSpriteSource.readResource(manager, BASE_RESOURCE);
        byte[] base32Bytes = PlasticTextureSpriteSource.readResource(manager, BASE_RESOURCE_32);
        byte[] base48Bytes = PlasticTextureSpriteSource.readResource(manager, BASE_RESOURCE_48);
        byte[] paletteBytes = PlasticTextureSpriteSource.readResource(manager, PALETTE_RESOURCE);
        try (NativeImage base8Image = PlasticTextureSpriteSource.readImage(BASE_RESOURCE_8, base8Bytes);
             NativeImage base16Image = PlasticTextureSpriteSource.readImage(BASE_RESOURCE, base16Bytes);
             NativeImage base32Image = PlasticTextureSpriteSource.readImage(BASE_RESOURCE_32, base32Bytes);
             NativeImage base48Image = PlasticTextureSpriteSource.readImage(BASE_RESOURCE_48, base48Bytes);
             NativeImage paletteImage = PlasticTextureSpriteSource.readImage(PALETTE_RESOURCE, paletteBytes)) {
            if (paletteImage.getHeight() != DyeColor.values().length) {
                throw new PlasticTextureResourceException(
                    "Universal plastic palette must contain one row for every DyeColor"
                );
            }
            PlasticBaseTextureSet bases = PlasticBaseTextureSet.of(
                grayscale(base8Image),
                grayscale(base16Image),
                grayscale(base32Image),
                grayscale(base48Image)
            );
            PlasticColorPalette palette = PlasticColorPalette.fromArgb(
                paletteImage.getHeight(),
                paletteImage.getWidth(),
                PlasticTextureSpriteSource.toArgbPixels(paletteImage)
            );
            return new LoadedResources(
                bases,
                palette,
                PlasticTextureInput.computeResourceSetHash(
                    base8Bytes,
                    base16Bytes,
                    base32Bytes,
                    base48Bytes
                ),
                PlasticTextureInput.computeResourceHash(paletteBytes)
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
