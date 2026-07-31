package dev.anvilcraft.plasticraft.api.texture;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 确定性 UV 图集尺寸及每张表面的内侧纹素区域。 */
public record PlasticTextureLayout(int atlasWidth, int atlasHeight, Map<String, UvRegion> regions) {
    public PlasticTextureLayout {
        if (atlasWidth <= 0 || atlasHeight <= 0) {
            throw new IllegalArgumentException("Plastic texture atlas dimensions must be positive");
        }
        regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
    }

    /**
     * @param x           图集中的左上角纹素坐标
     * @param y           图集中的左上角纹素坐标
     * @param width       表面纹素宽度
     * @param height      表面纹素高度
     * @param doubleSided 是否为无厚度双面表面
     */
    public record UvRegion(int x, int y, int width, int height, boolean doubleSided) {
        /** 转换为原版方块模型使用的 0 至 16 U 坐标。 */
        public float modelU0(int atlasWidth) {
            return 16.0F * this.x / atlasWidth;
        }

        public float modelU1(int atlasWidth) {
            return 16.0F * (this.x + this.width) / atlasWidth;
        }

        /** 转换为原版方块模型使用的 0 至 16 V 坐标。 */
        public float modelV0(int atlasHeight) {
            return 16.0F * this.y / atlasHeight;
        }

        public float modelV1(int atlasHeight) {
            return 16.0F * (this.y + this.height) / atlasHeight;
        }
    }
}
