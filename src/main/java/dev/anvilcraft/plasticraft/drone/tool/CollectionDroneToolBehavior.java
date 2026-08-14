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
        if (drone.isCollectionFull()) {
            drone.setActionState((byte) 0);
            landIfFlying(drone);
            tryUnload(drone, level, owner.get());
            return;
        }
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
        if (job != null
            && job.dimension().equals(level.dimension())
            && ConstructionJobController.isTaskCollectPhase(job)) {
            tickTask(drone, level, job, owner.get());
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
                landIfFlying(drone);
                tryUnload(drone, level, ownerId);
                return;
            }
            target = leasedItem(level, progress, drone.getUUID());
            if (target == null) return;
        }
        pursue(drone, level, target, progress, ownerId);
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
        pursue(drone, level, target, null, ownerId);
    }

    private static void pursue(
        DroneEntity drone,
        ServerLevel level,
        ItemEntity target,
        @Nullable ConstructionJobProgress progress,
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
            landIfFlying(drone);
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
