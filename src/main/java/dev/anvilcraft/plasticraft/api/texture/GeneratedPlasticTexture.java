package dev.anvilcraft.plasticraft.api.texture;

import java.nio.ByteBuffer;

/**
 * CPU 侧确定性贴图结果；不包含 Minecraft 纹理对象或任何 GPU 引用。
 */
public final class GeneratedPlasticTexture {
    private final PlasticTextureLayout layout;
    private final int[] argbPixels;
    private final byte[] grayscaleLevels;

    public GeneratedPlasticTexture(
        PlasticTextureLayout layout,
        int[] argbPixels,
        byte[] grayscaleLevels
    ) {
        int expectedPixels = layout.atlasWidth() * layout.atlasHeight();
        if (argbPixels.length != expectedPixels || grayscaleLevels.length != expectedPixels) {
            throw new IllegalArgumentException("Generated plastic texture pixel count does not match its layout");
        }
        this.layout = layout;
        this.argbPixels = argbPixels.clone();
        this.grayscaleLevels = grayscaleLevels.clone();
    }

    public PlasticTextureLayout layout() {
        return this.layout;
    }

    public int argbAt(int x, int y) {
        return this.argbPixels[y * this.layout.atlasWidth() + x];
    }

    public int grayscaleLevelAt(int x, int y) {
        return this.grayscaleLevels[y * this.layout.atlasWidth() + x];
    }

    /** 以固定 RGBA 字节序导出，供确定性测试和内容哈希使用。 */
    public byte[] toRgbaBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(this.argbPixels.length * Integer.BYTES);
        for (int color : this.argbPixels) {
            buffer.put((byte) (color >> 16 & 0xFF));
            buffer.put((byte) (color >> 8 & 0xFF));
            buffer.put((byte) (color & 0xFF));
            buffer.put((byte) (color >>> 24));
        }
        return buffer.array();
    }
}
