package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkMotions;
import dev.anvilcraft.plasticraft.blueprint.ConstructionDebris;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionWaitReason;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 收集动作:空手走近捡 1 个;磁铁按手持磁铁半径吸入并装入九格库存。
 */
public final class CollectionAllayToolBehavior implements AllayToolBehavior {
    public static final CollectionAllayToolBehavior INSTANCE = new CollectionAllayToolBehavior();
    public static final double FREE_RANGE = 16.0D;

    private CollectionAllayToolBehavior() {
    }

    public static boolean isVacuum(WorkingAllayEntity worker) {
        return worker.toolDefinition().inventorySize() > 0;
    }

    public static boolean canClaimTask(ConstructionJob job) {
        return ConstructionJobController.isTaskCollectPhase(job);
    }

    public static boolean isCollectionAssignment(WorkingAllayEntity worker, ConstructionJob job) {
        return canClaimTask(job) && worker.assignedJobId().filter(job.jobId()::equals).isPresent();
    }

    public static boolean hasFreeWork(WorkingAllayEntity worker, ServerLevel level) {
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
        ConstructionJob job = ConstructionJobIndex.get(level).activeJobOf(owner.get()).orElse(null);
        boolean sameSite = job != null && job.dimension().equals(level.dimension());
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
        if (worker.isCollectionFull()) return false;
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
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    @Nullable
    public static ItemEntity nextFreeTarget(WorkingAllayEntity worker, ServerLevel level) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        double range = collectRange(worker);
        AABB box = worker.getBoundingBox().inflate(range);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.isAlive() || entity.hasPickUpDelay() || entity.getItem().isEmpty()) continue;
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
                AllayWorkMotions.releaseToVanilla(worker);
                tryUnload(worker, level, ownerId);
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
            AllayWorkMotions.releaseToVanilla(worker);
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
        if (isVacuum(worker)) {
            worker.setActionState((byte) 1);
            if (!worker.prepareAction(target.position())) {
                return;
            }
            inhaleNearby(worker, level, progress);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            worker.clearAssignment(false);
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
        worker.clearAssignment(false);
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
        Player owner = level.getPlayerByUUID(ownerId);
        if (owner == null) return;
        double range = isVacuum(worker) ? FREE_RANGE : ConstructionJobController.reach(worker) + 0.5D;
        if (worker.distanceTo(owner) > range) {
            AllayWorkMotions.flyTo(worker, owner.position().add(0.0D, 1.0D, 0.0D));
            return;
        }
        if (!worker.prepareAction(owner.position().add(0.0D, 1.0D, 0.0D))) {
            return;
        }
        worker.unloadCollectionTo(owner);
        if (!worker.hasCollectionItems()) {
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }

    private static void releaseIfFlying(WorkingAllayEntity worker) {
        if (worker.flightState() == AllayFlightState.FLYING) {
            AllayWorkMotions.releaseToVanilla(worker);
        }
    }
}
