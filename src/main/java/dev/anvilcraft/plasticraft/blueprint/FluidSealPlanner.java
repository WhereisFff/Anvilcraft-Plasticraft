package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * 只在蓝图声明格内洪泛流体,并在区外一格建立封堵壳切断流入,不追壳外海洋。
 * 含水固体不替换,留给拆除。
 */
public final class FluidSealPlanner {
    private FluidSealPlanner() {
    }

    public static void plan(ServerLevel level, Set<BlockPos> declared, ConstructionJobProgress progress) {
        Set<BlockPos> flooded = floodDeclaredFluids(level, declared);
        Set<BlockPos> shell = new HashSet<>();
        for (BlockPos pos : flooded) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                if (declared.contains(neighbor) || flooded.contains(neighbor) || shell.contains(neighbor)) {
                    continue;
                }
                if (needsShell(level, neighbor, direction)) {
                    shell.add(neighbor);
                }
            }
        }
        for (BlockPos pos : flooded) {
            addSeal(progress, pos, false);
        }
        for (BlockPos pos : shell) {
            addSeal(progress, pos, true);
        }
    }

    public static void applyFill(ConstructionJobProgress progress, ItemStack fill) {
        if (fill.isEmpty()) return;
        ItemStack material = fill.copyWithCount(1);
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.SEAL) continue;
            if (!op.material().isEmpty()) continue;
            op.setMaterial(material);
        }
    }

    private static void addSeal(ConstructionJobProgress progress, BlockPos pos, boolean shell) {
        progress.addOperation(
            pos,
            Blocks.AIR.defaultBlockState(),
            ItemStack.EMPTY,
            ConstructionBuildOp.Kind.SEAL,
            ConstructionBuildOp.Status.PENDING,
            shell
        );
    }

    private static Set<BlockPos> floodDeclaredFluids(ServerLevel level, Set<BlockPos> declared) {
        Set<BlockPos> flooded = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos pos : declared) {
            if (!isSealableFluid(level.getBlockState(pos))) continue;
            if (!flooded.add(pos.immutable())) continue;
            queue.addLast(pos.immutable());
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (!declared.contains(next) || !isSealableFluid(level.getBlockState(next))) continue;
                if (flooded.add(next.immutable())) {
                    queue.addLast(next.immutable());
                }
            }
        }
        return flooded;
    }

    static boolean isSealableFluid(BlockState state) {
        if (state.getFluidState().isEmpty()) return false;
        if (state.getBlock() instanceof LiquidBlock) return true;
        return state.canBeReplaced() && !OrdinaryBlockAdapter.isAttachedHalf(state);
    }

    private static boolean needsShell(ServerLevel level, BlockPos neighbor, Direction fromFlood) {
        BlockState state = level.getBlockState(neighbor);
        if (!state.getFluidState().isEmpty()) return isSealableFluid(state) || state.canBeReplaced();
        if (!state.isAir() && !state.canBeReplaced()) return false;
        return fromFlood != Direction.UP;
    }
}
