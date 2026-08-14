package dev.anvilcraft.plasticraft.drone.tool;

import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.drone.DroneEnergyModel;
import dev.anvilcraft.plasticraft.drone.DroneFlightPlanner;
import dev.anvilcraft.plasticraft.drone.DroneFlightState;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 收集无人机:自由模式重扫 16 格吸入任意掉落,任务模式只收本任务标记物并向所有者卸货。 */
public final class CollectionDroneToolBehavior implements DroneToolBehavior {
    public static final CollectionDroneToolBehavior INSTANCE = new CollectionDroneToolBehavior();
    public static final double FREE_RANGE = 16.0D;

    private CollectionDroneToolBehavior() {
    }

    @Override
    public void serverTick(DroneEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) return;
        if (drone.flightState() == DroneFlightState.DOCKING) return;
        Optional<UUID> owner = drone.getOwner();
        if (owner.isEmpty()) {
            drone.clearAssignment(false);
            return;
        }
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
        boolean sameSite = job != null && job.dimension().equals(level.dimension());
        if (drone.isCollectionFull()) {
            drone.setActionState((byte) 0);
            if (sameSite && onSite(drone, job)) {
                leaveSiteThenLand(drone, level, job);
            } else {
                landIfFlying(drone);
            }
            tryUnload(drone, level, owner.get());
            return;
        }
        if (sameSite && ConstructionJobController.isTaskCollectPhase(job)) {
            tickTask(drone, level, job, owner.get());
            return;
        }
        if (sameSite && onSite(drone, job) && shouldEvacuateFinishedJob(job)) {
            leaveSiteThenLand(drone, level, job);
            tryUnload(drone, level, owner.get());
            return;
        }
        tickFree(drone, level, owner.get());
    }

    public static boolean tryClaim(
        DroneEntity drone,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (drone.isCollectionFull()) return false;
        ItemEntity current = leasedItem(level, progress, drone.getUUID());
        if (current != null) return true;
        ItemEntity target = ConstructionJobController.nextAssignableDebris(
            level,
            job,
            progress,
            drone.position()
        );
        if (target == null || !drone.canAcceptCollection(target.getItem())) return false;
        if (!acceptQuote(drone, target.position())) return false;
        if (!progress.leaseDebris(target.getUUID(), drone.getUUID())) return false;
        ConstructionDebris mark = ConstructionDebris.get(target.getItem());
        drone.assign(job.jobId(), mark == null ? -1 : mark.operationId());
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    @Nullable
    public static ItemEntity nextFreeTarget(DroneEntity drone, ServerLevel level) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        AABB box = drone.getBoundingBox().inflate(FREE_RANGE);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.isAlive() || entity.hasPickUpDelay() || entity.getItem().isEmpty()) continue;
            double distance = drone.distanceTo(entity);
            if (distance <= FREE_RANGE && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static void tickTask(DroneEntity drone, ServerLevel level, ConstructionJob job, UUID ownerId) {
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) {
            tickFree(drone, level, ownerId);
            return;
        }
        ItemEntity target = leasedItem(level, progress, drone.getUUID());
        if (target == null) {
            progress.releaseDebrisLeasesOf(drone.getUUID());
            drone.clearAssignment(false);
            if (!tryClaim(drone, level, job, progress)) {
                drone.setActionState((byte) 0);
                if (job.state() == ConstructionJob.STATE_COLLECTING_DEBRIS || progress.allDemolishResolved()) {
                    leaveSiteThenLand(drone, level, job);
                }
                tryUnload(drone, level, ownerId);
                return;
            }
            target = leasedItem(level, progress, drone.getUUID());
            if (target == null) return;
        }
        pursue(drone, level, target, progress, job, ownerId);
    }

    private static void tickFree(DroneEntity drone, ServerLevel level, UUID ownerId) {
        if (drone.assignedJobId().isPresent()) {
            drone.clearAssignment(false);
        }
        ItemEntity target = nextFreeTarget(drone, level);
        if (target == null) {
            drone.setActionState((byte) 0);
            landIfFlying(drone);
            tryUnload(drone, level, ownerId);
            return;
        }
        if (!drone.navigator().hasPath() || !drone.navigator().endsNear(target.position())) {
            if (!acceptQuote(drone, target.position())) {
                landIfFlying(drone);
                return;
            }
        }
        pursue(drone, level, target, null, null, ownerId);
    }

    private static void pursue(
        DroneEntity drone,
        ServerLevel level,
        ItemEntity target,
        @Nullable ConstructionJobProgress progress,
        @Nullable ConstructionJob job,
        UUID ownerId
    ) {
        if (!target.isAlive() || target.getItem().isEmpty()) {
            if (progress != null) {
                progress.releaseDebrisLease(target.getUUID());
                ConstructionJobStore.get(level).markDirty();
            }
            drone.setActionState((byte) 0);
            drone.clearAssignment(false);
            return;
        }
        if (drone.distanceTo(target) > ConstructionJobController.REACH) {
            drone.setActionState((byte) 0);
            flyTo(drone, target.position());
            return;
        }
        drone.setActionState((byte) 1);
        if (!ConstructionJobController.tryCollect(drone, target, progress)) {
            drone.setActionState((byte) 0);
            return;
        }
        drone.consumeEnergy(drone.toolDefinition().instantActionEnergyCost());
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
        drone.clearAssignment(false);
        if (progress != null) {
            ConstructionJobStore.get(level).markDirty();
        }
        if (drone.isCollectionFull()) {
            if (job != null && onSite(drone, job)) {
                leaveSiteThenLand(drone, level, job);
            } else {
                landIfFlying(drone);
            }
            tryUnload(drone, level, ownerId);
        }
    }

    private static boolean acceptQuote(DroneEntity drone, Vec3 target) {
        double distance = drone.position().distanceTo(target);
        DroneEnergyModel.Quote quote = new DroneEnergyModel.Quote(
            (int) Math.ceil(distance / DroneFlightPlanner.SPEED) + 20,
            distance,
            1
        );
        if (drone.canAcceptQuote(quote)) {
            drone.setWaitReason(ConstructionWaitReason.NONE);
            return true;
        }
        drone.setWaitReason(ConstructionWaitReason.ENERGY);
        return false;
    }

    @Nullable
    private static ItemEntity leasedItem(ServerLevel level, ConstructionJobProgress progress, UUID droneId) {
        UUID entityId = progress.leasedDebrisEntity(droneId);
        if (entityId == null) return null;
        Entity entity = level.getEntity(entityId);
        if (entity instanceof ItemEntity item && item.isAlive() && ConstructionDebris.isMarked(item.getItem())) {
            return item;
        }
        progress.releaseDebrisLease(entityId);
        return null;
    }

    private static boolean shouldEvacuateFinishedJob(ConstructionJob job) {
        return job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_COMMITTING;
    }

    private static boolean onSite(DroneEntity drone, ConstructionJob job) {
        return ConstructionJobController.worldBox(job).inflate(2.0D).intersects(drone.getBoundingBox());
    }

    /** 任务收完或满载后先离开蓝图包围盒再落地,避免落在即将建造的格子里。 */
    private static void leaveSiteThenLand(DroneEntity drone, ServerLevel level, ConstructionJob job) {
        AABB site = ConstructionJobController.worldBox(job);
        ServerPlayer owner = drone.getOwner()
            .map(id -> ConstructionJobController.findOwner(level.getServer(), level, id))
            .orElse(null);
        Vec3 goal = evacuateGoal(drone, site, owner);
        if (goal == null || arrivedOutside(drone, site, goal)) {
            drone.clearAssignment(false);
            landIfFlying(drone);
            return;
        }
        flyTo(drone, goal);
    }

    @Nullable
    private static Vec3 evacuateGoal(DroneEntity drone, AABB site, @Nullable ServerPlayer owner) {
        if (owner != null && !site.intersects(owner.getBoundingBox())) {
            return owner.position().add(0.0D, 1.0D, 0.0D);
        }
        if (!site.inflate(2.0D).intersects(drone.getBoundingBox())) {
            return null;
        }
        return pushOutside(drone, site);
    }

    private static boolean arrivedOutside(DroneEntity drone, AABB site, Vec3 goal) {
        return drone.position().distanceTo(goal) <= ConstructionJobController.REACH + 0.5D
            && !site.intersects(drone.getBoundingBox());
    }

    private static Vec3 pushOutside(DroneEntity drone, AABB site) {
        double cx = (site.minX + site.maxX) * 0.5D;
        double cz = (site.minZ + site.maxZ) * 0.5D;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int distance = 2; distance <= 4; distance++) {
            for (int[] side : sides) {
                double x = side[0] == 0 ? cx : (side[0] > 0 ? site.maxX : site.minX) + side[0] * distance;
                double z = side[1] == 0 ? cz : (side[1] > 0 ? site.maxZ : site.minZ) + side[1] * distance;
                Vec3 candidate = new Vec3(x, Math.max(drone.getY(), site.maxY), z);
                if (!drone.level().noCollision(drone, drone.getBoundingBox().move(candidate.subtract(drone.position())))) {
                    continue;
                }
                double dist = drone.position().distanceToSqr(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best != null ? best : drone.position().add(2.0D, 1.0D, 0.0D);
    }

    private static void tryUnload(DroneEntity drone, ServerLevel level, UUID ownerId) {
        Player owner = level.getPlayerByUUID(ownerId);
        if (owner == null || drone.distanceTo(owner) > FREE_RANGE) return;
        drone.unloadCollectionTo(owner);
    }

    private static void landIfFlying(DroneEntity drone) {
        if (drone.flightState() == DroneFlightState.FLYING) {
            drone.setNoGravity(false);
            drone.setFlightState(DroneFlightState.LANDING);
        }
    }

    private static void flyTo(DroneEntity drone, Vec3 goal) {
        drone.setNoGravity(true);
        if (drone.horizontalCollision) {
            drone.navigator().clear();
        }
        if (drone.flightState() != DroneFlightState.FLYING
            || !drone.navigator().hasPath()
            || !drone.navigator().endsNear(goal)) {
            List<Vec3> path = DroneFlightPlanner.plan(drone, goal);
            if (path.isEmpty()) {
                boolean blocked = drone.horizontalCollision
                    || !drone.level().noCollision(drone, drone.getBoundingBox());
                drone.setDeltaMovement(blocked ? new Vec3(0.0D, 0.12D, 0.0D) : Vec3.ZERO);
                drone.setFlightState(DroneFlightState.FLYING);
                return;
            }
            drone.navigator().setPath(path);
        }
        drone.setFlightState(DroneFlightState.FLYING);
    }
}
