package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/** 建设动作:从所有者背包取料,飞到工具触及处交付施工投影。 */
public final class ConstructionAllayToolBehavior implements AllayToolBehavior {
    public static final ConstructionAllayToolBehavior INSTANCE = new ConstructionAllayToolBehavior();

    private ConstructionAllayToolBehavior() {
    }

    public static boolean shouldHandle(WorkingAllayEntity worker, @Nullable ConstructionJob job) {
        if (!worker.hostedCarry().isEmpty()) {
            return isActiveBuildCarry(worker, job) || worker.assignedJobId().isPresent();
        }
        if (worker.assignedJobId().isEmpty() || job == null) return false;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty()) return false;
        return job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_SEALING_FLUID
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION
            || job.state() == ConstructionJob.STATE_COMMITTING;
    }

    @Override
    public void serverTick(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        if (worker.flightState() == AllayFlightState.DOCKING) return;
        Optional<UUID> owner = worker.getOwner();
        if (owner.isEmpty()) {
            worker.clearAssignment(false);
            return;
        }
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
        if (!worker.hostedCarry().isEmpty() && !isActiveBuildCarry(worker, job)) {
            returnCarryToOwner(worker, level, owner.get());
            return;
        }
        if (job == null || !job.dimension().equals(level.dimension())) {
            continueOrIdle(worker);
            return;
        }
        if (job.state() == ConstructionJob.STATE_COMMITTING) {
            leaveSiteThenIdle(worker, level, job);
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
            if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
                worker.setWaitReason(ConstructionWaitReason.MATERIAL);
            } else if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
                worker.setWaitReason(ConstructionWaitReason.DEMOLITION);
            } else if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
                worker.setWaitReason(ConstructionWaitReason.PERMISSION);
            } else {
                worker.setWaitReason(ConstructionWaitReason.SOURCE);
            }
            if (worker.flightState() == AllayFlightState.FLYING && worker.hostedCarry().isEmpty()) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (job.state() != ConstructionJob.STATE_BUILDING && job.state() != ConstructionJob.STATE_SEALING_FLUID) {
            return;
        }
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return;
        boolean sealing = job.state() == ConstructionJob.STATE_SEALING_FLUID;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty() || worker.taskOpId() < 0) {
            boolean claimed = sealing
                ? tryClaimSeal(worker, level, job, progress)
                : tryClaim(worker, level, job, progress);
            if (!claimed) {
                if (!sealing && progress.allPlaceResolved()) {
                    leaveSiteThenIdle(worker, level, job);
                }
                return;
            }
        }
        ConstructionBuildOp op = progress.operation(worker.taskOpId());
        if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.status() == ConstructionBuildOp.Status.DELIVERED) {
            worker.clearAssignment(false);
            if (!sealing && progress.allPlaceResolved()) {
                leaveSiteThenIdle(worker, level, job);
            }
            return;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, owner.get());
        if (player == null) {
            worker.setWaitReason(ConstructionWaitReason.SOURCE);
            AllayWorkMotions.holdStation(worker);
            return;
        }
        if (worker.hostedCarry().isEmpty()) {
            flyToPickup(worker, player, progress, op, job);
        } else if (sealing) {
            flyToSeal(worker, level, progress, op);
        } else {
            flyToDeliver(worker, level, progress, op);
        }
    }

    public static boolean tryClaim(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ConstructionBuildOp op = ConstructionJobController.nextAssignable(level, progress);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (worker.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
        if (player == null) return false;
        Vec3 pickup = player.position().add(0.0D, 1.0D, 0.0D);
        if (worker.position().distanceTo(pickup) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(job.jobId(), op.id());
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    public static boolean tryClaimSeal(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ConstructionBuildOp op = ConstructionJobController.nextAssignableSeal(level, progress);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (worker.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
        if (player == null) return false;
        Vec3 pickup = player.position().add(0.0D, 1.0D, 0.0D);
        if (worker.position().distanceTo(pickup) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(job.jobId(), op.id());
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    public static boolean isActiveBuildCarry(WorkingAllayEntity worker, @Nullable ConstructionJob job) {
        return job != null
            && (job.state() == ConstructionJob.STATE_BUILDING || job.state() == ConstructionJob.STATE_SEALING_FLUID)
            && worker.assignedJobId().filter(job.jobId()::equals).isPresent();
    }

    private static void flyToPickup(
        WorkingAllayEntity worker,
        ServerPlayer player,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        ConstructionJob job
    ) {
        worker.setActionState((byte) 1);
        if (worker.distanceTo(player) > ConstructionJobController.reach(worker) + 0.5D) {
            AllayWorkMotions.flyTo(worker, player.position().add(0.0D, 1.0D, 0.0D));
            return;
        }
        if (!worker.prepareAction(player.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        if (!ConstructionJobController.extractMaterial(player, progress, op, worker.getUUID())) {
            ConstructionJobController.applyShortage(
                player.server,
                job,
                progress,
                worker.shortageStrategy(),
                op.material()
            );
            worker.setWaitReason(ConstructionWaitReason.MATERIAL);
            worker.clearAssignment(false);
            if (worker.shortageStrategy() == AllayShortageStrategy.PAUSE) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        worker.setHostedCarry(op.material().copy());
        worker.setActionState((byte) 4);
        worker.setWaitReason(ConstructionWaitReason.NONE);
    }

    private static void returnCarryToOwner(WorkingAllayEntity worker, ServerLevel level, UUID ownerId) {
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, ownerId);
        if (player == null) {
            worker.setWaitReason(ConstructionWaitReason.SOURCE);
            AllayWorkMotions.holdStation(worker);
            return;
        }
        worker.setActionState((byte) 1);
        if (worker.distanceTo(player) > ConstructionJobController.reach(worker) + 0.5D) {
            AllayWorkMotions.flyTo(worker, player.position().add(0.0D, 1.0D, 0.0D));
            return;
        }
        if (!worker.prepareAction(player.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        ConstructionJobController.depositHostedCarry(worker, player);
        AllayWorkMotions.releaseToVanilla(worker);
    }

    private static void leaveSiteThenIdle(WorkingAllayEntity worker, ServerLevel level, ConstructionJob job) {
        AABB site = ConstructionJobController.worldBox(job);
        ServerPlayer owner = worker.getOwner()
            .map(id -> ConstructionJobController.findOwner(level.getServer(), level, id))
            .orElse(null);
        Vec3 goal = evacuateGoal(worker, site, owner);
        if (goal == null || arrivedOutside(worker, site, goal)) {
            worker.clearAssignment(false);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        AllayWorkMotions.flyTo(worker, goal);
    }

    private static void continueOrIdle(WorkingAllayEntity worker) {
        if (worker.navigator().hasPath()) {
            worker.setFlightState(AllayFlightState.FLYING);
            return;
        }
        worker.clearAssignment(false);
        AllayWorkMotions.releaseToVanilla(worker);
    }

    @Nullable
    private static Vec3 evacuateGoal(WorkingAllayEntity worker, AABB site, @Nullable ServerPlayer owner) {
        if (owner != null && !site.intersects(owner.getBoundingBox())) {
            return owner.position().add(0.0D, 1.0D, 0.0D);
        }
        if (!site.inflate(2.0D).intersects(worker.getBoundingBox())) {
            return null;
        }
        return pushOutside(worker, site);
    }

    private static boolean arrivedOutside(WorkingAllayEntity worker, AABB site, Vec3 goal) {
        return AllayWorkMotions.arrived(worker, goal) && !site.intersects(worker.getBoundingBox());
    }

    private static Vec3 pushOutside(WorkingAllayEntity worker, AABB site) {
        double cx = (site.minX + site.maxX) * 0.5D;
        double cz = (site.minZ + site.maxZ) * 0.5D;
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int distance = 2; distance <= 4; distance++) {
            for (int[] side : sides) {
                double x = side[0] == 0 ? cx : (side[0] > 0 ? site.maxX : site.minX) + side[0] * distance;
                double z = side[1] == 0 ? cz : (side[1] > 0 ? site.maxZ : site.minZ) + side[1] * distance;
                Vec3 candidate = new Vec3(x, Math.max(worker.getY(), site.maxY), z);
                if (!worker.level().noCollision(worker, worker.getBoundingBox().move(candidate.subtract(worker.position())))) {
                    continue;
                }
                double dist = worker.position().distanceToSqr(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
        }
        return best != null ? best : worker.position().add(2.0D, 1.0D, 0.0D);
    }

    private static void flyToSeal(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        worker.setActionState((byte) 4);
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op);
            if (approach != null) {
                op.setApproach(approach);
            }
        }
        double reach = ConstructionJobController.reach(worker);
        if (approach == null) {
            AllayWorkMotions.flyTo(worker, Vec3.atBottomCenterOf(op.pos().above(2)));
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(approach);
        AABB box = worker.getBoundingBox();
        AABB block = new AABB(op.pos());
        if (box.intersects(block) || !box.intersects(block.inflate(reach))) {
            AllayWorkMotions.flyTo(worker, target);
            return;
        }
        worker.setActionState((byte) 5);
        if (!worker.prepareAction(Vec3.atCenterOf(op.pos()))) {
            return;
        }
        if (ConstructionJobController.tryPlaceSeal(level, progress, op, worker)) {
            worker.setHostedCarry(ItemStack.EMPTY);
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
            if (job != null && job.state() == ConstructionJob.STATE_SEALING_FLUID
                && tryClaimSeal(worker, level, job, progress)) {
                return;
            }
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED) {
            worker.setWaitReason(ConstructionWaitReason.OCCUPIED);
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_WORLD) {
            worker.setWaitReason(ConstructionWaitReason.WORLD);
        }
    }

    private static void flyToDeliver(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        worker.setActionState((byte) 4);
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op);
            if (approach != null) {
                op.setApproach(approach);
            }
        }
        AABB box = worker.getBoundingBox();
        AABB block = new AABB(op.pos());
        double reach = ConstructionJobController.reach(worker);
        boolean inReach = box.intersects(block.inflate(reach));
        boolean inside = box.intersects(block);
        if (approach == null) {
            if (op.writesProjection() || !inReach) {
                AllayWorkMotions.flyTo(worker, op.writesProjection()
                    ? Vec3.atBottomCenterOf(op.pos().above(2))
                    : Vec3.atBottomCenterOf(op.pos()));
                return;
            }
        } else if (!inReach || (op.writesProjection() && inside)) {
            AllayWorkMotions.flyTo(worker, Vec3.atBottomCenterOf(approach));
            return;
        }
        worker.setActionState((byte) 5);
        if (!worker.prepareAction(Vec3.atCenterOf(op.pos()))) {
            return;
        }
        if (ConstructionJobController.tryDeliver(level, progress, op, worker)) {
            ItemStack returned = op.returnStack().copy();
            worker.setHostedCarry(returned);
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            if (!returned.isEmpty()) {
                return;
            }
            ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
            if (job != null && job.state() == ConstructionJob.STATE_BUILDING
                && tryClaim(worker, level, job, progress)) {
                return;
            }
            if (job != null && (progress.allPlaceResolved() || job.state() == ConstructionJob.STATE_COMMITTING)) {
                leaveSiteThenIdle(worker, level, job);
            } else if (job == null) {
                continueOrIdle(worker);
            }
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED) {
            worker.setWaitReason(ConstructionWaitReason.OCCUPIED);
        } else if (op.status() == ConstructionBuildOp.Status.WAITING_WORLD) {
            worker.setWaitReason(ConstructionWaitReason.WORLD);
        }
    }
}
