package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayHardHatTraits;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** 拆除动作:飞到工具触及处按切石机砸击语义瞬间拆除,不吸取掉落物。 */
public final class DemolitionAllayToolBehavior implements AllayToolBehavior {
    public static final DemolitionAllayToolBehavior INSTANCE = new DemolitionAllayToolBehavior();

    private DemolitionAllayToolBehavior() {
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
        ConstructionJob job = ConstructionJobController.jobForWorker(level, worker);
        if (job == null || !job.dimension().equals(level.dimension())) {
            if (worker.assignedJobId().isPresent()) {
                worker.clearAssignment(false);
                worker.setHomeLounge(null);
            }
            if (worker.homeLoungePos() != null) {
                restAtHome(worker, level);
                return;
            }
            continueOrIdle(worker);
            return;
        }
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress != null
            && worker.assignedJobId().isPresent()
            && !ConstructionJobController.canContinueJob(worker, progress)) {
            ConstructionJobController.revokeWorker(worker, level, progress);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
            worker.setWaitReason(ConstructionWaitReason.SOURCE);
            if (worker.flightState() == AllayFlightState.FLYING) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_OBSERVER
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
            if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
                worker.setWaitReason(ConstructionWaitReason.DEMOLITION);
            } else if (job.state() == ConstructionJob.STATE_WAITING_OBSERVER) {
                worker.setWaitReason(ConstructionWaitReason.OBSERVER);
            } else if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
                worker.setWaitReason(ConstructionWaitReason.PERMISSION);
            } else {
                worker.setWaitReason(ConstructionWaitReason.MATERIAL);
            }
            if (job.state() != ConstructionJob.STATE_WAITING_PERMISSION && worker.homeLoungePos() != null) {
                restAtHome(worker, level);
                return;
            }
            if (worker.flightState() == AllayFlightState.FLYING) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (job.state() != ConstructionJob.STATE_DEMOLISHING) {
            if (job.state() == ConstructionJob.STATE_COMMITTING || job.state() == ConstructionJob.STATE_BUILDING) {
                finishThenRest(worker, level, job);
            }
            return;
        }
        if (progress == null) return;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty() || worker.taskOpId() < 0) {
            if (!tryClaim(worker, level, job, progress)) {
                if (progress.allDemolishResolved()) {
                    finishThenRest(worker, level, job);
                }
                return;
            }
        }
        ConstructionBuildOp op = progress.operation(worker.taskOpId());
        if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.leaseAllay().filter(worker.getUUID()::equals).isEmpty()) {
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            if (progress.allDemolishResolved()) {
                finishThenRest(worker, level, job);
            }
            return;
        }
        flyToSmash(worker, level, progress, op);
    }

    public static boolean tryClaim(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        ConstructionBuildOp op = ConstructionJobController.nextAssignableDemolish(level, progress, worker);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (!ConstructionJobController.isWithinLoungeRange(progress, op.pos())) return false;
        if (!progress.hasCoordinator()
            && worker.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(job.jobId(), op.id());
        ConstructionTraffic.reserveApproach(level, worker.getUUID(), approach);
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    private static void flyToSmash(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        worker.noteProgress();
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach, worker)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op, worker);
            if (approach != null) {
                op.setApproach(approach);
                ConstructionTraffic.reserveApproach(level, worker.getUUID(), approach);
            }
        }
        if (approach == null || worker.isMotionStuck()) {
            releaseLease(worker, level, op);
            return;
        }
        Vec3 target = ConstructionWorkerSpace.navigationPoint(approach);
        AABB box = worker.getBoundingBox();
        AABB block = new AABB(op.pos());
        if (box.intersects(block) || !box.intersects(block.inflate(ConstructionJobController.reach(worker)))) {
            AllayWorkMotions.flyTo(worker, target, AllayPathPriority.PICKUP);
            return;
        }
        worker.setActionState((byte) 1);
        if (!worker.prepareAction(Vec3.atCenterOf(op.pos()))) {
            return;
        }
        BlockMiningEffect miningEffect = AllayHardHatTraits.miningEffectOf(worker.getHardHat());
        if (ConstructionJobController.tryDemolish(level, progress, op, miningEffect)) {
            worker.resetStuck();
            worker.clearAssignment(false);
            ConstructionTraffic.release(level, worker.getUUID());
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob current = ConstructionJobIndex.get(level).job(progress.jobId());
            if (current != null && current.state() == ConstructionJob.STATE_DEMOLISHING
                && tryClaim(worker, level, current, progress)) {
                return;
            }
            if (current != null && progress.allDemolishResolved()) {
                finishThenRest(worker, level, current);
            }
        }
    }

    private static void releaseLease(WorkingAllayEntity worker, ServerLevel level, ConstructionBuildOp op) {
        op.setStatus(ConstructionBuildOp.Status.PENDING);
        op.setLeaseAllay(null);
        op.setApproach(null);
        worker.clearAssignment(false);
        worker.resetStuck();
        ConstructionTraffic.release(level, worker.getUUID());
        ConstructionJobStore.get(level).markDirty();
    }

    private static void finishThenRest(WorkingAllayEntity worker, ServerLevel level, ConstructionJob job) {
        if (worker.homeLoungePos() != null) {
            restAtHome(worker, level);
            return;
        }
        leaveSiteThenIdle(worker, job);
    }

    private static void restAtHome(WorkingAllayEntity worker, ServerLevel level) {
        BlockPos home = worker.homeLoungePos();
        if (home == null) {
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        worker.clearAssignment(false);
        // 客工不入栈协调室,改为沿转运链返回原休息室
        if (ConstructionTransferService.sendGuestHome(level, worker)) return;
        if (!worker.startDockingTo(home)) {
            worker.setHomeLounge(null);
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }

    private static void leaveSiteThenIdle(WorkingAllayEntity worker, ConstructionJob job) {
        AABB site = ConstructionJobController.worldBox(job);
        if (!site.inflate(2.0D).intersects(worker.getBoundingBox())) {
            worker.clearAssignment(false);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        AllayWorkMotions.flyTo(worker, worker.position().add(2.0D, 1.0D, 0.0D), AllayPathPriority.LEAVE);
    }

    private static void continueOrIdle(WorkingAllayEntity worker) {
        if (worker.navigator().hasPath()) {
            worker.setFlightState(AllayFlightState.FLYING);
            return;
        }
        worker.clearAssignment(false);
        AllayWorkMotions.releaseToVanilla(worker);
    }
}
