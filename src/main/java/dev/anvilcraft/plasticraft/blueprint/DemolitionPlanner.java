package dev.anvilcraft.plasticraft.blueprint;

import dev.dubhe.anvilcraft.block.multipart.AbstractMultiPartBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonHeadBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描声明格上的可拆固体与封堵位,永久障碍跳过对应 PLACE;
 * 多方块与门/床/活塞头折到核心,只拆一次。
 */
public final class DemolitionPlanner {
    private DemolitionPlanner() {
    }

    public static void plan(ServerLevel level, Set<BlockPos> declared, ConstructionJobProgress progress) {
        Set<Long> cores = new HashSet<>();
        for (BlockPos pos : declared) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || FluidSealPlanner.isSealableFluid(state)) continue;
            if (StonecutterSmashAdapter.isPermanentObstacle(level, pos, state)) {
                skipPlaceAt(progress, pos);
                continue;
            }
            BlockPos core = coreOf(level, pos, state);
            if (!cores.add(core.asLong())) continue;
            BlockState coreState = level.getBlockState(core);
            if (StonecutterSmashAdapter.isPermanentObstacle(level, core, coreState)) {
                skipPlaceAt(progress, pos);
                skipPlaceAt(progress, core);
                continue;
            }
            addDemolish(progress, core, coreState, false);
        }
        List<ConstructionBuildOp> seals = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.SEAL) {
                seals.add(op);
            }
        }
        for (ConstructionBuildOp op : seals) {
            if (!cores.add(op.pos().asLong())) continue;
            addDemolish(progress, op.pos(), Blocks.AIR.defaultBlockState(), op.shell());
        }
    }

    public static BlockPos coreOf(ServerLevel level, BlockPos pos, BlockState state) {
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
        return pos;
    }

    public static void clearAttachedResidue(ServerLevel level, BlockPos smashed) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = smashed.relative(direction);
            BlockState state = level.getBlockState(neighbor);
            if (state.isAir()) continue;
            if (OrdinaryBlockAdapter.isAttachedHalf(state) || coreOf(level, neighbor, state).equals(smashed)) {
                level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
            }
        }
    }

    public static void skipPlaceAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.ATTACHED
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID) {
                continue;
            }
            if (!op.pos().equals(pos)) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) continue;
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            op.setLeaseAllay(null);
            progress.setIncomplete(true);
        }
    }

    private static void addDemolish(
        ConstructionJobProgress progress,
        BlockPos pos,
        BlockState target,
        boolean shell
    ) {
        progress.addOperation(
            pos,
            target,
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.PENDING,
            shell
        );
    }
}
