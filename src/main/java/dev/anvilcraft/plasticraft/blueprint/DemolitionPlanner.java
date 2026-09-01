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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 扫描声明格上的可拆固体与封堵位,永久障碍跳过对应 PLACE;
 * 多方块与门/床/活塞头折到核心,只拆一次。
 * 保留空白策略下只拆与蓝图实体格重合的世界方块,空白格原样留下。
 */
public final class DemolitionPlanner {
    private DemolitionPlanner() {
    }

    public static void plan(ServerLevel level, Set<BlockPos> declared, ConstructionJobProgress progress) {
        plan(level, declared, declared, progress);
    }

    /**
     * @param declared 蓝图声明的全部格,决定折叠后的核心是否算在工地内
     * @param demolishable 允许拆除的格;保留空白策略下只含蓝图会放置方块的格
     */
    public static void plan(
        ServerLevel level,
        Set<BlockPos> declared,
        Set<BlockPos> demolishable,
        ConstructionJobProgress progress
    ) {
        Set<Long> cores = new HashSet<>();
        for (BlockPos pos : declared) {
            if (!demolishable.contains(pos)) continue;
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
        // 封堵填充块是施工自己放下的,必须无条件清走,与空白格清场策略无关
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
        ConstructionJobController.withoutWorldChangeObservation(() -> {
            for (BlockPos neighbor : attachedResiduePositions(level, smashed)) {
                level.setBlockAndUpdate(neighbor, Blocks.AIR.defaultBlockState());
            }
            return null;
        });
    }

    /** 返回砸击后清理附属残留的实际方块位置，供权限检查和执行共用。 */
    public static List<BlockPos> attachedResiduePositions(ServerLevel level, BlockPos smashed) {
        List<BlockPos> result = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = smashed.relative(direction);
            BlockState state = level.getBlockState(neighbor);
            if (state.isAir()) continue;
            if (OrdinaryBlockAdapter.isAttachedHalf(state) || coreOf(level, neighbor, state).equals(smashed)) {
                result.add(neighbor.immutable());
            }
        }
        return result;
    }

    /** 返回拆除操作会触及的核心及附属残留位置。 */
    public static List<BlockPos> affectedPositions(ServerLevel level, BlockPos requested) {
        BlockPos smashed = StonecutterSmashAdapter.mainPartOf(level, requested);
        Set<BlockPos> result = new LinkedHashSet<>();
        result.add(smashed.immutable());
        BlockState state = level.getBlockState(smashed);
        if (state.getBlock() instanceof AbstractMultiPartBlock<?> multiPartBlock) {
            addMultiPartPositions(result, smashed, state, multiPartBlock);
        }
        result.addAll(attachedResiduePositions(level, smashed));
        return List.copyOf(result);
    }

    /** 为施工期间后来出现的真实方块追加一次拆除操作，组合件优先折叠到当前核心。 */
    public static BlockPos runtimeCore(
        ServerLevel level,
        BlockPos requested,
        ConstructionJobProgress progress
    ) {
        BlockState requestedState = level.getBlockState(requested);
        BlockPos core = coreOf(level, requested, requestedState);
        if (!progress.hasDeclaredTarget(core)
            || (level.getBlockState(core).isAir() && !requestedState.isAir())) {
            return requested.immutable();
        }
        return core.immutable();
    }

    public static ConstructionBuildOp addRuntimeDemolition(
        ServerLevel level,
        BlockPos requested,
        ConstructionJobProgress progress
    ) {
        BlockPos core = runtimeCore(level, requested, progress);
        BlockState coreState = level.getBlockState(core);
        ConstructionBuildOp op = progress.addOperation(
            core,
            coreState,
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.DEMOLISH,
            ConstructionBuildOp.Status.PENDING
        );
        op.setReactive(true);
        return op;
    }

    private static <P extends Enum<P>> void addMultiPartPositions(
        Set<BlockPos> positions,
        BlockPos main,
        BlockState state,
        AbstractMultiPartBlock<P> block
    ) {
        if (!state.hasProperty(block.getPart())) return;
        for (P part : block.getParts()) {
            positions.add(main.offset(block.offsetFrom(state, part)).immutable());
        }
    }

    public static void skipPlaceAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.ATTACHED
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.DECORATE) {
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
        for (ConstructionBuildOp existing : progress.operationsAt(pos)) {
            if (existing.kind() == ConstructionBuildOp.Kind.DEMOLISH && existing.shell() == shell) {
                return;
            }
        }
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
