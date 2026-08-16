package dev.anvilcraft.plasticraft.allay.path;

import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/**
 * 主线程只拷贝 BlockState 与已交付投影碰撞;工作线程编译碰撞并跑六向 A*。
 * 远距先在 16³ 区块段图上找走廊,再只对当前段做局部搜索。
 */
public final class AllayPathSnapshot {
    public static final int LOCAL_RANGE = 24;
    public static final int MARGIN = 16;
    public static final int NODE_BUDGET = 65536;
    private static final int SECTION_BUDGET = 96;
    private static final int CAPTURES_PER_TICK = 2;
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final Map<Level, CaptureBudget> CAPTURE_BUDGETS = new WeakHashMap<>();

    private AllayPathSnapshot() {
    }

    public static Vec3 nextLocalGoal(Entity worker, Vec3 start, Vec3 goal) {
        if (start.distanceTo(goal) <= LOCAL_RANGE) return goal;
        SectionPos from = SectionPos.of(BlockPos.containing(start));
        SectionPos to = SectionPos.of(BlockPos.containing(goal));
        if (from.equals(to) || adjacent(from, to)) return boundedLocalGoal(worker, start, goal);
        List<SectionPos> corridor = sectionCorridor(worker.level(), from, to);
        Vec3 candidate = corridor.size() < 2 ? goal : portal(worker, corridor.get(0), corridor.get(1), goal);
        return boundedLocalGoal(worker, start, candidate);
    }

    public static boolean acquireCapturePermit(Entity worker) {
        Level level = worker.level();
        long gameTime = level.getGameTime();
        synchronized (CAPTURE_BUDGETS) {
            CaptureBudget budget = CAPTURE_BUDGETS.computeIfAbsent(level, ignored -> new CaptureBudget());
            if (budget.gameTime != gameTime) {
                budget.gameTime = gameTime;
                budget.used = 0;
            }
            if (budget.used >= CAPTURES_PER_TICK) return false;
            budget.used++;
            return true;
        }
    }

    public static @Nullable Capture capture(Entity worker, Vec3 start, Vec3 goal) {
        BlockPos startPos = BlockPos.containing(start);
        BlockPos goalPos = BlockPos.containing(goal);
        int worldMinY = worker.level().getMinBuildHeight();
        int worldMaxY = worldMinY + worker.level().getHeight() - 1;
        int margin = start.distanceTo(goal) <= 8.0D ? 8 : MARGIN;
        int minX = Math.min(startPos.getX(), goalPos.getX()) - margin;
        int maxX = Math.max(startPos.getX(), goalPos.getX()) + margin;
        int minY = Math.max(worldMinY, Math.min(startPos.getY(), goalPos.getY()) - margin);
        int maxY = Math.min(worldMaxY, Math.max(startPos.getY(), goalPos.getY()) + margin);
        int minZ = Math.min(startPos.getZ(), goalPos.getZ()) - margin;
        int maxZ = Math.max(startPos.getZ(), goalPos.getZ()) + margin;
        if (minY > maxY) return null;
        CapturedBlockGetter blocks = CapturedBlockGetter.capture(
            worker.level(),
            minX,
            maxX,
            minY,
            maxY,
            minZ,
            maxZ
        );
        if (blocks == null) return null;
        AABB bounds = new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
        List<AABB> extras = new ArrayList<>();
        for (ConstructionProjectionIndex.Collision collision : ConstructionProjectionIndex.collisions(worker.level(), bounds)) {
            extras.addAll(collision.worldShape().toAabbs());
        }
        AABB relative = ConstructionWorkerSpace.boxAt(Vec3.ZERO);
        return new Capture(blocks, List.copyOf(extras), relative, worker.level().getWorldBorder().getMinX(),
            worker.level().getWorldBorder().getMaxX(),
            worker.level().getWorldBorder().getMinZ(),
            worker.level().getWorldBorder().getMaxZ());
    }

    public static List<Vec3> search(@Nullable Capture capture, Vec3 start, Vec3 goal) {
        return search(capture, start, goal, () -> false);
    }

    public static List<Vec3> search(
        @Nullable Capture capture,
        Vec3 start,
        Vec3 goal,
        BooleanSupplier cancelled
    ) {
        if (capture == null) return List.of();
        SearchCollision collision = capture.compile(cancelled);
        if (collision == null || cancelled.getAsBoolean()) return List.of();
        List<Vec3> path = astar(collision, start, goal, cancelled);
        if (path.isEmpty()) return List.of();
        return simplify(collision, collapse(path), start, cancelled);
    }

    private static Vec3 boundedLocalGoal(Entity worker, Vec3 start, Vec3 candidate) {
        Vec3 delta = candidate.subtract(start);
        Vec3 bounded = delta.length() <= LOCAL_RANGE ? candidate : start.add(delta.normalize().scale(LOCAL_RANGE));
        if (worker.level().noBlockCollision(worker, ConstructionWorkerSpace.boxAt(bounded))) {
            return bounded;
        }
        BlockPos origin = BlockPos.containing(bounded);
        Vec3 best = bounded;
        double bestDistance = Double.MAX_VALUE;
        for (int y = -2; y <= 3; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    Vec3 probe = ConstructionWorkerSpace.navigationPoint(
                        origin.getX() + x,
                        origin.getY() + y,
                        origin.getZ() + z
                    );
                    if (!worker.level().noBlockCollision(worker, ConstructionWorkerSpace.boxAt(probe))) continue;
                    double distance = probe.distanceToSqr(candidate);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = probe;
                    }
                }
            }
        }
        return best;
    }

    private static boolean adjacent(SectionPos a, SectionPos b) {
        return Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z()) <= 1;
    }

    private static List<SectionPos> sectionCorridor(Level level, SectionPos start, SectionPos goal) {
        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> cost = new HashMap<>();
        PriorityQueue<SectionNode> open = new PriorityQueue<>(Comparator.comparingInt(SectionNode::score));
        long startKey = start.asLong();
        cost.put(startKey, 0);
        open.add(new SectionNode(start, 0, sectionHeuristic(start, goal)));
        int expansions = 0;
        SectionPos found = null;
        while (!open.isEmpty() && expansions < SECTION_BUDGET) {
            SectionNode current = open.poll();
            expansions++;
            if (current.pos().equals(goal)) {
                found = current.pos();
                break;
            }
            for (Direction direction : DIRECTIONS) {
                SectionPos next = SectionPos.of(
                    current.pos().x() + direction.getStepX(),
                    current.pos().y() + direction.getStepY(),
                    current.pos().z() + direction.getStepZ()
                );
                if (!sectionOpen(level, next)) continue;
                int nextCost = cost.get(current.pos().asLong()) + 1;
                long nextKey = next.asLong();
                if (nextCost >= cost.getOrDefault(nextKey, Integer.MAX_VALUE)) continue;
                cost.put(nextKey, nextCost);
                cameFrom.put(nextKey, current.pos().asLong());
                open.add(new SectionNode(next, nextCost, nextCost + sectionHeuristic(next, goal)));
            }
        }
        if (found == null) return List.of();
        List<SectionPos> path = new ArrayList<>();
        long cursor = found.asLong();
        while (true) {
            path.add(SectionPos.of(cursor));
            if (cursor == startKey) break;
            Long previous = cameFrom.get(cursor);
            if (previous == null) break;
            cursor = previous;
        }
        Collections.reverse(path);
        return path;
    }

    private static boolean sectionOpen(Level level, SectionPos section) {
        BlockPos sample = section.origin().offset(8, 8, 8);
        if (!level.hasChunkAt(sample)) return false;
        LevelChunk chunk = level.getChunk(section.x(), section.z());
        int index = chunk.getSectionIndex(section.minBlockY());
        if (index < 0 || index >= chunk.getSectionsCount()) return false;
        LevelChunkSection chunkSection = chunk.getSection(index);
        AABB box = new AABB(
            section.minBlockX(),
            section.minBlockY(),
            section.minBlockZ(),
            section.minBlockX() + 16,
            section.minBlockY() + 16,
            section.minBlockZ() + 16
        );
        boolean projections = !ConstructionProjectionIndex.collisions(level, box).isEmpty();
        if (chunkSection.hasOnlyAir() && !projections) return true;
        Vec3[] samples = {
            ConstructionWorkerSpace.navigationPoint(sample),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(8, 1, 8)),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(8, 14, 8)),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(1, 8, 8)),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(14, 8, 8)),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(8, 8, 1)),
            ConstructionWorkerSpace.navigationPoint(section.origin().offset(8, 8, 14))
        };
        for (Vec3 point : samples) {
            if (level.noBlockCollision(null, ConstructionWorkerSpace.boxAt(point))) {
                return true;
            }
        }
        return false;
    }

    private static Vec3 portal(Entity worker, SectionPos from, SectionPos to, Vec3 goal) {
        int x = portalCoordinate(from.minBlockX(), to.x() - from.x());
        int y = portalCoordinate(from.minBlockY(), to.y() - from.y());
        int z = portalCoordinate(from.minBlockZ(), to.z() - from.z());
        Vec3 raw = ConstructionWorkerSpace.navigationPoint(x, y, z);
        if (worker.level().noBlockCollision(worker, ConstructionWorkerSpace.boxAt(raw))) {
            return raw;
        }
        BlockPos origin = BlockPos.containing(raw);
        for (int dy = -2; dy <= 4; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Vec3 candidate = ConstructionWorkerSpace.navigationPoint(
                        origin.getX() + dx,
                        origin.getY() + dy,
                        origin.getZ() + dz
                    );
                    if (worker.level().noBlockCollision(worker, ConstructionWorkerSpace.boxAt(candidate))) {
                        return candidate;
                    }
                }
            }
        }
        return goal;
    }

    private static int portalCoordinate(int sectionMin, int step) {
        return switch (Integer.signum(step)) {
            case -1 -> sectionMin - 1;
            case 1 -> sectionMin + SectionPos.SECTION_SIZE;
            default -> sectionMin + SectionPos.SECTION_SIZE / 2;
        };
    }

    private static int sectionHeuristic(SectionPos from, SectionPos to) {
        return Math.abs(from.x() - to.x()) + Math.abs(from.y() - to.y()) + Math.abs(from.z() - to.z());
    }

    private static List<Vec3> astar(
        SearchCollision collision,
        Vec3 start,
        Vec3 goal,
        BooleanSupplier cancelled
    ) {
        if (cancelled.getAsBoolean()) return List.of();
        BlockPos startPos = BlockPos.containing(start);
        BlockPos goalPos = BlockPos.containing(goal);
        SearchGrid grid = new SearchGrid(startPos, goalPos, MARGIN);
        if (grid.nodeCount > NODE_BUDGET * 2) return List.of();
        int[] costs = new int[grid.nodeCount];
        int[] parents = new int[grid.nodeCount];
        boolean[] closed = new boolean[grid.nodeCount];
        Arrays.fill(costs, Integer.MAX_VALUE);
        Arrays.fill(parents, -1);
        NodeHeap open = new NodeHeap(grid.nodeCount);
        int startIndex = grid.index(startPos.getX(), startPos.getY(), startPos.getZ());
        if (startIndex < 0) return List.of();
        costs[startIndex] = 0;
        open.addOrDecrease(startIndex, heuristic(startPos, goalPos));
        int reached = -1;
        int expanded = 0;
        while (!open.isEmpty() && expanded < NODE_BUDGET) {
            if ((expanded & 63) == 0 && cancelled.getAsBoolean()) return List.of();
            int current = open.removeFirst();
            if (closed[current]) continue;
            closed[current] = true;
            expanded++;
            int cx = grid.x(current);
            int cy = grid.y(current);
            int cz = grid.z(current);
            Vec3 currentPos = current == startIndex
                ? start
                : ConstructionWorkerSpace.navigationPoint(cx, cy, cz);
            if (Math.abs(cx - goalPos.getX()) + Math.abs(cy - goalPos.getY()) + Math.abs(cz - goalPos.getZ()) <= 1
                && collision.sweptClear(currentPos, goal, true)) {
                reached = current;
                break;
            }
            for (Direction direction : DIRECTIONS) {
                int nx = cx + direction.getStepX();
                int ny = cy + direction.getStepY();
                int nz = cz + direction.getStepZ();
                int next = grid.index(nx, ny, nz);
                if (next < 0 || closed[next]) continue;
                Vec3 nextPos = ConstructionWorkerSpace.navigationPoint(nx, ny, nz);
                if (!collision.sweptClear(currentPos, nextPos, false)) continue;
                int nextCost = costs[current] + 1;
                if (nextCost >= costs[next]) continue;
                costs[next] = nextCost;
                parents[next] = current;
                open.addOrDecrease(next, nextCost + heuristic(new BlockPos(nx, ny, nz), goalPos));
            }
        }
        if (reached < 0) {
            return List.of();
        }
        List<Vec3> points = new ArrayList<>();
        int cursor = reached;
        while (cursor != startIndex && cursor >= 0) {
            points.add(ConstructionWorkerSpace.navigationPoint(grid.x(cursor), grid.y(cursor), grid.z(cursor)));
            cursor = parents[cursor];
        }
        Collections.reverse(points);
        if (points.isEmpty() || points.getLast().distanceToSqr(goal) > 1.0E-6D) {
            if (!collision.sweptClear(points.isEmpty() ? start : points.getLast(), goal, true)) {
                return List.of();
            }
            points.add(goal);
        }
        return points;
    }

    private static int heuristic(BlockPos from, BlockPos to) {
        return from.distManhattan(to);
    }

    private static List<Vec3> collapse(List<Vec3> path) {
        if (path.size() < 3) return path;
        List<Vec3> collapsed = new ArrayList<>();
        collapsed.add(path.getFirst());
        Vec3 direction = path.get(1).subtract(path.get(0));
        for (int index = 2; index < path.size(); index++) {
            Vec3 step = path.get(index).subtract(path.get(index - 1));
            if (!collinear(direction, step)) {
                collapsed.add(path.get(index - 1));
                direction = step;
            }
        }
        collapsed.add(path.getLast());
        return collapsed;
    }

    private static boolean collinear(Vec3 a, Vec3 b) {
        return a.cross(b).lengthSqr() < 1.0E-6D && a.dot(b) > 0.0D;
    }

    private static List<Vec3> simplify(
        SearchCollision collision,
        List<Vec3> path,
        Vec3 start,
        BooleanSupplier cancelled
    ) {
        if (path.isEmpty()) return path;
        List<Vec3> simplified = new ArrayList<>();
        Vec3 current = start;
        int index = 0;
        while (index < path.size()) {
            if (cancelled.getAsBoolean()) return List.of();
            int best = -1;
            for (int probe = path.size() - 1; probe >= index; probe--) {
                if (collision.sweptClear(current, path.get(probe), probe == path.size() - 1)) {
                    best = probe;
                    break;
                }
            }
            if (best < 0) return List.of();
            Vec3 next = path.get(best);
            simplified.add(next);
            current = next;
            index = best + 1;
        }
        return simplified;
    }

    public record Capture(
        CapturedBlockGetter blocks,
        List<AABB> extras,
        AABB relative,
        double borderMinX,
        double borderMaxX,
        double borderMinZ,
        double borderMaxZ
    ) {
        private @Nullable SearchCollision compile(BooleanSupplier cancelled) {
            Map<Long, List<AABB>> boxes = new HashMap<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = this.blocks.minX; x <= this.blocks.maxX; x++) {
                if (cancelled.getAsBoolean()) return null;
                for (int y = this.blocks.minY; y <= this.blocks.maxY; y++) {
                    for (int z = this.blocks.minZ; z <= this.blocks.maxZ; z++) {
                        cursor.set(x, y, z);
                        BlockState state = this.blocks.getBlockState(cursor);
                        VoxelShape shape = this.blocks.dynamicCollisionShape(cursor);
                        if (shape == null) {
                            try {
                                shape = state.getCollisionShape(this.blocks, cursor);
                            } catch (RuntimeException ignored) {
                                continue;
                            }
                        }
                        if (shape.isEmpty()) continue;
                        boxes.put(cursor.asLong(), new ArrayList<>(shape.move(x, y, z).toAabbs()));
                    }
                }
            }
            for (AABB extra : this.extras) {
                if (cancelled.getAsBoolean()) return null;
                int minX = (int) Math.floor(extra.minX);
                int minY = (int) Math.floor(extra.minY);
                int minZ = (int) Math.floor(extra.minZ);
                int maxX = (int) Math.floor(Math.nextDown(extra.maxX));
                int maxY = (int) Math.floor(Math.nextDown(extra.maxY));
                int maxZ = (int) Math.floor(Math.nextDown(extra.maxZ));
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            boxes.computeIfAbsent(BlockPos.asLong(x, y, z), ignored -> new ArrayList<>()).add(extra);
                        }
                    }
                }
            }
            return new SearchCollision(
                boxes,
                this.relative,
                this.borderMinX,
                this.borderMaxX,
                this.borderMinZ,
                this.borderMaxZ
            );
        }
    }

    private record SearchCollision(
        Map<Long, List<AABB>> boxes,
        AABB relative,
        double borderMinX,
        double borderMaxX,
        double borderMinZ,
        double borderMaxZ
    ) {
        private boolean sweptClear(Vec3 from, Vec3 to, boolean allowContact) {
            AABB start = this.relative.move(from);
            AABB end = this.relative.move(to);
            AABB swept = start.minmax(end);
            if (allowContact) {
                swept = swept.deflate(0.002D);
            }
            return !this.blocked(swept);
        }

        private boolean blocked(AABB query) {
            if (query.minX < this.borderMinX
                || query.maxX > this.borderMaxX
                || query.minZ < this.borderMinZ
                || query.maxZ > this.borderMaxZ) {
                return true;
            }
            int minX = (int) Math.floor(query.minX);
            int minY = (int) Math.floor(query.minY);
            int minZ = (int) Math.floor(query.minZ);
            int maxX = (int) Math.floor(query.maxX);
            int maxY = (int) Math.floor(query.maxY);
            int maxZ = (int) Math.floor(query.maxZ);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        List<AABB> local = this.boxes.get(BlockPos.asLong(x, y, z));
                        if (local == null) continue;
                        for (AABB box : local) {
                            if (box.intersects(query)) return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    static final class CapturedBlockGetter implements BlockGetter {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeY;
        private final int sizeZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int minBuildHeight;
        private final int height;
        private final BlockState[] states;
        private final Map<Long, VoxelShape> dynamicCollisionShapes;

        private CapturedBlockGetter(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            int minBuildHeight,
            int height,
            BlockState[] states,
            Map<Long, VoxelShape> dynamicCollisionShapes
        ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeY = maxY - minY + 1;
            this.sizeZ = maxZ - minZ + 1;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.minBuildHeight = minBuildHeight;
            this.height = height;
            this.states = states;
            this.dynamicCollisionShapes = dynamicCollisionShapes;
        }

        private static @Nullable CapturedBlockGetter capture(
            Level level,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
        ) {
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            long stateCount = (long) sizeX * sizeY * sizeZ;
            if (stateCount <= 0L || stateCount > Integer.MAX_VALUE) return null;
            BlockState[] states = new BlockState[(int) stateCount];
            Map<Long, VoxelShape> dynamicCollisionShapes = new HashMap<>();
            CapturedBlockGetter result = new CapturedBlockGetter(
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                level.getMinBuildHeight(),
                level.getHeight(),
                states,
                dynamicCollisionShapes
            );
            Map<Long, LevelChunk> chunks = new HashMap<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            CollisionContext collisionContext = CollisionContext.empty();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    LevelChunk chunk = chunks.get(chunkKey);
                    if (chunk == null) {
                        cursor.set(x, minY, z);
                        if (!level.hasChunkAt(cursor)) return null;
                        chunk = level.getChunk(chunkX, chunkZ);
                        chunks.put(chunkKey, chunk);
                    }
                    int sectionIndex = Integer.MIN_VALUE;
                    LevelChunkSection section = null;
                    boolean sectionEmpty = false;
                    for (int y = minY; y <= maxY; y++) {
                        int nextSectionIndex = chunk.getSectionIndex(y);
                        if (nextSectionIndex < 0 || nextSectionIndex >= chunk.getSectionsCount()) {
                            states[result.index(x, y, z)] = Blocks.AIR.defaultBlockState();
                            sectionIndex = Integer.MIN_VALUE;
                            continue;
                        }
                        if (nextSectionIndex != sectionIndex) {
                            sectionIndex = nextSectionIndex;
                            section = chunk.getSection(sectionIndex);
                            sectionEmpty = section.hasOnlyAir();
                        }
                        BlockState state = sectionEmpty
                            ? Blocks.AIR.defaultBlockState()
                            : section.getBlockState(x & 15, y & 15, z & 15);
                        states[result.index(x, y, z)] = state;
                        if (!state.getBlock().hasDynamicShape()) continue;
                        cursor.set(x, y, z);
                        try {
                            dynamicCollisionShapes.put(
                                cursor.asLong(),
                                state.getCollisionShape(level, cursor, collisionContext)
                            );
                        } catch (RuntimeException ignored) {
                            // 动态碰撞无法在主线程解析时宁可绕行，不能让后台把未知占位当成空气。
                            dynamicCollisionShapes.put(cursor.asLong(), Shapes.block());
                        }
                    }
                }
            }
            return result;
        }

        private @Nullable VoxelShape dynamicCollisionShape(BlockPos pos) {
            return this.dynamicCollisionShapes.get(pos.asLong());
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < this.minX
                || pos.getX() > this.maxX
                || pos.getY() < this.minY
                || pos.getY() > this.maxY
                || pos.getZ() < this.minZ
                || pos.getZ() > this.maxZ) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.states[this.index(pos.getX(), pos.getY(), pos.getZ())];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getMinBuildHeight() {
            return this.minBuildHeight;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        private int index(int x, int y, int z) {
            return ((x - this.minX) * this.sizeZ + z - this.minZ) * this.sizeY + y - this.minY;
        }
    }

    private static final class SearchGrid {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int nodeCount;

        private SearchGrid(BlockPos start, BlockPos goal, int margin) {
            this.minX = Math.min(start.getX(), goal.getX()) - margin;
            this.minY = Math.min(start.getY(), goal.getY()) - margin;
            this.minZ = Math.min(start.getZ(), goal.getZ()) - margin;
            this.sizeX = Math.max(start.getX(), goal.getX()) + margin - this.minX + 1;
            this.sizeY = Math.max(start.getY(), goal.getY()) + margin - this.minY + 1;
            this.sizeZ = Math.max(start.getZ(), goal.getZ()) + margin - this.minZ + 1;
            this.nodeCount = this.sizeX * this.sizeY * this.sizeZ;
        }

        private int index(int x, int y, int z) {
            if (x < this.minX || y < this.minY || z < this.minZ
                || x >= this.minX + this.sizeX
                || y >= this.minY + this.sizeY
                || z >= this.minZ + this.sizeZ) {
                return -1;
            }
            return ((x - this.minX) * this.sizeY + y - this.minY) * this.sizeZ + z - this.minZ;
        }

        private int x(int index) {
            return index / (this.sizeY * this.sizeZ) + this.minX;
        }

        private int y(int index) {
            return index / this.sizeZ % this.sizeY + this.minY;
        }

        private int z(int index) {
            return index % this.sizeZ + this.minZ;
        }
    }

    private static final class NodeHeap {
        private final int[] nodes;
        private final double[] scores;
        private final int[] positions;
        private int size;

        private NodeHeap(int capacity) {
            this.nodes = new int[capacity];
            this.scores = new double[capacity];
            this.positions = new int[capacity];
            Arrays.fill(this.positions, -1);
        }

        private boolean isEmpty() {
            return this.size == 0;
        }

        private void addOrDecrease(int node, double score) {
            int position = this.positions[node];
            if (position < 0) {
                position = this.size++;
                this.nodes[position] = node;
                this.scores[position] = score;
                this.positions[node] = position;
            } else if (score >= this.scores[position]) {
                return;
            } else {
                this.scores[position] = score;
            }
            this.siftUp(position);
        }

        private int removeFirst() {
            int result = this.nodes[0];
            this.positions[result] = -1;
            int last = --this.size;
            if (last > 0) {
                this.nodes[0] = this.nodes[last];
                this.scores[0] = this.scores[last];
                this.positions[this.nodes[0]] = 0;
                this.siftDown(0);
            }
            return result;
        }

        private void siftUp(int position) {
            while (position > 0) {
                int parent = (position - 1) >>> 1;
                if (this.scores[parent] <= this.scores[position]) return;
                this.swap(parent, position);
                position = parent;
            }
        }

        private void siftDown(int position) {
            while (true) {
                int left = (position << 1) + 1;
                if (left >= this.size) return;
                int right = left + 1;
                int best = right < this.size && this.scores[right] < this.scores[left] ? right : left;
                if (this.scores[position] <= this.scores[best]) return;
                this.swap(position, best);
                position = best;
            }
        }

        private void swap(int left, int right) {
            int node = this.nodes[left];
            this.nodes[left] = this.nodes[right];
            this.nodes[right] = node;
            double score = this.scores[left];
            this.scores[left] = this.scores[right];
            this.scores[right] = score;
            this.positions[this.nodes[left]] = left;
            this.positions[this.nodes[right]] = right;
        }
    }

    private record SectionNode(SectionPos pos, int cost, int score) {
    }

    private static final class CaptureBudget {
        private long gameTime = Long.MIN_VALUE;
        private int used;
    }
}
