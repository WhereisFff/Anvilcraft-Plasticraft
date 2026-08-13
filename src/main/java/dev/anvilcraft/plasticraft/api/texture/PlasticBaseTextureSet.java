package dev.anvilcraft.plasticraft.api.texture;

import java.util.Objects;

/** 按目标表面尺寸提供对应规格的塑料灰度基础贴图。 */
public final class PlasticBaseTextureSet {
    private static final int SMALL_SIZE = 8;
    private static final int STANDARD_SIZE = 16;
    private static final int LARGE_SIZE = 32;
    private static final int MAXIMUM_SIZE = 48;

    private final PlasticGrayscaleImage small;
    private final PlasticGrayscaleImage standard;
    private final PlasticGrayscaleImage large;
    private final PlasticGrayscaleImage maximum;

    private PlasticBaseTextureSet(
        PlasticGrayscaleImage small,
        PlasticGrayscaleImage standard,
        PlasticGrayscaleImage large,
        PlasticGrayscaleImage maximum
    ) {
        this.small = requireSize(small, SMALL_SIZE);
        this.standard = requireSize(standard, STANDARD_SIZE);
        this.large = requireSize(large, LARGE_SIZE);
        this.maximum = requireSize(maximum, MAXIMUM_SIZE);
    }

    public static PlasticBaseTextureSet of(
        PlasticGrayscaleImage small,
        PlasticGrayscaleImage standard,
        PlasticGrayscaleImage large,
        PlasticGrayscaleImage maximum
    ) {
        return new PlasticBaseTextureSet(small, standard, large, maximum);
    }

    /**
     * 按表面长边向上选择 8、16、32 像素规格；长边超过 32 像素时固定使用 48 像素规格。
     */
    public PlasticGrayscaleImage select(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Plastic texture target dimensions must be positive");
        }
        int longestSide = Math.max(width, height);
        if (longestSide <= SMALL_SIZE) return this.small;
        if (longestSide <= STANDARD_SIZE) return this.standard;
        if (longestSide <= LARGE_SIZE) return this.large;
        return this.maximum;
    }

    private static PlasticGrayscaleImage requireSize(PlasticGrayscaleImage image, int expectedSize) {
        Objects.requireNonNull(image, "image");
        if (image.width() != expectedSize || image.height() != expectedSize) {
            throw new PlasticTextureResourceException(
                "Plastic base texture must be " + expectedSize + "x" + expectedSize + " pixels"
            );
        }
        return image;
    }
}
