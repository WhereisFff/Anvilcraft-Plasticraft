package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverageService;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * 空手与加强工具共用的调度器:建设、拆除、收集按能力叠加,
 * 没有可认领工作时让绑定悦灵回库，其余悦灵把飞行交还给原版大脑。
 */
public final class GeneralAllayToolBehavior implements AllayToolBehavior {
    public static final GeneralAllayToolBehavior INSTANCE = new GeneralAllayToolBehavior();

    /** 结队跟随时与观察悦灵保持的水平间距,够近以留在同一份九柱内,又不至于挤在同一格。 */
    private static final double FOLLOW_RADIUS = 3.0D;

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
        if (ConstructionTransferService.isIdleTransitWorker(level, worker)) {
            // 借调/返程悦灵在世界上只沿转运链走,不领取任务也不乱游荡
            ConstructionTransferService.routeWorldWorkerHome(level, worker);
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
        if (definition.hasCapability(AllayCapability.DELIVER_PROJECTION)
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
        if (job != null && job.dimension().equals(level.dimension()) && followObserver(worker, level, job)) {
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

    /**
     * 观察窗口还没完全进入实体刻时结队跟随观察悦灵,窗口一建立就地开工,不必回库再出库。
     * 只跟观察者实体本身:它永远在自己那份九柱里,跟随者因此不会越过前沿飞进未加载区块。
     */
    private static boolean followObserver(WorkingAllayEntity worker, ServerLevel level, ConstructionJob job) {
        if (!worker.assignedJobId().isEmpty()
            || worker.isEvacuating()
            || worker.hasCollectionItems()) {
            return false;
        }
        WorkingAllayEntity observer = ObservationCoverageService.convoyObserver(level, job, worker);
        if (observer == null || observer == worker) return false;
        worker.setActionState((byte) 0);
        // 每只跟随者按自身 UUID 取一个固定角度散开,避免整队挤在观察者同一格里互相挤压
        double angle = (worker.getUUID().getLeastSignificantBits() & 0xFFL) / 256.0D * Math.PI * 2.0D;
        Vec3 goal = observer.position().add(Math.cos(angle) * FOLLOW_RADIUS, 1.0D, Math.sin(angle) * FOLLOW_RADIUS);
        if (AllayWorkMotions.arrived(worker, goal)) {
            AllayWorkMotions.holdStation(worker);
            ConstructionTraffic.release(level, worker.getUUID());
            return true;
        }
        AllayWorkMotions.flyTo(worker, goal);
        return true;
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
        // 客工在协调室已无活可做时沿链返程,不挤占协调室的通道与托管位
        if (ConstructionTransferService.sendGuestHome(level, worker)) return true;
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
            || job.state() == ConstructionJob.STATE_WAITING_OBSERVER
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
        if (definition.hasCapability(AllayCapability.DELIVER_PROJECTION)
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
        if (definition.hasCapability(AllayCapability.DELIVER_PROJECTION)
            && job.state() == ConstructionJob.STATE_BUILDING
            && ConstructionAllayToolBehavior.tryClaim(worker, level, job, progress)) {
            ConstructionAllayToolBehavior.INSTANCE.serverTick(worker);
            return true;
        }
        return false;
    }
}
