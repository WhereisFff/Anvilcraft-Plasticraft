package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** 沿规划路点移动戴帽悦灵;到达后保持悬停等待工具执行器。 */
public final class AllayFlightNavigator {
    private static final int AVOIDANCE_LOOKAHEAD_TICKS = 4;
    private static final int AVOIDANCE_HOLD_TICKS = 8;
    private static final int COLLISION_REPLAN_TICKS = 2;
    private static final int STALLED_REPLAN_TICKS = 20;
    private static final double AVOIDANCE_CLEARANCE = 0.08D;
    private static final double INTERMEDIATE_WAYPOINT_REACHED_SQR = 0.01D;
    private static final double FINAL_WAYPOINT_REACHED_SQR = 0.09D;
    private static final double PROGRESS_EPSILON = 0.025D;
    private final List<Vec3> waypoints = new ArrayList<>();
    private int index;
    private Vec3 avoidanceDirection = Vec3.ZERO;
    private int avoidanceTicks;
    private int progressIndex = -1;
    private double bestWaypointDistance = Double.POSITIVE_INFINITY;
    private int stalledTicks;
    private int collisionTicks;
    private Vec3 lastCommandedMotion = Vec3.ZERO;
    private boolean replanRequested;

    public void setPath(List<Vec3> path) {
        this.waypoints.clear();
        this.waypoints.addAll(path);
        this.index = 0;
        this.resetRuntimeState();
        this.replanRequested = false;
    }

    public void clear() {
        this.waypoints.clear();
        this.index = 0;
        this.resetRuntimeState();
        this.replanRequested = false;
    }

    public void requestReplan() {
        this.waypoints.clear();
        this.index = 0;
        this.resetRuntimeState();
        this.replanRequested = true;
    }

    public boolean consumeReplanRequest() {
        boolean requested = this.replanRequested;
        this.replanRequested = false;
        return requested;
    }

    private void resetRuntimeState() {
        this.avoidanceDirection = Vec3.ZERO;
        this.avoidanceTicks = 0;
        this.progressIndex = -1;
        this.bestWaypointDistance = Double.POSITIVE_INFINITY;
        this.stalledTicks = 0;
        this.collisionTicks = 0;
        this.lastCommandedMotion = Vec3.ZERO;
    }

    public boolean hasPath() {
        return this.index < this.waypoints.size();
    }

    public boolean endsNear(Vec3 goal) {
        if (this.waypoints.isEmpty()) return false;
        return this.waypoints.getLast().distanceToSqr(goal) < FINAL_WAYPOINT_REACHED_SQR;
    }

    public boolean follow(Entity worker) {
        if (!this.hasPath()) {
            worker.setDeltaMovement(Vec3.ZERO);
            this.lastCommandedMotion = Vec3.ZERO;
            return false;
        }
        Vec3 target = this.waypoints.get(this.index);
        Vec3 delta = target.subtract(worker.position());
        while (delta.lengthSqr() < this.waypointReachedDistanceSqr() && this.canAdvanceWaypoint(worker)) {
            this.index++;
            if (!this.hasPath()) {
                // 终点判定容差(0.3 格)远大于落脚点与相邻格的间隙,必须精确落位:
                // 差之毫厘会让包围盒探进相邻格,交付判定据此认为工人占着目标格,而这点位移又小到无法执行。
                settleOn(worker, target);
                worker.setDeltaMovement(Vec3.ZERO);
                this.lastCommandedMotion = Vec3.ZERO;
                return false;
            }
            target = this.waypoints.get(this.index);
            delta = target.subtract(worker.position());
        }
        if (this.shouldReplan(worker, delta.length())) {
            this.requestReplan();
            worker.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        double speed = Math.min(AllayFlightPlanner.SPEED, delta.length());
        if (delta.lengthSqr() < 1.0E-8D) {
            worker.setDeltaMovement(Vec3.ZERO);
            this.lastCommandedMotion = Vec3.ZERO;
            return true;
        }
        Vec3 motion = delta.normalize().scale(speed);
        motion = this.avoidWorkers(worker, motion);
        if (!movementClear(worker, motion)) {
            this.requestReplan();
            worker.setDeltaMovement(Vec3.ZERO);
            return false;
        }
        worker.setDeltaMovement(motion);
        this.lastCommandedMotion = motion;
        if (motion.lengthSqr() > 1.0E-8D) {
            worker.setYRot((float) (Math.toDegrees(Math.atan2(-motion.x, motion.z))));
        }
        return true;
    }

    /**
     * 抵达终点时对齐到路点本身。路点来自规划器的自由格采样或落脚点,本身就是可站位置;
     * 只有该处此刻确实无碰撞才对齐,免得把工人塞进刚被别人占掉的格子。
     */
    private static void settleOn(Entity worker, Vec3 point) {
        if (worker.position().distanceToSqr(point) < 1.0E-8D) return;
        if (!worker.level().noBlockCollision(worker, ConstructionWorkerSpace.boxAt(point))) return;
        worker.setPos(point.x, point.y, point.z);
    }

    private boolean canAdvanceWaypoint(Entity worker) {
        // 跳点必须同样避开塑料实体,否则会把规划好的绕行直接抹平。
        return this.index == this.waypoints.size() - 1
            || AllayFlightPlanner.isClear(worker, worker.position(), this.waypoints.get(this.index + 1), true);
    }

    private boolean shouldReplan(Entity worker, double waypointDistance) {
        if (this.progressIndex != this.index) {
            this.progressIndex = this.index;
            this.bestWaypointDistance = waypointDistance;
            this.stalledTicks = 0;
            this.collisionTicks = 0;
        } else if (waypointDistance < this.bestWaypointDistance - PROGRESS_EPSILON) {
            this.bestWaypointDistance = waypointDistance;
            this.stalledTicks = 0;
        } else {
            this.stalledTicks++;
        }
        if (worker.horizontalCollision && this.lastCommandedMotion.horizontalDistanceSqr() > 1.0E-8D) {
            this.collisionTicks++;
        } else {
            this.collisionTicks = 0;
        }
        return this.collisionTicks >= COLLISION_REPLAN_TICKS || this.stalledTicks >= STALLED_REPLAN_TICKS;
    }

    private static boolean movementClear(Entity worker, Vec3 motion) {
        if (motion.lengthSqr() < 1.0E-8D) return true;
        AABB current = worker.getBoundingBox();
        if (!worker.level().noBlockCollision(worker, current)) return true;
        return worker.level().noBlockCollision(worker, current.expandTowards(motion));
    }

    private Vec3 avoidWorkers(Entity worker, Vec3 motion) {
        AABB swept = worker.getBoundingBox().expandTowards(motion.scale(AVOIDANCE_LOOKAHEAD_TICKS))
            .inflate(AllayFlightPlanner.SPEED * AVOIDANCE_LOOKAHEAD_TICKS + AVOIDANCE_CLEARANCE);
        List<WorkingAllayEntity> others = worker.level().getEntitiesOfClass(
            WorkingAllayEntity.class,
            swept,
            other -> other != worker && other.isAlive()
        );
        if (this.avoidanceTicks > 0) {
            Vec3 held = avoidanceMotion(motion, this.avoidanceDirection);
            if (clearAvoidance(worker, held, others)) {
                this.avoidanceTicks--;
                return held;
            }
            this.avoidanceDirection = Vec3.ZERO;
            this.avoidanceTicks = 0;
        }
        WorkingAllayEntity conflict = null;
        double nearest = Double.MAX_VALUE;
        for (WorkingAllayEntity other : others) {
            if (!mustYield(worker, other) || !predictedConflict(worker, motion, other)) continue;
            double distance = worker.distanceToSqr(other);
            if (distance < nearest) {
                nearest = distance;
                conflict = other;
            }
        }
        if (conflict == null) {
            return motion;
        }
        for (Vec3 direction : avoidanceDirections(worker, motion)) {
            Vec3 candidate = avoidanceMotion(motion, direction);
            if (!clearAvoidance(worker, candidate, others)) continue;
            this.avoidanceDirection = direction;
            this.avoidanceTicks = AVOIDANCE_HOLD_TICKS;
            return candidate;
        }
        this.avoidanceDirection = Vec3.ZERO;
        this.avoidanceTicks = 0;
        // 窄洞里没有侧移空间时继续原路线;施工悦灵没有硬碰撞,互相穿过比双向永久等待更可靠。
        return motion;
    }

    private static boolean predictedConflict(Entity worker, Vec3 motion, WorkingAllayEntity other) {
        Vec3 otherMotion = other.getDeltaMovement();
        for (int tick = 1; tick <= AVOIDANCE_LOOKAHEAD_TICKS; tick++) {
            AABB own = worker.getBoundingBox().move(motion.scale(tick)).inflate(AVOIDANCE_CLEARANCE);
            AABB theirs = other.getBoundingBox().move(otherMotion.scale(tick)).inflate(AVOIDANCE_CLEARANCE);
            if (own.intersects(theirs)) return true;
        }
        return false;
    }

    private static boolean mustYield(Entity worker, WorkingAllayEntity other) {
        if (worker instanceof WorkingAllayEntity workingWorker) {
            boolean workerDocking = workingWorker.flightState() == AllayFlightState.DOCKING;
            boolean otherDocking = other.flightState() == AllayFlightState.DOCKING;
            if (workerDocking != otherDocking) return workerDocking;
        }
        if (other.getDeltaMovement().lengthSqr() < 1.0E-4D && !other.navigator().hasPath()) return true;
        return worker.getUUID().compareTo(other.getUUID()) > 0;
    }

    private static List<Vec3> avoidanceDirections(Entity worker, Vec3 motion) {
        Vec3 forward = motion.normalize();
        Vec3 lateral = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (lateral.lengthSqr() < 1.0E-6D) {
            lateral = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            lateral = lateral.normalize();
        }
        double sign = (worker.getUUID().getLeastSignificantBits() & 1L) == 0L ? 1.0D : -1.0D;
        Vec3 vertical = sign > 0.0D ? new Vec3(0.0D, 1.0D, 0.0D) : new Vec3(0.0D, -1.0D, 0.0D);
        return List.of(lateral.scale(sign), lateral.scale(-sign), vertical, vertical.reverse());
    }

    private static Vec3 avoidanceMotion(Vec3 motion, Vec3 direction) {
        Vec3 candidate = motion.scale(0.35D).add(direction.scale(AllayFlightPlanner.SPEED * 0.8D));
        double length = candidate.length();
        return length > AllayFlightPlanner.SPEED
            ? candidate.scale(AllayFlightPlanner.SPEED / length)
            : candidate;
    }

    private static boolean clearAvoidance(
        Entity worker,
        Vec3 motion,
        List<WorkingAllayEntity> others
    ) {
        AABB swept = worker.getBoundingBox()
            .expandTowards(motion.scale(AVOIDANCE_LOOKAHEAD_TICKS))
            .inflate(0.002D, 0.0D, 0.002D);
        if (!worker.level().noBlockCollision(worker, swept)) return false;
        for (WorkingAllayEntity other : others) {
            if (mustYield(worker, other) && predictedConflict(worker, motion, other)) return false;
        }
        return true;
    }

    private double waypointReachedDistanceSqr() {
        return this.index == this.waypoints.size() - 1
            ? FINAL_WAYPOINT_REACHED_SQR
            : INTERMEDIATE_WAYPOINT_REACHED_SQR;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", this.index);
        ListTag points = new ListTag();
        for (Vec3 point : this.waypoints) {
            CompoundTag pointTag = new CompoundTag();
            pointTag.putDouble("X", point.x);
            pointTag.putDouble("Y", point.y);
            pointTag.putDouble("Z", point.z);
            points.add(pointTag);
        }
        tag.put("Points", points);
        return tag;
    }

    public void load(CompoundTag tag) {
        this.clear();
        this.index = tag.getInt("Index");
        ListTag points = tag.getList("Points", Tag.TAG_COMPOUND);
        for (int entry = 0; entry < points.size(); entry++) {
            CompoundTag pointTag = points.getCompound(entry);
            this.waypoints.add(new Vec3(pointTag.getDouble("X"), pointTag.getDouble("Y"), pointTag.getDouble("Z")));
        }
    }
}
