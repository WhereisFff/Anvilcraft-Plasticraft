package dev.anvilcraft.plasticraft.blueprint;

import dev.dubhe.anvilcraft.api.IHasMultiBlock;
import dev.dubhe.anvilcraft.block.multipart.AbstractMultiPartBlock;
import dev.dubhe.anvilcraft.util.AnvilUtil;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import dev.dubhe.anvilcraft.util.BreakBlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.UUID;

/**
 * 对齐普通铁砧砸中下方有切石机的方块:拒绝负硬度,再用本体公开 API 结算掉落与多方块回调。
 * 不调用 destroyBlock,也不走霜冻砧经验。
 */
public final class StonecutterSmashAdapter {
    private StonecutterSmashAdapter() {
    }

    public static boolean isPermanentObstacle(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getDestroySpeed(level, pos) < 0.0F;
    }

    public static boolean smash(ServerLevel level, BlockPos pos, UUID jobId, int operationId) {
        BlockPos breakPos = mainPartOf(level, pos);
        BlockState state = level.getBlockState(breakPos);
        if (state.isAir()) return true;
        if (isPermanentObstacle(level, breakPos, state)) return false;
        ItemStack dummyTool = BreakBlockUtil.createTool(level, state, BlockMiningEffect.NORMAL);
        state.spawnAfterBreak(level, breakPos, dummyTool, false);
        if (state.getBlock() instanceof IHasMultiBlock multiBlock) {
            multiBlock.onRemove(level, breakPos, state);
        }
        List<ItemStack> drops = BreakBlockUtil.drop(level, breakPos, BlockMiningEffect.NORMAL);
        for (ItemStack drop : drops) {
            ConstructionDebris.mark(drop, jobId, operationId);
        }
        AnvilUtil.dropItems(drops, level, breakPos.getCenter());
        level.setBlockAndUpdate(breakPos, Blocks.AIR.defaultBlockState());
        return true;
    }

    public static BlockPos mainPartOf(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbstractMultiPartBlock<?> multiPartBlock) {
            BlockPos main = multiPartBlock.getMainPartPos(pos, state);
            if (level.getBlockState(main).is(multiPartBlock)) {
                return main;
            }
        }
        return pos;
    }
}
