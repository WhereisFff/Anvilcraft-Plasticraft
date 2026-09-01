package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.GeneratedPlasticTexture;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureResourceException;
import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import dev.anvilcraft.plasticraft.item.CreativeColorVariantItem;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
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
    private static SpriteSourceType sourceType;

    private PlasticTextureSpriteSource() {
    }

    public static void registerType(RegisterSpriteSourceTypesEvent event) {
        sourceType = new SpriteSourceType(CODEC);
        event.register(TYPE_ID, sourceType);
    }

    public static ResourceLocation sprite(DyeColor color) {
        return sprite(PlasticMaterial.UNIVERSAL, color);
    }

    public static ResourceLocation sprite(PlasticMaterial material, DyeColor color) {
        return material.sprite(color);
    }

    @Override
    public void run(ResourceManager resourceManager, Output output) {
        PlasticTextureCache.clear();
        for (PlasticMaterial material : PlasticMaterial.values()) {
            try {
                PlasticTextureResourceLoader.LoadedResources resources = PlasticTextureResourceLoader.load(
                    resourceManager,
                    material
                );
                addGeneratedSprites(output, material, resources);
            } catch (IOException | IllegalArgumentException exception) {
                AnvilcraftPlasticraft.LOGGER.error(
                    "Unable to generate {} textures; using safe placeholders: {}",
                    material.key(),
                    exception.getMessage(),
                    exception
                );
                addPlaceholderSprites(output, material);
            }
        }
    }

    @Override
    public SpriteSourceType type() {
        return Objects.requireNonNull(sourceType, "Plastic sprite source type is not registered");
    }

    private static void addGeneratedSprites(
        Output output,
        PlasticMaterial material,
        PlasticTextureResourceLoader.LoadedResources resources
    ) {
        if (material.isTransparent()) {
            PlasticTextureInput input = input(material, resources.baseHash(), resources.paletteHash(), DyeColor.WHITE);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.generateTransparent(
                    input,
                    resources.bases(),
                    resources.palette()
                )
            );
            addSprite(output, material, DyeColor.WHITE, generated);
            for (DyeColor color : DyeColor.values()) {
                if (color == DyeColor.WHITE) continue;
                PlasticTextureInput coloredInput = input(material, resources.baseHash(), resources.paletteHash(), color);
                GeneratedPlasticTexture colored = PlasticTextureCache.getOrGenerate(
                    coloredInput,
                    () -> PlasticTextureGenerator.generateTransparent(
                        coloredInput,
                        resources.bases(),
                        resources.palette()
                    )
                );
                addSprite(output, material, color, colored);
            }
            return;
        }
        for (DyeColor color : DyeColor.values()) {
            PlasticTextureInput input = input(material, resources.baseHash(), resources.paletteHash(), color);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.generate(input, resources.bases(), resources.palette())
            );
            addSprite(output, material, color, generated);
        }
    }

    private static void addPlaceholderSprites(Output output, PlasticMaterial material) {
        if (material.isTransparent()) {
            PlasticTextureInput input = input(material, "unavailable", "none", DyeColor.WHITE);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.placeholder(input)
            );
            addSprite(output, material, DyeColor.WHITE, generated);
            for (DyeColor color : DyeColor.values()) {
                if (color == DyeColor.WHITE) continue;
                PlasticTextureInput coloredInput = input(material, "unavailable", "unavailable", color);
                GeneratedPlasticTexture colored = PlasticTextureCache.getOrGenerate(
                    coloredInput,
                    () -> PlasticTextureGenerator.placeholder(coloredInput)
                );
                addSprite(output, material, color, colored);
            }
            return;
        }
        for (DyeColor color : DyeColor.values()) {
            PlasticTextureInput input = input(material, "unavailable", "unavailable", color);
            GeneratedPlasticTexture generated = PlasticTextureCache.getOrGenerate(
                input,
                () -> PlasticTextureGenerator.placeholder(input)
            );
            addSprite(output, material, color, generated);
        }
    }

    private static void addSprite(
        Output output,
        PlasticMaterial material,
        DyeColor color,
        GeneratedPlasticTexture generated
    ) {
        ResourceLocation spriteId = sprite(material, color);
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

    private static PlasticTextureInput input(
        PlasticMaterial material,
        String baseHash,
        String paletteHash,
        DyeColor color
    ) {
        return new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            UniversalPlasticShape.SHAPE_HASH,
            UniversalPlasticShape.SURFACES,
            material.baseTexture(16).toString(),
            baseHash,
            material.hasPalette() ? material.paletteTexture().toString() : "none",
            paletteHash,
            material.hasPalette() ? CreativeColorVariantItem.paletteRow(color) : 0
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
