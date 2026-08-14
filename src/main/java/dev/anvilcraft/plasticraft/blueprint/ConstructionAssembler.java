package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 对目标碰撞做反向可拆解:从蓝图外部可达空间剥除能一格触及的表面块;
 * 正向施工使用拆解逆序,从而先完成内部、最后从外侧封口。
 */
public final class ConstructionAssembler {
    private ConstructionAssembler() {
    }

    public static void assignBuildOrder(Level level, ConstructionJobProgress progress) {
        Map<Long, BlockState> overlay = progress.overlayStates();
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        List<ConstructionBuildOp> colliding = new ArrayList<>();
        List<ConstructionBuildOp> nonColliding = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
            VoxelShape shape = op.target().getCollisionShape(view, op.pos(), CollisionContext.empty());
            if (shape.isEmpty()) {
                nonColliding.add(op);
            } else {
                colliding.add(op);
            }
        }
        List<BlockPos> collidingPos = new ArrayList<>();
        for (ConstructionBuildOp op : colliding) {
            collidingPos.add(op.pos());
        }
        List<BlockPos> removal = peelOrder(collidingPos);
        int order = 0;
        for (int index = removal.size() - 1; index >= 0; index--) {
            BlockPos pos = removal.get(index);
            for (ConstructionBuildOp op : colliding) {
                if (op.pos().equals(pos)) {
                    op.setOrder(order++);
                    break;
                }
            }
        }
        Set<Long> ordered = new HashSet<>();
        for (BlockPos pos : removal) {
            ordered.add(pos.asLong());
        }
        colliding.sort(Comparator
            .comparingInt((ConstructionBuildOp op) -> op.pos().getY())
            .thenComparingInt(op -> -op.pos().getZ())
            .thenComparingInt(op -> op.pos().getX()));
        for (ConstructionBuildOp op : colliding) {
            if (ordered.add(op.pos().asLong())) {
                op.setOrder(order++);
            }
        }
        nonColliding.sort(Comparator
            .comparingInt((ConstructionBuildOp op) -> op.pos().getY())
            .thenComparingInt(op -> -op.pos().getZ())
            .thenComparingInt(op -> op.pos().getX()));
        for (ConstructionBuildOp op : nonColliding) {
            op.setOrder(order++);
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            ConstructionBuildOp parent = parentOf(progress, op);
            op.setOrder(parent == null ? order++ : parent.order());
        }
    }

    private static ConstructionBuildOp parentOf(ConstructionJobProgress progress, ConstructionBuildOp attached) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = attached.pos().relative(direction);
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.kind() == ConstructionBuildOp.Kind.PLACE && op.pos().equals(neighbor)) {
                    return op;
                }
            }
        }
        return null;
    }

    /** 由外向内剥离顺序;拆除用正向,建造用其逆序。 */
    public static List<BlockPos> peelOrder(List<BlockPos> cells) {
        Set<Long> remaining = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            remaining.add(pos.asLong());
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if (remaining.isEmpty()) return List.of();
        minX--;
        minY--;
        minZ--;
        maxX++;
        maxY++;
        maxZ++;
        Set<Long> exterior = floodExterior(remaining, minX, minY, minZ, maxX, maxY, maxZ);
        List<BlockPos> removal = new ArrayList<>();
        while (!remaining.isEmpty()) {
            BlockPos best = null;
            for (BlockPos pos : cells) {
                if (!remaining.contains(pos.asLong())) continue;
                if (!touchesExterior(pos, remaining, exterior)) continue;
                if (betterPeel(best, pos)) {
                    best = pos;
                }
            }
            if (best == null) {
                for (BlockPos pos : cells) {
                    if (remaining.contains(pos.asLong()) && betterPeel(best, pos)) {
                        best = pos;
                    }
                }
            }
            if (best == null) break;
            remaining.remove(best.asLong());
            removal.add(best);
            expandExterior(best, remaining, exterior, minX, minY, minZ, maxX, maxY, maxZ);
        }
        return removal;
    }

    private static boolean betterPeel(@Nullable BlockPos current, BlockPos candidate) {
        if (current == null) return true;
        int y = Integer.compare(candidate.getY(), current.getY());
        if (y != 0) return y > 0;
        int z = Integer.compare(candidate.getZ(), current.getZ());
        if (z != 0) return z < 0;
        return candidate.getX() < current.getX();
    }

    private static boolean touchesExterior(BlockPos pos, Set<Long> remaining, Set<Long> exterior) {
        for (Direction direction : Direction.values()) {
            long key = pos.relative(direction).asLong();
            if (exterior.contains(key) && !remaining.contains(key)) return true;
        }
        return false;
    }

    private static Set<Long> floodExterior(
        Set<Long> remaining,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        Set<Long> exterior = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                offerSurface(new BlockPos(x, y, minZ), remaining, exterior, queue);
                offerSurface(new BlockPos(x, y, maxZ), remaining, exterior, queue);
            }
        }
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                offerSurface(new BlockPos(minX, y, z), remaining, exterior, queue);
                offerSurface(new BlockPos(maxX, y, z), remaining, exterior, queue);
            }
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                offerSurface(new BlockPos(x, minY, z), remaining, exterior, queue);
                offerSurface(new BlockPos(x, maxY, z), remaining, exterior, queue);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.getX() < minX || next.getX() > maxX
                    || next.getY() < minY || next.getY() > maxY
                    || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                long key = next.asLong();
                if (remaining.contains(key) || !exterior.add(key)) continue;
                queue.addLast(next);
            }
        }
        return exterior;
    }

    private static void offerSurface(
        BlockPos pos,
        Set<Long> remaining,
        Set<Long> exterior,
        ArrayDeque<BlockPos> queue
    ) {
        long key = pos.asLong();
        if (remaining.contains(key) || !exterior.add(key)) return;
        queue.addLast(pos);
    }

    private static void expandExterior(
        BlockPos freed,
        Set<Long> remaining,
        Set<Long> exterior,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        if (exterior.add(freed.asLong())) {
            queue.addLast(freed);
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (next.getX() < minX || next.getX() > maxX
                    || next.getY() < minY || next.getY() > maxY
                    || next.getZ() < minZ || next.getZ() > maxZ) {
                    continue;
                }
                long key = next.asLong();
                if (remaining.contains(key) || !exterior.add(key)) continue;
                queue.addLast(next);
            }
        }
    }
}
