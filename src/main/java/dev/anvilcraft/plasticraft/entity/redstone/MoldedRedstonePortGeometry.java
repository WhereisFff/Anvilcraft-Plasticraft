package dev.anvilcraft.plasticraft.entity.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

final class MoldedRedstonePortGeometry {
    private static final double FACE_EPSILON = 1.0E-5D;

    private MoldedRedstonePortGeometry() {
    }

    static AABB outwardRange(AABB bounds, Direction face) {
        return switch (face.getAxis()) {
            case X -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? new AABB(bounds.maxX, bounds.minY, bounds.minZ, bounds.maxX + 1.0D, bounds.maxY, bounds.maxZ)
                : new AABB(bounds.minX - 1.0D, bounds.minY, bounds.minZ, bounds.minX, bounds.maxY, bounds.maxZ);
            case Y -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? new AABB(bounds.minX, bounds.maxY, bounds.minZ, bounds.maxX, bounds.maxY + 1.0D, bounds.maxZ)
                : new AABB(bounds.minX, bounds.minY - 1.0D, bounds.minZ, bounds.maxX, bounds.minY, bounds.maxZ);
            case Z -> face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? new AABB(bounds.minX, bounds.minY, bounds.maxZ, bounds.maxX, bounds.maxY, bounds.maxZ + 1.0D)
                : new AABB(bounds.minX, bounds.minY, bounds.minZ - 1.0D, bounds.maxX, bounds.maxY, bounds.minZ);
        };
    }

    static List<List<BlockPos>> candidateLayers(AABB bounds, Direction face) {
        AABB range = outwardRange(bounds, face);
        int minimumX = containingMinimum(range.minX);
        int minimumY = containingMinimum(range.minY);
        int minimumZ = containingMinimum(range.minZ);
        int maximumX = containingMaximum(range.maxX);
        int maximumY = containingMaximum(range.maxY);
        int maximumZ = containingMaximum(range.maxZ);
        int nearest = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? axisMinimum(face.getAxis(), minimumX, minimumY, minimumZ)
            : axisMaximum(face.getAxis(), maximumX, maximumY, maximumZ);
        int farthest = face.getAxisDirection() == Direction.AxisDirection.POSITIVE
            ? axisMaximum(face.getAxis(), maximumX, maximumY, maximumZ)
            : axisMinimum(face.getAxis(), minimumX, minimumY, minimumZ);
        int step = face.getAxisDirection().getStep();

        List<List<BlockPos>> layers = new ArrayList<>();
        for (int axisCoordinate = nearest;
             step > 0 ? axisCoordinate <= farthest : axisCoordinate >= farthest;
             axisCoordinate += step) {
            List<BlockPos> layer = new ArrayList<>();
            for (BlockPos position : BlockPos.betweenClosed(
                minimumX,
                minimumY,
                minimumZ,
                maximumX,
                maximumY,
                maximumZ
            )) {
                if (axisCoordinate(position, face.getAxis()) == axisCoordinate) {
                    layer.add(position.immutable());
                }
            }
            if (!layer.isEmpty()) layers.add(List.copyOf(layer));
        }
        return List.copyOf(layers);
    }

    private static int containingMinimum(double coordinate) {
        return (int) Math.floor(coordinate + FACE_EPSILON);
    }

    private static int containingMaximum(double coordinate) {
        return (int) Math.floor(coordinate - FACE_EPSILON);
    }

    private static int axisMinimum(Direction.Axis axis, int x, int y, int z) {
        return switch (axis) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    private static int axisMaximum(Direction.Axis axis, int x, int y, int z) {
        return axisMinimum(axis, x, y, z);
    }

    private static int axisCoordinate(BlockPos position, Direction.Axis axis) {
        return switch (axis) {
            case X -> position.getX();
            case Y -> position.getY();
            case Z -> position.getZ();
        };
    }
}
