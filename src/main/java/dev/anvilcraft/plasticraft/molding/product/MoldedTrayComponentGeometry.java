package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Objects;

/** 支架承载元件在建模空间中的固定单位方块轮廓。 */
public final class MoldedTrayComponentGeometry {
    private static final double PIXELS_PER_BLOCK = 16.0D;

    private MoldedTrayComponentGeometry() {
    }

    public static AABB localBounds(MoldingTrayShapeAnalysis shape) {
        return localBounds(shape, MoldedTrayCell.CENTER);
    }

    public static AABB localBounds(MoldingTrayShapeAnalysis shape, MoldedTrayCell cell) {
        Objects.requireNonNull(shape, "shape");
        if (!shape.valid()) throw new IllegalArgumentException("Tray shape must be valid");
        return localBoundsAt(shape.topY() / PIXELS_PER_BLOCK, cell);
    }

    public static AABB localBounds(MoldedPlasticData data) {
        return localBounds(data, MoldedTrayCell.CENTER);
    }

    public static AABB localBounds(MoldedPlasticData data, MoldedTrayCell cell) {
        Objects.requireNonNull(data, "data");
        return localBoundsAt(data.surfaceBounds().maxY, cell);
    }

    public static AABB localOutputRange(MoldedPlasticData data, Direction outputDirection) {
        return localOutputRange(data, MoldedTrayCell.CENTER, outputDirection);
    }

    public static AABB localOutputRange(
        MoldedPlasticData data,
        MoldedTrayCell cell,
        Direction outputDirection
    ) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(cell, "cell");
        Objects.requireNonNull(outputDirection, "outputDirection");
        double bottomY = data.surfaceBounds().minY;
        return new AABB(
            cell.x(),
            bottomY,
            cell.z(),
            cell.x() + 1.0D,
            bottomY + 1.0D,
            cell.z() + 1.0D
        ).move(
            outputDirection.getStepX(),
            outputDirection.getStepY(),
            outputDirection.getStepZ()
        );
    }

    public static Vec3 localFaceCenter(MoldedPlasticData data, Direction face) {
        return localFaceCenter(data, MoldedTrayCell.CENTER, face);
    }

    public static Vec3 localFaceCenter(MoldedPlasticData data, MoldedTrayCell cell, Direction face) {
        Objects.requireNonNull(face, "face");
        AABB bounds = localBounds(data, cell);
        Vec3 center = bounds.getCenter();
        return switch (face.getAxis()) {
            case X -> new Vec3(face == Direction.WEST ? bounds.minX : bounds.maxX, center.y, center.z);
            case Y -> new Vec3(center.x, face == Direction.DOWN ? bounds.minY : bounds.maxY, center.z);
            case Z -> new Vec3(center.x, center.y, face == Direction.NORTH ? bounds.minZ : bounds.maxZ);
        };
    }

    public static VoxelShape localCollisionShape(
        MoldedPlasticData data,
        MoldedTrayCell cell,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return moveToTray(data, cell, state.getCollisionShape(level, position));
    }

    public static VoxelShape localCollisionShape(
        MoldedPlasticData data,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return localCollisionShape(data, MoldedTrayCell.CENTER, state, level, position);
    }

    public static VoxelShape localInteractionShape(
        MoldedPlasticData data,
        MoldedTrayCell cell,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return moveToTray(data, cell, state.getShape(level, position, CollisionContext.empty()));
    }

    public static VoxelShape localInteractionShape(
        MoldedPlasticData data,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return localInteractionShape(data, MoldedTrayCell.CENTER, state, level, position);
    }

    private static VoxelShape moveToTray(MoldedPlasticData data, MoldedTrayCell cell, VoxelShape shape) {
        AABB bounds = localBounds(data, cell);
        return shape.move(bounds.minX, bounds.minY, bounds.minZ);
    }

    private static AABB localBoundsAt(double bottomY, MoldedTrayCell cell) {
        if (!Double.isFinite(bottomY)) throw new IllegalArgumentException("Tray component height must be finite");
        Objects.requireNonNull(cell, "cell");
        return new AABB(
            cell.x(),
            bottomY,
            cell.z(),
            cell.x() + 1.0D,
            bottomY + 1.0D,
            cell.z() + 1.0D
        );
    }
}
