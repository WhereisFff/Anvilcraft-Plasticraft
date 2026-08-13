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
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** 无站建设无人机:从所有者背包取料,飞到一格触及处交付施工投影。 */
public final class ConstructionDroneToolBehavior implements DroneToolBehavior {
    public static final ConstructionDroneToolBehavior INSTANCE = new ConstructionDroneToolBehavior();

    private ConstructionDroneToolBehavior() {
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
            if (!drone.hostedCarry().isEmpty()) return;
            drone.clearAssignment(false);
            if (drone.flightState() == DroneFlightState.FLYING) {
                drone.setFlightState(DroneFlightState.LANDING);
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_COMMITTING) {
            if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
                drone.setWaitReason(ConstructionWaitReason.MATERIAL);
            } else if (job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
                drone.setWaitReason(ConstructionWaitReason.SOURCE);
            }
            if (drone.flightState() == DroneFlightState.FLYING && drone.hostedCarry().isEmpty()) {
                drone.setNoGravity(false);
                drone.setFlightState(DroneFlightState.LANDING);
            }
            return;
        }
        if (job.state() != ConstructionJob.STATE_BUILDING) return;
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return;
        if (drone.assignedJobId().filter(job.jobId()::equals).isEmpty() || drone.taskOpId() < 0) {
            if (!tryClaim(drone, level, job, progress)) return;
        }
        ConstructionBuildOp op = progress.operation(drone.taskOpId());
        if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.status() == ConstructionBuildOp.Status.DELIVERED) {
            drone.clearAssignment(false);
            return;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, owner.get());
        if (player == null) {
            drone.setWaitReason(ConstructionWaitReason.SOURCE);
            drone.setNoGravity(false);
            drone.setFlightState(DroneFlightState.LANDING);
            return;
        }
        if (drone.hostedCarry().isEmpty()) {
            flyToPickup(drone, player, progress, op, job);
        } else {
            flyToDeliver(drone, level, progress, op);
        }
    }

    public static boolean tryClaim(
        DroneEntity drone,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ConstructionBuildOp op = ConstructionJobController.nextAssignable(level, progress);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        Vec3 target = Vec3.atBottomCenterOf(approach);
        if (drone.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
        if (player == null) return false;
        Vec3 pickup = player.position().add(0.0D, 1.0D, 0.0D);
        if (drone.position().distanceTo(pickup) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        double distance = drone.position().distanceTo(pickup) + pickup.distanceTo(target);
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

    private static void flyToPickup(
        DroneEntity drone,
        ServerPlayer player,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        ConstructionJob job
    ) {
        drone.setActionState((byte) 1);
        if (drone.distanceTo(player) > ConstructionJobController.REACH + 0.5D) {
            flyTo(drone, player.position().add(0.0D, 1.0D, 0.0D));
            return;
        }
        if (!ConstructionJobController.extractMaterial(player, progress, op, drone.getUUID())) {
            ConstructionJobController.applyShortage(
                player.server,
                job,
                progress,
                drone.shortageStrategy(),
                op.material()
            );
            drone.setWaitReason(ConstructionWaitReason.MATERIAL);
            if (drone.shortageStrategy() == DroneShortageStrategy.PAUSE) {
                drone.clearAssignment(false);
                drone.setNoGravity(false);
                drone.setFlightState(DroneFlightState.LANDING);
            } else {
                drone.clearAssignment(false);
            }
            return;
        }
        drone.setHostedCarry(op.material().copyWithCount(1));
        drone.setActionState((byte) 4);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    private static void flyToDeliver(
        DroneEntity drone,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        drone.setActionState((byte) 4);
        BlockPos approach = op.approach().orElseGet(
            () -> ConstructionJobController.chooseApproach(level, progress, op)
        );
        if (approach == null) return;
        Vec3 target = Vec3.atBottomCenterOf(approach);
        AABB droneBox = drone.getBoundingBox();
        AABB block = new AABB(op.pos());
        if (droneBox.intersects(block) || !droneBox.intersects(block.inflate(ConstructionJobController.REACH))) {
            flyTo(drone, target);
            return;
        }
        drone.setActionState((byte) 5);
        if (ConstructionJobController.tryDeliver(level, progress, op, drone)) {
            drone.consumeEnergy(drone.toolDefinition().instantActionEnergyCost());
            drone.setHostedCarry(ItemStack.EMPTY);
            drone.clearAssignment(false);
            drone.setActionState((byte) 0);
            drone.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
            if (job == null || job.state() != ConstructionJob.STATE_BUILDING
                || !tryClaim(drone, level, job, progress)) {
                drone.setNoGravity(false);
                drone.setFlightState(DroneFlightState.LANDING);
            }
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED) {
            drone.setWaitReason(ConstructionWaitReason.OCCUPIED);
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_WORLD) {
            drone.setWaitReason(ConstructionWaitReason.WORLD);
        }
    }

    private static void flyTo(DroneEntity drone, Vec3 goal) {
        drone.setNoGravity(true);
        if (drone.flightState() != DroneFlightState.FLYING
            || !drone.navigator().hasPath()
            || !drone.navigator().endsNear(goal)) {
            drone.navigator().setPath(DroneFlightPlanner.plan(drone, goal));
        }
        drone.setFlightState(DroneFlightState.FLYING);
    }
}
