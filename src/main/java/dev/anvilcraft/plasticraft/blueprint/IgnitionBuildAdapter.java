package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 点火先交付施工意图，等实体结构提交完毕后才让火焰与传送门生效。 */
public final class IgnitionBuildAdapter {
    private IgnitionBuildAdapter() {
    }

    public static boolean supports(BlockState state) {
        return state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.NETHER_PORTAL);
    }

    public static boolean hasWork(ConstructionJobProgress progress) {
        return progress.openOperations(ConstructionBuildOp.Kind.PLACE).stream()
            .anyMatch(op -> supports(op.target()));
    }

    public static boolean hasReadyWork(ServerLevel level, ConstructionJobProgress progress) {
        return progress.openOperations(ConstructionBuildOp.Kind.PLACE).stream()
            .anyMatch(op -> supports(op.target()) && isReady(level, progress, op));
    }

    public static boolean canDeliver(WorkingAllayEntity worker, ConstructionBuildOp op) {
        boolean ignition = worker.toolDefinition().hasCapability(AllayCapability.IGNITE);
        return supports(op.target()) ? ignition && op.kind() == ConstructionBuildOp.Kind.PLACE : !ignition;
    }

    public static boolean isReady(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        if (!supports(op.target())) return true;
        List<ConstructionBuildOp> group = ignitionGroup(progress, op);
        if (!isAreaLoaded(level, group)) return false;
        // 普通邻接视图含尚未交付的规划格；点火只能依附真实方块或已经交付的投影。
        ConstructionOverlayView view = new ConstructionOverlayView(level, Map.of(), position -> {
            for (ConstructionBuildOp cell : progress.operationsAt(BlockPos.of(position))) {
                if (cell.writesProjection() && cell.status() == ConstructionBuildOp.Status.DELIVERED
                    && !cell.worldSatisfied()) return cell.target();
            }
            return null;
        });
        if (op.target().is(Blocks.NETHER_PORTAL)) return portalBottom(level, view, group, op) != null;
        BlockPos below = op.pos().below();
        BlockState support = view.getBlockState(below);
        if (op.target().is(Blocks.SOUL_FIRE)) return SoulFireBlock.canSurviveOnBlock(support);
        if (support.isFaceSturdy(view, below, Direction.UP)) return true;
        for (Direction direction : Direction.values()) {
            if (((FireBlock) Blocks.FIRE).canCatchFire(view, op.pos().relative(direction), direction.getOpposite())) {
                return true;
            }
        }
        return false;
    }

    static void skipInvalid(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp.Kind kind : List.of(ConstructionBuildOp.Kind.PLACE, ConstructionBuildOp.Kind.ATTACHED)) {
            if (progress.openOperations(kind).stream().anyMatch(op -> !supports(op.target()))) return;
        }
        // 普通方块仍在施工时继续等支撑；全部交付后仍无效的点火按残缺收尾，避免阻塞最终提交。
        for (ConstructionBuildOp op : List.copyOf(progress.openOperations(ConstructionBuildOp.Kind.PLACE))) {
            List<ConstructionBuildOp> group = ignitionGroup(progress, op);
            if (!isAreaLoaded(level, group) || isReady(level, progress, op)) continue;
            for (ConstructionBuildOp cell : group) {
                cell.setStatus(ConstructionBuildOp.Status.SKIPPED);
                cell.setLeaseAllay(null);
            }
            progress.setIncomplete(true);
        }
    }

    private static List<ConstructionBuildOp> ignitionGroup(ConstructionJobProgress progress, ConstructionBuildOp op) {
        List<ConstructionBuildOp> group = new ArrayList<>();
        group.add(op);
        group.addAll(progress.childrenOf(op));
        return group;
    }

    private static boolean isAreaLoaded(ServerLevel level, List<ConstructionBuildOp> group) {
        for (ConstructionBuildOp cell : group) {
            if (!level.hasChunkAt(cell.pos())) return false;
            for (Direction direction : Direction.values()) {
                if (!level.hasChunkAt(cell.pos().relative(direction))) return false;
            }
        }
        return true;
    }

    public static void plan(ConstructionJobProgress progress, BlockPos pos, BlockState target,
                            Map<Long, BlockState> targets) {
        if (!progress.operationsAt(pos).isEmpty()) return;
        ConstructionBuildOp core = progress.addOperation(pos, target, ItemStack.EMPTY,
            ConstructionBuildOp.Kind.PLACE, ConstructionBuildOp.Status.PENDING);
        if (!target.is(Blocks.NETHER_PORTAL)) return;
        for (BlockPos cell : portalCells(pos, targets)) {
            if (cell.equals(pos)) continue;
            ConstructionBuildOp child = progress.addOperation(cell, targets.get(cell.asLong()), ItemStack.EMPTY,
                ConstructionBuildOp.Kind.ATTACHED, ConstructionBuildOp.Status.PENDING);
            child.setParentId(core.id());
        }
    }

    private static Set<BlockPos> portalCells(BlockPos start, Map<Long, BlockState> targets) {
        Set<BlockPos> cells = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockState state = targets.get(start.asLong());
        Direction.Axis axis = state.getValue(NetherPortalBlock.AXIS);
        queue.add(start);
        cells.add(start);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                if (direction.getAxis() != Direction.Axis.Y && direction.getAxis() != axis) continue;
                BlockPos next = pos.relative(direction);
                BlockState candidate = targets.get(next.asLong());
                if (candidate != null && candidate.is(Blocks.NETHER_PORTAL)
                    && candidate.getValue(NetherPortalBlock.AXIS) == axis && cells.add(next)) {
                    queue.addLast(next);
                }
            }
        }
        return cells;
    }

    public static void consumeUse(ServerLevel level, WorkingAllayEntity worker, BlockPos pos) {
        if (worker.getMainHandItem().is(Items.FIRE_CHARGE)) {
            worker.getMainHandItem().shrink(1);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return;
        }
        worker.getMainHandItem().hurtAndBreak(1, worker, EquipmentSlot.MAINHAND);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    public static void commit(Level level, ConstructionJobProgress progress) {
        if (!(level instanceof ServerLevel server)) return;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE || !supports(op.target())
                || op.status() != ConstructionBuildOp.Status.DELIVERED || op.worldSatisfied()) continue;
            List<ConstructionBuildOp> group = ignitionGroup(progress, op);
            boolean ready = group.stream().allMatch(cell -> cell.status() == ConstructionBuildOp.Status.DELIVERED
                && server.hasChunkAt(cell.pos())
                && ConstructionJobController.enterIfDenied(server, progress, cell));
            if (ready && op.target().is(Blocks.NETHER_PORTAL)) {
                ready = commitPortal(server, group, op);
            } else if (ready) {
                ready = (level.getBlockState(op.pos()).isAir() || level.getBlockState(op.pos()).is(op.target().getBlock()))
                    && op.target().canSurvive(level, op.pos());
                if (ready) level.setBlock(op.pos(), op.target(), Block.UPDATE_ALL);
            }
            if (!ready) progress.setIncomplete(true);
        }
    }

    private static boolean commitPortal(ServerLevel level, List<ConstructionBuildOp> group, ConstructionBuildOp core) {
        BlockPos bottom = portalBottom(level, level, group, core);
        if (bottom == null) return false;
        PortalShape shape = new PortalShape(level, bottom, core.target().getValue(NetherPortalBlock.AXIS));
        if (!shape.isValid()) return false;
        shape.createPortalBlocks();
        return true;
    }

    private static @Nullable BlockPos portalBottom(
        ServerLevel level, BlockGetter view, List<ConstructionBuildOp> group, ConstructionBuildOp core
    ) {
        if (level.dimension() != Level.OVERWORLD && level.dimension() != Level.NETHER) return null;
        Direction.Axis axis = core.target().getValue(NetherPortalBlock.AXIS);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (ConstructionBuildOp op : group) {
            min = Math.min(min, op.pos().get(axis));
            max = Math.max(max, op.pos().get(axis));
            minY = Math.min(minY, op.pos().getY());
            maxY = Math.max(maxY, op.pos().getY());
            if (!level.hasChunkAt(op.pos()) || op.status() == ConstructionBuildOp.Status.SKIPPED) return null;
            BlockState present = view.getBlockState(op.pos());
            if (!present.isAir() && !present.is(Blocks.FIRE) && !present.is(Blocks.NETHER_PORTAL)) return null;
        }
        int width = max - min + 1;
        int height = maxY - minY + 1;
        if (width < 2 || width > 21 || height < 3 || height > 21 || group.size() != width * height) return null;
        BlockPos bottom = axis == Direction.Axis.X
            ? new BlockPos(min, minY, core.pos().getZ()) : new BlockPos(core.pos().getX(), minY, min);
        Direction along = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        // 显式锁定蓝图矩形的边框，防止原版搜索扩到蓝图外并点亮未交付的区域。
        for (int x = 0; x < width; x++) {
            if (!isFrame(level, view, bottom.relative(along, x).below())
                || !isFrame(level, view, bottom.relative(along, x).above(height))) return null;
        }
        for (int y = 0; y < height; y++) {
            if (!isFrame(level, view, bottom.relative(along, -1).above(y))
                || !isFrame(level, view, bottom.relative(along, width).above(y))) return null;
        }
        return bottom;
    }

    private static boolean isFrame(ServerLevel level, BlockGetter view, BlockPos pos) {
        return level.hasChunkAt(pos) && view.getBlockState(pos).isPortalFrame(view, pos);
    }
}
