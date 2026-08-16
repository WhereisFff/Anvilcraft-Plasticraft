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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 对目标碰撞做反向可拆解:从蓝图外部可达空间剥除能一格触及的表面块;
 * 正向施工使用拆解逆序,从而先完成内部、最后从外侧封口。
 */
public final class ConstructionAssembler {
    private static final long MIN_EXACT_EXTERIOR_VOLUME = 1_048_576L;
    private static final int EXACT_EXTERIOR_VOLUME_PER_CELL = 64;

    private ConstructionAssembler() {
    }

    public static void assignBuildOrder(Level level, ConstructionJobProgress progress) {
        Map<Long, BlockState> overlay = progress.overlayStates();
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        List<ConstructionBuildOp> colliding = new ArrayList<>();
        List<ConstructionBuildOp> nonColliding = new ArrayList<>();
        Map<Long, ConstructionBuildOp> collidingByPosition = new HashMap<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
            VoxelShape shape = op.target().getCollisionShape(view, op.pos(), CollisionContext.empty());
            if (shape.isEmpty()) {
                nonColliding.add(op);
            } else {
                colliding.add(op);
                collidingByPosition.put(op.pos().asLong(), op);
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
            ConstructionBuildOp op = collidingByPosition.get(pos.asLong());
            if (op != null) {
                op.setOrder(order++);
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
        Map<Integer, ConstructionBuildOp> byId = new HashMap<>();
        Map<Long, ConstructionBuildOp> placesByPosition = new HashMap<>();
        for (ConstructionBuildOp op : progress.operations()) {
            byId.put(op.id(), op);
            if (op.kind() == ConstructionBuildOp.Kind.PLACE) {
                placesByPosition.put(op.pos().asLong(), op);
            }
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY) {
                continue;
            }
            ConstructionBuildOp parent = parentOf(op, byId, placesByPosition);
            op.setOrder(parent == null ? order++ : parent.order());
        }
    }

    private static ConstructionBuildOp parentOf(
        ConstructionBuildOp attached,
        Map<Integer, ConstructionBuildOp> byId,
        Map<Long, ConstructionBuildOp> placesByPosition
    ) {
        if (attached.parentId() >= 0) {
            return byId.get(attached.parentId());
        }
        BlockPos core = MultiblockBuildAdapter.coreOf(attached.pos(), attached.target());
        ConstructionBuildOp parent = placesByPosition.get(core.asLong());
        if (parent != null) return parent;
        for (Direction direction : Direction.values()) {
            parent = placesByPosition.get(attached.pos().relative(direction).asLong());
            if (parent != null) return parent;
        }
        return null;
    }

    /** 蓝图碰撞外包络,封闭检测用它判断洪泛是否通到室外。 */
    public static Set<Long> exteriorOf(List<BlockPos> cells) {
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
        if (remaining.isEmpty()) return Set.of();
        minX--;
        minY--;
        minZ--;
        maxX++;
        maxY++;
        maxZ++;
        if (usesSparseFallback(remaining.size(), minX, minY, minZ, maxX, maxY, maxZ)) {
            return sparseTopology(remaining, minY, maxY).exterior();
        }
        return floodExterior(remaining, minX, minY, minZ, maxX, maxY, maxZ);
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
        if (usesSparseFallback(remaining.size(), minX, minY, minZ, maxX, maxY, maxZ)) {
            return sparsePeelOrder(cells, remaining, sparseTopology(remaining, minY, maxY));
        }
        Set<Long> exterior = floodExterior(remaining, minX, minY, minZ, maxX, maxY, maxZ);
        List<BlockPos> removal = new ArrayList<>();
        PriorityQueue<BlockPos> available = new PriorityQueue<>(Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .reversed()
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));
        Set<Long> offered = new HashSet<>();
        for (BlockPos pos : cells) {
            offerExposed(pos, remaining, exterior, available, offered);
        }
        while (!remaining.isEmpty()) {
            BlockPos best = pollRemaining(available, remaining);
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
            expandExterior(
                best,
                remaining,
                exterior,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                available,
                offered
            );
        }
        return removal;
    }

    @Nullable
    private static BlockPos pollRemaining(PriorityQueue<BlockPos> available, Set<Long> remaining) {
        while (!available.isEmpty()) {
            BlockPos candidate = available.poll();
            if (remaining.contains(candidate.asLong())) return candidate;
        }
        return null;
    }

    private static void offerExposed(
        BlockPos pos,
        Set<Long> remaining,
        Set<Long> exterior,
        PriorityQueue<BlockPos> available,
        Set<Long> offered
    ) {
        long key = pos.asLong();
        if (!remaining.contains(key) || offered.contains(key)) return;
        if (!touchesExterior(pos, remaining, exterior)) return;
        offered.add(key);
        available.add(pos);
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
        // 包围盒已向外扩一格，一个外角即可连通全部真实外部，避免先枚举六个完整表面。
        offerSurface(new BlockPos(minX, minY, minZ), remaining, exterior, queue);
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

    /**
     * 极稀疏结构把每根竖直柱中的连续空气压成区间，再按相邻柱的重叠区间合并。
     * 这样能精确区分弯折开口与封闭内腔，同时不遍历巨大空包围盒。
     */
    private static SparseTopology sparseTopology(Set<Long> occupied, int minY, int maxY) {
        Map<Long, AirColumn> columns = new HashMap<>();
        for (long key : occupied) {
            BlockPos pos = BlockPos.of(key);
            columns.computeIfAbsent(columnKey(pos.getX(), pos.getZ()), ignored -> new AirColumn(
                pos.getX(),
                pos.getZ()
            )).occupiedY.add(pos.getY());
        }
        List<AirInterval> intervals = new ArrayList<>();
        for (AirColumn column : columns.values()) {
            column.buildIntervals(minY, maxY, intervals);
        }
        UnionFind components = new UnionFind(intervals.size());
        for (AirColumn column : columns.values()) {
            AirColumn east = columns.get(columnKey(column.x + 1, column.z));
            if (east != null) connectColumns(column, east, components);
            AirColumn south = columns.get(columnKey(column.x, column.z + 1));
            if (south != null) connectColumns(column, south, components);
        }
        Set<Integer> exteriorComponents = new HashSet<>();
        for (AirColumn column : columns.values()) {
            boolean openSide = columns.get(columnKey(column.x - 1, column.z)) == null
                || columns.get(columnKey(column.x + 1, column.z)) == null
                || columns.get(columnKey(column.x, column.z - 1)) == null
                || columns.get(columnKey(column.x, column.z + 1)) == null;
            for (AirInterval interval : column.intervals) {
                if (openSide || interval.minY == minY || interval.maxY == maxY) {
                    exteriorComponents.add(components.find(interval.id));
                }
            }
        }
        Set<Long> exterior = new HashSet<>();
        Map<Long, Integer> surfaceComponents = new HashMap<>();
        Map<Integer, List<BlockPos>> cavityBoundaries = new HashMap<>();
        for (long key : occupied) {
            BlockPos pos = BlockPos.of(key);
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = pos.relative(direction);
                long neighborKey = neighbor.asLong();
                if (occupied.contains(neighborKey)) continue;
                AirColumn column = columns.get(columnKey(neighbor.getX(), neighbor.getZ()));
                int component = column == null
                    ? SparseTopology.EXTERIOR
                    : components.find(column.intervalAt(neighbor.getY()).id);
                surfaceComponents.put(neighborKey, component);
                if (component == SparseTopology.EXTERIOR || exteriorComponents.contains(component)) {
                    exterior.add(neighborKey);
                } else {
                    cavityBoundaries.computeIfAbsent(component, ignored -> new ArrayList<>()).add(pos);
                }
            }
        }
        return new SparseTopology(exterior, surfaceComponents, cavityBoundaries);
    }

    private static List<BlockPos> sparsePeelOrder(
        List<BlockPos> cells,
        Set<Long> remaining,
        SparseTopology topology
    ) {
        List<BlockPos> removal = new ArrayList<>();
        PriorityQueue<BlockPos> available = new PriorityQueue<>(Comparator
            .comparingInt((BlockPos pos) -> pos.getY())
            .reversed()
            .thenComparingInt(BlockPos::getZ)
            .thenComparingInt(BlockPos::getX));
        Set<Long> offered = new HashSet<>();
        Set<Integer> openedCavities = new HashSet<>();
        for (BlockPos pos : cells) {
            for (Direction direction : Direction.values()) {
                if (topology.exterior.contains(pos.relative(direction).asLong())) {
                    offerAvailable(pos, remaining, available, offered);
                    break;
                }
            }
        }
        while (!remaining.isEmpty()) {
            BlockPos best = pollRemaining(available, remaining);
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
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = best.relative(direction);
                long key = neighbor.asLong();
                if (remaining.contains(key)) {
                    offerAvailable(neighbor, remaining, available, offered);
                    continue;
                }
                Integer component = topology.surfaceComponents.get(key);
                if (component == null
                    || component == SparseTopology.EXTERIOR
                    || topology.exterior.contains(key)
                    || !openedCavities.add(component)) {
                    continue;
                }
                for (BlockPos boundary : topology.cavityBoundaries.getOrDefault(component, List.of())) {
                    offerAvailable(boundary, remaining, available, offered);
                }
            }
        }
        return removal;
    }

    private static void offerAvailable(
        BlockPos pos,
        Set<Long> remaining,
        PriorityQueue<BlockPos> available,
        Set<Long> offered
    ) {
        long key = pos.asLong();
        if (remaining.contains(key) && offered.add(key)) {
            available.add(pos);
        }
    }

    private static long columnKey(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    private static void connectColumns(AirColumn first, AirColumn second, UnionFind components) {
        int firstIndex = 0;
        int secondIndex = 0;
        while (firstIndex < first.intervals.size() && secondIndex < second.intervals.size()) {
            AirInterval firstInterval = first.intervals.get(firstIndex);
            AirInterval secondInterval = second.intervals.get(secondIndex);
            if (firstInterval.maxY < secondInterval.minY) {
                firstIndex++;
                continue;
            }
            if (secondInterval.maxY < firstInterval.minY) {
                secondIndex++;
                continue;
            }
            components.union(firstInterval.id, secondInterval.id);
            if (firstInterval.maxY <= secondInterval.maxY) firstIndex++;
            if (secondInterval.maxY <= firstInterval.maxY) secondIndex++;
        }
    }

    private static boolean usesSparseFallback(
        int cellCount,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        long volume = (long) (maxX - minX + 1)
            * (maxY - minY + 1)
            * (maxZ - minZ + 1);
        long proportionalBudget = (long) cellCount * EXACT_EXTERIOR_VOLUME_PER_CELL;
        return volume > Math.max(MIN_EXACT_EXTERIOR_VOLUME, proportionalBudget);
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
        int maxZ,
        PriorityQueue<BlockPos> available,
        Set<Long> offered
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
                if (remaining.contains(key)) {
                    if (offered.add(key)) available.add(next);
                    continue;
                }
                if (!exterior.add(key)) continue;
                queue.addLast(next);
            }
        }
    }

    private record SparseTopology(
        Set<Long> exterior,
        Map<Long, Integer> surfaceComponents,
        Map<Integer, List<BlockPos>> cavityBoundaries
    ) {
        private static final int EXTERIOR = -1;
    }

    private record AirInterval(int id, int minY, int maxY) {
    }

    private static final class AirColumn {
        private final int x;
        private final int z;
        private final List<Integer> occupiedY = new ArrayList<>();
        private final List<AirInterval> intervals = new ArrayList<>();

        private AirColumn(int x, int z) {
            this.x = x;
            this.z = z;
        }

        private void buildIntervals(int minY, int maxY, List<AirInterval> all) {
            this.occupiedY.sort(Integer::compareTo);
            int cursor = minY;
            int previous = Integer.MIN_VALUE;
            for (int y : this.occupiedY) {
                if (y == previous) continue;
                if (cursor < y) {
                    this.addInterval(cursor, y - 1, all);
                }
                cursor = y + 1;
                previous = y;
            }
            if (cursor <= maxY) {
                this.addInterval(cursor, maxY, all);
            }
        }

        private void addInterval(int minY, int maxY, List<AirInterval> all) {
            AirInterval interval = new AirInterval(all.size(), minY, maxY);
            all.add(interval);
            this.intervals.add(interval);
        }

        private AirInterval intervalAt(int y) {
            int low = 0;
            int high = this.intervals.size() - 1;
            while (low <= high) {
                int middle = (low + high) >>> 1;
                AirInterval interval = this.intervals.get(middle);
                if (y < interval.minY) {
                    high = middle - 1;
                } else if (y > interval.maxY) {
                    low = middle + 1;
                } else {
                    return interval;
                }
            }
            throw new IllegalStateException("Surface air is outside its compressed column");
        }
    }

    private static final class UnionFind {
        private final int[] parents;
        private final byte[] ranks;

        private UnionFind(int size) {
            this.parents = new int[size];
            this.ranks = new byte[size];
            for (int index = 0; index < size; index++) {
                this.parents[index] = index;
            }
        }

        private int find(int value) {
            int parent = this.parents[value];
            if (parent != value) {
                this.parents[value] = this.find(parent);
            }
            return this.parents[value];
        }

        private void union(int first, int second) {
            int firstRoot = this.find(first);
            int secondRoot = this.find(second);
            if (firstRoot == secondRoot) return;
            if (this.ranks[firstRoot] < this.ranks[secondRoot]) {
                this.parents[firstRoot] = secondRoot;
            } else {
                this.parents[secondRoot] = firstRoot;
                if (this.ranks[firstRoot] == this.ranks[secondRoot]) {
                    this.ranks[firstRoot]++;
                }
            }
        }
    }
}
