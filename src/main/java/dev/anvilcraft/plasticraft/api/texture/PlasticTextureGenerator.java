package dev.anvilcraft.plasticraft.api.texture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 与方块、实体和成型舱解耦的任意表面塑料贴图生成器。
 *
 * <p>图集布局只依赖规范化后的表面列表；基础贴图级别、边缘距离、凹槽和邻近遮蔽
 * 全部量化到统一的 0 至 15 灰度空间，再由指定色板行着色。</p>
 */
public final class PlasticTextureGenerator {
    public static final int VERSION = 3;

    private static final int PADDING = 1;
    private static final int MAX_ATLAS_SIZE = 4096;

    private PlasticTextureGenerator() {
    }

    /** 生成带颜色的图集与表面到 UV 的稳定映射。 */
    public static GeneratedPlasticTexture generate(
        PlasticTextureInput input,
        PlasticGrayscaleImage base,
        PlasticColorPalette palette
    ) {
        if (input.generatorVersion() != VERSION) {
            throw new IllegalArgumentException("Unsupported plastic texture generator version: " + input.generatorVersion());
        }
        if (input.paletteRow() >= palette.rows()) {
            throw new IllegalArgumentException("Plastic palette row is unavailable: " + input.paletteRow());
        }

        PlasticTextureLayout layout = layout(input.surfaces());
        int pixelCount = layout.atlasWidth() * layout.atlasHeight();
        int[] colors = new int[pixelCount];
        byte[] grayscale = new byte[pixelCount];
        Arrays.fill(grayscale, (byte) -1);

        Map<String, PlasticSurface> surfacesById = new LinkedHashMap<>();
        canonicalSurfaces(input.surfaces()).forEach(surface -> surfacesById.put(surface.id(), surface));
        for (Map.Entry<String, PlasticTextureLayout.UvRegion> entry : layout.regions().entrySet()) {
            PlasticSurface surface = surfacesById.get(entry.getKey());
            PlasticTextureLayout.UvRegion region = entry.getValue();
            renderSurface(base, palette, input.paletteRow(), surface, region, layout, colors, grayscale);
        }
        return new GeneratedPlasticTexture(layout, colors, grayscale);
    }

    /**
     * 只计算 UV 布局，供数据生成器和未来动态网格烘焙器复用。
     */
    public static PlasticTextureLayout layout(List<PlasticSurface> surfaces) {
        List<PlasticSurface> canonical = canonicalSurfaces(surfaces);
        Set<String> ids = new HashSet<>();
        long paddedArea = 0L;
        int widestTile = 0;
        for (PlasticSurface surface : canonical) {
            if (!ids.add(surface.id())) {
                throw new IllegalArgumentException("Duplicate plastic surface id: " + surface.id());
            }
            int tileWidth = surface.pixelWidth() + PADDING * 2;
            int tileHeight = surface.pixelHeight() + PADDING * 2;
            paddedArea += (long) tileWidth * tileHeight;
            widestTile = Math.max(widestTile, tileWidth);
        }

        int atlasWidth = nextPowerOfTwo(Math.max(widestTile, (int) Math.ceil(Math.sqrt(paddedArea))));
        if (atlasWidth > MAX_ATLAS_SIZE) {
            throw new IllegalArgumentException("Plastic texture atlas exceeds " + MAX_ATLAS_SIZE + " pixels");
        }

        Map<String, PlasticTextureLayout.UvRegion> regions = new LinkedHashMap<>();
        int cursorX = 0;
        int cursorY = 0;
        int rowHeight = 0;
        for (PlasticSurface surface : canonical) {
            int tileWidth = surface.pixelWidth() + PADDING * 2;
            int tileHeight = surface.pixelHeight() + PADDING * 2;
            if (cursorX > 0 && cursorX + tileWidth > atlasWidth) {
                cursorX = 0;
                cursorY += rowHeight;
                rowHeight = 0;
            }
            regions.put(
                surface.id(),
                new PlasticTextureLayout.UvRegion(
                    cursorX + PADDING,
                    cursorY + PADDING,
                    surface.pixelWidth(),
                    surface.pixelHeight(),
                    surface.doubleSided()
                )
            );
            cursorX += tileWidth;
            rowHeight = Math.max(rowHeight, tileHeight);
        }
        int atlasHeight = nextPowerOfTwo(cursorY + rowHeight);
        if (atlasHeight > MAX_ATLAS_SIZE) {
            throw new IllegalArgumentException("Plastic texture atlas exceeds " + MAX_ATLAS_SIZE + " pixels");
        }
        return new PlasticTextureLayout(atlasWidth, atlasHeight, regions);
    }

    /** 资源损坏时保留同一 UV 布局并返回醒目的安全占位图。 */
    public static GeneratedPlasticTexture placeholder(PlasticTextureInput input) {
        PlasticTextureLayout layout = layout(input.surfaces());
        int[] colors = new int[layout.atlasWidth() * layout.atlasHeight()];
        byte[] grayscale = new byte[colors.length];
        for (int y = 0; y < layout.atlasHeight(); y++) {
            for (int x = 0; x < layout.atlasWidth(); x++) {
                int index = y * layout.atlasWidth() + x;
                colors[index] = ((x >> 2) + (y >> 2) & 1) == 0 ? 0xFFFF00FF : 0xFF202020;
                grayscale[index] = (byte) (((x >> 2) + (y >> 2) & 1) == 0 ? 15 : 0);
            }
        }
        return new GeneratedPlasticTexture(layout, colors, grayscale);
    }

    private static void renderSurface(
        PlasticGrayscaleImage base,
        PlasticColorPalette palette,
        int paletteRow,
        PlasticSurface surface,
        PlasticTextureLayout.UvRegion region,
        PlasticTextureLayout layout,
        int[] colors,
        byte[] grayscale
    ) {
        // 连同一纹素边距一起生成并复制边缘颜色，降低图集 mipmap 采样串色。
        for (int paddedY = -PADDING; paddedY < region.height() + PADDING; paddedY++) {
            for (int paddedX = -PADDING; paddedX < region.width() + PADDING; paddedX++) {
                int localX = Math.clamp(paddedX, 0, region.width() - 1);
                int localY = Math.clamp(paddedY, 0, region.height() - 1);
                int level = quantizedLevel(base, surface, localX, localY);
                int atlasX = region.x() + paddedX;
                int atlasY = region.y() + paddedY;
                int index = atlasY * layout.atlasWidth() + atlasX;
                colors[index] = palette.colorAt(paletteRow, level);
                grayscale[index] = (byte) level;
            }
        }
    }

    private static int quantizedLevel(
        PlasticGrayscaleImage base,
        PlasticSurface surface,
        int localX,
        int localY
    ) {
        // 每张表面独立映射完整基础图，不能把 16x16 图案直接裁成侧面的 16x14。
        int baseLevel = base.normalizedLevelAtResampled(
            localX,
            localY,
            surface.pixelWidth(),
            surface.pixelHeight()
        );
        int edgeDistance = Math.min(
            Math.min(localX, surface.pixelWidth() - 1 - localX),
            Math.min(localY, surface.pixelHeight() - 1 - localY)
        );
        // 三纹素内形成离散边缘高光；凹槽和邻近遮蔽再逐级压暗。
        // 当前六级基础图的高亮位于外缘，这个组合也能实际寻址色板的全部十六级。
        int edgeShade = Math.max(0, 3 - edgeDistance);
        return Math.clamp(
            baseLevel + edgeShade - surface.grooveShade() - surface.ambientOcclusionShade(),
            0,
            PlasticGrayscaleImage.MAX_LEVELS - 1
        );
    }

    private static List<PlasticSurface> canonicalSurfaces(List<PlasticSurface> surfaces) {
        if (surfaces == null || surfaces.isEmpty()) {
            throw new IllegalArgumentException("Plastic texture layout requires at least one surface");
        }
        List<PlasticSurface> canonical = new ArrayList<>(surfaces);
        canonical.sort(Comparator.comparing(PlasticSurface::id));
        return List.copyOf(canonical);
    }

    private static int nextPowerOfTwo(int value) {
        if (value <= 1) return 1;
        int highest = Integer.highestOneBit(value - 1);
        if (highest > MAX_ATLAS_SIZE / 2) return MAX_ATLAS_SIZE + 1;
        return highest << 1;
    }
}
