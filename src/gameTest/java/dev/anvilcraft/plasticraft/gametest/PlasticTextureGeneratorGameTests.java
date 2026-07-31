package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.GeneratedPlasticTexture;
import dev.anvilcraft.plasticraft.api.texture.PlasticColorPalette;
import dev.anvilcraft.plasticraft.api.texture.PlasticGrayscaleImage;
import dev.anvilcraft.plasticraft.api.texture.PlasticSurface;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureGenerator;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureInput;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureResourceException;
import dev.anvilcraft.plasticraft.block.UniversalPlasticShape;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.DyeColor;
import net.neoforged.fml.ModList;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/** TODO-00 公共贴图生成接口及真实资源的服务端回归测试。 */
public final class PlasticTextureGeneratorGameTests {
    private static final String BASE_PATH =
        "assets/anvilcraftplasticraft/textures/palette/plastic_base.png";
    private static final String PALETTE_PATH =
        "assets/anvilcraftplasticraft/textures/palette/universal_plastic_palette.png";

    private PlasticTextureGeneratorGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Plastic base uses absolute brightness and the palette exposes sixteen ordered levels")
    static void realResourcesExposeOrderedLevels(ExtendedGameTestHelper helper) {
        ImageData baseData = readImage(BASE_PATH);
        int[] basePixels = baseData.pixels();
        PlasticGrayscaleImage base = PlasticGrayscaleImage.fromArgb(
            baseData.width(),
            baseData.height(),
            basePixels
        );
        int[] expectedBaseLevels = Arrays.stream(basePixels)
            .map(pixel -> pixel & 0xFF)
            .distinct()
            .sorted()
            .toArray();
        check(
            base.levelCount() >= 1 && base.levelCount() <= PlasticGrayscaleImage.MAX_LEVELS,
            "plastic_base.png contains an unsupported number of source levels"
        );
        check(
            Arrays.equals(base.levels(), expectedBaseLevels),
            "grayscale loader did not preserve every level from plastic_base.png"
        );
        // 基础图只使用较亮的局部灰度范围时，最低值仍必须保持较亮，不能按资源内部排名压到第 0 级。
        for (int y = 0; y < baseData.height(); y++) {
            for (int x = 0; x < baseData.width(); x++) {
                int gray = basePixels[y * baseData.width() + x] & 0xFF;
                int expectedLevel = Math.round((float) gray * (PlasticGrayscaleImage.MAX_LEVELS - 1) / 255.0F);
                check(
                    base.normalizedLevelAt(x, y) == expectedLevel,
                    "plastic_base.png gray " + gray + " was not mapped by absolute brightness"
                );
            }
        }

        ImageData paletteData = readImage(PALETTE_PATH);
        PlasticColorPalette palette = PlasticColorPalette.fromArgb(
            paletteData.height(),
            paletteData.width(),
            paletteData.pixels()
        );
        check(palette.rows() == DyeColor.values().length, "palette does not contain one row per DyeColor");
        check(palette.levels() == 16, "palette does not expose all sixteen levels");
        for (int row = 0; row < palette.rows(); row++) {
            Set<Integer> colors = new HashSet<>();
            long previousLuminance = -1L;
            for (int level = 0; level < 16; level++) {
                int color = palette.colorAt(row, level);
                check(colors.add(color), "palette row " + row + " repeats level " + level);
                long luminance = luminance(color);
                check(luminance > previousLuminance, "palette row " + row + " is not strictly ordered");
                previousLuminance = luminance;
            }
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Synthetic one-to-sixteen-level bases load and a seventeenth level is rejected")
    static void grayscaleLevelLimits(ExtendedGameTestHelper helper) {
        for (int levels = 1; levels <= 16; levels++) {
            PlasticGrayscaleImage image = PlasticGrayscaleImage.fromArgb(
                levels,
                1,
                orderedGrayscalePixels(levels)
            );
            check(image.levelCount() == levels, "synthetic " + levels + "-level base lost a level");
            if (levels == 1) {
                check(image.normalizedLevelAt(0, 0) == 8, "single synthetic level was remapped incorrectly");
            } else {
                check(image.normalizedLevelAt(0, 0) == 0, "darkest synthetic level was remapped incorrectly");
                check(
                    image.normalizedLevelAt(levels - 1, 0) == 15,
                    "brightest synthetic level was remapped incorrectly"
                );
            }
        }

        expectResourceError(() -> PlasticGrayscaleImage.fromArgb(17, 1, orderedGrayscalePixels(17)));
        expectResourceError(() -> PlasticGrayscaleImage.fromArgb(1, 1, new int[]{0xFFFF0000}));
        expectResourceError(() -> PlasticGrayscaleImage.fromArgb(1, 1, new int[]{0x00FFFFFF}));
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Base edits change generated bytes and short surfaces preserve the complete border")
    static void baseEditsAndShortFacesPreserveWholePattern(ExtendedGameTestHelper helper) {
        PlasticSurface shortSurface = new PlasticSurface(
            "short_face",
            List.of(point(0, 0, 0), point(4, 0, 0), point(4, 3, 0), point(0, 3, 0)),
            point(0, 0, 1),
            4,
            3,
            false,
            0,
            0
        );
        PlasticGrayscaleImage framed = PlasticGrayscaleImage.fromArgb(4, 4, framedPixels(false));
        PlasticGrayscaleImage inverted = PlasticGrayscaleImage.fromArgb(4, 4, framedPixels(true));
        PlasticColorPalette palette = PlasticColorPalette.fromArgb(1, 16, orderedPalettePixels());
        PlasticTextureInput input = textureInput(List.of(shortSurface), 0);

        GeneratedPlasticTexture framedTexture = PlasticTextureGenerator.generate(input, framed, palette);
        GeneratedPlasticTexture invertedTexture = PlasticTextureGenerator.generate(input, inverted, palette);
        check(
            !Arrays.equals(framedTexture.toRgbaBytes(), invertedTexture.toRgbaBytes()),
            "editing the plastic base pattern did not change generated texture bytes"
        );

        PlasticTextureLayout.UvRegion region = framedTexture.layout().regions().get(shortSurface.id());
        int topBorder = framedTexture.grayscaleLevelAt(region.x() + 1, region.y());
        int center = framedTexture.grayscaleLevelAt(region.x() + 1, region.y() + 1);
        int bottomBorder = framedTexture.grayscaleLevelAt(region.x() + 1, region.y() + 2);
        check(topBorder == bottomBorder, "short surface cropped one edge of the base texture border");
        check(topBorder < center, "short surface no longer preserves its dark border around the face");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Concave, disconnected, rotated, and double-sided surfaces share deterministic UVs")
    static void arbitrarySurfaceLayoutIsDeterministic(ExtendedGameTestHelper helper) {
        double diagonal = Math.sqrt(0.5D);
        PlasticSurface concave = new PlasticSurface(
            "concave",
            List.of(
                point(0.0D, 0.0D, 0.0D),
                point(12.0D, 0.0D, 0.0D),
                point(12.0D, 4.0D, 0.0D),
                point(4.0D, 4.0D, 0.0D),
                point(4.0D, 12.0D, 0.0D),
                point(0.0D, 12.0D, 0.0D)
            ),
            point(0.0D, 0.0D, 1.0D),
            12,
            12,
            false,
            1,
            2
        );
        PlasticSurface rotated = new PlasticSurface(
            "rotated_triangle",
            List.of(
                point(20.0D, 0.0D, 0.0D),
                point(24.0D, 4.0D, -4.0D),
                point(20.0D, 8.0D, -8.0D)
            ),
            point(0.0D, diagonal, diagonal),
            8,
            10,
            true,
            0,
            0
        );
        PlasticSurface disconnected = new PlasticSurface(
            "disconnected",
            List.of(
                point(-8.0D, 0.0D, 3.0D),
                point(-4.0D, 0.0D, 3.0D),
                point(-4.0D, 4.0D, 3.0D),
                point(-8.0D, 4.0D, 3.0D)
            ),
            point(0.0D, 0.0D, 1.0D),
            4,
            4,
            false,
            3,
            1
        );
        List<PlasticSurface> firstOrder = List.of(concave, rotated, disconnected);
        List<PlasticSurface> secondOrder = List.of(disconnected, concave, rotated);

        PlasticTextureLayout firstLayout = PlasticTextureGenerator.layout(firstOrder);
        PlasticTextureLayout secondLayout = PlasticTextureGenerator.layout(secondOrder);
        check(firstLayout.equals(secondLayout), "surface input order changed the UV atlas");
        check(
            PlasticTextureInput.computeShapeHash(firstOrder)
                .equals(PlasticTextureInput.computeShapeHash(secondOrder)),
            "surface input order changed the stable shape hash"
        );
        expectIllegalArgument(() -> new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            "mismatched-shape-hash",
            firstOrder,
            "test:base",
            "base-hash",
            "test:palette",
            "palette-hash",
            0
        ));
        check(
            firstLayout.regions().get("rotated_triangle").doubleSided(),
            "double-sided surface flag was lost from the UV mapping"
        );

        PlasticGrayscaleImage base = PlasticGrayscaleImage.fromArgb(16, 1, orderedGrayscalePixels(16));
        PlasticColorPalette palette = PlasticColorPalette.fromArgb(1, 16, orderedPalettePixels());
        PlasticTextureInput input = textureInput(firstOrder, 0);
        GeneratedPlasticTexture first = PlasticTextureGenerator.generate(input, base, palette);
        GeneratedPlasticTexture second = PlasticTextureGenerator.generate(input, base, palette);
        check(first.layout().equals(second.layout()), "repeated generation changed the UV layout");
        check(Arrays.equals(first.toRgbaBytes(), second.toRgbaBytes()), "repeated generation changed atlas bytes");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Plastic generation reaches all levels and universal plastic uses all sixteen colours")
    static void generatorUsesEveryPaletteLevelAndAllColors(ExtendedGameTestHelper helper) {
        ImageData baseData = readImage(BASE_PATH);
        ImageData paletteData = readImage(PALETTE_PATH);
        PlasticGrayscaleImage base = PlasticGrayscaleImage.fromArgb(
            baseData.width(),
            baseData.height(),
            baseData.pixels()
        );
        PlasticColorPalette palette = PlasticColorPalette.fromArgb(
            paletteData.height(),
            paletteData.width(),
            paletteData.pixels()
        );

        PlasticSurface coverageSurface = new PlasticSurface(
            "coverage",
            List.of(point(0, 0, 0), point(10, 0, 0), point(10, 10, 0), point(0, 10, 0)),
            point(0, 0, 1),
            10,
            10,
            false,
            0,
            0
        );
        PlasticGrayscaleImage coverageBase = PlasticGrayscaleImage.fromArgb(10, 10, coveragePixels());
        GeneratedPlasticTexture coverage = PlasticTextureGenerator.generate(
            textureInput(List.of(coverageSurface), 0),
            coverageBase,
            palette
        );
        Set<Integer> usedLevels = new HashSet<>();
        collectLevels(coverage, usedLevels);
        check(usedLevels.size() == 16, "synthetic plastic texture did not address all sixteen palette levels");

        Set<String> colorHashes = new HashSet<>();
        for (DyeColor color : DyeColor.values()) {
            PlasticTextureInput input = textureInput(UniversalPlasticShape.SURFACES, color.getId());
            GeneratedPlasticTexture generated = PlasticTextureGenerator.generate(input, base, palette);
            colorHashes.add(PlasticTextureInput.computeResourceHash(generated.toRgbaBytes()));
        }
        check(colorHashes.size() == 16, "multiple DyeColor rows produced identical generated atlases");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Plastic CPU texture cache reuses entries and releases them on clear")
    static void cpuCacheLifecycle(ExtendedGameTestHelper helper) {
        PlasticTextureInput input = textureInput(UniversalPlasticShape.SURFACES, DyeColor.CYAN.getId());
        GeneratedPlasticTexture placeholder = PlasticTextureGenerator.placeholder(input);
        AtomicInteger generations = new AtomicInteger();
        PlasticTextureCache.clear();
        GeneratedPlasticTexture first = PlasticTextureCache.getOrGenerate(input, () -> {
            generations.incrementAndGet();
            return placeholder;
        });
        GeneratedPlasticTexture second = PlasticTextureCache.getOrGenerate(input, () -> {
            generations.incrementAndGet();
            return PlasticTextureGenerator.placeholder(input);
        });
        check(first == second, "cache did not reuse the generated CPU texture");
        check(generations.get() == 1 && PlasticTextureCache.size() == 1, "cache generated a duplicate entry");

        PlasticTextureInput editedInput = textureInput(
            UniversalPlasticShape.SURFACES,
            DyeColor.CYAN.getId(),
            "edited-base-hash"
        );
        GeneratedPlasticTexture edited = PlasticTextureCache.getOrGenerate(editedInput, () -> {
            generations.incrementAndGet();
            return PlasticTextureGenerator.placeholder(editedInput);
        });
        check(edited != first, "edited base resource hash reused the previous cache entry");
        check(generations.get() == 2 && PlasticTextureCache.size() == 2, "edited base was not cached separately");

        PlasticTextureCache.clear();
        check(PlasticTextureCache.size() == 0, "cache clear retained CPU textures");
        PlasticTextureCache.getOrGenerate(input, () -> {
            generations.incrementAndGet();
            return placeholder;
        });
        check(generations.get() == 3, "cache clear did not force regeneration");
        PlasticTextureCache.clear();
        helper.succeed();
    }

    private static PlasticTextureInput textureInput(List<PlasticSurface> surfaces, int paletteRow) {
        return textureInput(surfaces, paletteRow, "base-hash");
    }

    private static PlasticTextureInput textureInput(
        List<PlasticSurface> surfaces,
        int paletteRow,
        String baseHash
    ) {
        return new PlasticTextureInput(
            PlasticTextureGenerator.VERSION,
            PlasticTextureInput.computeShapeHash(surfaces),
            surfaces,
            "test:base",
            baseHash,
            "test:palette",
            "palette-hash",
            paletteRow
        );
    }

    private static PlasticSurface.Point point(double x, double y, double z) {
        return new PlasticSurface.Point(x, y, z);
    }

    private static int[] orderedGrayscalePixels(int levels) {
        int[] pixels = new int[levels];
        for (int index = 0; index < levels; index++) {
            // 多级样本覆盖完整绝对亮度范围；单级样本固定在中灰，以验证它不会被特殊拉伸。
            int gray = levels == 1
                ? 128
                : Math.round((float) index * 255.0F / (levels - 1));
            pixels[index] = 0xFF000000 | gray << 16 | gray << 8 | gray;
        }
        return pixels;
    }

    private static int[] orderedPalettePixels() {
        int[] pixels = new int[16];
        for (int level = 0; level < pixels.length; level++) {
            int gray = level * 17;
            pixels[level] = 0xFF000000 | gray << 16 | gray << 8 | gray;
        }
        return pixels;
    }

    private static int[] framedPixels(boolean inverted) {
        int[] pixels = new int[16];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                boolean border = x == 0 || x == 3 || y == 0 || y == 3;
                int gray = border == inverted ? 0xFF : 0x00;
                pixels[y * 4 + x] = 0xFF000000 | gray << 16 | gray << 8 | gray;
            }
        }
        return pixels;
    }

    /** 把十六级放在离边缘至少 3 px 的区域，单独验证生成器能够无修饰地寻址全部色阶。 */
    private static int[] coveragePixels() {
        int[] pixels = new int[100];
        int middleGray = 7 * 17;
        Arrays.fill(pixels, 0xFF000000 | middleGray << 16 | middleGray << 8 | middleGray);
        for (int index = 0; index < 16; index++) {
            int x = 3 + index % 4;
            int y = 3 + index / 4;
            int gray = index * 17;
            pixels[y * 10 + x] = 0xFF000000 | gray << 16 | gray << 8 | gray;
        }
        return pixels;
    }

    private static void collectLevels(GeneratedPlasticTexture generated, Set<Integer> levels) {
        for (int y = 0; y < generated.layout().atlasHeight(); y++) {
            for (int x = 0; x < generated.layout().atlasWidth(); x++) {
                int level = generated.grayscaleLevelAt(x, y);
                if (level >= 0) levels.add(level);
            }
        }
    }

    private static long luminance(int color) {
        return 2126L * (color >> 16 & 0xFF)
            + 7152L * (color >> 8 & 0xFF)
            + 722L * (color & 0xFF);
    }

    private static ImageData readImage(String path) {
        Path resourcePath = ModList.get()
            .getModFileById(AnvilcraftPlasticraft.MOD_ID)
            .getFile()
            .findResource(path.split("/"));
        try (InputStream stream = Files.newInputStream(resourcePath)) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new GameTestAssertException("invalid test image " + path);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new ImageData(image.getWidth(), image.getHeight(), pixels);
        } catch (IOException exception) {
            throw new GameTestAssertException("unable to read test image " + path + ": " + exception.getMessage());
        }
    }

    private static void expectResourceError(Runnable action) {
        try {
            action.run();
        } catch (PlasticTextureResourceException expected) {
            return;
        }
        throw new GameTestAssertException("invalid plastic texture resource was accepted");
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new GameTestAssertException("inconsistent plastic texture input was accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    private record ImageData(int width, int height, int[] pixels) {
        private ImageData {
            pixels = pixels.clone();
        }

        @Override
        public int[] pixels() {
            return this.pixels.clone();
        }
    }
}
