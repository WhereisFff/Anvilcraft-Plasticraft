package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.GeneratedPlasticTexture;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureResourceException;
import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceMetadata;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

/** 在方块图集准备阶段生成各颜色通用塑料精灵。 */
public final class PlasticTextureSpriteSource implements SpriteSource {
    public static final PlasticTextureSpriteSource INSTANCE = new PlasticTextureSpriteSource();
    public static final MapCodec<PlasticTextureSpriteSource> CODEC = MapCodec.unit(INSTANCE);

    private static final ResourceLocation TYPE_ID = AnvilcraftPlasticraft.of("generated_plastic");
    static final ResourceLocation BASE_RESOURCE = AnvilcraftPlasticraft.of(
        "textures/palette/plastic_base.png"
    );
    static final ResourceLocation PALETTE_RESOURCE = AnvilcraftPlasticraft.of(
        "textures/palette/universal_plastic_palette.png"
    );
    private static SpriteSourceType sourceType;

    private PlasticTextureSpriteSource() {
    }

    public static void registerType(RegisterSpriteSourceTypesEvent event) {
        sourceType = new SpriteSourceType(CODEC);
        event.register(TYPE_ID, sourceType);
    }

    public static ResourceLocation sprite(DyeColor color) {
        return UniversalPlasticShape.sprite(color);
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        PlasticTextureCache.clear();
        try {
            byte[] baseBytes = readResource(resourceManager, BASE_RESOURCE);
            byte[] paletteBytes = readResource(resourceManager, PALETTE_RESOURCE);
            try (NativeImage baseImage = readImage(BASE_RESOURCE, baseBytes);
                 NativeImage paletteImage = readImage(PALETTE_RESOURCE, paletteBytes)) {
                PlasticGrayscaleImage base = PlasticGrayscaleImage.fromArgb(
                    baseImage.getWidth(),
                    baseImage.getHeight(),
                    toArgbPixels(baseImage)
                );
                if (paletteImage.getHeight() != DyeColor.values().length) {
                    throw new PlasticTextureResourceException(
                        "Universal plastic palette must contain one row for every DyeColor"
                    );
                }
                PlasticColorPalette palette = PlasticColorPalette.fromArgb(
                    paletteImage.getHeight(),
                    paletteImage.getWidth(),
                    toArgbPixels(paletteImage)
                );
                addGeneratedSprites(output, baseBytes, paletteBytes, base, palette);
            }
        } catch (IOException | IllegalArgumentException exception) {
            AnvilcraftPlasticraft.LOGGER.error(
                "Unable to generate universal plastic textures; using safe placeholders: {}",
                exception.getMessage(),
                exception
            );
            addPlaceholderSprites(output);
        }
    }

    @Override
    public SpriteSourceType type() {
        return Objects.requireNonNull(sourceType, "Plastic sprite source type is not registered");
    }

    private static void addGeneratedSprites(
        Output output,
        byte[] baseBytes,
        byte[] paletteBytes,
        PlasticGrayscaleImage base,
        PlasticColorPalette palette
    ) {
        String baseHash = PlasticTextureInput.computeResourceHash(baseBytes);
        String paletteHash = PlasticTextureInput.computeResourceHash(paletteBytes);
        for (DyeColor color : DyeColor.values()) {
            PlasticTextureInput input = input(baseHash, paletteHash, color);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.generate(input, base, palette)
            );
            addSprite(output, color, generated);
        }
    }

    private static void addPlaceholderSprites(Output output) {
        for (DyeColor color : DyeColor.values()) {
            PlasticTextureInput input = input("unavailable", "unavailable", color);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.placeholder(input)
            );
            addSprite(output, color, generated);
        }
    }

    private static void addSprite(Output output, DyeColor color, GeneratedPlasticTexture generated) {
        ResourceLocation spriteId = sprite(color);
        output.add(spriteId, loader -> createSprite(spriteId, generated));
    }

    private static SpriteContents createSprite(ResourceLocation id, GeneratedPlasticTexture generated) {
        int width = generated.layout().atlasWidth();
        int height = generated.layout().atlasHeight();
        NativeImage image = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixelRGBA(x, y, swapRedBlue(generated.argbAt(x, y)));
                }
            }
            return new SpriteContents(id, new FrameSize(width, height), image, ResourceMetadata.EMPTY);
        } catch (RuntimeException exception) {
            image.close();
            throw exception;
        }
    }

    private static PlasticTextureInput input(String baseHash, String paletteHash, DyeColor color) {
        return new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            UniversalPlasticShape.SHAPE_HASH,
            UniversalPlasticShape.SURFACES,
            BASE_RESOURCE.toString(),
            baseHash,
            PALETTE_RESOURCE.toString(),
            paletteHash,
            color.getId()
        );
    }

    static byte[] readResource(ResourceManager manager, ResourceLocation id) throws IOException {
        Resource resource = manager.getResource(id).orElseThrow(() ->
            new PlasticTextureResourceException("Missing plastic texture resource " + id)
        );
        try (var stream = resource.open()) {
            return stream.readAllBytes();
        }
    }

    static NativeImage readImage(ResourceLocation id, byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new PlasticTextureResourceException("Invalid plastic texture image " + id, exception);
        }
    }

    static int[] toArgbPixels(NativeImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[y * image.getWidth() + x] = swapRedBlue(image.getPixelRGBA(x, y));
            }
        }
        return pixels;
    }

    /** NativeImage 使用 ABGR，而公共生成器固定使用 ARGB；交换红蓝通道即可双向转换。 */
    static int swapRedBlue(int color) {
        return color & 0xFF00FF00 | color >> 16 & 0xFF | (color & 0xFF) << 16;
    }
}
