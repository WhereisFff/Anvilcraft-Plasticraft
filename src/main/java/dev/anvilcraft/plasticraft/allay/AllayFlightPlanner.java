package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.allay.path.AllayPathExecutor;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.path.AllayPathSnapshot;
import dev.anvilcraft.plasticraft.allay.path.AllayPlasticAvoidance;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 悦灵分层飞行规划:主线程直线扫掠与快照,工作线程局部 AABB A*。
 * 不复用树脂牵引线程池,也不把矿车当墙。
 */
public final class AllayFlightPlanner {
    public static final double SPEED = 0.25D;
    private static final double STALE_MOVE_SQR = 16.0D;
    private static final int STALE_TICKS = 40;
    // tick sprint 会让游戏时间远快于工作线程，搜索失效必须按真实时间判定。
    private static final long SEARCH_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(5L);

    private AllayFlightPlanner() {
    }

    /** 测试与调试用同步入口;玩法路径走 {@link FlightTask#advance}。 */
    public static List<Vec3> plan(Entity worker, Vec3 goal) {
        Vec3 actual = worker.position();
        Vec3 start = alignToGrid(worker, snapToFree(worker, actual));
        Vec3 safeGoal = snapToFree(worker, goal);
        List<Vec3> path;
        if (start.distanceToSqr(safeGoal) < 0.04D) {
            path = List.of(safeGoal);
        } else if (isClear(worker, start, safeGoal, true)) {
            path = List.of(safeGoal);
        } else {
            Vec3 localGoal = AllayPathSnapshot.nextLocalGoal(worker, start, safeGoal);
            path = AllayPathSnapshot.search(AllayPathSnapshot.capture(worker, start, localGoal), start, localGoal);
            if (path.isEmpty()) {
                path = elevate(worker, start, safeGoal);
            }
        }
        return escape(actual, start, path);
    }

    public static double length(List<Vec3> path, Vec3 from) {
        double total = 0.0D;
        Vec3 previous = from;
        for (Vec3 point : path) {
            total += previous.distanceTo(point);
            previous = point;
        }
        return total;
    }

    public static Vec3 snapToFree(Entity worker, Vec3 goal) {
        if (worker.level().noBlockCollision(worker, boxAt(worker, goal))) return goal;
        BlockPos origin = BlockPos.containing(goal);
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int y = -1; y <= 4; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    Vec3 candidate = ConstructionWorkerSpace.navigationPoint(
                        origin.getX() + x,
                        origin.getY() + y,
                        origin.getZ() + z
                    );
                    if (!worker.level().noBlockCollision(worker, boxAt(worker, candidate))) continue;
                    double dist = candidate.distanceToSqr(goal);
                    if (dist < bestDist) {
                        bestDist = dist;
                        best = candidate;
                    }
                }
            }
        }
        return best != null ? best : goal;
    }

    /**
     * 已经被方块包住时的脱困点。碰撞钳制会把指令位移全部吃掉,悦灵只会原地抖动并持续窒息,
     * 所以先按标准吸附窗口找,再逐圈扩大搜索半径;彻底找不到才返回 {@code null} 交给上层放弃。
     */
    @Nullable
    public static Vec3 escapeSolid(Entity worker, int maxRadius) {
        Vec3 from = worker.position();
        Vec3 snapped = snapToFree(worker, from);
        if (!snapped.equals(from)) return snapped;
        BlockPos origin = BlockPos.containing(from);
        for (int radius = 4; radius <= maxRadius; radius++) {
            Vec3 best = null;
            double bestDist = Double.MAX_VALUE;
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z))) != radius) continue;
                        Vec3 candidate = ConstructionWorkerSpace.navigationPoint(
                            origin.getX() + x,
                            origin.getY() + y,
                            origin.getZ() + z
                        );
                        if (!worker.level().noBlockCollision(worker, boxAt(worker, candidate))) continue;
                        double dist = candidate.distanceToSqr(from);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = candidate;
                        }
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /**
     * 把规划起点拉回所在格的导航点。悦灵实际停靠位常有亚格漂移,包围盒会同时压住上一格,
     * 于是每一步扫掠都要连带检测那一格的天花板并被整体否决,明明只差两格也规划不出路径;
     * 对齐后包围盒只落在本格内,不会改变实际站位,吸附失败时保持原姿态。
     */
    public static Vec3 alignToGrid(Entity worker, Vec3 from) {
        Vec3 aligned = ConstructionWorkerSpace.navigationPoint(BlockPos.containing(from));
        if (aligned.distanceToSqr(from) < 1.0E-6D) return from;
        if (!worker.level().noBlockCollision(worker, boxAt(worker, aligned))) return from;
        if (!isClear(worker, from, aligned)) return from;
        return aligned;
    }

    public static boolean isClear(Entity worker, Vec3 from, Vec3 to) {
        return isClear(worker, from, to, false);
    }

    /**
     * {@code avoidPlastic} 为真时塑料实体的整体包围盒也算阻挡,只用于否决直飞捷径,
     * 让后续 A* 用绕行代价重新选路;抬升兜底仍按可推动处理,不因塑料判定无路可走。
     */
    public static boolean isClear(Entity worker, Vec3 from, Vec3 to, boolean avoidPlastic) {
        double distance = from.distanceTo(to);
        if (distance < 1.0E-4D) return true;
        int steps = Math.max(1, (int) Math.ceil(distance));
        Vec3 previous = from;
        for (int step = 1; step <= steps; step++) {
            Vec3 point = from.lerp(to, (double) step / (double) steps);
            AABB swept = boxAt(worker, previous).minmax(boxAt(worker, point));
            if (!worker.level().noBlockCollision(worker, swept)) return false;
            previous = point;
        }
        if (!avoidPlastic) return true;
        AABB corridor = boxAt(worker, from).minmax(boxAt(worker, to)).deflate(0.002D);
        return !AllayPlasticAvoidance.blocked(worker.level(), corridor);
    }

    static AABB boxAt(Entity worker, Vec3 pos) {
        AABB current = worker.getBoundingBox();
        return current.move(pos.subtract(worker.position()));
    }

    private static List<Vec3> escape(Vec3 actual, Vec3 start, List<Vec3> path) {
        if (start.distanceToSqr(actual) < 0.04D) return path;
        List<Vec3> escaped = new ArrayList<>(path.size() + 1);
        escaped.add(start);
        escaped.addAll(path);
        return escaped;
    }

    private static List<Vec3> elevate(Entity worker, Vec3 start, Vec3 goal) {
        for (int rise = 2; rise <= 8; rise++) {
            Vec3 up = start.add(0.0D, rise, 0.0D);
            Vec3 aboveGoal = goal.add(0.0D, rise, 0.0D);
            if (!isClear(worker, start, up)) continue;
            if (isClear(worker, up, aboveGoal) && isClear(worker, aboveGoal, goal)) {
                return List.of(up, aboveGoal, goal);
            }
            Vec3 over = new Vec3(goal.x, start.y + rise, goal.z);
            if (isClear(worker, up, over) && isClear(worker, over, aboveGoal) && isClear(worker, over, goal)) {
                return List.of(up, over, aboveGoal, goal);
            }
        }
        return List.of();
    }

    public static final class FlightTask {
        private static final int RECALL_FAILURES = 3;
        private final UUID allayId;
        private Vec3 goal;
        private AllayPathPriority priority;
        private Vec3 snapshotStart;
        private CompletableFuture<List<Vec3>> future;
        private List<Vec3> path = List.of();
        private boolean complete;
        private int age;
        private boolean submitted;
        private int failCooldown;
        private int failedPlans;
        private long submittedAtNanos;

        public FlightTask(UUID allayId, Vec3 goal, AllayPathPriority priority) {
            this.allayId = allayId;
            this.goal = goal;
            this.priority = priority;
        }

        public boolean sameGoal(Vec3 goal) {
            return this.goal.distanceToSqr(goal) < 0.0625D;
        }

        public void setPriority(AllayPathPriority priority) {
            this.priority = priority;
        }

        public List<Vec3> path() {
            return this.path;
        }

        public boolean isComplete() {
            return this.complete;
        }

        public boolean repeatedlyUnreachable() {
            return this.failedPlans >= RECALL_FAILURES;
        }

        /** 搜索尚未产出可执行路径时保持施工飞行状态,避免被主线程误判为悬停。 */
        public boolean isPending() {
            return (!this.complete || this.submitted) && !this.searchTimedOut();
        }

        public boolean isSearching() {
            return this.submitted && this.future != null && !this.future.isDone() && !this.searchTimedOut();
        }

        public void cancel() {
            AllayPathExecutor.cancel(this.allayId);
            this.future = null;
            this.snapshotStart = null;
            this.path = List.of();
            this.submitted = false;
            this.complete = false;
            this.age = 0;
            this.failCooldown = 0;
            this.failedPlans = 0;
            this.submittedAtNanos = 0L;
        }

        public boolean advance(Entity worker) {
            this.age++;
            Vec3 actual = worker.position();
            if (this.submitted) {
                if (this.searchTimedOut()) {
                    AllayPathExecutor.cancel(this.allayId);
                    return this.finish(worker, actual, List.of());
                }
                if (this.stale(worker)) {
                    this.cancel();
                    return false;
                }
                if (this.future == null || !this.future.isDone()) {
                    return false;
                }
                return this.finishSearch(worker, actual);
            }
            // 上一次规划失败就退避,不能只看 path 是否为空:失败时也可能产出一个脱困跳点,
            // 那条"路径"一到达就消耗完,若不退避就会每 tick 重跑一次局部 A* 空转烧 CPU。
            if (this.complete && (this.path.isEmpty() || this.failedPlans > 0) && this.failCooldown++ < 10) {
                return false;
            }
            Vec3 free = snapToFree(worker, actual);
            Vec3 start = alignToGrid(worker, free);
            Vec3 safeGoal = snapToFree(worker, this.goal);
            if (start.distanceToSqr(safeGoal) < 0.04D) {
                this.path = escape(actual, start, List.of(safeGoal));
                this.complete = true;
                this.failCooldown = 0;
                this.failedPlans = 0;
                return true;
            }
            if (isClear(worker, start, safeGoal, true)) {
                this.path = escape(actual, start, List.of(safeGoal));
                this.complete = true;
                this.failCooldown = 0;
                this.failedPlans = 0;
                return true;
            }
            if (!this.submitted) {
                if (!AllayPathSnapshot.acquireCapturePermit(worker)) return false;
                // 失效判定衡量的是"悦灵离拍快照时的位置有多远",必须锚在真实姿态上,
                // 否则起点对齐带来的固定偏移会把静止不动的悦灵也判成移动过。
                this.snapshotStart = actual;
                // 连续失败时交替走"分段走廊"与"直取目标":同一份失败计划重跑再多次也是同样结果,
                // 换掉局部目标才可能让 A* 走出另一侧的绕行。
                Vec3 localGoal = AllayPathSnapshot.nextLocalGoal(
                    worker,
                    start,
                    safeGoal,
                    this.failedPlans % 2 == 1
                );
                AllayPathSnapshot.Capture capture;
                try {
                    capture = AllayPathSnapshot.capture(worker, start, localGoal);
                } catch (RuntimeException error) {
                    List<Vec3> elevated = elevate(worker, start, safeGoal);
                    this.path = escape(actual, start, elevated);
                    this.complete = true;
                    this.failCooldown = elevated.isEmpty() ? 1 : 0;
                    this.failedPlans = elevated.isEmpty() ? this.failedPlans + 1 : 0;
                    return true;
                }
                AllayPathPriority effective = free.distanceToSqr(actual) > 0.25D
                    ? AllayPathPriority.ESCAPE
                    : this.priority;
                this.future = AllayPathExecutor.submit(this.allayId, effective, cancelled ->
                    AllayPathSnapshot.search(capture, start, localGoal, cancelled)
                );
                this.submitted = true;
                this.submittedAtNanos = System.nanoTime();
                this.complete = false;
                this.age = 0;
                this.failCooldown = 0;
                return false;
            }
            return false;
        }

        private boolean finishSearch(Entity worker, Vec3 actual) {
            List<Vec3> found = List.of();
            try {
                if (!this.future.isCancelled()) {
                    found = this.future.get();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException error) {
                found = List.of();
            }
            return this.finish(worker, actual, found);
        }

        private boolean finish(Entity worker, Vec3 actual, List<Vec3> found) {
            Vec3 start = alignToGrid(worker, snapToFree(worker, actual));
            if (found.isEmpty()) {
                Vec3 safeGoal = snapToFree(worker, this.goal);
                found = elevate(worker, start, safeGoal);
            }
            this.failedPlans = found.isEmpty() ? this.failedPlans + 1 : 0;
            this.path = escape(actual, start, found);
            this.complete = true;
            this.submitted = false;
            this.future = null;
            this.snapshotStart = null;
            this.submittedAtNanos = 0L;
            this.age = 0;
            this.failCooldown = found.isEmpty() ? 1 : 0;
            return true;
        }

        private boolean searchTimedOut() {
            return this.submitted
                && this.future != null
                && !this.future.isDone()
                && System.nanoTime() - this.submittedAtNanos >= SEARCH_TIMEOUT_NANOS;
        }

        private boolean stale(Entity worker) {
            if (this.snapshotStart != null
                && worker.position().distanceToSqr(this.snapshotStart) > STALE_MOVE_SQR) {
                return true;
            }
            return this.submitted
                && this.age >= STALE_TICKS
                && this.snapshotStart != null
                && worker.position().distanceToSqr(this.snapshotStart) > 0.25D;
        }
    }
}
