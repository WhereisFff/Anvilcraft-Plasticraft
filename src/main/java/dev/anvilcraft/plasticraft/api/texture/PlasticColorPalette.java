package dev.anvilcraft.plasticraft.api.texture;

import java.util.HashSet;
import java.util.Set;

/**
 * 每种材料颜色均拥有独立有序色阶的塑料色板。
 *
 * <p>像素使用标准 {@code 0xAARRGGBB}。色阶数可为 1 至 16，当前通用塑料资源使用
 * 完整 16 级；每行拒绝重复或亮度倒序，从数据层阻止用相邻重复颜色伪装扩容。</p>
 */
public final class PlasticColorPalette {
    private final int rows;
    private final int levels;
    private final int[] colors;

    private PlasticColorPalette(int rows, int levels, int[] colors) {
        this.rows = rows;
        this.levels = levels;
        this.colors = colors;
    }

    public static PlasticColorPalette fromArgb(int rows, int levels, int[] colors) {
        if (rows <= 0 || levels <= 0 || levels > PlasticGrayscaleImage.MAX_LEVELS) {
            throw new PlasticTextureResourceException(
                "Plastic palette must contain 1 to " + PlasticGrayscaleImage.MAX_LEVELS + " levels per row"
            );
        }
        if (colors == null || colors.length != rows * levels) {
            throw new PlasticTextureResourceException("Plastic palette pixel count does not match its dimensions");
        }

        int[] copy = colors.clone();
        for (int row = 0; row < rows; row++) {
            Set<Integer> distinct = new HashSet<>();
            long previousLuminance = -1L;
            for (int level = 0; level < levels; level++) {
                int color = copy[row * levels + level];
                if (color >>> 24 != 0xFF) {
                    throw new PlasticTextureResourceException(
                        "Plastic palette row " + row + " level " + level + " is not fully opaque"
                    );
                }
                if (!distinct.add(color)) {
                    throw new PlasticTextureResourceException(
                        "Plastic palette row " + row + " repeats a color at level " + level
                    );
                }
                long luminance = luminance(color);
                if (luminance <= previousLuminance) {
                    throw new PlasticTextureResourceException(
                        "Plastic palette row " + row + " is not strictly ordered at level " + level
                    );
                }
                previousLuminance = luminance;
            }
        }
        return new PlasticColorPalette(rows, levels, copy);
    }

    public int rows() {
        return this.rows;
    }

    public int levels() {
        return this.levels;
    }

    /** 把生成器的 0 至 15 灰度级映射到当前色板实际提供的有序级别。 */
    public int colorAt(int row, int normalizedLevel) {
        if (row < 0 || row >= this.rows) {
            throw new IllegalArgumentException("Plastic palette row is out of range: " + row);
        }
        if (normalizedLevel < 0 || normalizedLevel >= PlasticGrayscaleImage.MAX_LEVELS) {
            throw new IllegalArgumentException("Plastic grayscale level is out of range: " + normalizedLevel);
        }
        int paletteLevel = Math.round(
            (float) normalizedLevel * (this.levels - 1) / (PlasticGrayscaleImage.MAX_LEVELS - 1)
        );
        return this.colors[row * this.levels + paletteLevel];
    }

    private static long luminance(int color) {
        int red = color >> 16 & 0xFF;
        int green = color >> 8 & 0xFF;
        int blue = color & 0xFF;
        // 使用整数形式的 Rec. 709 权重，避免平台浮点差异影响资源校验。
        return 2126L * red + 7152L * green + 722L * blue;
    }
}
