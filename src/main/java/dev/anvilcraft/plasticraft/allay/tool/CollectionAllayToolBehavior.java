package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.path.AllayPathPriority;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import dev.anvilcraft.plasticraft.blueprint.ConstructionMaterialAccess;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 收集动作:空手走近捡 1 个;磁铁按手持磁铁半径吸入并装入九格库存。
 */
public final class CollectionAllayToolBehavior implements AllayToolBehavior {
    public static final CollectionAllayToolBehavior INSTANCE = new CollectionAllayToolBehavior();
    public static final double FREE_RANGE = 16.0D;
    private static final int TASK_RETRY_DELAY_TICKS = 100;
    private static final Map<ServerLevel, Map<UUID, Long>> TASK_RETRY_AFTER = new WeakHashMap<>();

    private CollectionAllayToolBehavior() {
    }

    public static boolean isVacuum(WorkingAllayEntity worker) {
        return isVacuumTool(worker.getMainHandItem());
    }

    public static boolean isVacuumTool(ItemStack held) {
        return AllayToolDefinitions.fromHeldItem(held).inventorySize() > 0;
    }

    public static boolean hasCollectionCapacity(AllayWorkRecord record) {
        int inventorySize = AllayToolDefinitions.fromHeldItem(record.heldTool()).inventorySize();
        if (inventorySize <= 0) return record.hostedCarry().isEmpty();
        if (record.collectionInventory().size() < inventorySize) return true;
        for (int index = 0; index < inventorySize; index++) {
            ItemStack stack = record.collectionInventory().get(index);
            if (stack.isEmpty() || stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    public static boolean canAttemptTask(WorkingAllayEntity worker, ServerLevel level) {
        return canAttemptTask(worker.getUUID(), level);
    }

    public static boolean canAttemptTask(AllayWorkRecord record, ServerLevel level) {
        return canAttemptTask(record.entityId(), level);
    }

    public static boolean canClaimTask(ConstructionJob job) {
        return ConstructionJobController.isTaskCollectPhase(job);
    }

    public static boolean isCollectionAssignment(WorkingAllayEntity worker, ConstructionJob job) {
        return canClaimTask(job) && worker.assignedJobId().filter(job.jobId()::equals).isPresent();
    }

    public static boolean hasFreeWork(WorkingAllayEntity worker, ServerLevel level) {
        if (!canAttemptTask(worker, level)) return false;
        if (worker.isCollectionFull()) return worker.hasCollectionItems();
        return nextFreeTarget(worker, level) != null;
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
        boolean sameSite = job != null && job.dimension().equals(level.dimension());
        ConstructionJobProgress assignedProgress = job == null
            ? null
            : ConstructionJobStore.get(level).get(job.jobId());
        if (assignedProgress != null
            && worker.assignedJobId().isPresent()
            && !ConstructionJobController.canContinueJob(worker, assignedProgress)) {
            ConstructionJobController.revokeCollectionWorker(worker, level, job, assignedProgress);
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (job == null && worker.assignedJobId().isPresent()) {
            worker.clearAssignment(false);
            worker.dropCollectionAt(worker.position());
            AllayWorkMotions.releaseToVanilla(worker);
            return;
        }
        if (worker.isCollectionFull()) {
            worker.setActionState((byte) 0);
            if (sameSite && onSite(worker, job)) {
                leaveSiteThenIdle(worker, level, job);
            } else {
                releaseIfFlying(worker);
            }
            tryUnload(worker, level, owner.get());
            return;
        }
        if (sameSite && ConstructionJobController.isTaskCollectPhase(job)) {
            tickTask(worker, level, job, owner.get());
            return;
        }
        if (sameSite && onSite(worker, job) && shouldEvacuateFinishedJob(job)) {
            leaveSiteThenIdle(worker, level, job);
            tryUnload(worker, level, owner.get());
            return;
        }
        tickFree(worker, level, owner.get());
    }

    public static boolean tryClaim(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!ConstructionJobController.canClaimJob(worker, progress)) return false;
        if (!ConstructionJobController.canContinueJob(worker, progress)) return false;
        if (!canAttemptTask(worker, level)) return false;
        if (worker.isCollectionFull()) return false;
        if (!isVacuum(worker) && hasAvailableVacuum(level, job, progress)) return false;
        ItemEntity current = leasedItem(level, progress, worker.getUUID());
        if (current != null) return true;
        ItemEntity target = ConstructionJobController.nextAssignableDebris(
            level,
            job,
            progress,
            worker.position()
        );
        if (target == null || !worker.canAcceptCollection(target.getItem())) return false;
        if (!progress.leaseDebris(target.getUUID(), worker.getUUID())) return false;
        ConstructionDebris mark = ConstructionDebris.get(target.getItem());
        worker.assign(job.jobId(), mark == null ? -1 : mark.operationId());
        worker.resetStuck();
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    @Nullable
    public static ItemEntity nextFreeTarget(WorkingAllayEntity worker, ServerLevel level) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = collectRange(worker);
        UUID ownerId = worker.getOwner().orElse(null);
        if (ownerId == null) return null;
        AABB box = worker.getBoundingBox().inflate(range);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.isAlive() || entity.hasPickUpDelay() || entity.getItem().isEmpty()) continue;
            if (!ConstructionPermission.canModify(level, BlockPos.containing(entity.position()), ownerId)) continue;
            double distance = worker.distanceTo(entity);
            if (distance <= range && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static double collectRange(WorkingAllayEntity worker) {
        return isVacuum(worker) ? FREE_RANGE : Math.max(FREE_RANGE, ConstructionJobController.reach(worker));
    }

    private static void tickTask(WorkingAllayEntity worker, ServerLevel level, ConstructionJob job, UUID ownerId) {
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(job.jobId());
        if (progress == null) {
            tickFree(worker, level, ownerId);
            return;
        }
        ItemEntity target = leasedItem(level, progress, worker.getUUID());
        if (target == null) {
            progress.releaseDebrisLeasesOf(worker.getUUID());
            worker.clearAssignment(false);
            if (!tryClaim(worker, level, job, progress)) {
                worker.setActionState((byte) 0);
                if (job.state() == ConstructionJob.STATE_COLLECTING_DEBRIS || progress.allDemolishResolved()) {
                    leaveSiteThenIdle(worker, level, job);
                }
                tryUnload(worker, level, ownerId);
                return;
            }
            target = leasedItem(level, progress, worker.getUUID());
            if (target == null) return;
        }
        pursue(worker, level, target, progress, job, ownerId);
    }

    private static void tickFree(WorkingAllayEntity worker, ServerLevel level, UUID ownerId) {
        if (worker.assignedJobId().isPresent()) {
            worker.clearAssignment(false);
        }
        if (!canAttemptTask(worker, level)) {
            worker.setActionState((byte) 0);
            if (worker.hasCollectionItems()) {
                tryUnload(worker, level, ownerId);
            } else {
                restOrIdle(worker);
            }
            return;
        }
        if (isVacuum(worker)) {
            ItemEntity aim = nextFreeTarget(worker, level);
            if (aim != null && worker.prepareAction(aim.position())) {
                inhaleNearby(worker, level, null);
            }
            if (worker.hasCollectionItems()) {
                tryUnload(worker, level, ownerId);
            }
            if (worker.isCollectionFull()) {
                releaseIfFlying(worker);
                return;
            }
            if (nextFreeTarget(worker, level) == null) {
                worker.setActionState((byte) 0);
                if (worker.hasCollectionItems()) {
                    tryUnload(worker, level, ownerId);
                } else {
                    restOrIdle(worker);
                }
            }
            return;
        }
        ItemEntity target = nextFreeTarget(worker, level);
        if (target == null) {
            worker.setActionState((byte) 0);
            if (worker.hasCollectionItems()) {
                tryUnload(worker, level, ownerId);
                return;
            }
            restOrIdle(worker);
            return;
        }
        pursue(worker, level, target, null, null, ownerId);
    }

    private static void pursue(
        WorkingAllayEntity worker,
        ServerLevel level,
        ItemEntity target,
        @Nullable ConstructionJobProgress progress,
        @Nullable ConstructionJob job,
        UUID ownerId
    ) {
        if (!target.isAlive() || target.getItem().isEmpty()) {
            if (progress != null) {
                progress.releaseDebrisLease(target.getUUID());
                ConstructionJobStore.get(level).markDirty();
            }
            worker.setActionState((byte) 0);
            worker.clearAssignment(false);
            return;
        }
        UUID workerOwner = worker.getOwner().orElse(null);
        UUID permissionOwner = job == null ? workerOwner : job.owner();
        if (permissionOwner == null
            || !ConstructionPermission.canModify(level, BlockPos.containing(target.position()), permissionOwner)) {
            if (progress != null) {
                ConstructionJob taskJob = ConstructionJobIndex.get(level).job(progress.jobId());
                if (taskJob != null) {
                    ConstructionJobController.revokeCollectionWorker(worker, level, taskJob, progress);
                } else {
                    progress.releaseDebrisLease(target.getUUID());
                    worker.clearAssignment(false);
                }
            } else {
                worker.clearAssignment(false);
            }
            worker.setActionState((byte) 0);
            return;
        }
        if (isVacuum(worker)) {
            if (worker.distanceTo(target) > collectRange(worker)) {
                worker.setActionState((byte) 0);
                AllayWorkMotions.flyTo(worker, target.position(), AllayPathPriority.PICKUP);
                releaseUnreachableTarget(worker, level, target, progress);
                return;
            }
            worker.setActionState((byte) 1);
            if (!worker.prepareAction(target.position())) {
                return;
            }
            inhaleNearby(worker, level, progress);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            if (progress == null) {
                worker.clearAssignment(false);
            }
            worker.resetStuck();
            if (progress != null) {
                ConstructionJobStore.get(level).markDirty();
            }
            if (worker.isCollectionFull()) {
                if (job != null && onSite(worker, job)) {
                    leaveSiteThenIdle(worker, level, job);
                } else {
                    releaseIfFlying(worker);
                }
                tryUnload(worker, level, ownerId);
            }
            return;
        }
        if (worker.distanceTo(target) > ConstructionJobController.reach(worker)) {
            worker.setActionState((byte) 0);
            AllayWorkMotions.flyTo(worker, target.position());
            releaseUnreachableTarget(worker, level, target, progress);
            return;
        }
        worker.setActionState((byte) 1);
        if (!worker.prepareAction(target.position())) {
            return;
        }
        if (!ConstructionJobController.tryCollect(worker, target, progress)) {
            worker.setActionState((byte) 0);
            return;
        }
        worker.setActionState((byte) 0);
        worker.setWaitReason(ConstructionWaitReason.NONE);
        if (progress == null) {
            worker.clearAssignment(false);
        }
        worker.resetStuck();
        if (progress != null) {
            ConstructionJobStore.get(level).markDirty();
        }
        if (worker.isCollectionFull()) {
            if (job != null && onSite(worker, job)) {
                leaveSiteThenIdle(worker, level, job);
            } else {
                releaseIfFlying(worker);
            }
            tryUnload(worker, level, ownerId);
        }
    }

    private static void releaseUnreachableTarget(
        WorkingAllayEntity worker,
        ServerLevel level,
        ItemEntity target,
        @Nullable ConstructionJobProgress progress
    ) {
        worker.noteProgress();
        if (!worker.isMotionStuck() || worker.hasPendingFlightTask()) return;
        if (progress != null) {
            progress.releaseDebrisLease(target.getUUID());
            ConstructionJobStore.get(level).markDirty();
        }
        TASK_RETRY_AFTER.computeIfAbsent(level, ignored -> new HashMap<>())
            .put(worker.getUUID(), level.getGameTime() + TASK_RETRY_DELAY_TICKS);
        worker.setActionState((byte) 0);
        worker.clearAssignment(false);
        worker.resetStuck();
        AllayWorkMotions.releaseToVanilla(worker);
    }

    private static boolean canAttemptTask(UUID workerId, ServerLevel level) {
        Map<UUID, Long> retryTimes = TASK_RETRY_AFTER.get(level);
        if (retryTimes == null) return true;
        Long retryAfter = retryTimes.get(workerId);
        if (retryAfter == null) return true;
        if (retryAfter > level.getGameTime()) return false;
        retryTimes.remove(workerId);
        if (retryTimes.isEmpty()) TASK_RETRY_AFTER.remove(level);
        return true;
    }

    private static void inhaleNearby(
        WorkingAllayEntity worker,
        ServerLevel level,
        @Nullable ConstructionJobProgress progress
    ) {
        AABB box = worker.getBoundingBox().inflate(FREE_RANGE);
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box);
        for (ItemEntity entity : items) {
            if (!entity.isAlive() || entity.hasPickUpDelay() || entity.getItem().isEmpty()) continue;
            if (worker.distanceTo(entity) > FREE_RANGE) continue;
            if (progress != null) {
                ConstructionDebris mark = ConstructionDebris.get(entity.getItem());
                if (mark == null || !mark.jobId().equals(progress.jobId())) continue;
            } else {
                UUID ownerId = worker.getOwner().orElse(null);
                if (ownerId == null
                    || !ConstructionPermission.canModify(level, BlockPos.containing(entity.position()), ownerId)) {
                    continue;
                }
            }
            if (!worker.canAcceptCollection(entity.getItem())) continue;
            ConstructionJobController.tryCollect(worker, entity, progress);
            if (worker.isCollectionFull()) return;
        }
    }

    @Nullable
    private static ItemEntity leasedItem(ServerLevel level, ConstructionJobProgress progress, UUID workerId) {
        UUID entityId = progress.leasedDebrisEntity(workerId);
        if (entityId == null) return null;
        Entity entity = level.getEntity(entityId);
        if (entity instanceof ItemEntity item && item.isAlive() && ConstructionDebris.isMarked(item.getItem())) {
            return item;
        }
        progress.releaseDebrisLease(entityId);
        return null;
    }

    private static boolean shouldEvacuateFinishedJob(ConstructionJob job) {
        return job.state() == ConstructionJob.STATE_BUILDING
            || job.state() == ConstructionJob.STATE_COMMITTING;
    }

    private static boolean onSite(WorkingAllayEntity worker, ConstructionJob job) {
        return ConstructionJobController.worldBox(job).inflate(2.0D).intersects(worker.getBoundingBox());
    }

    private static void leaveSiteThenIdle(WorkingAllayEntity worker, ServerLevel level, ConstructionJob job) {
        if (worker.homeLoungePos() != null) {
            if (worker.hasCollectionItems()) {
                tryUnload(worker, level, job.owner());
                return;
            }
            worker.clearAssignment(false);
            worker.startDockingTo(worker.homeLoungePos());
            return;
        }
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

    private static void tryUnload(WorkingAllayEntity worker, ServerLevel level, UUID ownerId) {
        BlockPos home = worker.homeLoungePos();
        if (home != null) {
            AllayLoungeBlockEntity lounge = level.getBlockEntity(home) instanceof AllayLoungeBlockEntity found
                ? found
                : null;
            if (lounge == null) {
                worker.setHomeLounge(null);
                home = null;
            } else if (!lounge.canHost(worker)
                || lounge.owner() == null
                || !ConstructionPermission.canModify(level, home, lounge.owner())
                || !ConstructionPermission.canModify(level, home.below(), lounge.owner())) {
                ConstructionJob job = ConstructionJobController.jobForWorker(level, worker);
                ConstructionJobProgress progress = job == null
                    ? null
                    : ConstructionJobStore.get(level).get(job.jobId());
                if (worker.assignedJobId().isPresent()) {
                    ConstructionJobController.revokeCollectionWorker(worker, level, job, progress);
                } else {
                    worker.dropCollectionAt(worker.position());
                    worker.setHomeLounge(null);
                }
                return;
            }
            Vec3 target = lounge.dockApproachPoint();
            double range = isVacuum(worker) ? FREE_RANGE : ConstructionJobController.reach(worker) + 0.5D;
            if (worker.position().distanceTo(target) > range) {
                AllayWorkMotions.flyTo(worker, target);
                return;
            }
            if (!worker.prepareAction(target)) {
                return;
            }
            ConstructionJob taskJob = ConstructionJobController.jobForWorker(level, worker);
            ConstructionJobProgress taskProgress = taskJob == null
                ? null
                : ConstructionJobStore.get(level).get(taskJob.jobId());
            if (worker.assignedJobId().isPresent()
                && (taskJob == null
                    || taskProgress == null
                    || !ConstructionJobController.canContinueJob(worker, taskProgress))) {
                ConstructionJobController.revokeCollectionWorker(worker, level, taskJob, taskProgress);
                return;
            }
            if (lounge.owner() == null
                || !ConstructionPermission.canModify(level, home, lounge.owner())
                || !ConstructionPermission.canModify(level, home.below(), lounge.owner())
                || !lounge.canHost(worker)) {
                if (worker.assignedJobId().isPresent()) {
                    ConstructionJobController.revokeCollectionWorker(worker, level, taskJob, taskProgress);
                } else {
                    worker.dropCollectionAt(worker.position());
                    worker.setHomeLounge(null);
                }
                return;
            }
            worker.unloadCollectionTo(ConstructionMaterialAccess.below(level, home));
            if (!worker.hasCollectionItems()) {
                worker.startDockingTo(home);
            }
            return;
        }
        ConstructionJob taskJob = ConstructionJobController.jobForWorker(level, worker);
        if (worker.assignedJobId().isPresent()) {
            ConstructionJobProgress progress = taskJob == null
                ? null
                : ConstructionJobStore.get(level).get(taskJob.jobId());
            if (taskJob == null
                || progress == null
                || !ConstructionJobController.canContinueJob(worker, progress)) {
                ConstructionJobController.revokeCollectionWorker(worker, level, taskJob, progress);
                return;
            }
        }
        ServerPlayer owner = ConstructionPermission.findOnlineCollaborator(level, ownerId);
        if (owner == null) return;
        double range = isVacuum(worker) ? FREE_RANGE : ConstructionJobController.reach(worker) + 0.5D;
        if (worker.distanceTo(owner) > range) {
            AllayWorkMotions.flyTo(worker, owner.position().add(0.0D, 1.0D, 0.0D));
            return;
        }
        if (!worker.prepareAction(owner.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        if (!ConstructionPermission.areCollaborators(level.getServer(), ownerId, owner.getUUID())) {
            return;
        }
        worker.unloadCollectionTo(owner);
        if (!worker.hasCollectionItems()) {
            worker.clearAssignment(false);
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }

    private static void restOrIdle(WorkingAllayEntity worker) {
        BlockPos home = worker.homeLoungePos();
        if (home != null && isVacuum(worker)) {
            worker.startDockingTo(home);
            return;
        }
        AllayWorkMotions.releaseToVanilla(worker);
    }

    public static boolean hasAvailableVacuum(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        for (WorkingAllayEntity other : ConstructionJobController.loadedWorkers(level, job.owner())) {
            if (!ConstructionJobController.isWorkerAvailableForJob(level, other, job, progress)) continue;
            if (isVacuum(other) && !other.isCollectionFull() && canAttemptTask(other, level)) {
                return true;
            }
        }
        if (progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            for (AllayWorkRecord record : lounge.hosted()) {
                if (record.assignedJobId().filter(assigned -> !assigned.equals(job.jobId())).isEmpty()
                    && record.owner()
                    .map(owner -> ConstructionPermission.areCollaborators(level.getServer(), owner, job.owner()))
                    .orElse(false)
                    && isVacuumTool(record.heldTool())
                    && hasCollectionCapacity(record)
                    && canAttemptTask(record, level)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void releaseIfFlying(WorkingAllayEntity worker) {
        if (worker.flightState() == AllayFlightState.FLYING) {
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }
}
