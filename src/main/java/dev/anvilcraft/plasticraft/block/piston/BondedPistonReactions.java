package dev.anvilcraft.plasticraft.block.piston;

import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 让树脂胶连接的 DESTROY 方块按普通可推方块加入活塞和滑轨结构。 */
public final class BondedPistonReactions {
    private BondedPistonReactions() {
    }

    public static boolean isAdhesivelyGrouped(Level level, BlockPos pos) {
        return groupedAdhesion(level, pos, null) != null;
    }

    public static boolean shouldTreatAsPushable(Level level, BlockPos pos, BlockState state) {
        return shouldTreatAsPushable(level, pos, state, null);
    }

    public static boolean shouldTreatAsPushable(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable Direction pushDirection
    ) {
        return state.getPistonPushReaction() == PushReaction.DESTROY
            && groupedAdhesion(level, pos, state, pushDirection) != null;
    }

    public static PushReaction pushReaction(Level level, BlockPos pos, BlockState state, PushReaction original) {
        return pushReaction(level, pos, state, original, null);
    }

    public static PushReaction pushReaction(
        Level level,
        BlockPos pos,
        BlockState state,
        PushReaction original,
        @Nullable Direction pushDirection
    ) {
        return original == PushReaction.DESTROY && groupedAdhesion(level, pos, state, pushDirection) != null
            ? PushReaction.NORMAL
            : original;
    }

    public static List<Direction> blockBondFaces(Level level, BlockPos pos, Direction pushDirection) {
        BlockAdhesionState adhesion = groupedAdhesion(level, pos, level.getBlockState(pos), pushDirection);
        if (adhesion == null) return List.of();
        ArrayList<Direction> result = new ArrayList<>();
        for (Direction face : Direction.values()) {
            if (adhesion.hasBlockBond(face)) result.add(face);
        }
        return result;
    }

    public static boolean hasBlockBond(
        Level level,
        BlockPos pos,
        Direction face,
        @Nullable Direction pushDirection
    ) {
        BlockAdhesionState adhesion = groupedAdhesion(level, pos, level.getBlockState(pos), pushDirection);
        return adhesion != null && adhesion.hasBlockBond(face);
    }

    public static boolean claimDestroyBlock(
        Level level,
        List<BlockPos> toPush,
        List<BlockPos> toDestroy,
        BlockPos pos,
        Direction pushDirection
    ) {
        if (toPush.contains(pos)) return true;
        if (!shouldTreatAsPushable(level, pos, level.getBlockState(pos), pushDirection)) return false;
        toDestroy.remove(pos);
        toPush.add(pos.immutable());
        return true;
    }

    public static void claimDestroyBlocks(
        Level level,
        List<BlockPos> toPush,
        List<BlockPos> toDestroy,
        Direction pushDirection
    ) {
        for (BlockPos pos : List.copyOf(toDestroy)) {
            claimDestroyBlock(level, toPush, toDestroy, pos, pushDirection);
        }
    }

    public static BlockState settleState(
        LevelAccessor level,
        BlockPos pos,
        BlockState movedState,
        BlockState updated
    ) {
        if (!updated.isAir() || !(level instanceof Level world) || !isAdhesivelyGrouped(world, pos)) {
            return updated;
        }
        return movedState;
    }

    public static boolean shouldSkipSettleNeighborUpdate(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return isAdhesivelyGrouped(level, pos) && !state.canSurvive(level, pos);
    }

    /** 活塞先搬走支撑时，源位置上的胶粘 DESTROY 方块不能再按原版打碎掉落。 */
    public static boolean shouldSuppressMovementBreakDrop(Level level, BlockPos pos) {
        if (!PistonAdhesionController.isMovingPosition(level, pos)) return false;
        return level.getBlockState(pos).getPistonPushReaction() == PushReaction.DESTROY;
    }

    private static @Nullable BlockAdhesionState groupedAdhesion(
        Level level,
        BlockPos pos,
        @Nullable BlockState state
    ) {
        return groupedAdhesion(level, pos, state, null);
    }

    private static @Nullable BlockAdhesionState groupedAdhesion(
        Level level,
        BlockPos pos,
        @Nullable BlockState state,
        @Nullable Direction pushDirection
    ) {
        BlockAdhesionState adhesion = matchingGroupedAdhesion(level, pos, state);
        if (adhesion != null) return adhesion;
        if (pushDirection == null) return null;
        // 客户端活塞事件可能晚于胶粘附件包，此时粘合数据已经位于目标坐标。
        return matchingGroupedAdhesion(level, pos.relative(pushDirection), state);
    }

    private static @Nullable BlockAdhesionState matchingGroupedAdhesion(
        Level level,
        BlockPos pos,
        @Nullable BlockState state
    ) {
        if (!level.hasChunkAt(pos)) return null;
        BlockAdhesionState adhesion = BondedFallingBlocks.getAdhesion(level, pos);
        if (adhesion == null || adhesion.blockBondMask() == 0) return null;
        if (state != null && !adhesion.matches(state)) return null;
        return adhesion;
    }
}
