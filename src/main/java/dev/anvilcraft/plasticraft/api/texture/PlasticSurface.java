package dev.anvilcraft.plasticraft.api.texture;

import java.util.List;
import java.util.Objects;

/**
 * 任意塑料制品的一张外露四边形表面。
 *
 * <p>顶点使用模型局部坐标而不是方块或实体类型，法线同样位于模型局部空间。
 * 顶点数和法线方向都不限定为方块六面，因此凹形、断开模型、任意角度旋转后的模型
 * 和零厚度 cube 的双面表面都能经过同一个公共入口。</p>
 *
 * @param id                    表面在形状内的稳定 ID
 * @param vertices              按环绕顺序排列的至少三个局部顶点
 * @param normal                指向表面外侧的局部单位法线
 * @param pixelWidth            按稳定纹素密度展开后的宽度
 * @param pixelHeight           按稳定纹素密度展开后的高度
 * @param doubleSided           零厚度 cube 的表面是否需要双面渲染
 * @param grooveShade           凹槽造成的离散变暗级数
 * @param ambientOcclusionShade 邻近遮蔽造成的离散变暗级数
 */
public record PlasticSurface(
    String id,
    List<Point> vertices,
    Point normal,
    int pixelWidth,
    int pixelHeight,
    boolean doubleSided,
    int grooveShade,
    int ambientOcclusionShade
) {
    public PlasticSurface {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Plastic surface id must not be blank");
        }
        vertices = List.copyOf(Objects.requireNonNull(vertices, "vertices"));
        if (vertices.size() < 3 || vertices.stream().distinct().count() != vertices.size()) {
            throw new IllegalArgumentException(
                "Plastic surface " + id + " must contain at least three distinct vertices"
            );
        }
        Objects.requireNonNull(normal, "normal");
        if (!normal.isUnitVector()) {
            throw new IllegalArgumentException("Plastic surface " + id + " must use a finite unit normal");
        }
        if (pixelWidth <= 0 || pixelHeight <= 0) {
            throw new IllegalArgumentException("Plastic surface " + id + " must have a positive texture size");
        }
        if (grooveShade < 0 || grooveShade > 15
            || ambientOcclusionShade < 0 || ambientOcclusionShade > 15) {
            throw new IllegalArgumentException("Plastic surface shade modifiers must be between 0 and 15");
        }
    }

    /** 表面顶点和法线共用的双精度局部坐标，不丢失非网格旋转烘焙后的方向。 */
    public record Point(double x, double y, double z) {
        private static final double UNIT_EPSILON = 1.0E-6D;

        public Point {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Plastic surface coordinates must be finite");
            }
            // 规范化正负零，避免几何等价输入得到不同记录值和形状哈希。
            x = x == 0.0D ? 0.0D : x;
            y = y == 0.0D ? 0.0D : y;
            z = z == 0.0D ? 0.0D : z;
        }

        private boolean isUnitVector() {
            double lengthSquared = this.x * this.x + this.y * this.y + this.z * this.z;
            return Math.abs(lengthSquared - 1.0D) <= UNIT_EPSILON;
        }
    }
}
