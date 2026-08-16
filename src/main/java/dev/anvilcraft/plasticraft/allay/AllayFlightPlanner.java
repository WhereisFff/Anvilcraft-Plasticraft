package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.allay.path.AllayPathExecutor;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.path.AllayPathSnapshot;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
        Vec3 start = snapToFree(worker, actual);
        Vec3 safeGoal = snapToFree(worker, goal);
        List<Vec3> path;
        if (start.distanceToSqr(safeGoal) < 0.04D) {
            path = List.of(safeGoal);
        } else if (isClear(worker, start, safeGoal)) {
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

    public static boolean isClear(Entity worker, Vec3 from, Vec3 to) {
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
        return true;
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
            if (this.complete && this.path.isEmpty() && this.failCooldown++ < 10) {
                return false;
            }
            Vec3 start = snapToFree(worker, actual);
            Vec3 safeGoal = snapToFree(worker, this.goal);
            if (start.distanceToSqr(safeGoal) < 0.04D) {
                this.path = escape(actual, start, List.of(safeGoal));
                this.complete = true;
                this.failCooldown = 0;
                this.failedPlans = 0;
                return true;
            }
            if (isClear(worker, start, safeGoal)) {
                this.path = escape(actual, start, List.of(safeGoal));
                this.complete = true;
                this.failCooldown = 0;
                this.failedPlans = 0;
                return true;
            }
            if (!this.submitted) {
                if (!AllayPathSnapshot.acquireCapturePermit(worker)) return false;
                this.snapshotStart = start;
                Vec3 localGoal = AllayPathSnapshot.nextLocalGoal(worker, start, safeGoal);
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
                AllayPathPriority effective = start.distanceToSqr(actual) > 0.25D
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
            Vec3 start = snapToFree(worker, actual);
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
