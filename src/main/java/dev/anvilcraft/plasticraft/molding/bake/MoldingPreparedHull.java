package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 打印掩码填充用的凸体查询。
 * SAT 只作为保守拒绝：明确分离的格子不再做多面体裁剪；可能相交的格子仍用体积判定。
 */
public final class MoldingPreparedHull {
    /** 必须与 {@link MoldingModelBaker} 的裁剪容差一致。 */
    static final double SEPARATION_EPSILON = 1.0E-7D;
    private static final double AXIS_ALIGN_DOT = 1.0D - 1.0E-6D;

    private final MoldingConvexHull hull;
    private final double minX;
    private final double minY;
    private final double minZ;
    private final double maxX;
    private final double maxY;
    private final double maxZ;
    private final double[] axisNx;
    private final double[] axisNy;
    private final double[] axisNz;
    private final double[] axisMin;
    private final double[] axisMax;

    private MoldingPreparedHull(
        MoldingConvexHull hull,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ,
        double[] axisNx,
        double[] axisNy,
        double[] axisNz,
        double[] axisMin,
        double[] axisMax
    ) {
        this.hull = hull;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.axisNx = axisNx;
        this.axisNy = axisNy;
        this.axisNz = axisNz;
        this.axisMin = axisMin;
        this.axisMax = axisMax;
    }

    public static MoldingPreparedHull prepare(MoldingConvexHull hull) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        List<MoldingVec3> vertices = hull.vertices();
        for (MoldingVec3 vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
        }

        List<double[]> axes = new ArrayList<>(6 + hull.faces().size());
        addAxis(axes, 1.0D, 0.0D, 0.0D);
        addAxis(axes, 0.0D, 1.0D, 0.0D);
        addAxis(axes, 0.0D, 0.0D, 1.0D);
        for (MoldingConvexFace face : hull.faces()) {
            MoldingVec3 normal = face.normal();
            addAxis(axes, normal.x(), normal.y(), normal.z());
        }

        int axisCount = axes.size();
        double[] axisNx = new double[axisCount];
        double[] axisNy = new double[axisCount];
        double[] axisNz = new double[axisCount];
        double[] axisMin = new double[axisCount];
        double[] axisMax = new double[axisCount];
        for (int index = 0; index < axisCount; index++) {
            double[] axis = axes.get(index);
            axisNx[index] = axis[0];
            axisNy[index] = axis[1];
            axisNz[index] = axis[2];
            double projectedMin = Double.POSITIVE_INFINITY;
            double projectedMax = Double.NEGATIVE_INFINITY;
            for (MoldingVec3 vertex : vertices) {
                double projected = axis[0] * vertex.x() + axis[1] * vertex.y() + axis[2] * vertex.z();
                projectedMin = Math.min(projectedMin, projected);
                projectedMax = Math.max(projectedMax, projected);
            }
            axisMin[index] = projectedMin;
            axisMax[index] = projectedMax;
        }
        return new MoldingPreparedHull(
            hull,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            axisNx,
            axisNy,
            axisNz,
            axisMin,
            axisMax
        );
    }

    public MoldingConvexHull hull() {
        return this.hull;
    }

    public int cellMinX(int size) {
        return cellMinimum(this.minX, size);
    }

    public int cellMinY(int size) {
        return cellMinimum(this.minY, size);
    }

    public int cellMinZ(int size) {
        return cellMinimum(this.minZ, size);
    }

    public int cellMaxX(int size) {
        return cellMaximum(this.maxX, size);
    }

    public int cellMaxY(int size) {
        return cellMaximum(this.maxY, size);
    }

    public int cellMaxZ(int size) {
        return cellMaximum(this.maxZ, size);
    }

    /**
     * 格子与凸体在已测轴上均未分离时返回 true。
     * 假阳性会再走体积判定；不得把仍有正体积交集的格子判成分离。
     */
    public boolean mayOverlapCell(int x, int y, int z) {
        double minCellX = x;
        double maxCellX = x + 1.0D;
        double minCellY = y;
        double maxCellY = y + 1.0D;
        double minCellZ = z;
        double maxCellZ = z + 1.0D;
        if (this.maxX <= minCellX + SEPARATION_EPSILON || this.minX >= maxCellX - SEPARATION_EPSILON
            || this.maxY <= minCellY + SEPARATION_EPSILON || this.minY >= maxCellY - SEPARATION_EPSILON
            || this.maxZ <= minCellZ + SEPARATION_EPSILON || this.minZ >= maxCellZ - SEPARATION_EPSILON) {
            return false;
        }
        double centerX = x + 0.5D;
        double centerY = y + 0.5D;
        double centerZ = z + 0.5D;
        for (int index = 0; index < this.axisNx.length; index++) {
            double nx = this.axisNx[index];
            double ny = this.axisNy[index];
            double nz = this.axisNz[index];
            double center = nx * centerX + ny * centerY + nz * centerZ;
            double radius = 0.5D * (Math.abs(nx) + Math.abs(ny) + Math.abs(nz));
            double cellMin = center - radius;
            double cellMax = center + radius;
            if (this.axisMax[index] <= cellMin + SEPARATION_EPSILON
                || cellMax <= this.axisMin[index] + SEPARATION_EPSILON) {
                return false;
            }
        }
        return true;
    }

    private static int cellMinimum(double coordinate, int size) {
        return Math.clamp((int) Math.floor(coordinate), 0, size - 1);
    }

    private static int cellMaximum(double coordinate, int size) {
        return Math.clamp((int) Math.ceil(coordinate) - 1, 0, size - 1);
    }

    private static void addAxis(List<double[]> axes, double x, double y, double z) {
        double lengthSquared = x * x + y * y + z * z;
        if (lengthSquared <= SEPARATION_EPSILON * SEPARATION_EPSILON) return;
        double inverse = 1.0D / Math.sqrt(lengthSquared);
        x *= inverse;
        y *= inverse;
        z *= inverse;
        for (double[] existing : axes) {
            if (Math.abs(existing[0] * x + existing[1] * y + existing[2] * z) >= AXIS_ALIGN_DOT) {
                return;
            }
        }
        axes.add(new double[]{x, y, z});
    }
}
