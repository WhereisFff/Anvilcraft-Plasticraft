package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;
import java.util.UUID;

/**
 * 空手与加强工具共用的调度器:建设、拆除、收集按能力叠加,
 * 没有任务时把飞行交还给原版悦灵大脑。
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
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
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
        if (job != null && job.dimension().equals(level.dimension()) && tryClaimWork(worker, level, job, definition)) {
            return;
        }
        if (definition.hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.hasFreeWork(worker, level)) {
            CollectionAllayToolBehavior.INSTANCE.serverTick(worker);
            return;
        }
        if (job != null && job.dimension().equals(level.dimension()) && isLiveJob(job)) {
            return;
        }
        AllayWorkMotions.releaseToVanilla(worker);
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
