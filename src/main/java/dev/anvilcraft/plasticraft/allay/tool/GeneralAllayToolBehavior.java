package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * 空手与加强工具共用的调度器:建设、拆除、收集按能力叠加,
 * 没有可认领工作时让绑定悦灵回库，其余悦灵把飞行交还给原版大脑。
 */
public final class GeneralAllayToolBehavior implements AllayToolBehavior {
    public static final GeneralAllayToolBehavior INSTANCE = new GeneralAllayToolBehavior();

    private GeneralAllayToolBehavior() {
    }

    @Override
    public void serverTick(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        if (worker.flightState() == AllayFlightState.DOCKING) return;
        Optional<UUID> owner = worker.getOwner();
        if (owner.isEmpty()) {
            worker.clearAssignment(false);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        AllayToolDefinition definition = worker.toolDefinition();
        ConstructionJob job = ConstructionJobController.jobForWorker(level, worker);
        ConstructionJobProgress assignedProgress = job == null
            ? null
            : ConstructionJobStore.get(level).get(job.jobId());
        if (assignedProgress != null
            && worker.assignedJobId().isPresent()
            && !ConstructionJobController.canContinueJob(worker, assignedProgress)) {
            if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
                && CollectionAllayToolBehavior.isCollectionAssignment(worker, job)) {
                ConstructionJobController.revokeCollectionWorker(worker, level, job, assignedProgress);
            } else {
                ConstructionJobController.revokeWorker(worker, level, assignedProgress);
            }
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (!worker.hostedCarry().isEmpty() && !definition.hasCapability(AllayCapability.PICK_UP_MATERIAL)) {
            worker.clearAssignment(false);
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (definition.hasCapability(AllayCapability.PICK_UP_MATERIAL)
            && ConstructionAllayToolBehavior.shouldHandle(worker, job)) {
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && worker.hasCollectionItems()) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (worker.assignedJobId().isPresent() && job != null && job.dimension().equals(level.dimension())) {
            if (routeAssigned(worker, level, job, definition)) {
                return;
            }
        }
        if (worker.assignedJobId().isPresent()) {
            worker.clearAssignment(false);
        }
        if (job != null && job.dimension().equals(level.dimension()) && tryClaimWork(worker, level, job, definition)) {
            return;
        }
        if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.hasFreeWork(worker, level)) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (dockWhenNoWorkCanBeClaimed(worker, level)) {
            return;
        }
        if (isCollectOnly(definition)
            && job != null
            && job.dimension().equals(level.dimension())) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (job != null && job.dimension().equals(level.dimension()) && isLiveJob(job)) {
            return;
        }
        AllayWorkMotions.releaseToVanilla(worker);
    }

    private static boolean dockWhenNoWorkCanBeClaimed(WorkingAllayEntity worker, ServerLevel level) {
        if (worker.homeLoungePos() == null
            || !worker.assignedJobId().isEmpty()
            || !worker.hostedCarry().isEmpty()
            || worker.hasCollectionItems()
            || worker.isEvacuating()) {
            return false;
        }
        worker.setActionState((byte) 0);
        AllayWorkMotions.releaseToVanilla(worker);
        ConstructionTraffic.release(level, worker.getUUID());
        worker.resetStuck();
        BlockPos home = worker.homeLoungePos();
        if (home != null && worker.startDockingTo(home)) return true;
        worker.setHomeLounge(null);
        return false;
    }

    private static boolean isCollectOnly(AllayToolDefinition definition) {
        return definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && !definition.hasCapability(AllayCapability.PICK_UP_MATERIAL);
    }

    private static boolean isLiveJob(ConstructionJob job) {
        return job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_SEALING_FLUID
            || job.state() == ConstructionJob.STATE_DEMOLISHING
            || job.state() == ConstructionJob.STATE_COLLECTING_DEBRIS
            || job.state() == ConstructionJob.STATE_COMMITTING
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE;
    }

    private static boolean routeAssigned(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        AllayToolDefinition definition
    ) {
        if (definition.hasCapability(AllayCapability.DEMOLISH)
            && job.state() == ConstructionJob.STATE_DEMOLISHING) {
            DemolitionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.isCollectionAssignment(worker, job)) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        if (definition.hasCapability(AllayCapability.PICK_UP_MATERIAL)
            && (job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_SEALING_FLUID)) {
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        return false;
    }

    private static boolean tryClaimWork(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        AllayToolDefinition definition
    ) {
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return false;
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        if (definition.hasCapability(AllayCapability.DEMOLISH)
            && job.state() == ConstructionJob.STATE_DEMOLISHING
            && DemolitionAllayToolBehavior.tryClaim(worker, level, job, progress)) {
            DemolitionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.canClaimTask(job)
            && CollectionAllayToolBehavior.tryClaim(worker, level, job, progress)) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        if (definition.hasCapability(AllayCapability.PICK_UP_MATERIAL)
            && job.state() == ConstructionJob.STATE_SEALING_FLUID
            && ConstructionAllayToolBehavior.tryClaimSeal(worker, level, job, progress)) {
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        if (definition.hasCapability(AllayCapability.PICK_UP_MATERIAL)
            && job.state() == ConstructionJob.STATE_BUILDING
            && ConstructionAllayToolBehavior.tryClaim(worker, level, job, progress)) {
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        return false;
    }
}
