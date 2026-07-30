package dev.anvilcraft.plasticraft.api.texture;

import java.util.Arrays;
import java.util.TreeSet;

/**
 * 已验证并按亮度排序的灰度基础贴图。
 *
 * <p>资源可以提供 1 至 16 个真实灰度值，并保留每一级在 {@code 0..255} 中的绝对亮度。
 * 映射不会把资源当前使用的局部亮度范围强行拉伸到完整色板，因而资源作者调整基础图后，
 * 生成结果也会保持相同的亮度方向。</p>
 */
public final class PlasticGrayscaleImage {
    public static final int MAX_LEVELS = 16;

    private final int width;
    private final int height;
    private final int[] levels;
    private final byte[] pixelLevels;

    private PlasticGrayscaleImage(int width, int height, int[] levels, byte[] pixelLevels) {
        this.width = width;
        this.height = height;
        this.levels = levels;
        this.pixelLevels = pixelLevels;
    }

    /** 从标准 {@code 0xAARRGGBB} 像素创建并验证灰度资源。 */
    public static PlasticGrayscaleImage fromArgb(int width, int height, int[] pixels) {
        validateDimensions(width, height, pixels);
        TreeSet<Integer> uniqueLevels = new TreeSet<>();
        for (int index = 0; index < pixels.length; index++) {
            int pixel = pixels[index];
            int alpha = pixel >>> 24;
            int red = pixel >> 16 & 0xFF;
            int green = pixel >> 8 & 0xFF;
            int blue = pixel & 0xFF;
            if (alpha != 0xFF) {
                throw new PlasticTextureResourceException(
                    "Plastic base texture pixel " + index + " is not fully opaque"
                );
            }
            if (red != green || green != blue) {
                throw new PlasticTextureResourceException(
                    "Plastic base texture pixel " + index + " is not grayscale"
                );
            }
            uniqueLevels.add(red);
            if (uniqueLevels.size() > MAX_LEVELS) {
                throw new PlasticTextureResourceException(
                    "Plastic base texture contains more than " + MAX_LEVELS + " grayscale levels"
                );
            }
        }
        if (uniqueLevels.isEmpty()) {
            throw new PlasticTextureResourceException("Plastic base texture contains no pixels");
        }

        int[] levels = uniqueLevels.stream().mapToInt(Integer::intValue).toArray();
        byte[] pixelLevels = new byte[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            int gray = pixels[index] & 0xFF;
            pixelLevels[index] = (byte) Arrays.binarySearch(levels, gray);
        }
        return new PlasticGrayscaleImage(width, height, levels, pixelLevels);
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** 返回资源中原样识别到的有序灰度值副本。 */
    public int[] levels() {
        return this.levels.clone();
    }

    public int levelCount() {
        return this.levels.length;
    }

    /** 按像素的绝对灰度亮度映射到生成器的 0 至 15 灰度空间。 */
    public int normalizedLevelAt(int x, int y) {
        int wrappedX = Math.floorMod(x, this.width);
        int wrappedY = Math.floorMod(y, this.height);
        int sourceIndex = Byte.toUnsignedInt(this.pixelLevels[wrappedY * this.width + wrappedX]);
        int gray = this.levels[sourceIndex];
        return Math.round((float) gray * (MAX_LEVELS - 1) / 255.0F);
    }

    /**
     * 把完整基础贴图以端点对齐的最近邻采样映射到一个表面，保证非 16x16 表面也同时保留首末边框。
     */
    public int normalizedLevelAtResampled(int x, int y, int targetWidth, int targetHeight) {
        if (targetWidth <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Plastic texture target dimensions must be positive");
        }
        int sourceX = resampleCoordinate(x, targetWidth, this.width);
        int sourceY = resampleCoordinate(y, targetHeight, this.height);
        return this.normalizedLevelAt(sourceX, sourceY);
    }

    private static int resampleCoordinate(int coordinate, int targetLength, int sourceLength) {
        if (targetLength == 1 || sourceLength == 1) return 0;
        int wrapped = Math.floorMod(coordinate, targetLength);
        return (int) Math.round((double) wrapped * (sourceLength - 1) / (targetLength - 1));
    }

    private static void validateDimensions(int width, int height, int[] pixels) {
        if (width <= 0 || height <= 0) {
            throw new PlasticTextureResourceException("Plastic base texture dimensions must be positive");
        }
        if (pixels == null || pixels.length != width * height) {
            throw new PlasticTextureResourceException("Plastic base texture pixel count does not match its dimensions");
        }
    }
}
