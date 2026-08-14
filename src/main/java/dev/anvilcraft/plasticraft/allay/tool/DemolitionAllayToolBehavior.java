package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
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
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
        if (job == null || !job.dimension().equals(level.dimension())) {
            continueOrIdle(worker);
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
            if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
                worker.setWaitReason(ConstructionWaitReason.DEMOLITION);
            } else if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
                worker.setWaitReason(ConstructionWaitReason.PERMISSION);
            } else if (job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
                worker.setWaitReason(ConstructionWaitReason.SOURCE);
            }
            if (worker.flightState() == AllayFlightState.FLYING) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (job.state() != ConstructionJob.STATE_DEMOLISHING) {
            if (job.state() == ConstructionJob.STATE_COMMITTING || job.state() == ConstructionJob.STATE_BUILDING) {
                leaveSiteThenIdle(worker, job);
            }
            return;
        }
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty() || worker.taskOpId() < 0) {
            if (!tryClaim(worker, level, job, progress)) {
                if (progress.allDemolishResolved()) {
                    leaveSiteThenIdle(worker, job);
                }
                return;
            }
        }
        ConstructionBuildOp op = progress.operation(worker.taskOpId());
        if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.status() == ConstructionBuildOp.Status.DELIVERED) {
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            if (progress.allDemolishResolved()) {
                leaveSiteThenIdle(worker, job);
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
        ConstructionBuildOp op = ConstructionJobController.nextAssignableDemolish(level, progress);
        if (op == null) return false;
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (worker.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
            return false;
        }
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(job.jobId(), op.id());
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    private static void flyToSmash(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op);
            if (approach != null) {
                op.setApproach(approach);
            }
        }
        if (approach == null) {
            AllayWorkMotions.flyTo(worker, Vec3.atBottomCenterOf(op.pos().above(2)));
            return;
        }
        Vec3 target = Vec3.atBottomCenterOf(approach);
        AABB box = worker.getBoundingBox();
        AABB block = new AABB(op.pos());
        if (box.intersects(block) || !box.intersects(block.inflate(ConstructionJobController.reach(worker)))) {
            AllayWorkMotions.flyTo(worker, target);
            return;
        }
        worker.setActionState((byte) 1);
        if (!worker.prepareAction(Vec3.atCenterOf(op.pos()))) {
            return;
        }
        if (ConstructionJobController.tryDemolish(level, progress, op)) {
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob current = ConstructionJobIndex.get(level).job(progress.jobId());
            if (current != null && current.state() == ConstructionJob.STATE_DEMOLISHING
                && tryClaim(worker, level, current, progress)) {
                return;
            }
            if (current != null && progress.allDemolishResolved()) {
                leaveSiteThenIdle(worker, current);
            }
        }
    }

    private static void leaveSiteThenIdle(WorkingAllayEntity worker, ConstructionJob job) {
        AABB site = ConstructionJobController.worldBox(job);
        if (!site.inflate(2.0D).intersects(worker.getBoundingBox())) {
            worker.clearAssignment(false);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        AllayWorkMotions.flyTo(worker, worker.position().add(2.0D, 1.0D, 0.0D));
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
