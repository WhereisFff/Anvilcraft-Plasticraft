package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/** 将方块局部坐标中的组合形状旋转到塑料实体的离散朝向。 */
public final class PlasticEntityCollisionShapes {
    private PlasticEntityCollisionShapes() {
    }

    public static VoxelShape rotate(VoxelShape shape, PlasticEntityOrientation orientation) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(orientation, "orientation");
        if (shape.isEmpty()) return shape;

        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        VoxelShape transformed = Shapes.empty();
        for (AABB box : shape.toAabbs()) {
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (int x = 0; x < 2; x++) {
                for (int y = 0; y < 2; y++) {
                    for (int z = 0; z < 2; z++) {
                        double localX = (x == 0 ? box.minX : box.maxX) - 0.5D;
                        double localY = (y == 0 ? box.minY : box.maxY) - 0.5D;
                        double localZ = (z == 0 ? box.minZ : box.maxZ) - 0.5D;
                        double transformedX = 0.5D
                            + localX * xAxis.getStepX()
                            + localY * yAxis.getStepX()
                            + localZ * zAxis.getStepX();
                        double transformedY = 0.5D
                            + localX * xAxis.getStepY()
                            + localY * yAxis.getStepY()
                            + localZ * zAxis.getStepY();
                        double transformedZ = 0.5D
                            + localX * xAxis.getStepZ()
                            + localY * yAxis.getStepZ()
                            + localZ * zAxis.getStepZ();
                        minX = Math.min(minX, transformedX);
                        minY = Math.min(minY, transformedY);
                        minZ = Math.min(minZ, transformedZ);
                        maxX = Math.max(maxX, transformedX);
                        maxY = Math.max(maxY, transformedY);
                        maxZ = Math.max(maxZ, transformedZ);
                    }
                }
            }
            transformed = Shapes.or(
                transformed,
                Shapes.box(
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ
                )
            );
        }
        return transformed.optimize();
    }
}
