package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 安静提交:直接写入已交付的目标方块状态,不发邻居更新、不掉落、不调用假玩家。
 * 方块实体 NBT 与多方块复原留给后续 TODO。
 */
public final class ConstructionCommitService {
    private static final int QUIET_FLAGS =
        Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private ConstructionCommitService() {
    }

    public static void commitDelivered(Level level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) continue;
            BlockPos pos = op.pos();
            BlockState target = op.target();
            level.setBlock(pos, target, QUIET_FLAGS);
        }
        ConstructionProjectionIndex.clearJob(level, progress.jobId());
    }
}
