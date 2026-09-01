package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 假设当前作业后,从目标六邻可飞格分别洪泛,判断是否会封住未完成作业或其他悦灵。
 * 超时按可能封闭推迟,不用“区域大于 N 即开放”。
 */
public final class ConstructionEnclosure {
    private static final int FLOOD_BUDGET = 8192;
    private static final Map<ConstructionJobProgress, JobCache> CACHES = new WeakHashMap<>();

    private ConstructionEnclosure() {
    }

    public record Analysis(
        boolean open,
        Set<Long> cavity,
        boolean enclosesWork,
        boolean enclosesOthers,
        boolean enclosesSelf,
        boolean serialSeal
    ) {
        public boolean wouldEnclose() {
            return !this.open && (this.enclosesWork || this.enclosesOthers || this.serialSeal);
        }

        public boolean blocksDelivery() {
            return this.wouldEnclose() || this.enclosesSelf;
        }

        public boolean approachSafe(BlockPos approach) {
            if (this.open) return true;
            return !this.cavity.contains(approach.asLong());
        }
    }

    public static boolean wouldEnclose(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable WorkingAllayEntity worker
    ) {
        return analyze(level, progress, op, worker).wouldEnclose();
    }

    public static boolean approachSafe(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        BlockPos approach,
        @Nullable WorkingAllayEntity worker
    ) {
        return analyze(level, progress, op, worker).approachSafe(approach);
    }

    public static boolean bodyIntersects(Entity worker, ConstructionBuildOp op, ConstructionOverlayView view) {
        VoxelShape shape = op.target().getCollisionShape(view, op.pos(), CollisionContext.empty());
        if (shape.isEmpty()) return false;
        VoxelShape world = shape.move(op.pos().getX(), op.pos().getY(), op.pos().getZ());
        return Shapes.joinIsNotEmpty(world, Shapes.create(worker.getBoundingBox()), BooleanOp.AND);
    }

    public static Set<Long> blueprintExterior(ServerLevel level, ConstructionJobProgress progress) {
        return cache(level, progress).exterior;
    }

    public static Analysis analyze(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable WorkingAllayEntity worker
    ) {
        JobCache cache = cache(level, progress);
        Geometry geometry = cache.geometry.computeIfAbsent(
            op.id(),
            ignored -> analyzeGeometry(level, progress, op, cache)
        );
        if (geometry.open) {
            return new Analysis(true, Set.of(), false, false, false, false);
        }
        boolean enclosesOthers = enclosesOthers(level, cache.site, geometry.cavity, worker);
        boolean enclosesSelf = worker != null && intersectsCavity(geometry.cavity, worker);
        return new Analysis(
            false,
            geometry.cavity,
            geometry.enclosesWork,
            enclosesOthers,
            enclosesSelf,
            geometry.serialSeal
        );
    }

    private static Geometry analyzeGeometry(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        JobCache cache
    ) {
        ClosedRegion region = closedRegion(level, progress, op, cache, -1);
        if (region.budgetExceeded()) {
            return new Geometry(false, Set.of(), true, true);
        }
        if (region.open()) {
            return new Geometry(true, Set.of(), false, false);
        }
        boolean enclosesWork = enclosesWork(level, progress, op, region.cavity(), cache);
        boolean serialSeal = hasOtherLeasedWall(progress, op, region.cavity());
        return new Geometry(false, region.cavity(), enclosesWork, serialSeal);
    }

    private static ClosedRegion closedRegion(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        JobCache cache,
        int ignoredLeasedOperation
    ) {
        GeometryKey key = new GeometryKey(op.id(), ignoredLeasedOperation);
        ClosedRegion cached = cache.closedRegions.get(key);
        if (cached != null) return cached;
        Set<Long> forced = forcedWallCells(progress, op, ignoredLeasedOperation);
        Set<Long> closed = new HashSet<>();
        Set<Long> explored = new HashSet<>();
        int visited = 0;
        for (BlockPos seed : floodSeeds(op, progress)) {
            long seedKey = seed.asLong();
            if (forced.contains(seedKey) || explored.contains(seedKey)) continue;
            // 先判外部再判可飞:开放工地上六个种子多半已经落在外包络里,
            // 可飞判定要跑一次真实碰撞扫掠,是这里最贵的一步,不该为注定跳过的种子付这个代价
            if (outside(seed, cache.exterior, cache.site)) continue;
            if (!flyable(level, cache, forced, seed)) continue;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Set<Long> region = new HashSet<>();
            explored.add(seedKey);
            region.add(seedKey);
            queue.addLast(seed);
            boolean regionOpen = false;
            while (!queue.isEmpty()) {
                if (visited++ >= FLOOD_BUDGET) {
                    ClosedRegion result = new ClosedRegion(false, Set.of(), true);
                    cache.closedRegions.put(key, result);
                    return result;
                }
                BlockPos pos = queue.removeFirst();
                if (outside(pos, cache.exterior, cache.site)) {
                    regionOpen = true;
                    break;
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    long nextKey = next.asLong();
                    if (forced.contains(nextKey) || explored.contains(nextKey)) continue;
                    if (!flyable(level, cache, forced, next)) continue;
                    explored.add(nextKey);
                    region.add(nextKey);
                    queue.addLast(next);
                }
            }
            if (!regionOpen) {
                closed.addAll(region);
            }
        }
        if (closed.isEmpty()) {
            ClosedRegion result = new ClosedRegion(true, Set.of(), false);
            cache.closedRegions.put(key, result);
            return result;
        }
        ClosedRegion result = new ClosedRegion(false, Set.copyOf(closed), false);
        cache.closedRegions.put(key, result);
        return result;
    }

    private static List<BlockPos> collidingCells(ConstructionJobProgress progress, ConstructionOverlayView view) {
        List<BlockPos> cells = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE && op.kind() != ConstructionBuildOp.Kind.SEAL) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.SKIPPED) continue;
            VoxelShape shape = op.target().getCollisionShape(view, op.pos(), CollisionContext.empty());
            if (!shape.isEmpty()) {
                cells.add(op.pos());
            }
        }
        return cells;
    }

    private static AABB siteBox(ConstructionJobProgress progress) {
        AABB box = null;
        for (ConstructionBuildOp op : progress.operations()) {
            AABB cell = new AABB(op.pos());
            box = box == null ? cell : box.minmax(cell);
        }
        return box == null ? new AABB(BlockPos.ZERO) : box.inflate(1.0D);
    }

    private static boolean outside(BlockPos pos, Set<Long> exterior, AABB site) {
        return exterior.contains(pos.asLong()) || !site.intersects(new AABB(pos));
    }

    private static Set<Long> forcedWallCells(
        ConstructionJobProgress progress,
        ConstructionBuildOp current,
        int ignoredLeasedOperation
    ) {
        Set<Long> forced = new HashSet<>();
        addForcedIfColliding(forced, current);
        for (ConstructionBuildOp child : progress.childrenOf(current)) {
            if (child.kind() == ConstructionBuildOp.Kind.ATTACHED) {
                addForcedIfColliding(forced, child);
            }
        }
        for (ConstructionBuildOp op : progress.leasedWallOperations()) {
            if (op.id() != current.id() && op.id() != ignoredLeasedOperation) {
                addForcedIfColliding(forced, op);
            }
        }
        return forced;
    }

    private static void addForcedIfColliding(Set<Long> forced, ConstructionBuildOp op) {
        if (!op.target().getCollisionShape(EmptyBlockGetter.INSTANCE, op.pos(), CollisionContext.empty())
            .isEmpty()) {
            forced.add(op.pos().asLong());
        }
    }

    private static List<BlockPos> floodSeeds(ConstructionBuildOp op, ConstructionJobProgress progress) {
        List<BlockPos> seeds = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            seeds.add(op.pos().relative(direction));
        }
        for (ConstructionBuildOp child : progress.childrenOf(op)) {
            if (child.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            for (Direction direction : Direction.values()) {
                seeds.add(child.pos().relative(direction));
            }
        }
        return seeds;
    }

    private static boolean flyable(
        ServerLevel level,
        JobCache cache,
        Set<Long> forced,
        BlockPos pos
    ) {
        long key = pos.asLong();
        if (forced.contains(key)) return false;
        return cache.freeCell(level, pos, key);
    }

    /**
     * 最后缺口必须串行:两只悦灵同刻各封一个缺口会把对方或第三只悦灵关在里面。
     * 但只有真正压在同一腔体边界上的租约才需要让行,否则任务另一头任意一份墙体租约
     * 都会让这里永远拒绝施工;让行方按封堵序取较后的一方,较前的一方当选主封者先封,
     * 避免双方互相让行谁都不动。无碰撞形状的方块(铁轨、红石粉等)封不住腔体,不参与串行。
     */
    private static boolean hasOtherLeasedWall(
        ConstructionJobProgress progress,
        ConstructionBuildOp current,
        Set<Long> cavity
    ) {
        for (ConstructionBuildOp op : progress.leasedWallOperations()) {
            if (op.id() == current.id() || op.parentId() == current.id()) continue;
            if (!isCollidingWall(op) || !touchesCavity(cavity, op.pos())) continue;
            if (compareSealOrder(current, op) < 0) continue;
            return true;
        }
        return false;
    }

    private static boolean touchesCavity(Set<Long> cavity, BlockPos pos) {
        if (cavity.contains(pos.asLong())) return true;
        for (Direction direction : Direction.values()) {
            if (cavity.contains(pos.relative(direction).asLong())) return true;
        }
        return false;
    }

    private static boolean enclosesWork(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp current,
        Set<Long> cavity,
        JobCache cache
    ) {
        Map<Integer, ConstructionBuildOp> blockers = new HashMap<>();
        for (long cell : cavity) {
            for (ConstructionBuildOp op : progress.operationsAt(BlockPos.of(cell))) {
                if (op.id() == current.id()) continue;
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                if (op.kind() != ConstructionBuildOp.Kind.PLACE
                    && op.kind() != ConstructionBuildOp.Kind.CONTENT
                    && op.kind() != ConstructionBuildOp.Kind.FLUID
                    && op.kind() != ConstructionBuildOp.Kind.ENTITY
                    && op.kind() != ConstructionBuildOp.Kind.DECORATE
                    && op.kind() != ConstructionBuildOp.Kind.SEAL
                    && op.kind() != ConstructionBuildOp.Kind.ATTACHED) {
                    continue;
                }
                ConstructionBuildOp blocker = sealingRoot(progress, op);
                if (blocker == null || blocker.id() == current.id()) continue;
                blockers.put(blocker.id(), blocker);
            }
        }
        for (ConstructionBuildOp blocker : blockers.values()) {
            if (!breaksMutualSeal(level, progress, current, blocker, cache)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static ConstructionBuildOp sealingRoot(
        ConstructionJobProgress progress,
        ConstructionBuildOp operation
    ) {
        if (operation.kind() == ConstructionBuildOp.Kind.ATTACHED) {
            return progress.parentOf(operation);
        }
        return operation;
    }

    private static boolean breaksMutualSeal(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp current,
        ConstructionBuildOp blocker,
        JobCache cache
    ) {
        if (!isCollidingWall(current) || !isCollidingWall(blocker)) return false;
        if (compareSealOrder(current, blocker) >= 0) return false;
        ClosedRegion reverse = closedRegion(level, progress, blocker, cache, current.id());
        if (reverse.open() || reverse.budgetExceeded()) return false;
        if (reverse.cavity().contains(current.pos().asLong())) return true;
        for (ConstructionBuildOp child : progress.childrenOf(current)) {
            if (child.kind() == ConstructionBuildOp.Kind.ATTACHED
                && reverse.cavity().contains(child.pos().asLong())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCollidingWall(ConstructionBuildOp operation) {
        if (operation.kind() != ConstructionBuildOp.Kind.PLACE
            && operation.kind() != ConstructionBuildOp.Kind.SEAL) {
            return false;
        }
        return !operation.target().getCollisionShape(
            EmptyBlockGetter.INSTANCE,
            operation.pos(),
            CollisionContext.empty()
        ).isEmpty();
    }

    private static int compareSealOrder(ConstructionBuildOp first, ConstructionBuildOp second) {
        int order = Integer.compare(first.order(), second.order());
        return order != 0 ? order : Integer.compare(first.id(), second.id());
    }

    private static boolean enclosesOthers(
        ServerLevel level,
        AABB site,
        Set<Long> cavity,
        @Nullable WorkingAllayEntity self
    ) {
        for (Entity entity : level.getEntities(self, site.inflate(1.0D))) {
            if (!(entity instanceof WorkingAllayEntity other) || !other.isAlive()) continue;
            if (intersectsCavity(cavity, other)) return true;
        }
        return false;
    }

    private static boolean intersectsCavity(Set<Long> cavity, Entity entity) {
        AABB box = entity.getBoundingBox();
        int minX = (int) Math.floor(box.minX);
        int maxX = (int) Math.floor(Math.nextDown(box.maxX));
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.floor(Math.nextDown(box.maxY));
        int minZ = (int) Math.floor(box.minZ);
        int maxZ = (int) Math.floor(Math.nextDown(box.maxZ));
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (cavity.contains(BlockPos.asLong(x, y, z))) return true;
                }
            }
        }
        return false;
    }

    private static JobCache cache(ServerLevel level, ConstructionJobProgress progress) {
        synchronized (CACHES) {
            long enclosureRevision = progress.enclosureRevision();
            long layoutRevision = progress.layoutRevision();
            long geometryRevision = progress.geometryRevision();
            long gameTime = level.getGameTime();
            JobCache cache = CACHES.get(progress);
            if (cache == null || cache.level != level || cache.layoutRevision != layoutRevision) {
                Map<Long, BlockState> overlay = progress.overlayStates();
                ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
                cache = new JobCache(
                    level,
                    enclosureRevision,
                    layoutRevision,
                    geometryRevision,
                    gameTime,
                    Set.copyOf(ConstructionAssembler.exteriorOf(collidingCells(progress, view))),
                    siteBox(progress)
                );
                CACHES.put(progress, cache);
                return cache;
            }
            // 可飞判定只看真实方块与已交付投影,租约变化不影响它;一刻内同一格不必反复跑碰撞扫掠
            if (cache.geometryRevision != geometryRevision || cache.analysisTick != gameTime) {
                cache.geometryRevision = geometryRevision;
                cache.freeCells.clear();
            }
            if (cache.enclosureRevision != enclosureRevision || cache.analysisTick != gameTime) {
                cache.enclosureRevision = enclosureRevision;
                cache.analysisTick = gameTime;
                cache.geometry.clear();
                cache.closedRegions.clear();
            }
            return cache;
        }
    }

    private record GeometryKey(int operationId, int ignoredLeasedOperation) {
    }

    private record ClosedRegion(boolean open, Set<Long> cavity, boolean budgetExceeded) {
    }

    private record Geometry(boolean open, Set<Long> cavity, boolean enclosesWork, boolean serialSeal) {
    }

    private static final class JobCache {
        private final ServerLevel level;
        private long enclosureRevision;
        private final long layoutRevision;
        private long geometryRevision;
        private long analysisTick;
        private final Set<Long> exterior;
        private final AABB site;
        private final Map<Integer, Geometry> geometry = new HashMap<>();
        private final Map<GeometryKey, ClosedRegion> closedRegions = new HashMap<>();
        /** 逐格可飞判定结果,按几何版本与服务器 tick 复用;洪泛与多只悦灵会反复问到同一批格子。 */
        private final Map<Long, Boolean> freeCells = new HashMap<>();

        private JobCache(
            ServerLevel level,
            long enclosureRevision,
            long layoutRevision,
            long geometryRevision,
            long analysisTick,
            Set<Long> exterior,
            AABB site
        ) {
            this.level = level;
            this.enclosureRevision = enclosureRevision;
            this.layoutRevision = layoutRevision;
            this.geometryRevision = geometryRevision;
            this.analysisTick = analysisTick;
            this.exterior = exterior;
            this.site = site;
        }

        /**
         * 该格是否容得下悦灵。{@code noBlockCollision} 已由碰撞 mixin 把已交付投影和粘合制品
         * 溢出轮廓一并算进去,不必再单独查一遍投影索引。
         */
        private boolean freeCell(ServerLevel level, BlockPos pos, long key) {
            Boolean cached = this.freeCells.get(key);
            if (cached != null) return cached;
            boolean free = level.noBlockCollision(null, ConstructionWorkerSpace.boxAt(pos));
            this.freeCells.put(key, free);
            return free;
        }
    }
}
