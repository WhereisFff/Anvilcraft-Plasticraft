package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalyzer;
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
    private static final double MINIMUM_XZ = MoldingTrayShapeAnalyzer.CENTER_MIN / PIXELS_PER_BLOCK;
    private static final double MAXIMUM_XZ = MoldingTrayShapeAnalyzer.CENTER_MAX_EXCLUSIVE / PIXELS_PER_BLOCK;

    private MoldedTrayComponentGeometry() {
    }

    public static AABB localBounds(MoldingTrayShapeAnalysis shape) {
        Objects.requireNonNull(shape, "shape");
        if (!shape.valid()) throw new IllegalArgumentException("Tray shape must be valid");
        return localBoundsAt(shape.topY() / PIXELS_PER_BLOCK);
    }

    public static AABB localBounds(MoldedPlasticData data) {
        Objects.requireNonNull(data, "data");
        return localBoundsAt(data.surfaceBounds().maxY);
    }

    public static AABB localOutputRange(MoldedPlasticData data, Direction outputDirection) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(outputDirection, "outputDirection");
        double bottomY = data.surfaceBounds().minY;
        return new AABB(
            MINIMUM_XZ,
            bottomY,
            MINIMUM_XZ,
            MAXIMUM_XZ,
            bottomY + 1.0D,
            MAXIMUM_XZ
        ).move(
            outputDirection.getStepX(),
            outputDirection.getStepY(),
            outputDirection.getStepZ()
        );
    }

    public static Vec3 localFaceCenter(MoldedPlasticData data, Direction face) {
        Objects.requireNonNull(face, "face");
        AABB bounds = localBounds(data);
        Vec3 center = bounds.getCenter();
        return switch (face.getAxis()) {
            case X -> new Vec3(face == Direction.WEST ? bounds.minX : bounds.maxX, center.y, center.z);
            case Y -> new Vec3(center.x, face == Direction.DOWN ? bounds.minY : bounds.maxY, center.z);
            case Z -> new Vec3(center.x, center.y, face == Direction.NORTH ? bounds.minZ : bounds.maxZ);
        };
    }

    public static VoxelShape localCollisionShape(
        MoldedPlasticData data,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return moveToTray(data, state.getCollisionShape(level, position));
    }

    public static VoxelShape localInteractionShape(
        MoldedPlasticData data,
        BlockState state,
        BlockGetter level,
        BlockPos position
    ) {
        return moveToTray(data, state.getShape(level, position, CollisionContext.empty()));
    }

    private static VoxelShape moveToTray(MoldedPlasticData data, VoxelShape shape) {
        AABB bounds = localBounds(data);
        return shape.move(bounds.minX, bounds.minY, bounds.minZ);
    }

    private static AABB localBoundsAt(double bottomY) {
        if (!Double.isFinite(bottomY)) throw new IllegalArgumentException("Tray component height must be finite");
        return new AABB(
            MINIMUM_XZ,
            bottomY,
            MINIMUM_XZ,
            MAXIMUM_XZ,
            bottomY + 1.0D,
            MAXIMUM_XZ
        );
    }
}
