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

    public ConstructionOverlayView(Level level, Map<Long, BlockState> overlay) {
        this.level = level;
        this.overlay = overlay;
    }

    public Level level() {
        return this.level;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.overlay.containsKey(pos.asLong()) ? null : this.level.getBlockEntity(pos);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        BlockState overlayState = this.overlay.get(pos.asLong());
        return overlayState != null ? overlayState : this.level.getBlockState(pos);
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
