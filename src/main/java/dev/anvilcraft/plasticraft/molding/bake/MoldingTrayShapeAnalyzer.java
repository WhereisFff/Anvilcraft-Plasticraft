package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.BitSet;
import java.util.List;

/** 按固定模型 Y 轴扫描支架的上下承载面。 */
public final class MoldingTrayShapeAnalyzer {
    public static final int CELL_SIZE = 16;
    public static final int GRID_SIZE = 3;
    public static final int GRID_MIN = 0;
    public static final int GRID_MAX_EXCLUSIVE = CELL_SIZE * GRID_SIZE;
    public static final int MAX_HEIGHT = 4;
    private static final double EPSILON = 1.0E-7D;

    private MoldingTrayShapeAnalyzer() {
    }

    public static MoldingTrayShapeAnalysis analyze(
        MoldingVolumeMask volume,
        List<MoldingQuad> surfaces
    ) {
        if (volume.isEmpty()) return MoldingTrayShapeAnalysis.invalid("tray_empty");
        if (volume.sizeX() != MoldingVolumeMask.SIZE
            || volume.sizeY() != MoldingVolumeMask.SIZE
            || volume.sizeZ() != MoldingVolumeMask.SIZE) {
            return MoldingTrayShapeAnalysis.invalid("tray_outside_center");
        }

        SurfaceBounds bounds = surfaceBounds(surfaces);
        if (bounds == null) return MoldingTrayShapeAnalysis.invalid("tray_bottom_not_flat");
        if (bounds.minimumX < GRID_MIN - EPSILON
            || bounds.maximumX > GRID_MAX_EXCLUSIVE + EPSILON
            || bounds.minimumZ < GRID_MIN - EPSILON
            || bounds.maximumZ > GRID_MAX_EXCLUSIVE + EPSILON) {
            return MoldingTrayShapeAnalysis.invalid("tray_outside_center");
        }
        double height = bounds.maximumY - bounds.minimumY;
        if (height > MAX_HEIGHT + EPSILON) {
            return MoldingTrayShapeAnalysis.invalid("tray_too_tall");
        }

        BitSet bottom = horizontalFootprint(surfaces, bounds.minimumY, -1.0D);
        if (bottom.isEmpty()) {
            return MoldingTrayShapeAnalysis.invalid("tray_bottom_not_flat");
        }
        BitSet top = horizontalFootprint(surfaces, bounds.maximumY, 1.0D);
        if (top.isEmpty()) {
            return MoldingTrayShapeAnalysis.invalid("tray_top_not_flat");
        }
        return new MoldingTrayShapeAnalysis(
            true,
            "",
            bounds.minimumY,
            bounds.maximumY,
            height,
            bottom.cardinality(),
            supportedCellMask(volume)
        );
    }

    /** 超限调试模型也只暴露标准建模区域内的九个承载格。 */
    public static int supportedCellMask(MoldingVolumeMask volume) {
        BitSet cells = volume.copyBits();
        int result = 0;
        for (int index = cells.nextSetBit(0); index >= 0; index = cells.nextSetBit(index + 1)) {
            int x = volume.xOf(index);
            int z = volume.zOf(index);
            if (x < GRID_MIN || x >= GRID_MAX_EXCLUSIVE
                || z < GRID_MIN || z >= GRID_MAX_EXCLUSIVE) {
                continue;
            }
            int cellX = x / CELL_SIZE;
            int cellZ = z / CELL_SIZE;
            result |= 1 << (cellZ * GRID_SIZE + cellX);
        }
        return result;
    }

    private static SurfaceBounds surfaceBounds(List<MoldingQuad> surfaces) {
        if (surfaces.isEmpty()) return null;
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (MoldingQuad surface : surfaces) {
            for (MoldingVec3 point : vertices(surface)) {
                minimumX = Math.min(minimumX, point.x());
                minimumY = Math.min(minimumY, point.y());
                minimumZ = Math.min(minimumZ, point.z());
                maximumX = Math.max(maximumX, point.x());
                maximumY = Math.max(maximumY, point.y());
                maximumZ = Math.max(maximumZ, point.z());
            }
        }
        return new SurfaceBounds(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
    }

    private static BitSet horizontalFootprint(
        List<MoldingQuad> surfaces,
        double planeY,
        double expectedNormalY
    ) {
        BitSet result = new BitSet(MoldingVolumeMask.SIZE * MoldingVolumeMask.SIZE);
        for (MoldingQuad surface : surfaces) {
            if (!isHorizontalSurfaceAt(surface, planeY, expectedNormalY)) continue;
            for (int x = GRID_MIN; x < GRID_MAX_EXCLUSIVE; x++) {
                for (int z = GRID_MIN; z < GRID_MAX_EXCLUSIVE; z++) {
                    if (contains(surface, x + 0.5D, z + 0.5D)) result.set(index(x, z));
                }
            }
        }
        return result;
    }

    private static boolean isHorizontalSurfaceAt(
        MoldingQuad surface,
        double planeY,
        double expectedNormalY
    ) {
        return !surface.doubleSided()
            && Math.abs(surface.normal().x()) <= EPSILON
            && Math.abs(surface.normal().z()) <= EPSILON
            && Math.abs(surface.normal().y() - expectedNormalY) <= EPSILON
            && vertices(surface).stream().allMatch(point -> Math.abs(point.y() - planeY) <= EPSILON);
    }

    private static boolean contains(MoldingQuad surface, double x, double z) {
        return containsTriangle(surface.first(), surface.second(), surface.third(), x, z)
            || containsTriangle(surface.first(), surface.third(), surface.fourth(), x, z);
    }

    private static boolean containsTriangle(
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third,
        double x,
        double z
    ) {
        if (Math.abs(cross(first, second, third.x(), third.z())) <= EPSILON) return false;
        double firstCross = cross(first, second, x, z);
        double secondCross = cross(second, third, x, z);
        double thirdCross = cross(third, first, x, z);
        boolean hasNegative = firstCross < -EPSILON || secondCross < -EPSILON || thirdCross < -EPSILON;
        boolean hasPositive = firstCross > EPSILON || secondCross > EPSILON || thirdCross > EPSILON;
        return !(hasNegative && hasPositive);
    }

    private static double cross(MoldingVec3 first, MoldingVec3 second, double x, double z) {
        return (second.x() - first.x()) * (z - first.z())
            - (second.z() - first.z()) * (x - first.x());
    }

    private static List<MoldingVec3> vertices(MoldingQuad surface) {
        return List.of(surface.first(), surface.second(), surface.third(), surface.fourth());
    }

    private static int index(int x, int z) {
        return x * MoldingVolumeMask.SIZE + z;
    }

    private record SurfaceBounds(
        double minimumX,
        double minimumY,
        double minimumZ,
        double maximumX,
        double maximumY,
        double maximumZ
    ) {
    }
}
