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
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.client.event.RegisterSpriteSourcesEvent;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/** 在方块图集准备阶段生成各颜色通用塑料精灵。 */
public final class PlasticTextureSpriteSource implements SpriteSource {
    public static final PlasticTextureSpriteSource INSTANCE = new PlasticTextureSpriteSource();
    public static final MapCodec<PlasticTextureSpriteSource> CODEC = MapCodec.unit(INSTANCE);

    private static final Identifier TYPE_ID = AnvilcraftPlasticraft.of("generated_plastic");

    private PlasticTextureSpriteSource() {
    }

    public static void registerType(RegisterSpriteSourcesEvent event) {
        event.register(TYPE_ID, CODEC);
    }

    public static Identifier sprite(DyeColor color) {
        return sprite(PlasticMaterial.UNIVERSAL, color);
    }

    public static Identifier sprite(PlasticMaterial material, DyeColor color) {
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
    public MapCodec<PlasticTextureSpriteSource> codec() {
        return CODEC;
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
        Identifier spriteId = sprite(material, color);
        output.add(spriteId, loader -> createSprite(spriteId, generated));
    }

    private static SpriteContents createSprite(Identifier id, GeneratedPlasticTexture generated) {
        int width = generated.layout().atlasWidth();
        int height = generated.layout().atlasHeight();
        NativeImage image = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    image.setPixel(x, y, generated.argbAt(x, y));
                }
            }
            return new SpriteContents(id, new FrameSize(width, height), image);
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

    static byte[] readResource(ResourceManager manager, Identifier id) throws IOException {
        Resource resource = manager.getResource(id).orElseThrow(() ->
            new PlasticTextureResourceException("Missing plastic texture resource " + id)
        );
        try (var stream = resource.open()) {
            return stream.readAllBytes();
        }
    }

    static NativeImage readImage(Identifier id, byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException exception) {
            throw new PlasticTextureResourceException("Invalid plastic texture image " + id, exception);
        }
    }

    static int[] toArgbPixels(NativeImage image) {
        return image.getPixels();
    }
}
