package dev.anvilcraft.plasticraft.drone.tool;

import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
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
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 无站拆除无人机:飞到一格触及处按切石机砸击语义瞬间拆除,不吸取掉落物。 */
public final class DemolitionDroneToolBehavior implements DroneToolBehavior {
    public static final DemolitionDroneToolBehavior INSTANCE = new DemolitionDroneToolBehavior();

    private DemolitionDroneToolBehavior() {
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
        if (job == null || !job.dimension().equals(level.dimension())) {
            continueOrLand(drone);
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
            if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
                drone.setWaitReason(ConstructionWaitReason.DEMOLITION);
            } else if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
                drone.setWaitReason(ConstructionWaitReason.PERMISSION);
            } else if (job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
                drone.setWaitReason(ConstructionWaitReason.SOURCE);
            }
            landIfFlying(drone);
            return;
        }
        if (job.state() != ConstructionJob.STATE_DEMOLISHING) {
            if (job.state() == ConstructionJob.STATE_COMMITTING || job.state() == ConstructionJob.STATE_BUILDING) {
                leaveSiteThenLand(drone, level, job);
            }
            return;
        }
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return;
        if (drone.assignedJobId().filter(job.jobId()::equals).isEmpty() || drone.taskOpId() < 0) {
            if (!tryClaim(drone, level, job, progress)) {
                if (progress.allDemolishResolved()) {
                    leaveSiteThenLand(drone, level, job);
                }
                return;
            }
        }
        ConstructionBuildOp op = progress.operation(drone.taskOpId());
        if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.status() == ConstructionBuildOp.Status.DELIVERED) {
            drone.clearAssignment(false);
            drone.setActionState((byte) 0);
            if (progress.allDemolishResolved()) {
                leaveSiteThenLand(drone, level, job);
            }
            return;
        }
        flyToSmash(drone, level, progress, op, job);
    }

    public static boolean tryClaim(
        DroneEntity drone,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ConstructionBuildOp op = ConstructionJobController.nextAssignableDemolish(level, progress);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (drone.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        Vec3 target = Vec3.atBottomCenterOf(approach);
        double distance = drone.position().distanceTo(target);
        DroneEnergyModel.Quote quote = new DroneEnergyModel.Quote(
            (int) Math.ceil(distance / DroneFlightPlanner.SPEED) + 20,
            distance,
            1
        );
        if (!drone.canAcceptQuote(quote)) {
            drone.setWaitReason(ConstructionWaitReason.ENERGY);
            return false;
        }
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseDrone(drone.getUUID());
        drone.assign(job.jobId(), op.id());
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    private static void flyToSmash(
        DroneEntity drone,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        ConstructionJob job
    ) {
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op);
            if (approach != null) {
                op.setApproach(approach);
            }
        }
        if (approach == null) {
            flyTo(drone, Vec3.atBottomCenterOf(op.pos().above(2)));
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(approach);
        AABB droneBox = drone.getBoundingBox();
        AABB block = new AABB(op.pos());
        if (droneBox.intersects(block) || !droneBox.intersects(block.inflate(ConstructionJobController.REACH))) {
            flyTo(drone, target);
            return;
        }
        drone.setActionState((byte) 1);
        if (ConstructionJobController.tryDemolish(level, progress, op)) {
            drone.consumeEnergy(drone.toolDefinition().instantActionEnergyCost());
            drone.clearAssignment(false);
            drone.setActionState((byte) 0);
            drone.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob current = ConstructionJobIndex.get(level).job(progress.jobId());
            if (current != null && current.state() == ConstructionJob.STATE_DEMOLISHING
                && tryClaim(drone, level, current, progress)) {
                return;
            }
            if (current != null && progress.allDemolishResolved()) {
                leaveSiteThenLand(drone, level, current);
            }
        }
    }

    private static void leaveSiteThenLand(DroneEntity drone, ServerLevel level, ConstructionJob job) {
        AABB site = ConstructionJobController.worldBox(job);
        if (!site.inflate(2.0D).intersects(drone.getBoundingBox())) {
            drone.clearAssignment(false);
            landIfFlying(drone);
            return;
        }
        flyTo(drone, drone.position().add(2.0D, 1.0D, 0.0D));
    }

    private static void continueOrLand(DroneEntity drone) {
        if (drone.navigator().hasPath()) {
            drone.setNoGravity(true);
            drone.setFlightState(DroneFlightState.FLYING);
            return;
        }
        drone.clearAssignment(false);
        landIfFlying(drone);
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
