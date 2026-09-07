package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPlacementLimits;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWorkerSpace;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 建设动作:无室从所有者背包取料,认领后从休息室下方容器取料,飞到工具触及处交付施工投影。 */
public final class ConstructionAllayToolBehavior implements AllayToolBehavior {
    public static final ConstructionAllayToolBehavior INSTANCE = new ConstructionAllayToolBehavior();
    /** 同一目标累计确认不可达的次数上限;每次都要求悦灵完整卡住一轮,阈值内还回租约让别人先试。 */
    private static final int UNREACHABLE_STRIKES = 4;

    private ConstructionAllayToolBehavior() {
    }

    public static boolean shouldHandle(WorkingAllayEntity worker, @Nullable ConstructionJob job) {
        // 只有台账里的施工余料才必须走建设调度;自由收集物交给收集调度入库
        if (worker.isEvacuating()
            || worker.hasEscrowCarry()
            || ConstructionJobController.hasEscrowLedger(worker)) {
            return true;
        }
        if (worker.assignedJobId().isEmpty() || job == null) return false;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty()) return false;
        return job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_SEALING_FLUID
            || job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_OBSERVER
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
        boolean hasEscrowLedger = ConstructionJobController.hasEscrowLedger(worker);
        ConstructionJob job = ConstructionJobController.jobForWorker(level, worker);
        ConstructionJobProgress progress = job == null
            ? null
            : ConstructionJobStore.get(level).get(job.jobId());
        ConstructionBuildOp assignedOperation = progress == null
            ? null
            : progress.operation(worker.taskOpId());
        boolean validAssignment = progress != null
            && worker.assignedJobId().filter(job.jobId()::equals).isPresent()
            && assignedOperation != null
            && assignedOperation.leaseAllay().filter(worker.getUUID()::equals).isPresent();
        if (progress != null && hasEscrowLedger && !validAssignment) {
            // 实体字段可能在重载时先于任务租约恢复;先补回协调室绑定,再按台账认领原操作。
            ConstructionJobController.restoreWorkerBinding(worker, progress);
            assignedOperation = progress.operation(worker.taskOpId());
            validAssignment = worker.assignedJobId().filter(job.jobId()::equals).isPresent()
                && assignedOperation != null
                && assignedOperation.leaseAllay().filter(worker.getUUID()::equals).isPresent();
        }
        if (progress != null
            && hasEscrowLedger
            && !validAssignment
            && worker.hostedCarry().isEmpty()
            && !progress.carriedEntries(worker.getUUID()).isEmpty()) {
            // 没有实体租约且手上没有材料时,不能把 CARRIED 台账当作尚未取出的预约重新生成。
            ConstructionJobController.revokeWorker(worker, level, progress);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (progress != null
            && (worker.assignedJobId().isPresent() || worker.hasEscrowCarry() || hasEscrowLedger)
            && !ConstructionJobController.canContinueJob(worker, progress)) {
            ConstructionJobController.revokeWorker(worker, level, progress);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (job == null && (worker.assignedJobId().isPresent() || worker.hasEscrowCarry() || hasEscrowLedger)) {
            worker.clearAssignment(false);
            // 任务已消失,台账无从核销,只能就地实体化;丢出的物品带拾取延迟,不会被自己立刻捡回
            worker.dropCollectionAt(worker.position());
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (job != null && job.dimension().equals(level.dimension())
            && job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
            worker.setWaitReason(ConstructionWaitReason.SOURCE);
            if (worker.flightState() == AllayFlightState.FLYING) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (!worker.hostedCarry().isEmpty()
            && !isActiveBuildCarry(worker, job)
            && !worker.isEvacuating()) {
            returnLeftoverCarry(worker, level, owner.get(), job);
            return;
        }
        if (job == null || !job.dimension().equals(level.dimension())) {
            worker.endEvacuation();
            ConstructionTraffic.release(level, worker.getUUID());
            if (!worker.hostedCarry().isEmpty()) {
                returnLeftoverCarry(worker, level, owner.get(), job);
                return;
            }
            restOrIdle(worker, level);
            return;
        }
        if (job.state() == ConstructionJob.STATE_COMMITTING) {
            finishThenRest(worker, level, job);
            return;
        }
        progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) return;
        if (worker.isEvacuating()) {
            Vec3 target = worker.evacuationTarget();
            if (target == null || AllayWorkMotions.arrived(worker, target)) {
                worker.endEvacuation();
                ConstructionTraffic.release(level, worker.getUUID());
                if (job.state() != ConstructionJob.STATE_BUILDING
                    && job.state() != ConstructionJob.STATE_SEALING_FLUID) {
                    finishThenRest(worker, level, job);
                    return;
                }
                if (!worker.hostedCarry().isEmpty() && !isActiveBuildCarry(worker, job)) {
                    returnLeftoverCarry(worker, level, owner.get(), job);
                    return;
                }
            } else {
                AllayWorkMotions.flyTo(worker, target, AllayPathPriority.ESCAPE);
                return;
            }
        }
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION
            || job.state() == ConstructionJob.STATE_WAITING_OBSERVER
            || job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
            if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
                worker.setWaitReason(ConstructionWaitReason.MATERIAL);
            } else if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
                worker.setWaitReason(ConstructionWaitReason.DEMOLITION);
            } else if (job.state() == ConstructionJob.STATE_WAITING_OBSERVER) {
                worker.setWaitReason(ConstructionWaitReason.OBSERVER);
            } else {
                worker.setWaitReason(ConstructionWaitReason.PERMISSION);
            }
            if (job.state() != ConstructionJob.STATE_WAITING_PERMISSION && worker.homeLoungePos() != null) {
                finishThenRest(worker, level, job);
                return;
            }
            if (worker.flightState() == AllayFlightState.FLYING && worker.hostedCarry().isEmpty()) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        if (job.state() != ConstructionJob.STATE_BUILDING && job.state() != ConstructionJob.STATE_SEALING_FLUID) {
            return;
        }
        boolean sealing = job.state() == ConstructionJob.STATE_SEALING_FLUID;
        if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty() || worker.taskOpId() < 0) {
            // 自动恢复只认实体中确实存在的在途材料;公开认领入口仍可先登记尚未装载的预留
            boolean claimed = hasEscrowLedger
                ? ConstructionJobController.hasPhysicalCarriedMaterial(level.getServer(), progress, worker)
                    && tryClaimCarried(worker, level, job, progress)
                : sealing
                ? tryClaimSeal(worker, level, job, progress)
                : tryClaim(worker, level, job, progress);
            if (!claimed) {
                if (hasEscrowLedger && !ConstructionJobController.hasPhysicalCarriedMaterial(
                    level.getServer(), progress, worker
                )) {
                    // 台账数量大于实体实物时,只结清现存物并把缺失操作退回待供料,禁止凭空装载。
                    ConstructionJobController.revokeWorker(worker, level, progress);
                    AllayWorkMotions.releaseToVanilla(worker);
                    return;
                }
                if (phaseResolved(progress, sealing)) {
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
            if (phaseResolved(progress, sealing)) {
                finishThenRest(worker, level, job);
            }
            return;
        }
        if (!progress.hasCoordinator()) {
            ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
            if (player == null) {
                worker.setWaitReason(ConstructionWaitReason.SOURCE);
                AllayWorkMotions.holdStation(worker);
                return;
            }
        }
        if (worker.hostedCarry().isEmpty() && op.needsMaterial()) {
            flyToPickup(worker, level, progress, op, job);
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
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        return claimOp(
            worker,
            level,
            job,
            progress,
            ConstructionJobController.nextAssignable(level, progress, worker)
        );
    }

    public static boolean tryClaimSeal(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        return claimOp(
            worker,
            level,
            job,
            progress,
            ConstructionJobController.nextAssignableSeal(level, progress, worker)
        );
    }

    public static boolean tryClaimCarried(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        if (!worker.hostedCarry().isEmpty()
            && !ConstructionJobController.hasPhysicalCarriedMaterial(level.getServer(), progress, worker)) {
            return false;
        }
        return claimOp(
            worker,
            level,
            job,
            progress,
            ConstructionJobController.nextAssignableCarried(level, progress, worker)
        );
    }

    private static boolean claimOp(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        @Nullable ConstructionBuildOp op
    ) {
        if (op == null) return false;
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        if ((op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.ENTITY)
            && !ConstructionPlacementLimits.canDeliver(worker, op)) {
            return false;
        }
        BlockPos approach = op.approach().orElse(null);
        if (approach == null) return false;
        if (!ConstructionJobController.isWithinLoungeRange(progress, op.pos())) return false;
        if (!progress.hasCoordinator()) {
            if (worker.position().distanceTo(Vec3.atCenterOf(op.pos())) > ConstructionJobController.DISCOVERY_RANGE) {
                return false;
            }
            ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
            if (player == null) return false;
            Vec3 pickup = player.position().add(0.0D, 1.0D, 0.0D);
            if (worker.position().distanceTo(pickup) > ConstructionJobController.DISCOVERY_RANGE) {
                return false;
            }
        } else if (!ConstructionJobController.isSourceAvailable(level.getServer(), level, job, progress)) {
            return false;
        }
        ItemStack reserved = progress.carriedBy(worker.getUUID(), op.id());
        if (progress.hasCarriedMaterial(op.id()) && reserved == null) return false;
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(job.jobId(), op.id());
        ConstructionTraffic.reserveApproach(level, worker.getUUID(), approach);
        if (progress.hasCoordinator() && op.needsMaterial() && reserved == null) {
            if (!ConstructionJobController.extractMaterialFromLounge(level, progress, op, worker.getUUID())) {
                releaseLease(worker);
                ConstructionJobController.applyShortage(
                    level.getServer(),
                    job,
                    progress,
                    worker.shortageStrategy(),
                    op
                );
                return false;
            }
        }
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    private static void releaseLease(WorkingAllayEntity worker) {
        worker.clearAssignment(false);
        worker.resetStuck();
        ConstructionTraffic.release(worker.level(), worker.getUUID());
        ConstructionJobStore.get(worker.level()).markDirty();
    }

    /**
     * 交付途中确认飞不到时先还回租约让别的悦灵换个方向试,
     * 连续 {@link #UNREACHABLE_STRIKES} 次都不行才把这个位置压一段退避。
     * 只释放租约是不够的:托管材料仍记在台账上,下一 tick 同一只悦灵会立刻重领同一操作,
     * 于是"领取 - 规划失败 - 释放"无限循环,悦灵原地不动、任务永远停在施工阶段。
     * 退避只是暂时轮空,位置仍然是待办,不会因为飞不到就当作建完。
     */
    private static void abandonUnreachable(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        int strikes = op.noteUnreachable();
        if (strikes >= UNREACHABLE_STRIKES) {
            ConstructionJobController.deferUnreachable(level, progress, op, worker);
        }
        releaseLease(worker);
    }

    public static boolean isActiveBuildCarry(WorkingAllayEntity worker, @Nullable ConstructionJob job) {
        return job != null
            && (job.state() == ConstructionJob.STATE_BUILDING || job.state() == ConstructionJob.STATE_SEALING_FLUID)
            && worker.assignedJobId().filter(job.jobId()::equals).isPresent();
    }

    private static void flyToPickup(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        ConstructionJob job
    ) {
        worker.setActionState((byte) 1);
        worker.noteProgress();
        if (worker.isMotionStuck() && !worker.hasPendingFlightTask()) {
            releaseLease(worker);
            return;
        }
        if (progress.hasCoordinator()) {
            BlockPos loungePos = progress.coordinatorLounge();
            if (loungePos == null || !approachLounge(worker, level, loungePos)) {
                return;
            }
            ItemStack reserved = progress.carriedBy(worker.getUUID(), op.id());
            if (reserved == null || reserved.isEmpty()) {
                ConstructionJobController.applyShortage(
                    level.getServer(),
                    job,
                    progress,
                    worker.shortageStrategy(),
                    op
                );
                worker.setWaitReason(ConstructionWaitReason.MATERIAL);
                worker.clearAssignment(false);
                return;
            }
            worker.setHostedCarry(loadBatch(worker, level, progress, op, reserved, null));
            if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
                lounge.clearPickupDisplay(worker.getUUID());
            }
            worker.setActionState((byte) 4);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            return;
        }
        ServerPlayer player = ConstructionJobController.findOwner(level.getServer(), level, job.owner());
        if (player == null) {
            worker.setWaitReason(ConstructionWaitReason.SOURCE);
            AllayWorkMotions.holdStation(worker);
            return;
        }
        if (worker.distanceTo(player) > ConstructionJobController.reach(worker) + 0.5D) {
            AllayWorkMotions.flyTo(worker, player.position().add(0.0D, 1.0D, 0.0D), AllayPathPriority.PICKUP);
            return;
        }
        if (!worker.prepareAction(player.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        ItemStack taken = progress.carriedBy(worker.getUUID(), op.id());
        if (taken == null && !ConstructionJobController.extractMaterial(player, progress, op, worker.getUUID())) {
            ConstructionJobController.applyShortage(
                player.server,
                job,
                progress,
                worker.shortageStrategy(),
                op
            );
            worker.setWaitReason(ConstructionWaitReason.MATERIAL);
            worker.clearAssignment(false);
            if (worker.shortageStrategy() == AllayShortageStrategy.PAUSE) {
                AllayWorkMotions.holdStation(worker);
            }
            return;
        }
        taken = progress.carriedBy(worker.getUUID(), op.id());
        if (taken == null || taken.isEmpty()) {
            worker.clearAssignment(false);
            return;
        }
        worker.setHostedCarry(loadBatch(worker, level, progress, op, taken, player));
        worker.setActionState((byte) 4);
        worker.setWaitReason(ConstructionWaitReason.NONE);
    }

    private static ItemStack loadBatch(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp seed,
        ItemStack first,
        @Nullable ServerPlayer player
    ) {
        ItemStack carry = first.copy();
        int capacity = carry.getMaxStackSize() - carry.getCount();
        for (ConstructionBuildOp candidate : ConstructionJobController.batchMaterialCandidates(
            level,
            progress,
            worker,
            seed,
            capacity
        )) {
            boolean extracted = player == null
                ? ConstructionJobController.extractMaterialFromLounge(
                    level,
                    progress,
                    candidate,
                    worker.getUUID()
                )
                : ConstructionJobController.extractMaterial(player, progress, candidate, worker.getUUID());
            if (!extracted) break;
            ItemStack reserved = progress.carriedBy(worker.getUUID(), candidate.id());
            if (reserved == null || !ItemStack.isSameItemSameComponents(carry, reserved)) break;
            carry.grow(reserved.getCount());
            capacity -= reserved.getCount();
            if (capacity <= 0) break;
        }
        return carry;
    }

    private static void returnLeftoverCarry(
        WorkingAllayEntity worker,
        ServerLevel level,
        UUID ownerId,
        @Nullable ConstructionJob job
    ) {
        if (worker.homeLoungePos() != null
            && (!(level.getBlockEntity(worker.homeLoungePos()) instanceof AllayLoungeBlockEntity lounge)
                || !lounge.canHost(worker))) {
            worker.setHomeLounge(null);
        }
        if (worker.homeLoungePos() == null) {
            returnCarryToOwner(worker, level, ownerId);
            return;
        }
        if (job != null
            && (job.state() == ConstructionJob.STATE_BUILDING || job.state() == ConstructionJob.STATE_SEALING_FLUID)) {
            if (!depositAtLounge(worker, level)) return;
            ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
            if (progress == null) {
                restAtHome(worker, level);
                return;
            }
            boolean claimed = job.state() == ConstructionJob.STATE_SEALING_FLUID
                ? tryClaimSeal(worker, level, job, progress)
                : tryClaim(worker, level, job, progress);
            if (!claimed && (progress.allPlaceResolved() || progress.allSealResolved())) {
                restAtHome(worker, level);
            }
            return;
        }
        finishThenRest(worker, level, job);
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
            AllayWorkMotions.flyTo(worker, player.position().add(0.0D, 1.0D, 0.0D), AllayPathPriority.LEAVE);
            return;
        }
        if (!worker.prepareAction(player.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        ConstructionJobController.depositHostedCarry(worker, player);
        if (worker.hostedCarry().isEmpty()) {
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }

    private static void finishThenRest(WorkingAllayEntity worker, ServerLevel level, @Nullable ConstructionJob job) {
        worker.endEvacuation();
        ConstructionTraffic.release(level, worker.getUUID());
        if (worker.homeLoungePos() != null) {
            if (!worker.hostedCarry().isEmpty() && !depositAtLounge(worker, level)) {
                return;
            }
            restAtHome(worker, level);
            return;
        }
        if (job != null) {
            leaveSiteThenIdle(worker, level, job);
            return;
        }
        continueOrIdle(worker);
    }

    private static boolean phaseResolved(ConstructionJobProgress progress, boolean sealing) {
        return sealing ? progress.allSealResolved() : progress.allPlaceResolved();
    }

    private static void restOrIdle(WorkingAllayEntity worker, ServerLevel level) {
        if (worker.homeLoungePos() != null) {
            if (!worker.hostedCarry().isEmpty() && !depositAtLounge(worker, level)) {
                return;
            }
            restAtHome(worker, level);
            return;
        }
        continueOrIdle(worker);
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

    private static boolean depositAtLounge(WorkingAllayEntity worker, ServerLevel level) {
        BlockPos home = worker.homeLoungePos();
        if (home == null) return true;
        if (!(level.getBlockEntity(home) instanceof AllayLoungeBlockEntity lounge) || !lounge.canHost(worker)) {
            worker.setHomeLounge(null);
            return false;
        }
        if (!approachLounge(worker, level, home)) return false;
        ConstructionJobController.depositHostedCarryToLounge(worker, level, home);
        return worker.hostedCarry().isEmpty();
    }

    private static boolean approachLounge(WorkingAllayEntity worker, ServerLevel level, BlockPos loungePos) {
        Vec3 target = loungeApproach(level, loungePos);
        if (worker.position().distanceTo(target) > ConstructionJobController.reach(worker) + 0.5D) {
            AllayWorkMotions.flyTo(worker, target, AllayPathPriority.PICKUP);
            return false;
        }
        return worker.prepareAction(target);
    }

    private static Vec3 loungeApproach(ServerLevel level, BlockPos loungePos) {
        if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            return lounge.dockApproachPoint();
        }
        return Vec3.atCenterOf(loungePos).add(0.0D, 1.05D, 0.0D);
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
        AllayWorkMotions.flyTo(worker, goal, AllayPathPriority.LEAVE);
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
        worker.noteProgress();
        BlockPos approach = refreshApproach(worker, level, progress, op);
        if (approach == null && !isBlockedWait(op)) {
            // 连一个可用接近位都挑不出来,同样算一次"飞不到";只释放租约会让这一格反复空领
            abandonUnreachable(worker, level, progress, op);
            return;
        }
        double reach = ConstructionJobController.reach(worker);
        boolean sealInReach = boxInReach(worker, op, reach);
        if (sealInReach) {
            op.clearUnreachable();
        }
        if (worker.isMotionStuck() && !worker.hasPendingFlightTask()) {
            // 已经够得到或只是在等世界清空,都不是"飞不过去",不能按不可达升级
            if (sealInReach || isBlockedWait(op)) {
                releaseLease(worker);
            } else {
                abandonUnreachable(worker, level, progress, op);
            }
            return;
        }
        if (isBlockedWait(op)) {
            holdBlocked(worker, op);
            if (!sealInReach) {
                return;
            }
        } else {
            Vec3 target = ConstructionWorkerSpace.navigationPoint(approach);
            AABB box = worker.getBoundingBox();
            AABB block = new AABB(op.pos());
            if (box.intersects(block) || !box.intersects(block.inflate(reach))) {
                AllayWorkMotions.flyTo(worker, target, AllayPathPriority.DELIVER);
                return;
            }
        }
        worker.setActionState((byte) 5);
        if (!worker.prepareAction(Vec3.atCenterOf(op.pos()))) {
            return;
        }
        if (ConstructionJobController.tryPlaceSeal(level, progress, op, worker)) {
            worker.resetStuck();
            worker.setHostedCarry(ItemStack.EMPTY);
            worker.clearAssignment(false);
            ConstructionTraffic.release(level, worker.getUUID());
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            if (maybeEvacuate(worker, level, op, approach)) return;
            ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
            if (job != null && job.state() == ConstructionJob.STATE_SEALING_FLUID
                && tryClaimSeal(worker, level, job, progress)) {
                return;
            }
            if (job != null && progress.allSealResolved()) {
                finishThenRest(worker, level, job);
            }
        } else if (isBlockedWait(op)) {
            holdBlocked(worker, op);
        }
    }

    private static void flyToDeliver(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        worker.setActionState((byte) 4);
        worker.noteProgress();
        ItemStack supplied = op.needsMaterial()
            ? progress.carriedBy(worker.getUUID(), op.id())
            : ItemStack.EMPTY;
        if (op.needsMaterial() && !canConsume(worker.hostedCarry(), supplied)) {
            releaseLease(worker);
            return;
        }
        BlockPos approach = refreshApproach(worker, level, progress, op);
        if (approach == null && !isBlockedWait(op)) {
            // 连一个可用接近位都挑不出来,同样算一次"飞不到";只释放租约会让这一格反复空领
            abandonUnreachable(worker, level, progress, op);
            return;
        }
        AABB box = worker.getBoundingBox();
        double reach = ConstructionJobController.reach(worker);
        ConstructionJobController.DeliveryInteraction interaction =
            ConstructionJobController.deliveryInteraction(progress, op, box, reach);
        boolean inReach = interaction.inReach();
        boolean inside = interaction.inside();
        if (inReach) {
            op.clearUnreachable();
        }
        if (worker.isMotionStuck() && !worker.hasPendingFlightTask()) {
            // 已经够得到或只是在等世界清空,都不是"飞不过去",不能按不可达升级
            if (inReach || isBlockedWait(op)) {
                releaseLease(worker);
            } else {
                abandonUnreachable(worker, level, progress, op);
            }
            return;
        }
        if (isBlockedWait(op)) {
            holdBlocked(worker, op);
            if (!inReach) {
                return;
            }
        } else if (!inReach || (op.writesProjection() && inside)) {
            AllayWorkMotions.flyTo(worker, ConstructionWorkerSpace.navigationPoint(approach), AllayPathPriority.DELIVER);
            return;
        }
        worker.setActionState((byte) 5);
        if (!worker.prepareAction(Vec3.atCenterOf(interaction.target()))) {
            return;
        }
        if (ConstructionJobController.tryDeliver(level, progress, op, worker)) {
            worker.resetStuck();
            ItemStack remaining = worker.hostedCarry().copy();
            if (!supplied.isEmpty()) remaining.shrink(supplied.getCount());
            ItemStack returned = op.returnStack().copy();
            worker.setHostedCarry(returned.isEmpty() ? remaining : returned);
            worker.clearAssignment(false);
            ConstructionTraffic.release(level, worker.getUUID());
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
            boolean claimedCarried = returned.isEmpty()
                && !remaining.isEmpty()
                && job != null
                && job.state() == ConstructionJob.STATE_BUILDING
                && tryClaimCarried(worker, level, job, progress);
            if (maybeEvacuate(worker, level, op, approach)) return;
            if (claimedCarried || !worker.hostedCarry().isEmpty()) return;
            if (job != null && job.state() == ConstructionJob.STATE_BUILDING
                && tryClaim(worker, level, job, progress)) {
                return;
            }
            if (job != null && (progress.allPlaceResolved() || job.state() == ConstructionJob.STATE_COMMITTING)) {
                finishThenRest(worker, level, job);
            } else if (job == null) {
                restOrIdle(worker, level);
            }
        } else if (isBlockedWait(op)) {
            holdBlocked(worker, op);
        }
    }

    private static boolean canConsume(ItemStack carry, @Nullable ItemStack supplied) {
        return supplied != null
            && !supplied.isEmpty()
            && ItemStack.isSameItemSameComponents(carry, supplied)
            && carry.getCount() >= supplied.getCount();
    }

    @Nullable
    private static BlockPos refreshApproach(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        BlockPos approach = op.approach().orElse(null);
        if (!ConstructionJobController.isUsableApproach(level, progress, op, approach, worker)) {
            approach = ConstructionJobController.chooseApproach(level, progress, op, worker);
            if (approach != null) {
                op.setApproach(approach);
                ConstructionTraffic.reserveApproach(level, worker.getUUID(), approach);
            }
        }
        return approach;
    }

    private static boolean maybeEvacuate(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionBuildOp op,
        @Nullable BlockPos approach
    ) {
        if (approach == null || !worker.getBoundingBox().intersects(new AABB(op.pos()))) {
            return false;
        }
        Vec3 target = ConstructionWorkerSpace.navigationPoint(approach);
        ConstructionTraffic.reserveEvacuation(
            level,
            worker.getUUID(),
            List.of(BlockPos.containing(worker.position()), approach)
        );
        worker.beginEvacuation(target);
        AllayWorkMotions.flyTo(worker, target, AllayPathPriority.ESCAPE);
        return true;
    }

    private static boolean isBlockedWait(ConstructionBuildOp op) {
        return op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED
            || op.status() == ConstructionBuildOp.Status.WAITING_WORLD;
    }

    private static boolean boxInReach(WorkingAllayEntity worker, ConstructionBuildOp op, double reach) {
        return worker.getBoundingBox().intersects(new AABB(op.pos()).inflate(reach));
    }

    private static void holdBlocked(WorkingAllayEntity worker, ConstructionBuildOp op) {
        if (op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED) {
            worker.setWaitReason(ConstructionWaitReason.OCCUPIED);
        } else {
            worker.setWaitReason(ConstructionWaitReason.WORLD);
        }
        AllayWorkMotions.holdStation(worker);
    }
}
