package dev.anvilcraft.plasticraft.blueprint;

import dev.dubhe.anvilcraft.block.multipart.AbstractMultiPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * 建造侧多方块折叠:核心 PLACE 只消耗一份控制器物品,其余 part 为 ATTACHED。
 * 门上半/床头/活塞头/成对大箱子左半仍由 OrdinaryBlockAdapter 识别,这里只提供核心坐标。
 */
public final class MultiblockBuildAdapter {
    private MultiblockBuildAdapter() {
    }

    public static boolean isMultiPart(BlockState state) {
        return state.getBlock() instanceof AbstractMultiPartBlock<?>;
    }

    public static boolean isCore(BlockPos pos, BlockState state) {
        return pos.equals(coreOf(pos, state));
    }

    public static BlockPos coreOf(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof AbstractMultiPartBlock<?> multiPartBlock) {
            return multiPartBlock.getMainPartPos(pos, state);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
            && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
            && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD
            && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        }
        if (state.getBlock() instanceof PistonHeadBlock && state.hasProperty(BlockStateProperties.FACING)) {
            return pos.relative(state.getValue(BlockStateProperties.FACING).getOpposite());
        }
        if (OrdinaryBlockAdapter.isDoubleChestHalf(state)
            && state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.LEFT) {
            return OrdinaryBlockAdapter.partnerPos(pos, state);
        }
        return pos;
    }

    public static ItemStack coreMaterial(BlockState state) {
        if (!isMultiPart(state)) {
            return ItemStack.EMPTY;
        }
        if (state.getBlock().asItem() == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(state.getBlock().asItem());
    }

    /** 静默写入后多方块只依赖已写出的部件状态;公开 API 没有额外装配回调。 */
    public static void restore(Level level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) {
                continue;
            }
            if (!isMultiPart(op.target()) || !isCore(op.pos(), op.target())) {
                continue;
            }
            if (!level.getBlockState(op.pos()).is(op.target().getBlock())) {
                ConstructionCommitService.quietSet(level, op.pos(), op.target());
            }
        }
    }
}
