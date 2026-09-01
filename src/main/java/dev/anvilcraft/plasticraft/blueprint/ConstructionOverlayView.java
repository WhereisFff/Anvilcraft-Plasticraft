package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * 只读蓝图覆盖视图:已交付和规划目标优先于真实世界状态,
 * 用于计算栅栏等邻接形状,不创建方块实体、不执行方块逻辑。
 */
public final class ConstructionOverlayView implements BlockGetter {
    private final Level level;
    private final Map<Long, BlockState> overlay;
    private final ConstructionProjectionIndex.PlannedOverlay planned;

    public ConstructionOverlayView(Level level, Map<Long, BlockState> overlay) {
        this(level, overlay, position -> null);
    }

    public ConstructionOverlayView(
        Level level,
        Map<Long, BlockState> overlay,
        ConstructionProjectionIndex.PlannedOverlay planned
    ) {
        this.level = level;
        this.overlay = overlay;
        this.planned = planned;
    }

    public Level level() {
        return this.level;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.overlayState(pos.asLong()) != null ? null : this.level.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState overlayState = this.overlayState(pos.asLong());
        return overlayState != null ? overlayState : this.level.getBlockState(pos);
    }

    private @Nullable BlockState overlayState(long position) {
        BlockState state = this.overlay.get(position);
        return state != null ? state : this.planned.stateAt(position);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return this.getBlockState(pos).getFluidState();
    }

    @Override
    public int getHeight() {
        return this.level.getHeight();
    }

    @Override
    public int getMinBuildHeight() {
        return this.level.getMinBuildHeight();
    }
}
