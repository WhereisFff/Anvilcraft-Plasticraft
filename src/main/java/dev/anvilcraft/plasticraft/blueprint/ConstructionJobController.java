package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.allay.tool.CollectionAllayToolBehavior;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftEntityBuildAdapters;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StructureVoidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 施工任务服务端协调器:规划、暂停/取消、安静提交、材料台账与同前沿并行派发。
 * 无人机侧自行领取任务,这里不扫描 128 格实体。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ConstructionJobController {
    /** 无站无人机发现范围:到最近可执行目标的直线距离。 */
    public static final double DISCOVERY_RANGE = 128.0D;
    public static final double REACH = 1.0D;
    public static final int MAX_PARTICIPANTS = 64;
    private static final int FAILED_ASSIGNMENT_RETRY_TICKS = 5;
    private static final Map<ConstructionJobProgress, AssignmentRuntime> ASSIGNMENT_RUNTIME = new WeakHashMap<>();
    private static final Map<ServerLevel, WorkerSnapshot> WORKER_SNAPSHOTS = new WeakHashMap<>();

    public static double reach(WorkingAllayEntity worker) {
        return worker.toolDefinition().reachDistance();
    }

    /** 原子组合件可从核心或任一依附格交互,但仍只执行核心操作。 */
    public record DeliveryInteraction(BlockPos target, boolean inReach, boolean inside) {
    }

    /** 已放置蓝图在世界中的方块包围盒,用于完工后把无人机带离工地。 */
    public static AABB worldBox(ConstructionJob job) {
        return AABB.of(BlueprintPlacement.of(job).bounds(job.size()));
    }

    private ConstructionJobController() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ConstructionJobIndex index = ConstructionJobIndex.get(server);
        for (ConstructionJob job : index.jobs()) {
            if (!job.isActive()) continue;
            ServerLevel level = server.getLevel(job.dimension());
            if (level == null) continue;
            tickJob(server, level, job);
        }
        for (ServerLevel level : server.getAllLevels()) {
            ConstructionProjectionIndex.flushDirty(level);
        }
        WORKER_SNAPSHOTS.clear();
    }

    @SubscribeEvent
    public static void onWorkingAllayJoined(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof WorkingAllayEntity worker) {
            WorkerSnapshot snapshot = WORKER_SNAPSHOTS.get(level);
            if (snapshot != null && snapshot.gameTime == level.getGameTime()) {
                snapshot.add(worker);
            }
        }
    }

    @SubscribeEvent
    public static void onWorkingAllayLeft(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level
            && event.getEntity() instanceof WorkingAllayEntity worker) {
            WorkerSnapshot snapshot = WORKER_SNAPSHOTS.get(level);
            if (snapshot != null) {
                snapshot.remove(worker);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level() instanceof ServerLevel level) {
            ConstructionProjectionIndex.syncNearby(level, player);
            ConstructionEntityProjectionIndex.syncNearby(level, player);
        }
    }

    @SubscribeEvent
    public static void onChunkSent(ChunkWatchEvent.Sent event) {
        ConstructionProjectionIndex.syncChunk(event.getLevel(), event.getPlayer(), event.getPos());
        ConstructionEntityProjectionIndex.syncChunk(event.getLevel(), event.getPlayer(), event.getPos());
    }

    public static void tickJob(MinecraftServer server, ServerLevel level, ConstructionJob job) {
        ConstructionJobStore store = ConstructionJobStore.get(server);
        ConstructionJobProgress progress = store.getOrCreate(job.jobId());
        if (job.state() == ConstructionJob.STATE_PLANNING || job.state() == ConstructionJob.STATE_ACTIVE) {
            plan(level, job, progress);
            store.markDirty();
            return;
        }
        if (job.state() == ConstructionJob.STATE_SOURCE_UNAVAILABLE) {
            if (isSourceAvailable(server, level, job, progress)) {
                setState(server, job, resumePhase(level, job, progress));
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
            if (hasRequiredMaterial(server, level, job, progress)) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                progress.setMissingMaterial(ItemStack.EMPTY);
                setState(server, job, resumePhase(level, job, progress));
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            if (hasAvailableDemolitionAllay(level, job, progress)) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                setState(server, job, ConstructionJob.STATE_DEMOLISHING);
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
            if (permissionRestored(level, job, progress)) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                setState(server, job, nextPhase(progress));
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_COMMITTING) {
            if (ConstructionCommitService.tick(level, progress)) {
                complete(server, level, job, progress);
            } else {
                store.markDirty();
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_SEALING_FLUID) {
            tickSealing(server, level, job, progress);
            return;
        }
        if (job.state() == ConstructionJob.STATE_COLLECTING_DEBRIS) {
            tickCollecting(server, level, job, progress);
            return;
        }
        if (job.state() == ConstructionJob.STATE_DEMOLISHING) {
            tickDemolishing(server, level, job, progress);
            return;
        }
        if (job.state() != ConstructionJob.STATE_BUILDING) return;
        ensureIndex(level, progress);
        if (!isSourceAvailable(server, level, job, progress)) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        tryLaunchForJob(level, job, progress);
        refreshWorldWaits(level, progress);
        if (progress.allPlaceResolved()) {
            setState(server, job, ConstructionJob.STATE_COMMITTING);
            if (ConstructionCommitService.tick(level, progress)) {
                complete(server, level, job, progress);
            } else {
                store.markDirty();
            }
            return;
        }
        ConstructionWaitReason reason = currentWait(progress);
        progress.setWaitReason(reason);
        reportOnce(server, job, progress, reason);
        store.markDirty();
    }

    public static void plan(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        try {
            ConstructionBlueprintService.validatePlacement(level, job);
            if (progress.planned() && !progress.operations().isEmpty()) {
                setState(level.getServer(), job, nextPhase(progress));
                return;
            }
            progress.clearOperations();
            CompoundTag tag = ConstructionStructureLibrary.load(level.getServer(), job.hash());
            StructureSnapshot snapshot = StructureSnapshotCodec.parse(tag, level.registryAccess()).snapshot();
            BlueprintPlacement placement = BlueprintPlacement.of(job);
            boolean incomplete = false;
            Set<BlockPos> declared = new HashSet<>();
            Map<Long, CompoundTag> blockEntities = new HashMap<>();
            Map<Long, BlockState> worldTargets = new HashMap<>();
            for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
                BlockState target = placement.stateOf(snapshot.stateOf(entry));
                if (target.getBlock() instanceof StructureVoidBlock) {
                    continue;
                }
                worldTargets.put(placement.worldOf(entry.pos()).asLong(), target);
            }
            for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
                BlockState local = snapshot.stateOf(entry);
                BlockState target = placement.stateOf(local);
                BlockPos worldPos = placement.worldOf(entry.pos());
                if (target.getBlock() instanceof StructureVoidBlock) {
                    continue;
                }
                declared.add(worldPos);
                entry.nbt().ifPresent(nbt -> blockEntities.put(worldPos.asLong(), nbt.copy()));
                if (MultiblockBuildAdapter.isMultiPart(target)) {
                    if (MultiblockBuildAdapter.isCore(worldPos, target)) {
                        ItemStack material = MultiblockBuildAdapter.coreMaterial(target);
                        if (material.isEmpty()) {
                            progress.addOperation(
                                worldPos,
                                target,
                                ItemStack.EMPTY,
                                ConstructionBuildOp.Kind.UNSUPPORTED,
                                ConstructionBuildOp.Status.SKIPPED
                            );
                            incomplete = true;
                        } else {
                            progress.addOperation(
                                worldPos,
                                target,
                                material,
                                ConstructionBuildOp.Kind.PLACE,
                                ConstructionBuildOp.Status.PENDING
                            );
                        }
                    } else {
                        progress.addOperation(
                            worldPos,
                            target,
                            ItemStack.EMPTY,
                            ConstructionBuildOp.Kind.ATTACHED,
                            ConstructionBuildOp.Status.PENDING
                        );
                    }
                    continue;
                }
                if (FluidBuildAdapter.isLiquidBlock(target)) {
                    FluidStack fluid = FluidBuildAdapter.liquidOf(target);
                    ItemStack bucket = FluidBuildAdapter.bucketOf(fluid);
                    if (fluid.isEmpty() || bucket.isEmpty()) {
                        progress.addOperation(
                            worldPos,
                            target,
                            ItemStack.EMPTY,
                            ConstructionBuildOp.Kind.UNSUPPORTED,
                            ConstructionBuildOp.Status.SKIPPED
                        );
                        incomplete = true;
                    } else {
                        ConstructionBuildOp place = progress.addOperation(
                            worldPos,
                            target,
                            bucket,
                            ConstructionBuildOp.Kind.PLACE,
                            ConstructionBuildOp.Status.PENDING
                        );
                        place.setFluid(fluid);
                        place.setReturnStack(FluidBuildAdapter.emptyBucket());
                    }
                    continue;
                }
                if (FluidBuildAdapter.isFilledCauldron(target)) {
                    FluidStack fluid = FluidBuildAdapter.cauldronFluidOf(target);
                    ConstructionBuildOp place = progress.addOperation(
                        worldPos,
                        target,
                        FluidBuildAdapter.cauldronItem(target),
                        ConstructionBuildOp.Kind.PLACE,
                        ConstructionBuildOp.Status.PENDING
                    );
                    if (!fluid.isEmpty()) {
                        ConstructionBuildOp child = progress.addOperation(
                            worldPos,
                            target,
                            FluidBuildAdapter.bucketOf(fluid),
                            ConstructionBuildOp.Kind.FLUID,
                            ConstructionBuildOp.Status.PENDING
                        );
                        child.setParentId(place.id());
                        child.setFluid(fluid);
                        if (!FluidBuildAdapter.bucketOf(fluid).isEmpty()) {
                            child.setReturnStack(FluidBuildAdapter.emptyBucket());
                        }
                    }
                    continue;
                }
                if (OrdinaryBlockAdapter.isPairedChestAttached(worldPos, target, worldTargets)) {
                    progress.addOperation(
                        worldPos,
                        target,
                        ItemStack.EMPTY,
                        ConstructionBuildOp.Kind.ATTACHED,
                        ConstructionBuildOp.Status.PENDING
                    );
                    continue;
                }
                if (OrdinaryBlockAdapter.isPairedChestCore(worldPos, target, worldTargets)) {
                    ItemStack material = OrdinaryBlockAdapter.pairedChestMaterial(target);
                    if (material.isEmpty()) {
                        progress.addOperation(
                            worldPos,
                            target,
                            ItemStack.EMPTY,
                            ConstructionBuildOp.Kind.UNSUPPORTED,
                            ConstructionBuildOp.Status.SKIPPED
                        );
                        incomplete = true;
                    } else {
                        progress.addOperation(
                            worldPos,
                            target,
                            material,
                            ConstructionBuildOp.Kind.PLACE,
                            ConstructionBuildOp.Status.PENDING
                        );
                    }
                    continue;
                }
                OrdinaryBlockAdapter.Mapping mapping = OrdinaryBlockAdapter.mapping(target);
                switch (mapping) {
                    case AIR -> {
                    }
                    case PLACE -> progress.addOperation(
                        worldPos,
                        target,
                        OrdinaryBlockAdapter.material(target),
                        ConstructionBuildOp.Kind.PLACE,
                        ConstructionBuildOp.Status.PENDING
                    );
                    case ATTACHED -> progress.addOperation(
                        worldPos,
                        target,
                        ItemStack.EMPTY,
                        ConstructionBuildOp.Kind.ATTACHED,
                        ConstructionBuildOp.Status.PENDING
                    );
                    case UNSUPPORTED -> {
                        progress.addOperation(
                            worldPos,
                            target,
                            ItemStack.EMPTY,
                            ConstructionBuildOp.Kind.UNSUPPORTED,
                            ConstructionBuildOp.Status.SKIPPED
                        );
                        incomplete = true;
                    }
                }
            }
            incomplete |= linkParents(progress);
            incomplete |= extractBlockEntityContents(progress, blockEntities, level.registryAccess());
            incomplete |= extractBlockEntityFluids(progress, blockEntities, level.registryAccess());
            incomplete |= planEntities(level, snapshot, placement, progress);
            FluidSealPlanner.plan(level, declared, progress);
            DemolitionPlanner.plan(level, declared, progress);
            skipOrphanedChildren(progress);
            ConstructionAssembler.assignBuildOrder(level, progress);
            ServerPlayer owner = ownerInLevel(level.getServer(), job, level);
            if (owner != null) {
                FluidSealPlanner.applyFill(progress, FluidSealFill.choose(owner, progress));
            }
            progress.setPlanned(true);
            progress.setIncomplete(incomplete || progress.incomplete());
            setState(level.getServer(), job, nextPhase(progress));
        } catch (ConstructionBlueprintException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Construction planning failed: {}", exception.reason());
            setState(level.getServer(), job, ConstructionJob.STATE_FAILED);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Construction planning failed unexpectedly", exception);
            setState(level.getServer(), job, ConstructionJob.STATE_FAILED);
        }
    }

    public static void pause(MinecraftServer server, ConstructionJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ConstructionJobStore store = ConstructionJobStore.get(server);
        ConstructionJobProgress progress = store.get(job.jobId());
        if (progress != null && level != null) {
            progress.clearDebrisLeases();
            returnInTransit(server, level, job, progress);
            releaseLeases(progress);
            store.markDirty();
        }
        ConstructionJob paused = job.withState(ConstructionJob.STATE_INACTIVE);
        ConstructionJobIndex.get(server).put(paused);
        BlueprintJobSync.syncPut(server, paused);
    }

    public static void cancel(MinecraftServer server, ConstructionJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ConstructionJobStore store = ConstructionJobStore.get(server);
        ConstructionJobProgress progress = store.get(job.jobId());
        if (progress != null && level != null) {
            progress.clearDebrisLeases();
            returnInTransit(server, level, job, progress);
            ConstructionCommitService.commitDelivered(level, progress);
            clearCoordinatorDisk(level, progress, job.jobId());
        } else if (level != null) {
            ConstructionProjectionIndex.clearJob(level, job.jobId());
            ConstructionEntityProjectionIndex.clearJob(level, job.jobId());
        }
        store.remove(job.jobId());
        ConstructionJobIndex.get(server).remove(job.jobId());
        BlueprintJobSync.syncRemove(server, job.jobId());
    }

    public static void finish(MinecraftServer server, ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        ConstructionCommitService.commitDelivered(level, progress);
        complete(server, level, job, progress);
    }

    private static void complete(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        smashRemainingShells(level, progress);
        ConstructionCommitService.restoreWirePorts(level, progress);
        byte terminal = progress.incomplete() || hasSkippedPlace(progress)
            ? ConstructionJob.STATE_COMPLETED_INCOMPLETE
            : ConstructionJob.STATE_COMPLETED;
        clearCoordinatorDisk(level, progress, job.jobId());
        ConstructionJobStore.get(server).remove(job.jobId());
        ConstructionJobIndex.get(server).remove(job.jobId());
        BlueprintJobSync.syncRemove(server, job.jobId());
        ServerPlayer owner = server.getPlayerList().getPlayer(job.owner());
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable(
                terminal == ConstructionJob.STATE_COMPLETED
                    ? "message.anvilcraftplasticraft.construction.completed"
                    : "message.anvilcraftplasticraft.construction.completed_incomplete"
            ));
            clearOwnerDisk(owner, job.jobId());
        }
    }

    public static boolean hasProgressLock(MinecraftServer server, UUID jobId) {
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        if (job != null && job.isActive()) return true;
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobId);
        return progress != null && progress.hasNonEmptyProgress();
    }

    public static byte nextPhase(ConstructionJobProgress progress) {
        if (!progress.allSealResolved()) return ConstructionJob.STATE_SEALING_FLUID;
        if (!progress.allDemolishResolved()) return ConstructionJob.STATE_DEMOLISHING;
        return ConstructionJob.STATE_BUILDING;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableSeal(ServerLevel level, ConstructionJobProgress progress) {
        return nextAssignableSeal(level, progress, null);
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableSeal(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker
    ) {
        if (assignmentScanBlocked(level, progress, AssignmentScan.SEAL)) return null;
        ConstructionBuildOp result = findAssignableSeal(level, progress, worker);
        noteAssignmentResult(level, progress, AssignmentScan.SEAL, result);
        return result;
    }

    @Nullable
    private static ConstructionBuildOp findAssignableSeal(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker
    ) {
        int lowestOpenY = lowestOpenSealY(progress);
        for (ConstructionBuildOp op : operationsInOrder(progress, AssignmentScan.SEAL)) {
            if (op.kind() != ConstructionBuildOp.Kind.SEAL) continue;
            if (op.leaseAllay().isPresent()
                || op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.pos().getY() != lowestOpenY) continue;
            if (!isWithinLoungeRange(progress, op.pos())) continue;
            if (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null) continue;
            BlockState current = level.getBlockState(op.pos());
            if (!canReplaceWithFill(current)) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op, worker);
            if (approach == null) continue;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            return op;
        }
        return null;
    }

    private static int lowestOpenSealY(ConstructionJobProgress progress) {
        int lowest = Integer.MAX_VALUE;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.SEAL) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            lowest = Math.min(lowest, op.pos().getY());
        }
        return lowest;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableDemolish(ServerLevel level, ConstructionJobProgress progress) {
        return nextAssignableDemolish(level, progress, null);
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableDemolish(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker
    ) {
        if (assignmentScanBlocked(level, progress, AssignmentScan.DEMOLISH)) return null;
        ConstructionBuildOp result = findAssignableDemolish(level, progress, worker);
        noteAssignmentResult(level, progress, AssignmentScan.DEMOLISH, result);
        return result;
    }

    @Nullable
    private static ConstructionBuildOp findAssignableDemolish(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker
    ) {
        for (ConstructionBuildOp op : operationsInOrder(progress, AssignmentScan.DEMOLISH)) {
            if (op.shell()) continue;
            if (op.leaseAllay().isPresent()
                || op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (!isWithinLoungeRange(progress, op.pos())) continue;
            BlockState current = level.getBlockState(op.pos());
            if (current.isAir()) {
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                op.setLeaseAllay(null);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op, worker);
            if (approach == null) continue;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            return op;
        }
        return null;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignable(ServerLevel level, ConstructionJobProgress progress) {
        return nextAssignable(level, progress, null);
    }

    @Nullable
    public static ConstructionBuildOp nextAssignable(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker
    ) {
        AssignmentScan scan = worker != null && !canPlaceLongReach(worker)
            ? AssignmentScan.BUILD_SHORT
            : AssignmentScan.BUILD_LONG;
        if (assignmentScanBlocked(level, progress, scan)) return null;
        ConstructionBuildOp result = findAssignable(
            level,
            progress,
            worker,
            operationsInOrder(progress, scan),
            null
        );
        noteAssignmentResult(level, progress, scan, result);
        return result;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableCarried(
        ServerLevel level,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker
    ) {
        return findAssignable(
            level,
            progress,
            worker,
            progress.carriedOperations(worker.getUUID()),
            worker.getUUID()
        );
    }

    public static List<ConstructionBuildOp> batchMaterialCandidates(
        ServerLevel level,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker,
        ConstructionBuildOp seed,
        int capacity
    ) {
        if (capacity <= 0 || !isStackBatchable(seed)) return List.of();
        List<ConstructionBuildOp> result = new ArrayList<>();
        int remaining = capacity;
        for (ConstructionBuildOp op : progress.operationsForMaterial(seed.material())) {
            if (op.id() == seed.id()
                || !isStackBatchable(op)
                || !ItemStack.isSameItemSameComponents(op.material(), seed.material())
                || op.status() != ConstructionBuildOp.Status.PENDING
                || op.leaseAllay().isPresent()
                || progress.hasCarriedMaterial(op.id())
                || !isWithinLoungeRange(progress, op.pos())
                || !ConstructionPlacementLimits.canDeliver(worker, op)
                || !level.getBlockState(op.pos()).isAir()) {
                continue;
            }
            int count = op.material().getCount();
            if (count > remaining) continue;
            result.add(op);
            remaining -= count;
            if (remaining == 0) break;
        }
        return List.copyOf(result);
    }

    private static boolean isStackBatchable(ConstructionBuildOp op) {
        return op.kind() == ConstructionBuildOp.Kind.PLACE
            && op.needsMaterial()
            && !op.material().isEmpty()
            && op.material().getMaxStackSize() > 1
            && op.returnStack().isEmpty()
            && !PlasticraftEntityBuildAdapters.returnsResin(op.material());
    }

    @Nullable
    private static ConstructionBuildOp findAssignable(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker,
        List<ConstructionBuildOp> candidates,
        @Nullable UUID carriedBy
    ) {
        Map<Long, BlockState> overlay = progress.overlayStates();
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        for (ConstructionBuildOp op : candidates) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY) {
                continue;
            }
            if (worker != null
                && (op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.ENTITY)
                && !ConstructionPlacementLimits.canDeliver(worker, op)) {
                continue;
            }
            if (op.leaseAllay().isPresent()
                || op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (carriedBy == null) {
                if (progress.hasCarriedMaterial(op.id())) continue;
            } else if (!progress.isCarriedBy(carriedBy, op.id())) {
                continue;
            }
            if (!isWithinLoungeRange(progress, op.pos())) continue;
            if (op.kind() == ConstructionBuildOp.Kind.CONTENT || op.kind() == ConstructionBuildOp.Kind.FLUID) {
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) {
                    continue;
                }
                BlockPos approach = chooseApproach(level, progress, op, worker);
                if (approach == null) continue;
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setApproach(approach);
                return op;
            }
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                if (hasOpenSupportPlace(progress, op.pos())) {
                    continue;
                }
                BlockPos approach = chooseApproach(level, progress, op, worker);
                if (approach == null) continue;
                if (entityOccupied(level, op, null)) {
                    op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
                    continue;
                }
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setApproach(approach);
                return op;
            }
            if (!level.getBlockState(op.pos()).isAir()) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op, worker);
            if (approach == null) continue;
            if (groupOccupied(level, progress, op, overlay, view, null)) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
                continue;
            }
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            return op;
        }
        return null;
    }

    public static boolean tryDeliver(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        return tryDeliver(level, progress, op, null);
    }

    public static boolean tryDeliver(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable Entity ignore
    ) {
        if (op.kind() == ConstructionBuildOp.Kind.CONTENT || op.kind() == ConstructionBuildOp.Kind.FLUID) {
            return tryDeliverContent(level, progress, op);
        }
        if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
            return tryDeliverEntity(level, progress, op, ignore);
        }
        if (!op.writesProjection()) return false;
        if (!level.getBlockState(op.pos()).isAir()) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
            return false;
        }
        Map<Long, BlockState> overlay = progress.overlayStates();
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        if (ignore instanceof WorkingAllayEntity worker) {
            if (ConstructionEnclosure.analyze(level, progress, op, worker).blocksDelivery()) {
                return false;
            }
            if (ConstructionEnclosure.bodyIntersects(worker, op, view)) {
                return false;
            }
        }
        if (groupOccupied(level, progress, op, overlay, view, ignore)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return false;
        }
        BlockState stored = OrdinaryBlockAdapter.projectionState(op.target(), op.pos(), overlay);
        if (!ConstructionProjectionIndex.tryDeliver(
            level,
            progress.jobId(),
            op.pos(),
            stored,
            overlay,
            ignore
        )) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return false;
        }
        markOpDone(level, progress, op);
        deliverAttached(level, progress, op, overlay);
        ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
        return true;
    }

    private static boolean tryDeliverContent(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        ConstructionBuildOp parent = progress.parentOf(op);
        if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) {
            return false;
        }
        markOpDone(level, progress, op);
        return true;
    }

    private static boolean tryDeliverEntity(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable Entity ignore
    ) {
        if (entityOccupied(level, op, ignore)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return false;
        }
        CompoundTag nbt = op.entityNbt();
        if (nbt == null) {
            return false;
        }
        Vec3 pos = posOf(nbt, op.pos());
        ConstructionEntityProjectionIndex.deliver(level, progress.jobId(), op.id(), pos, nbt);
        markOpDone(level, progress, op);
        if (op.returnStack().isEmpty()) {
            ItemStack extra = EntityBuildAdapters.returnAfterDeliver(level, op);
            if (!extra.isEmpty()) {
                op.setReturnStack(extra);
            }
        }
        return true;
    }

    private static boolean entityOccupied(ServerLevel level, ConstructionBuildOp op, @Nullable Entity ignore) {
        AABB box = new AABB(op.pos()).inflate(0.1D);
        CompoundTag nbt = op.entityNbt();
        if (nbt != null) {
            Vec3 pos = posOf(nbt, op.pos());
            box = new AABB(pos.x - 0.4D, pos.y, pos.z - 0.4D, pos.x + 0.4D, pos.y + 1.8D, pos.z + 0.4D);
        }
        return ConstructionProjectionIndex.isOccupied(level, Shapes.create(box), ignore);
    }

    private static Vec3 posOf(CompoundTag nbt, BlockPos fallback) {
        if (nbt.contains("Pos")) {
            ListTag pos = nbt.getList("Pos", Tag.TAG_DOUBLE);
            if (pos.size() == 3) {
                return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
            }
        }
        return Vec3.atBottomCenterOf(fallback);
    }

    public static boolean tryPlaceSeal(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable Entity ignore
    ) {
        if (op.kind() != ConstructionBuildOp.Kind.SEAL) return false;
        if (!enterIfDenied(level, progress, op.pos())) return false;
        BlockState fill = FluidSealFill.stateOf(op.material());
        if (fill == null) return false;
        BlockState current = level.getBlockState(op.pos());
        if (current.is(fill.getBlock())) {
            markOpDone(level, progress, op);
            return true;
        }
        if (!canReplaceWithFill(current)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
            return false;
        }
        if (ignore instanceof WorkingAllayEntity worker) {
            if (ConstructionEnclosure.analyze(level, progress, op, worker).blocksDelivery()) return false;
        }
        VoxelShape local = fill.getCollisionShape(level, op.pos(), CollisionContext.empty());
        VoxelShape worldShape = local.isEmpty()
            ? Shapes.empty()
            : local.move(op.pos().getX(), op.pos().getY(), op.pos().getZ());
        if (!worldShape.isEmpty() && ConstructionProjectionIndex.isOccupied(level, worldShape, ignore)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return false;
        }
        level.setBlockAndUpdate(op.pos(), fill);
        markOpDone(level, progress, op);
        return true;
    }

    public static boolean tryDemolish(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH) return false;
        if (!enterIfDenied(level, progress, op.pos())) return false;
        BlockState current = level.getBlockState(op.pos());
        if (current.isAir()) {
            markOpDone(level, progress, op);
            return true;
        }
        if (StonecutterSmashAdapter.isPermanentObstacle(level, op.pos(), current)) {
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            op.setLeaseAllay(null);
            DemolitionPlanner.skipPlaceAt(progress, op.pos());
            skipOrphanedChildren(progress);
            ConstructionJobStore.get(level).markDirty();
            return false;
        }
        BlockPos smashPos = StonecutterSmashAdapter.mainPartOf(level, op.pos());
        if (!StonecutterSmashAdapter.smash(level, smashPos, progress.jobId(), op.id(), progress)) {
            return false;
        }
        DemolitionPlanner.clearAttachedResidue(level, smashPos);
        markOpDone(level, progress, op);
        return true;
    }

    public static boolean extractMaterial(Player player, ConstructionJobProgress progress, ConstructionBuildOp op, UUID allayId) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial()) return true;
        if (progress.hasCoordinator()) {
            return false;
        }
        ItemStack taken = takeBuildMaterial(player, op);
        if (taken.isEmpty()) return false;
        progress.addLedger(op.id(), taken, allayId);
        if (PlasticraftEntityBuildAdapters.returnsResin(taken) && op.returnStack().isEmpty()
            && player.level() instanceof ServerLevel serverLevel) {
            op.setReturnStack(PlasticraftEntityBuildAdapters.resinReturn(serverLevel));
        }
        ConstructionJobStore.get(player.level()).markDirty();
        return true;
    }

    public static boolean extractMaterialFromLounge(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        UUID allayId
    ) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial()) return true;
        BlockPos loungePos = progress.coordinatorLounge();
        if (loungePos == null) return false;
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, loungePos);
        if (!access.isAvailable()) return false;
        ItemStack taken = access.extract(op);
        if (taken.isEmpty()) return false;
        progress.addLedger(op.id(), taken, allayId);
        if (PlasticraftEntityBuildAdapters.returnsResin(taken) && op.returnStack().isEmpty()) {
            op.setReturnStack(PlasticraftEntityBuildAdapters.resinReturn(level));
        }
        if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.setPickupDisplay(pickupSide(level, loungePos, allayId), taken);
        }
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    private static ItemStack takeBuildMaterial(Player player, ConstructionBuildOp op) {
        if (player.isCreative()) {
            return creativeSupply(op);
        }
        if (op.kind() == ConstructionBuildOp.Kind.FLUID && !op.fluid().isEmpty()) {
            if (!op.material().isEmpty() && takeMatching(player, op.material())) {
                return op.material().copy();
            }
            if (takeExactFluid(player, op.fluid())) {
                return op.material().isEmpty() ? new ItemStack(Items.BUCKET) : op.material().copy();
            }
            return ItemStack.EMPTY;
        }
        if (takeMatching(player, op.material())) {
            return op.material().copy();
        }
        if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
            return takeMatchingResin(player, op);
        }
        return ItemStack.EMPTY;
    }

    public static void applyShortage(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayShortageStrategy strategy,
        ItemStack missing
    ) {
        int operationId = strategy == AllayShortageStrategy.SKIP ? progress.missingOperationId() : -1;
        applyShortage(server, job, progress, strategy, missing, operationId);
    }

    public static void applyShortage(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayShortageStrategy strategy,
        ConstructionBuildOp missing
    ) {
        applyShortage(server, job, progress, strategy, missing.material(), missing.id());
    }

    private static void applyShortage(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayShortageStrategy strategy,
        ItemStack missing,
        int missingOperationId
    ) {
        ConstructionBuildOp missingOperation = progress.operation(missingOperationId);
        if (strategy == AllayShortageStrategy.SKIP) {
            ServerLevel level = server.getLevel(job.dimension());
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                boolean sameItem = matchesShortage(op, missingOperation, missing);
                if (op.kind() == ConstructionBuildOp.Kind.SEAL && sameItem) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    op.setLeaseAllay(null);
                    continue;
                }
                if ((op.kind() == ConstructionBuildOp.Kind.PLACE
                    || op.kind() == ConstructionBuildOp.Kind.CONTENT
                    || op.kind() == ConstructionBuildOp.Kind.FLUID
                    || op.kind() == ConstructionBuildOp.Kind.ENTITY)
                    && sameItem) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    op.setLeaseAllay(null);
                }
            }
            skipOrphanedChildren(progress);
            if (level != null) {
                skipPlaceOnRemainingFluids(level, progress);
            }
            progress.setIncomplete(true);
            progress.setWaitReason(ConstructionWaitReason.NONE);
            progress.setMissingMaterial(ItemStack.EMPTY);
            ConstructionJobStore.get(server).markDirty();
            setState(server, job, nextPhase(progress));
            return;
        }
        progress.setWaitReason(ConstructionWaitReason.MATERIAL);
        progress.setMissingMaterial(missing);
        progress.setMissingOperationId(missingOperationId);
        reportOnce(server, job, progress, ConstructionWaitReason.MATERIAL);
        setState(server, job, ConstructionJob.STATE_WAITING_MATERIAL);
        ConstructionJobStore.get(server).markDirty();
    }

    public static void onShortageStrategyChanged(ServerPlayer player, AllayShortageStrategy strategy) {
        ConstructionJob job = ConstructionJobIndex.get(player.server).activeJobOf(player.getUUID()).orElse(null);
        applySkipIfWaiting(player.server, job, strategy);
    }

    public static void onLoungeShortageStrategyChanged(
        ServerLevel level,
        BlockPos loungePos,
        AllayShortageStrategy strategy
    ) {
        if (strategy != AllayShortageStrategy.SKIP) return;
        if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) return;
        UUID jobId = lounge.diskJobId();
        if (jobId == null) return;
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(jobId);
        applySkipIfWaiting(level.getServer(), job, strategy);
    }

    private static void applySkipIfWaiting(
        MinecraftServer server,
        @Nullable ConstructionJob job,
        AllayShortageStrategy strategy
    ) {
        if (strategy != AllayShortageStrategy.SKIP || job == null) return;
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(job.jobId());
        if (progress == null) return;
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            applyDemolitionShortage(server, job, progress, AllayShortageStrategy.SKIP);
            return;
        }
        if (job.state() != ConstructionJob.STATE_WAITING_MATERIAL) return;
        ItemStack missing = progress.missingMaterial();
        int missingOperationId = progress.missingOperationId();
        if (missing.isEmpty() && missingOperationId < 0) return;
        applyShortage(server, job, progress, AllayShortageStrategy.SKIP, missing, missingOperationId);
    }

    public static void applyDemolitionShortage(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayShortageStrategy strategy
    ) {
        if (strategy == AllayShortageStrategy.SKIP) {
            ServerLevel level = server.getLevel(job.dimension());
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                op.setLeaseAllay(null);
                if (level != null && !level.getBlockState(op.pos()).isAir()) {
                    DemolitionPlanner.skipPlaceAt(progress, op.pos());
                }
            }
            skipOrphanedChildren(progress);
            progress.setIncomplete(true);
            progress.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionJobStore.get(server).markDirty();
            setState(server, job, nextPhase(progress));
            return;
        }
        progress.setWaitReason(ConstructionWaitReason.DEMOLITION);
        reportOnce(server, job, progress, ConstructionWaitReason.DEMOLITION);
        setState(server, job, ConstructionJob.STATE_WAITING_DEMOLITION);
        ConstructionJobStore.get(server).markDirty();
    }

    public static void resumeFromSkipWait(MinecraftServer server, ConstructionJob job) {
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(job.jobId());
        if (progress == null) return;
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL
            || job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            setState(server, job, nextPhase(progress));
        }
    }

    public static boolean isUsableApproach(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable BlockPos approach
    ) {
        return isUsableApproach(level, progress, op, approach, null);
    }

    public static boolean isUsableApproach(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable BlockPos approach,
        @Nullable WorkingAllayEntity worker
    ) {
        if (approach == null) return false;
        if (isApproachReserved(progress, op, approach)) return false;
        if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH) {
            ConstructionEnclosure.Analysis analysis = ConstructionEnclosure.analyze(level, progress, op, worker);
            if (analysis.wouldEnclose() || !analysis.approachSafe(approach)) return false;
        }
        UUID except = worker == null ? null : worker.getUUID();
        if (!ConstructionWorkerSpace.fitsWorker(level, approach, except)) return false;
        AABB workerBox = ConstructionWorkerSpace.boxAt(approach);
        for (BlockPos target : approachTargets(progress, op)) {
            if (workerBox.intersects(new AABB(target).inflate(REACH))) {
                return true;
            }
        }
        return false;
    }

    public static DeliveryInteraction deliveryInteraction(
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        AABB workerBox,
        double reach
    ) {
        BlockPos closest = op.pos();
        Vec3 workerCenter = workerBox.getCenter();
        double closestDistance = Double.MAX_VALUE;
        boolean inReach = false;
        boolean inside = false;
        for (BlockPos target : approachTargets(progress, op)) {
            AABB targetBox = new AABB(target);
            if (workerBox.intersects(targetBox)) {
                inside = true;
            }
            if (!workerBox.intersects(targetBox.inflate(reach))) {
                continue;
            }
            double distance = workerCenter.distanceToSqr(target.getCenter());
            if (distance < closestDistance) {
                closestDistance = distance;
                closest = target;
            }
            inReach = true;
        }
        return new DeliveryInteraction(closest, inReach, inside);
    }

    @Nullable
    public static BlockPos chooseApproach(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        return chooseApproach(level, progress, op, null);
    }

    @Nullable
    public static BlockPos chooseApproach(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        @Nullable WorkingAllayEntity worker
    ) {
        ConstructionEnclosure.Analysis analysis = ConstructionEnclosure.analyze(level, progress, op, worker);
        boolean demolition = op.kind() == ConstructionBuildOp.Kind.DEMOLISH;
        if (!demolition && analysis.wouldEnclose()) return null;
        Set<Long> exterior = ConstructionEnclosure.blueprintExterior(level, progress);
        UUID except = worker == null ? null : worker.getUUID();
        List<BlockPos> targets = approachTargets(progress, op);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos target : targets) {
            for (Direction direction : Direction.values()) {
                BlockPos candidate = target.relative(direction);
                double score = approachScore(
                    level,
                    progress,
                    op,
                    analysis,
                    exterior,
                    demolition,
                    except,
                    target,
                    candidate
                );
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        if (best == null && !op.writesProjection()
            && !isApproachReserved(progress, op, op.pos())
            && ConstructionWorkerSpace.fitsWorker(level, op.pos(), except)
            && (demolition || analysis.approachSafe(op.pos()))) {
            return op.pos();
        }
        if (best == null) {
            for (BlockPos target : targets) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 1) continue;
                            BlockPos candidate = target.offset(dx, dy, dz);
                            double score = approachScore(
                                level,
                                progress,
                                op,
                                analysis,
                                exterior,
                                demolition,
                                except,
                                target,
                                candidate
                            );
                            if (score < bestScore) {
                                bestScore = score;
                                best = candidate;
                            }
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double approachScore(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        ConstructionEnclosure.Analysis analysis,
        Set<Long> exterior,
        boolean demolition,
        @Nullable UUID except,
        BlockPos target,
        BlockPos candidate
    ) {
        if (isApproachReserved(progress, op, candidate)) return Double.MAX_VALUE;
        if (!ConstructionWorkerSpace.fitsWorker(level, candidate, except)) return Double.MAX_VALUE;
        if (!demolition && !analysis.approachSafe(candidate)) return Double.MAX_VALUE;
        AABB workerBox = ConstructionWorkerSpace.boxAt(candidate);
        if (!workerBox.intersects(new AABB(target).inflate(REACH))) return Double.MAX_VALUE;
        return (demolition || exterior.contains(candidate.asLong()) ? 0.0D : 1_000_000.0D)
            + candidate.getY()
            + candidate.distManhattan(op.pos()) * 0.01D;
    }

    private static boolean isApproachReserved(
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        BlockPos candidate
    ) {
        if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH) return false;
        if (op.kind() == ConstructionBuildOp.Kind.SEAL && candidate.getY() > op.pos().getY()) {
            return false;
        }
        return isReservedBuildCell(progress, op, candidate);
    }

    private static List<ConstructionBuildOp> operationsInOrder(
        ConstructionJobProgress progress,
        AssignmentScan scan
    ) {
        synchronized (ASSIGNMENT_RUNTIME) {
            AssignmentRuntime runtime = ASSIGNMENT_RUNTIME.computeIfAbsent(
                progress,
                ignored -> new AssignmentRuntime(progress.enclosureRevision())
            );
            List<ConstructionBuildOp> ordered;
            if (scan == AssignmentScan.DEMOLISH) {
                ordered = demolitionOperationsInOrder(progress, runtime);
            } else {
                ordered = buildOperationsInOrder(progress, runtime);
            }
            int scanIndex = scan.ordinal();
            int first = runtime.firstUnresolved[scanIndex];
            while (first < ordered.size() && canSkipPermanently(ordered.get(first), scan)) {
                first++;
            }
            runtime.firstUnresolved[scanIndex] = first;
            return first == 0 ? ordered : ordered.subList(first, ordered.size());
        }
    }

    private static List<ConstructionBuildOp> buildOperationsInOrder(
        ConstructionJobProgress progress,
        AssignmentRuntime runtime
    ) {
        if (runtime.orderRevision == progress.operationOrderRevision()) {
            return runtime.ordered;
        }
        List<ConstructionBuildOp> ordered = new ArrayList<>(progress.operations());
        ordered.sort(Comparator
            .comparingInt(ConstructionBuildOp::order)
            .thenComparingInt(ConstructionBuildOp::id));
        runtime.ordered = List.copyOf(ordered);
        runtime.orderRevision = progress.operationOrderRevision();
        Arrays.fill(runtime.firstUnresolved, 0);
        return runtime.ordered;
    }

    private static List<ConstructionBuildOp> demolitionOperationsInOrder(
        ConstructionJobProgress progress,
        AssignmentRuntime runtime
    ) {
        if (runtime.demolitionOrderRevision == progress.operationOrderRevision()) {
            return runtime.demolitionOrdered;
        }
        List<BlockPos> positions = new ArrayList<>();
        Map<Long, ConstructionBuildOp> byPosition = new HashMap<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
            positions.add(op.pos());
            byPosition.put(op.pos().asLong(), op);
        }
        List<ConstructionBuildOp> ordered = new ArrayList<>();
        for (BlockPos pos : ConstructionAssembler.peelOrder(positions)) {
            ConstructionBuildOp op = byPosition.get(pos.asLong());
            if (op != null) ordered.add(op);
        }
        runtime.demolitionOrdered = List.copyOf(ordered);
        runtime.demolitionOrderRevision = progress.operationOrderRevision();
        runtime.firstUnresolved[AssignmentScan.DEMOLISH.ordinal()] = 0;
        return runtime.demolitionOrdered;
    }

    private static boolean canSkipPermanently(ConstructionBuildOp op, AssignmentScan scan) {
        if (op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.status() == ConstructionBuildOp.Status.SKIPPED) {
            return true;
        }
        return switch (scan) {
            case BUILD_SHORT -> !isBuildAssignment(op) || ConstructionPlacementLimits.requiresLongReach(op);
            case BUILD_LONG -> !isBuildAssignment(op);
            case SEAL -> op.kind() != ConstructionBuildOp.Kind.SEAL;
            case DEMOLISH -> op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell();
        };
    }

    private static boolean isBuildAssignment(ConstructionBuildOp op) {
        return op.kind() == ConstructionBuildOp.Kind.PLACE
            || op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.ENTITY;
    }

    private static boolean canPlaceLongReach(WorkingAllayEntity worker) {
        return worker.toolDefinition().reachDistance() >= AllayToolDefinitions.CONSTRUCTION.reachDistance();
    }

    private static boolean assignmentScanBlocked(
        ServerLevel level,
        ConstructionJobProgress progress,
        AssignmentScan scan
    ) {
        synchronized (ASSIGNMENT_RUNTIME) {
            AssignmentRuntime runtime = ASSIGNMENT_RUNTIME.get(progress);
            if (runtime == null || runtime.revision != progress.enclosureRevision()) return false;
            return runtime.retryAfter[scan.ordinal()] > level.getGameTime();
        }
    }

    private static void noteAssignmentResult(
        ServerLevel level,
        ConstructionJobProgress progress,
        AssignmentScan scan,
        @Nullable ConstructionBuildOp result
    ) {
        synchronized (ASSIGNMENT_RUNTIME) {
            AssignmentRuntime runtime = ASSIGNMENT_RUNTIME.computeIfAbsent(
                progress,
                ignored -> new AssignmentRuntime(progress.enclosureRevision())
            );
            if (runtime.revision != progress.enclosureRevision()) {
                runtime.reset(progress.enclosureRevision());
            }
            runtime.retryAfter[scan.ordinal()] = result == null
                ? level.getGameTime() + FAILED_ASSIGNMENT_RETRY_TICKS
                : 0L;
        }
    }

    private enum AssignmentScan {
        BUILD_SHORT,
        BUILD_LONG,
        SEAL,
        DEMOLISH
    }

    private static final class AssignmentRuntime {
        private long revision;
        private final long[] retryAfter = new long[AssignmentScan.values().length];
        private final int[] firstUnresolved = new int[AssignmentScan.values().length];
        private long orderRevision = Long.MIN_VALUE;
        private List<ConstructionBuildOp> ordered = List.of();
        private long demolitionOrderRevision = Long.MIN_VALUE;
        private List<ConstructionBuildOp> demolitionOrdered = List.of();

        private AssignmentRuntime(long revision) {
            this.revision = revision;
        }

        private void reset(long revision) {
            this.revision = revision;
            Arrays.fill(this.retryAfter, 0L);
        }
    }

    public static boolean playerHasMaterial(Player player, ItemStack needed) {
        if (needed.isEmpty()) return true;
        return countMatching(player, needed) >= needed.getCount();
    }

    private static boolean playerHasMaterial(Player player, ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial() || player.isCreative()) return true;
        if (op.kind() == ConstructionBuildOp.Kind.FLUID && !op.fluid().isEmpty()) {
            return (!op.material().isEmpty() && playerHasMaterial(player, op.material()))
                || playerHasFluid(player, op.fluid());
        }
        if (playerHasMaterial(player, op.material())) return true;
        return op.kind() == ConstructionBuildOp.Kind.ENTITY && playerHasMatchingResin(player, op);
    }

    private static void deliverAttached(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp parent,
        Map<Long, BlockState> overlay
    ) {
        for (ConstructionBuildOp op : progress.childrenOf(parent)) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            BlockState stored = OrdinaryBlockAdapter.projectionState(op.target(), op.pos(), overlay);
            if (ConstructionProjectionIndex.tryDeliver(level, progress.jobId(), op.pos(), stored, overlay)) {
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
            }
        }
    }

    private static void ensureIndex(ServerLevel level, ConstructionJobProgress progress) {
        if (progress.projectionIndexReady()) return;
        Map<Long, BlockState> overlay = progress.overlayStates();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) continue;
            if (ConstructionProjectionIndex.isOwnedBy(level, op.pos(), progress.jobId())) continue;
            ConstructionProjectionIndex.tryDeliver(
                level,
                progress.jobId(),
                op.pos(),
                OrdinaryBlockAdapter.projectionState(op.target(), op.pos(), overlay),
                overlay
            );
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE || op.status() != ConstructionBuildOp.Status.DELIVERED) {
                continue;
            }
            deliverAttached(level, progress, op, overlay);
        }
        boolean complete = true;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                && op.writesProjection()
                && !ConstructionProjectionIndex.isOwnedBy(level, op.pos(), progress.jobId())) {
                complete = false;
                break;
            }
        }
        progress.setProjectionIndexReady(complete);
    }

    private static void refreshWorldWaits(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.waitingWorldPlaces()) {
            if (level.getBlockState(op.pos()).isAir()) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
            }
        }
    }

    private static ConstructionWaitReason currentWait(ConstructionJobProgress progress) {
        if (progress.hasWaitingOccupied()) return ConstructionWaitReason.OCCUPIED;
        if (progress.hasWaitingWorld() && !progress.hasOpenPlace()) return ConstructionWaitReason.WORLD;
        return ConstructionWaitReason.NONE;
    }

    /**
     * 已加载且仍拿着材料的无人机自己飞回玩家再还物;没有对应实体的台账条目才当场返还,避免复制。
     */
    private static void returnInTransit(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        List<WorkingAllayEntity> loaded = new ArrayList<>();
        for (WorkingAllayEntity drone : loadedWorkers(level, job.owner())) {
            if (!drone.isAlive()) continue;
            if (drone.assignedJobId().filter(job.jobId()::equals).isEmpty()) continue;
            loaded.add(drone);
        }
        Set<UUID> holding = new HashSet<>();
        for (WorkingAllayEntity drone : loaded) {
            drone.navigator().clear();
            ConstructionTraffic.release(level, drone.getUUID());
            if (drone.hostedCarry().isEmpty()) {
                drone.clearAssignment(false);
            } else {
                holding.add(drone.getUUID());
            }
        }
        ServerPlayer owner = findOwner(server, level, job.owner());
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (entry.state() != ConstructionLedgerEntry.State.CARRIED) continue;
            if (entry.allayId() != null && holding.contains(entry.allayId())) continue;
            giveOrDrop(owner, level, job, progress, entry.stack().copy());
            progress.markCarryReturned(entry);
        }
        if (progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            lounge.clearPickupDisplays();
        }
    }

    /** 无人机飞到所有者触及范围后把托管携带物塞回背包,并勾掉对应台账。 */
    public static void depositHostedCarry(WorkingAllayEntity drone, ServerPlayer player) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty()) return;
        player.getInventory().placeItemBackInInventory(carry.copy());
        markCarryReturned(drone, player.server);
        drone.setHostedCarry(ItemStack.EMPTY);
        drone.clearAssignment(false);
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    public static void depositHostedCarryToLounge(WorkingAllayEntity drone, ServerLevel level, BlockPos loungePos) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty()) return;
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, loungePos);
        access.insertOrDrop(carry.copy());
        markCarryReturned(drone, level.getServer());
        if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.clearPickupDisplays();
        }
        drone.setHostedCarry(ItemStack.EMPTY);
        drone.clearAssignment(false);
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    private static void markCarryReturned(WorkingAllayEntity drone, MinecraftServer server) {
        ConstructionJobStore store = ConstructionJobStore.get(server);
        boolean changed = false;
        for (ConstructionJobProgress progress : store.progresses()) {
            changed |= progress.markCarriesReturned(drone.getUUID());
        }
        if (changed) store.markDirty();
    }

    private static void giveOrDrop(
        @Nullable ServerPlayer owner,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ItemStack stack
    ) {
        if (progress.hasCoordinator()) {
            ConstructionMaterialAccess.below(level, progress.coordinatorLounge()).insertOrDrop(stack);
            return;
        }
        if (owner != null) {
            owner.getInventory().placeItemBackInInventory(stack);
            return;
        }
        Vec3 drop = Vec3.atCenterOf(job.anchor());
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, stack));
    }

    private static void releaseLeases(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.isOpen() && op.leaseAllay().isPresent()) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setLeaseAllay(null);
            }
        }
    }

    /** 创造模式或不消耗的创造板条箱:按蓝图需要直接给出材料。 */
    static ItemStack creativeSupply(ConstructionBuildOp op) {
        if (op.kind() == ConstructionBuildOp.Kind.FLUID && !op.fluid().isEmpty()) {
            return op.material().isEmpty() ? new ItemStack(Items.BUCKET) : op.material().copy();
        }
        return op.material().isEmpty() ? ItemStack.EMPTY : op.material().copy();
    }

    private static boolean takeMatching(Player player, ItemStack needed) {
        if (needed.isEmpty()) return true;
        if (countMatching(player, needed) < needed.getCount()) return false;
        int remaining = needed.getCount();
        remaining -= takeFromList(player.getInventory().items, needed, remaining);
        if (remaining > 0) {
            remaining -= takeFromList(player.getInventory().offhand, needed, remaining);
        }
        return remaining <= 0;
    }

    private static int takeFromList(List<ItemStack> slots, ItemStack needed, int remaining) {
        int taken = 0;
        for (ItemStack stack : slots) {
            if (remaining - taken <= 0) break;
            if (!matchesMaterial(stack, needed)) continue;
            int remove = Math.min(stack.getCount(), remaining - taken);
            stack.shrink(remove);
            taken += remove;
        }
        return taken;
    }

    private static int countMatching(Player player, ItemStack needed) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (matchesMaterial(stack, needed)) count += stack.getCount();
        }
        if (matchesMaterial(player.getInventory().offhand.getFirst(), needed)) {
            count += player.getInventory().offhand.getFirst().getCount();
        }
        return count;
    }

    static boolean matchesMaterial(ItemStack have, ItemStack needed) {
        return !have.isEmpty() && !needed.isEmpty() && ItemStack.isSameItemSameComponents(have, needed);
    }

    private static boolean matchesShortage(
        ConstructionBuildOp op,
        @Nullable ConstructionBuildOp missingOperation,
        ItemStack missing
    ) {
        if (missingOperation == null) {
            return missing.isEmpty() || matchesMaterial(op.material(), missing);
        }
        if (!missingOperation.material().isEmpty()) {
            return matchesMaterial(op.material(), missingOperation.material());
        }
        FluidStack needed = missingOperation.fluid();
        FluidStack candidate = op.fluid();
        if (missingOperation.kind() == ConstructionBuildOp.Kind.FLUID && op.kind() == ConstructionBuildOp.Kind.FLUID) {
            return needed.getAmount() == candidate.getAmount()
                && FluidStack.isSameFluidSameComponents(candidate, needed);
        }
        return op.id() == missingOperation.id();
    }

    private static boolean playerHasFluid(Player player, FluidStack needed) {
        return hasFluidIn(player.getInventory().items, needed)
            || hasFluidIn(player.getInventory().offhand, needed);
    }

    private static boolean hasFluidIn(List<ItemStack> slots, FluidStack needed) {
        for (ItemStack stack : slots) {
            if (FluidBuildAdapter.canProvideExactFluid(stack, needed)) return true;
        }
        return false;
    }

    private static boolean playerHasMatchingResin(Player player, ConstructionBuildOp op) {
        for (ItemStack stack : player.getInventory().items) {
            if (isMatchingResin(stack, op, player)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isMatchingResin(stack, op, player)) return true;
        }
        return false;
    }

    private static boolean takeExactFluid(Player player, FluidStack needed) {
        if (needed.isEmpty()) {
            return false;
        }
        if (takeExactFluidFrom(player.getInventory().items, needed)) {
            return true;
        }
        return takeExactFluidFrom(player.getInventory().offhand, needed);
    }

    private static boolean takeExactFluidFrom(List<ItemStack> slots, FluidStack needed) {
        for (int index = 0; index < slots.size(); index++) {
            ItemStack stack = slots.get(index);
            ItemStack copy = stack.copy();
            if (!FluidBuildAdapter.takeExactFluid(copy, needed)) {
                continue;
            }
            slots.set(index, copy);
            return true;
        }
        return false;
    }

    private static ItemStack takeMatchingResin(Player player, ConstructionBuildOp op) {
        ItemStack taken = takeResinFrom(player.getInventory().items, op, player);
        if (!taken.isEmpty()) {
            return taken;
        }
        return takeResinFrom(player.getInventory().offhand, op, player);
    }

    private static ItemStack takeResinFrom(List<ItemStack> slots, ConstructionBuildOp op, Player player) {
        for (ItemStack stack : slots) {
            if (!isMatchingResin(stack, op, player)) {
                continue;
            }
            ItemStack taken = stack.copyWithCount(1);
            stack.shrink(1);
            return taken;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isMatchingResin(ItemStack stack, ConstructionBuildOp op, Player player) {
        if (!PlasticraftEntityBuildAdapters.isResinCapture(stack) || op.entityNbt() == null) {
            return false;
        }
        EntityType<?> needed = EntityType.by(op.entityNbt()).orElse(null);
        SavedEntity saved = stack.get(ModComponents.SAVED_ENTITY);
        if (needed == null || saved == null) {
            return false;
        }
        Entity captured = saved.toEntity(player.level());
        if (captured != null && captured.getType() == needed) {
            return true;
        }
        return EntityType.by(saved.tag()).orElse(null) == needed;
    }

    private static boolean isReservedBuildCell(
        ConstructionJobProgress progress,
        ConstructionBuildOp current,
        BlockPos pos
    ) {
        for (ConstructionBuildOp operation : progress.operationsAt(pos)) {
            if (operation.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (operation.kind() == ConstructionBuildOp.Kind.PLACE
                || operation.kind() == ConstructionBuildOp.Kind.SEAL) {
                if (operation.status() == ConstructionBuildOp.Status.DELIVERED) {
                    continue;
                }
                if (canUseLaterBuildCell(current, operation)) continue;
                return true;
            }
            if (operation.kind() == ConstructionBuildOp.Kind.ATTACHED
                && operation.status() != ConstructionBuildOp.Status.DELIVERED) {
                ConstructionBuildOp parent = progress.parentOf(operation);
                if (parent != null && canUseLaterBuildCell(current, parent)) continue;
                return true;
            }
        }
        return false;
    }

    /** 只允许前序 PLACE 借用未租用的后序目标格，保证占位依赖单向且不会成环。 */
    private static boolean canUseLaterBuildCell(
        ConstructionBuildOp current,
        ConstructionBuildOp reserved
    ) {
        if (current.kind() != ConstructionBuildOp.Kind.PLACE || !reserved.isOpen()) return false;
        if (reserved.status() == ConstructionBuildOp.Status.LEASED || current.id() == reserved.id()) return false;
        int order = Integer.compare(current.order(), reserved.order());
        return order < 0 || order == 0 && current.id() < reserved.id();
    }

    private static List<BlockPos> approachTargets(ConstructionJobProgress progress, ConstructionBuildOp op) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(op.pos());
        if (op.kind() != ConstructionBuildOp.Kind.PLACE) {
            return targets;
        }
        for (ConstructionBuildOp child : progress.childrenOf(op)) {
            if (child.kind() == ConstructionBuildOp.Kind.ATTACHED
                && child.status() != ConstructionBuildOp.Status.SKIPPED) {
                targets.add(child.pos());
            }
        }
        return targets;
    }

    public static int participantCount(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        int count = 0;
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!worker.isAlive()) continue;
            boolean bound = isBoundToCoordinator(worker, progress);
            boolean leased = worker.assignedJobId().filter(job.jobId()::equals).isPresent();
            if (bound || leased) {
                count++;
            }
        }
        return count;
    }

    public static boolean canAcceptMoreParticipants(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        return participantCount(level, job, progress) < MAX_PARTICIPANTS;
    }

    private static boolean hasSkippedPlace(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if ((op.kind() == ConstructionBuildOp.Kind.PLACE
                || op.kind() == ConstructionBuildOp.Kind.CONTENT
                || op.kind() == ConstructionBuildOp.Kind.FLUID
                || op.kind() == ConstructionBuildOp.Kind.ENTITY)
                && op.status() == ConstructionBuildOp.Status.SKIPPED) {
                return true;
            }
        }
        return progress.incomplete();
    }

    private static boolean linkParents(ConstructionJobProgress progress) {
        boolean incomplete = false;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED || op.parentId() >= 0) {
                continue;
            }
            BlockPos core = MultiblockBuildAdapter.coreOf(op.pos(), op.target());
            for (ConstructionBuildOp candidate : progress.operationsAt(core)) {
                if (candidate.kind() == ConstructionBuildOp.Kind.PLACE) {
                    op.setParentId(candidate.id());
                    break;
                }
            }
            if (op.parentId() < 0) {
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                incomplete = true;
            }
        }
        return incomplete;
    }

    private static boolean extractBlockEntityContents(
        ConstructionJobProgress progress,
        Map<Long, CompoundTag> blockEntities,
        HolderLookup.Provider registries
    ) {
        boolean incomplete = false;
        List<ConstructionBuildOp> places = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if ((op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.ATTACHED)
                && op.status() != ConstructionBuildOp.Status.SKIPPED) {
                places.add(op);
            }
        }
        for (ConstructionBuildOp place : places) {
            CompoundTag nbt = blockEntities.get(place.pos().asLong());
            if (nbt == null) continue;
            BlockEntityContentAdapter.Extracted extracted =
                BlockEntityContentAdapter.extract(place.target(), nbt, registries);
            place.setBlockEntity(extracted.config());
            if (extracted.unmapped()) {
                progress.addOperation(
                    place.pos(),
                    place.target(),
                    ItemStack.EMPTY,
                    ConstructionBuildOp.Kind.UNSUPPORTED,
                    ConstructionBuildOp.Status.SKIPPED
                ).setParentId(place.id());
                incomplete = true;
                continue;
            }
            for (BlockEntityContentAdapter.SlotStack content : extracted.contents()) {
                ConstructionBuildOp child = progress.addOperation(
                    place.pos(),
                    place.target(),
                    content.stack(),
                    ConstructionBuildOp.Kind.CONTENT,
                    ConstructionBuildOp.Status.PENDING
                );
                child.setParentId(place.id());
                child.setSlot(content.slot());
            }
        }
        return incomplete;
    }

    private static boolean extractBlockEntityFluids(
        ConstructionJobProgress progress,
        Map<Long, CompoundTag> blockEntities,
        HolderLookup.Provider registries
    ) {
        boolean incomplete = false;
        List<ConstructionBuildOp> places = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if ((op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.ATTACHED)
                && op.status() != ConstructionBuildOp.Status.SKIPPED) {
                places.add(op);
            }
        }
        for (ConstructionBuildOp place : places) {
            CompoundTag nbt = blockEntities.get(place.pos().asLong());
            if (nbt == null) continue;
            FluidBuildAdapter.Extracted extracted =
                FluidBuildAdapter.extractTanks(place.target(), nbt, registries);
            if (extracted.unmapped()) {
                progress.addOperation(
                    place.pos(),
                    place.target(),
                    ItemStack.EMPTY,
                    ConstructionBuildOp.Kind.UNSUPPORTED,
                    ConstructionBuildOp.Status.SKIPPED
                ).setParentId(place.id());
                incomplete = true;
                continue;
            }
            for (FluidBuildAdapter.TankFluid tank : extracted.tanks()) {
                ConstructionBuildOp child = progress.addOperation(
                    place.pos(),
                    place.target(),
                    FluidBuildAdapter.bucketOf(tank.fluid()),
                    ConstructionBuildOp.Kind.FLUID,
                    ConstructionBuildOp.Status.PENDING
                );
                child.setParentId(place.id());
                child.setSlot(tank.tank());
                child.setFluid(tank.fluid());
                if (!FluidBuildAdapter.bucketOf(tank.fluid()).isEmpty()) {
                    child.setReturnStack(FluidBuildAdapter.emptyBucket());
                }
            }
        }
        return incomplete;
    }

    private static boolean planEntities(
        ServerLevel level,
        StructureSnapshot snapshot,
        BlueprintPlacement placement,
        ConstructionJobProgress progress
    ) {
        boolean incomplete = false;
        for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
            CompoundTag nbt = entry.nbt().copy();
            EntityType<?> type = EntityType.by(nbt).orElse(null);
            if (type == null || EntityBuildAdapters.isTransient(type)) {
                incomplete = true;
                continue;
            }
            Vec3 world = placement.localOf(entry.pos(), entry.blockPos())
                .add(placement.anchor().getX(), placement.anchor().getY(), placement.anchor().getZ());
            CompoundTag transformed = EntityBuildAdapters.withWorldPos(nbt, world);
            EntityBuildAdapter adapter = EntityBuildAdapters.find(type, transformed).orElse(null);
            if (adapter == null) {
                incomplete = true;
                continue;
            }
            EntityBuildAdapter.Planned planned = adapter.plan(level, entry, transformed);
            if (planned.unsupported()) {
                incomplete = true;
                continue;
            }
            BlockPos blockPos = placement.worldOf(entry.blockPos());
            ConstructionBuildOp entityOp = progress.addOperation(
                blockPos,
                level.getBlockState(blockPos),
                planned.material(),
                ConstructionBuildOp.Kind.ENTITY,
                ConstructionBuildOp.Status.PENDING
            );
            entityOp.setEntityNbt(planned.entityNbt());
            entityOp.setReturnStack(planned.returned());
            try {
                Entity preview = EntityType.create(planned.entityNbt(), level).orElse(null);
                if (preview != null) {
                    entityOp.setLongReach(ConstructionPlacementLimits.isLarge(preview));
                }
            } catch (RuntimeException ignored) {
            }
            for (EntityBuildAdapter.SlotStack content : planned.contents()) {
                ConstructionBuildOp child = progress.addOperation(
                    blockPos,
                    entityOp.target(),
                    content.stack(),
                    ConstructionBuildOp.Kind.CONTENT,
                    ConstructionBuildOp.Status.PENDING
                );
                child.setParentId(entityOp.id());
                child.setSlot(content.slot());
            }
            for (FluidBuildAdapter.TankFluid tank : planned.fluids()) {
                ConstructionBuildOp child = progress.addOperation(
                    blockPos,
                    entityOp.target(),
                    FluidBuildAdapter.bucketOf(tank.fluid()),
                    ConstructionBuildOp.Kind.FLUID,
                    ConstructionBuildOp.Status.PENDING
                );
                child.setParentId(entityOp.id());
                child.setSlot(tank.tank());
                child.setFluid(tank.fluid());
                if (!FluidBuildAdapter.bucketOf(tank.fluid()).isEmpty()) {
                    child.setReturnStack(FluidBuildAdapter.emptyBucket());
                }
            }
        }
        return incomplete;
    }

    private static void skipOrphanedChildren(ConstructionJobProgress progress) {
        boolean changed;
        do {
            changed = false;
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.parentId() < 0
                    || op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent != null && parent.status() == ConstructionBuildOp.Status.SKIPPED) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    op.setLeaseAllay(null);
                    changed = true;
                }
            }
        } while (changed);
    }

    private static boolean groupOccupied(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp parent,
        Map<Long, BlockState> overlay,
        ConstructionOverlayView view,
        @Nullable Entity ignore
    ) {
        UUID except = ignore instanceof WorkingAllayEntity worker ? worker.getUUID() : null;
        if (ConstructionTraffic.isReserved(level, parent.pos(), except)) {
            return true;
        }
        if (shapeOccupied(level, parent, view, ignore)) {
            return true;
        }
        for (ConstructionBuildOp child : progress.childrenOf(parent)) {
            if (child.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            if (child.status() == ConstructionBuildOp.Status.SKIPPED) continue;
            if (ConstructionTraffic.isReserved(level, child.pos(), except)) {
                return true;
            }
            if (shapeOccupied(level, child, view, ignore)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shapeOccupied(
        ServerLevel level,
        ConstructionBuildOp op,
        ConstructionOverlayView view,
        @Nullable Entity ignore
    ) {
        VoxelShape local = ConstructionProjectionIndex.projectionShape(op.target(), view, op.pos());
        VoxelShape worldShape = local.isEmpty()
            ? Shapes.empty()
            : local.move(op.pos().getX(), op.pos().getY(), op.pos().getZ());
        return !worldShape.isEmpty() && ConstructionProjectionIndex.isOccupied(level, worldShape, ignore, op.target());
    }

    /** 同一格还有未完成的 PLACE 时,矿车等实体先等铁轨交付,避免实体投影先占格。 */
    private static boolean hasOpenSupportPlace(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp other : progress.operationsAt(pos)) {
            if (other.kind() != ConstructionBuildOp.Kind.PLACE) {
                continue;
            }
            return other.status() != ConstructionBuildOp.Status.DELIVERED
                && other.status() != ConstructionBuildOp.Status.SKIPPED;
        }
        return false;
    }

    private static void tickSealing(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!isSourceAvailable(server, level, job, progress)) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        tryLaunchForJob(level, job, progress);
        if (needsFillMaterial(progress)) {
            FluidSealPlanner.applyFill(progress, chooseFill(level, job, progress));
        }
        if (progress.allSealResolved()) {
            setState(server, job, nextPhase(progress));
            storeDirty(level);
            return;
        }
        storeDirty(level);
    }

    private static void tickDemolishing(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!isSourceAvailable(server, level, job, progress)) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        tryLaunchForJob(level, job, progress);
        if (progress.allDemolishResolved()) {
            reconcileDebris(level, job, progress);
            if (hasWorldDebris(level, job, progress) && hasAvailableCollectionAllay(level, job, progress)) {
                setState(server, job, ConstructionJob.STATE_COLLECTING_DEBRIS);
            } else {
                setState(server, job, ConstructionJob.STATE_BUILDING);
            }
            storeDirty(level);
            return;
        }
        if (!progress.hasLeasedDemolish() && !hasAvailableDemolitionAllay(level, job, progress)) {
            applyDemolitionShortage(server, job, progress, ownerShortageStrategy(level, job, progress));
            return;
        }
        storeDirty(level);
    }

    private static void tickCollecting(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        tryLaunchForJob(level, job, progress);
        reconcileDebris(level, job, progress);
        if (!hasWorldDebris(level, job, progress) || !hasAvailableCollectionAllay(level, job, progress)) {
            setState(server, job, ConstructionJob.STATE_BUILDING);
            storeDirty(level);
            return;
        }
        storeDirty(level);
    }

    public static boolean hasAvailableDemolitionAllay(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()) {
            return hasBoundCapability(level, job, progress, AllayCapability.DEMOLISH)
                && nextAssignableDemolish(level, progress) != null;
        }
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (WorkingAllayEntity drone : level.getEntitiesOfClass(WorkingAllayEntity.class, search)) {
            if (!drone.isAlive() || drone.getOwner().filter(job.owner()::equals).isEmpty()) continue;
            if (!drone.toolDefinition().hasCapability(AllayCapability.DEMOLISH)) continue;
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                if (drone.position().distanceTo(Vec3.atCenterOf(op.pos())) <= DISCOVERY_RANGE) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasAvailableCollectionAllay(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()) {
            if (!hasBoundCapability(level, job, progress, AllayCapability.COLLECT_ITEMS)) {
                return false;
            }
            Vec3 lounge = Vec3.atCenterOf(progress.coordinatorLounge());
            return nearestMarkedDebris(level, job, progress, lounge, DISCOVERY_RANGE) != null;
        }
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (WorkingAllayEntity drone : level.getEntitiesOfClass(WorkingAllayEntity.class, search)) {
            if (!isOwnerCollectionAllay(drone, job)
                || drone.isCollectionFull()
                || !CollectionAllayToolBehavior.canAttemptTask(drone, level)) {
                continue;
            }
            if (progress.hasCoordinator() && !isBoundToCoordinator(drone, progress)) continue;
            ItemEntity nearest = nearestMarkedDebris(level, job, progress, drone.position(), DISCOVERY_RANGE);
            if (nearest != null) return true;
        }
        return false;
    }

    public static boolean hasWorldDebris(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        return !markedDebrisIn(level, job, progress, worldBox(job)).isEmpty();
    }

    public static void reconcileDebris(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        Set<Integer> seen = new HashSet<>();
        for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job))) {
            ConstructionDebris mark = ConstructionDebris.get(entity.getItem());
            if (mark != null) seen.add(mark.operationId());
        }
        for (ConstructionDebrisAccount account : progress.debris()) {
            seen.add(account.operationId());
        }
        for (int operationId : seen) {
            int worldCount = 0;
            for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job))) {
                ConstructionDebris mark = ConstructionDebris.get(entity.getItem());
                if (mark != null && mark.operationId() == operationId) {
                    worldCount += entity.getItem().getCount();
                }
            }
            int accounted = progress.debrisSettled(operationId) + worldCount;
            int spawned = progress.debrisSpawned(operationId);
            if (spawned > accounted) {
                progress.addDebrisExternal(operationId, spawned - accounted);
            }
        }
    }

    public static @Nullable ItemEntity nextAssignableDebris(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        Vec3 from
    ) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        Vec3 origin = progress.hasCoordinator()
            ? Vec3.atCenterOf(progress.coordinatorLounge())
            : from;
        for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job).inflate(DISCOVERY_RANGE))) {
            if (!entity.isAlive() || entity.hasPickUpDelay()) continue;
            UUID holder = progress.debrisLease(entity.getUUID());
            if (holder != null
                && level.getEntity(holder) instanceof WorkingAllayEntity leased
                && leased.isAlive()
                && leased.assignedJobId().filter(job.jobId()::equals).isPresent()
                && leased.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS)
                && !leased.isCollectionFull()
                && CollectionAllayToolBehavior.canAttemptTask(leased, level)) {
                continue;
            }
            if (holder != null) {
                progress.releaseDebrisLease(entity.getUUID());
            }
            if (origin.distanceTo(entity.position()) > DISCOVERY_RANGE) continue;
            double distance = from.distanceTo(entity.position());
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static boolean tryCollect(
        WorkingAllayEntity drone,
        ItemEntity entity,
        @Nullable ConstructionJobProgress progress
    ) {
        if (!entity.isAlive() || entity.getItem().isEmpty()) return false;
        if (!drone.canAcceptCollection(entity.getItem())) return false;
        ItemStack stack = entity.getItem();
        ConstructionDebris mark = ConstructionDebris.get(stack);
        if (progress != null && (mark == null || !mark.jobId().equals(progress.jobId()))) {
            return false;
        }
        int inserted = drone.tryInsertCollection(stack);
        if (inserted <= 0) return false;
        if (progress != null && mark != null && mark.jobId().equals(progress.jobId())) {
            progress.addDebrisCollected(mark.operationId(), inserted);
            progress.releaseDebrisLease(entity.getUUID());
        }
        stack.shrink(inserted);
        if (stack.isEmpty()) {
            entity.discard();
        }
        return true;
    }

    public static boolean isTaskCollectPhase(ConstructionJob job) {
        return job.state() == ConstructionJob.STATE_DEMOLISHING
            || job.state() == ConstructionJob.STATE_COLLECTING_DEBRIS;
    }

    public static List<ItemEntity> markedDebrisIn(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AABB box
    ) {
        List<ItemEntity> found = new ArrayList<>();
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.isAlive()) continue;
            ConstructionDebris mark = ConstructionDebris.get(entity.getItem());
            if (mark != null && mark.jobId().equals(progress.jobId())) {
                found.add(entity);
            }
        }
        return found;
    }

    private static @Nullable ItemEntity nearestMarkedDebris(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        Vec3 from,
        double range
    ) {
        ItemEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job).inflate(range))) {
            if (!entity.isAlive()) continue;
            double distance = from.distanceTo(entity.position());
            if (distance <= range && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static boolean isOwnerCollectionAllay(WorkingAllayEntity drone, ConstructionJob job) {
        return drone.isAlive()
            && drone.getOwner().filter(job.owner()::equals).isPresent()
            && drone.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS);
    }

    private static AllayShortageStrategy ownerShortageStrategy(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            return lounge.shortageStrategy();
        }
        AllayShortageStrategy strategy = AllayShortageStrategy.PAUSE;
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (WorkingAllayEntity drone : level.getEntitiesOfClass(WorkingAllayEntity.class, search)) {
            if (!drone.isAlive() || drone.getOwner().filter(job.owner()::equals).isEmpty()) continue;
            if (drone.toolDefinition().hasCapability(AllayCapability.DEMOLISH)) {
                return drone.shortageStrategy();
            }
            if (drone.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL)) {
                strategy = drone.shortageStrategy();
            }
        }
        return strategy;
    }

    private static boolean permissionRestored(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (!op.isOpen()) continue;
            if (op.kind() != ConstructionBuildOp.Kind.SEAL && op.kind() != ConstructionBuildOp.Kind.DEMOLISH) {
                continue;
            }
            if (!ConstructionPermission.canModify(level, op.pos(), job.owner())) return false;
        }
        return true;
    }

    private static boolean enterIfDenied(ServerLevel level, ConstructionJobProgress progress, BlockPos pos) {
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        if (job == null) return true;
        if (ConstructionPermission.canModify(level, pos, job.owner())) return true;
        progress.setWaitReason(ConstructionWaitReason.PERMISSION);
        reportOnce(level.getServer(), job, progress, ConstructionWaitReason.PERMISSION);
        setState(level.getServer(), job, ConstructionJob.STATE_WAITING_PERMISSION);
        ConstructionJobStore.get(level).markDirty();
        return false;
    }

    private static void markOpDone(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        op.setStatus(ConstructionBuildOp.Status.DELIVERED);
        op.setLeaseAllay(null);
        progress.markOperationDelivered(op.id());
        ConstructionJobStore.get(level).markDirty();
    }

    private static void smashRemainingShells(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || !op.shell()) continue;
            if (op.status() == ConstructionBuildOp.Status.SKIPPED
                || op.status() == ConstructionBuildOp.Status.DELIVERED) {
                continue;
            }
            try {
                if (!level.isInWorldBounds(op.pos())) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    progress.setIncomplete(true);
                    continue;
                }
                if (level.getBlockState(op.pos()).isAir()) {
                    op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                    continue;
                }
                if (StonecutterSmashAdapter.smash(level, op.pos(), progress.jobId(), op.id(), progress)) {
                    DemolitionPlanner.clearAttachedResidue(level, op.pos());
                    op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                }
            } catch (RuntimeException exception) {
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                progress.setIncomplete(true);
                AnvilcraftPlasticraft.LOGGER.error(
                    "Failed to clear construction shell for operation {}",
                    op.id(),
                    exception
                );
            }
        }
    }

    private static boolean canReplaceWithFill(BlockState state) {
        return state.isAir()
            || state.getBlock() instanceof LiquidBlock
            || state.canBeReplaced()
            || FluidSealPlanner.isSealableFluid(state);
    }

    private static boolean needsFillMaterial(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.SEAL
                && op.status() != ConstructionBuildOp.Status.SKIPPED
                && op.material().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static void skipPlaceOnRemainingFluids(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (FluidSealPlanner.isSealableFluid(level.getBlockState(op.pos()))) {
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                op.setLeaseAllay(null);
            }
        }
        skipOrphanedChildren(progress);
    }

    private static void storeDirty(ServerLevel level) {
        ConstructionJobStore.get(level).markDirty();
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        ItemStack stack = event.getItemEntity().getItem();
        ConstructionDebris mark = ConstructionDebris.get(stack);
        if (mark == null) return;
        MinecraftServer server = event.getPlayer().getServer();
        if (server != null) {
            ConstructionJobProgress progress = ConstructionJobStore.get(server).get(mark.jobId());
            if (progress != null) {
                progress.addDebrisExternal(mark.operationId(), stack.getCount());
                progress.releaseDebrisLease(event.getItemEntity().getUUID());
            }
        }
        ConstructionDebris.clear(stack);
    }

    private static void setState(MinecraftServer server, ConstructionJob job, byte state) {
        ConstructionJob updated = job.withState(state);
        ConstructionJobIndex.get(server).put(updated);
        BlueprintJobSync.syncPut(server, updated);
    }

    private static void setWait(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ConstructionWaitReason reason
    ) {
        progress.setWaitReason(reason);
        reportOnce(server, job, progress, reason);
        ConstructionJobStore.get(server).markDirty();
    }

    private static void reportOnce(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ConstructionWaitReason reason
    ) {
        if (reason == ConstructionWaitReason.NONE || reason == progress.lastReported()) return;
        progress.setLastReported(reason);
        ServerPlayer owner = server.getPlayerList().getPlayer(job.owner());
        if (owner == null) return;
        String key = "message.anvilcraftplasticraft.construction.wait." + reason.name().toLowerCase(Locale.ROOT);
        if (reason == ConstructionWaitReason.SOURCE && progress.hasCoordinator()) {
            key = "message.anvilcraftplasticraft.construction.wait.source_lounge";
        }
        owner.sendSystemMessage(Component.translatable(key));
    }

    /** 磁盘入槽只认领协调站,不启动任务;启动仍走菜单空手右击黄底变绿底。 */
    public static void claimLounge(ServerLevel level, BlockPos loungePos, UUID jobId) {
        MinecraftServer server = level.getServer();
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        if (job == null) return;
        ConstructionJobProgress progress = ConstructionJobStore.get(server).getOrCreate(jobId);
        progress.setCoordinatorLounge(loungePos.immutable());
        evictUnhostedWorkers(level, job, progress);
        ConstructionJobStore.get(server).markDirty();
    }

    public static void unclaimLounge(ServerLevel level, BlockPos loungePos, @Nullable UUID jobId) {
        if (jobId == null) return;
        MinecraftServer server = level.getServer();
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobId);
        if (progress != null && loungePos.equals(progress.coordinatorLounge())) {
            if (job != null && job.isActive()) {
                pause(server, job);
                progress = ConstructionJobStore.get(server).get(jobId);
            }
            if (progress != null) {
                progress.setCoordinatorLounge(null);
                if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
                    lounge.clearPickupDisplays();
                }
                ConstructionJobStore.get(server).markDirty();
            }
        }
    }

    public static void clearCoordinatorDisk(ServerLevel level, ConstructionJobProgress progress, UUID jobId) {
        BlockPos loungePos = progress.coordinatorLounge();
        if (loungePos == null) return;
        if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.clearDiskJobId(jobId);
            lounge.clearPickupDisplays();
        }
    }

    public static boolean isBoundToCoordinator(WorkingAllayEntity worker, ConstructionJobProgress progress) {
        return progress.hasCoordinator() && progress.coordinatorLounge().equals(worker.homeLoungePos());
    }

    public static boolean canClaimJob(WorkingAllayEntity worker, ConstructionJobProgress progress) {
        if (progress.hasCoordinator()) return isBoundToCoordinator(worker, progress);
        if (!(worker.level() instanceof ServerLevel level)) return false;
        if (worker.assignedJobId().filter(progress.jobId()::equals).isPresent()) return true;
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        return job != null && participantCount(level, job, progress) < MAX_PARTICIPANTS;
    }

    public static boolean isWithinLoungeRange(ConstructionJobProgress progress, BlockPos target) {
        if (!progress.hasCoordinator()) return true;
        return Vec3.atCenterOf(progress.coordinatorLounge()).distanceTo(Vec3.atCenterOf(target)) <= DISCOVERY_RANGE;
    }

    public static boolean isSourceAvailable(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()) {
            return ConstructionMaterialAccess.below(level, progress.coordinatorLounge()).isAvailable();
        }
        return ownerInLevel(server, job, level) != null;
    }

    private static boolean hasRequiredMaterial(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ItemStack missing = progress.missingMaterial();
        ConstructionBuildOp missingOperation = progress.operation(progress.missingOperationId());
        if (progress.hasCoordinator()) {
            ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, progress.coordinatorLounge());
            if (!access.isAvailable()) return false;
            if (missingOperation != null) return access.hasMaterial(missingOperation);
            if (missing.isEmpty()) return true;
            return access.hasMaterial(missing);
        }
        ServerPlayer owner = ownerInLevel(server, job, level);
        if (owner == null) return false;
        return missingOperation == null
            ? playerHasMaterial(owner, missing)
            : playerHasMaterial(owner, missingOperation);
    }

    public static byte resumePhase(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        if (progress.allDemolishResolved()
            && hasWorldDebris(level, job, progress)
            && hasAvailableCollectionAllay(level, job, progress)) {
            return ConstructionJob.STATE_COLLECTING_DEBRIS;
        }
        return nextPhase(progress);
    }

    private static ItemStack chooseFill(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        if (progress.hasCoordinator()) {
            return FluidSealFill.choose(ConstructionMaterialAccess.below(level, progress.coordinatorLounge()), progress);
        }
        ServerPlayer owner = ownerInLevel(level.getServer(), job, level);
        return owner == null ? ItemStack.EMPTY : FluidSealFill.choose(owner, progress);
    }

    private static void tryLaunchForJob(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        if (!progress.hasCoordinator()) return;
        if (!(level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge)) return;
        if (lounge.isBayBusy()) return;
        if (!canAcceptMoreParticipants(level, job, progress)) return;
        byte state = job.state();
        if (state == ConstructionJob.STATE_SEALING_FLUID || state == ConstructionJob.STATE_BUILDING) {
            launchIfNeeded(level, job, progress, lounge, AllayCapability.PICK_UP_MATERIAL);
            return;
        }
        if (state == ConstructionJob.STATE_DEMOLISHING) {
            if (!progress.allDemolishResolved()) {
                launchIfNeeded(level, job, progress, lounge, AllayCapability.DEMOLISH);
            }
            if (hasWorldDebris(level, job, progress)) {
                launchIfNeeded(level, job, progress, lounge, AllayCapability.COLLECT_ITEMS);
            }
            return;
        }
        if (state == ConstructionJob.STATE_COLLECTING_DEBRIS && hasWorldDebris(level, job, progress)) {
            launchIfNeeded(level, job, progress, lounge, AllayCapability.COLLECT_ITEMS);
        }
    }

    private static void launchIfNeeded(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayLoungeBlockEntity lounge,
        AllayCapability capability
    ) {
        if (lounge.isBayBusy()) return;
        if (!canAcceptMoreParticipants(level, job, progress)) return;
        if (capability == AllayCapability.COLLECT_ITEMS) {
            tryLaunchCollector(level, job, progress, lounge);
            return;
        }
        boolean building = capability == AllayCapability.PICK_UP_MATERIAL
            && job.state() == ConstructionJob.STATE_BUILDING;
        boolean shortWork = building && hasMorePotentialBuildWork(level, progress, false, 0);
        boolean longWork = building && hasMorePotentialBuildWork(level, progress, true, 0);
        if (building && !shortWork && !longWork) {
            return;
        }
        if (building
            && longWork
            && hasMorePotentialBuildWork(
                level,
                progress,
                true,
                countLoadedLongReachBuilders(level, job, progress)
            )
            && lounge.tryLaunch(record -> record.owner().filter(job.owner()::equals).isPresent()
                && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(capability)
                && canPlaceLongReach(record))) {
            return;
        }
        if (!building && !hasAssignableForLaunch(level, job, progress, capability)) return;
        int workers = countLoadedBoundCapability(level, job, progress, capability);
        if (!hasMorePotentiallyOpen(level, progress, capability, workers)) return;
        lounge.tryLaunch(record -> record.owner().filter(job.owner()::equals).isPresent()
            && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(capability)
            && (!building || canPlaceLongReach(record) || shortWork));
    }

    private static boolean hasMorePotentiallyOpen(
        ServerLevel level,
        ConstructionJobProgress progress,
        AllayCapability capability,
        int workers
    ) {
        int upperBound = capability == AllayCapability.DEMOLISH
            ? progress.unleasedDemolishCount()
            : progress.unleasedMaterialCount();
        if (upperBound <= workers) return false;
        int count = 0;
        if (capability == AllayCapability.DEMOLISH) {
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
                if (op.status() == ConstructionBuildOp.Status.LEASED
                    || op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                if (level.getBlockState(op.pos()).isAir()) continue;
                if (++count > workers) return true;
            }
            return false;
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY
                && op.kind() != ConstructionBuildOp.Kind.SEAL) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.CONTENT || op.kind() == ConstructionBuildOp.Kind.FLUID) {
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && !level.getBlockState(op.pos()).isAir()) {
                continue;
            }
            if (++count > workers) return true;
        }
        return false;
    }

    public static List<WorkingAllayEntity> loadedWorkers(ServerLevel level, UUID ownerId) {
        long gameTime = level.getGameTime();
        WorkerSnapshot snapshot = WORKER_SNAPSHOTS.get(level);
        if (snapshot == null || snapshot.gameTime != gameTime) {
            snapshot = WorkerSnapshot.capture(level, gameTime);
            WORKER_SNAPSHOTS.put(level, snapshot);
        }
        return snapshot.ownedBy(ownerId);
    }

    private static boolean hasAssignableForLaunch(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayCapability capability
    ) {
        if (capability == AllayCapability.DEMOLISH) {
            return nextAssignableDemolish(level, progress) != null;
        }
        if (job.state() == ConstructionJob.STATE_SEALING_FLUID) {
            return nextAssignableSeal(level, progress) != null;
        }
        return nextAssignable(level, progress) != null;
    }

    private static boolean hasMorePotentialBuildWork(
        ServerLevel level,
        ConstructionJobProgress progress,
        boolean requiresLongReach,
        int workers
    ) {
        int count = 0;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY) {
                continue;
            }
            if (ConstructionPlacementLimits.requiresLongReach(op) != requiresLongReach) continue;
            if (op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (!isWithinLoungeRange(progress, op.pos())) continue;
            if (op.kind() == ConstructionBuildOp.Kind.CONTENT || op.kind() == ConstructionBuildOp.Kind.FLUID) {
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.PLACE && !level.getBlockState(op.pos()).isAir()) continue;
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY
                && (hasOpenSupportPlace(progress, op.pos()) || entityOccupied(level, op, null))) {
                continue;
            }
            if (chooseApproach(level, progress, op) != null && ++count > workers) return true;
        }
        return false;
    }

    private static int countLoadedLongReachBuilders(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        int count = 0;
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!worker.isAlive() || !isBoundToCoordinator(worker, progress)) continue;
            if (!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL)) continue;
            if (canPlaceLongReach(worker)) count++;
        }
        return count;
    }

    private static boolean canPlaceLongReach(AllayWorkRecord record) {
        return AllayToolDefinitions.fromHeldItem(record.heldTool()).reachDistance()
            >= AllayToolDefinitions.CONSTRUCTION.reachDistance();
    }

    /** 收集出库先放磁铁;只有没有可用磁铁时才放空手收集工. */
    public static boolean tryLaunchCollector(AllayLoungeBlockEntity lounge) {
        if (lounge.tryLaunch(record -> CollectionAllayToolBehavior.isVacuumTool(record.heldTool())
            && CollectionAllayToolBehavior.hasCollectionCapacity(record))) {
            return true;
        }
        return lounge.tryLaunch(record -> AllayToolDefinitions.fromHeldItem(record.heldTool())
            .hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.hasCollectionCapacity(record));
    }

    private static void tryLaunchCollector(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayLoungeBlockEntity lounge
    ) {
        if (nextAssignableDebris(
            level,
            job,
            progress,
            Vec3.atCenterOf(progress.coordinatorLounge())
        ) == null) {
            return;
        }
        if (hasLoadedVacuum(level, job, progress)) {
            return;
        }
        if (lounge.tryLaunch(record -> record.owner().filter(job.owner()::equals).isPresent()
            && CollectionAllayToolBehavior.isVacuumTool(record.heldTool())
            && CollectionAllayToolBehavior.hasCollectionCapacity(record)
            && CollectionAllayToolBehavior.canAttemptTask(record, level))) {
            return;
        }
        if (hasLoadedBoundCapability(level, job, progress, AllayCapability.COLLECT_ITEMS)) {
            return;
        }
        lounge.tryLaunch(record -> record.owner().filter(job.owner()::equals).isPresent()
            && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.hasCollectionCapacity(record)
            && CollectionAllayToolBehavior.canAttemptTask(record, level));
    }

    private static boolean hasLoadedVacuum(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!worker.isAlive()) continue;
            if (!isBoundToCoordinator(worker, progress)) continue;
            if (CollectionAllayToolBehavior.isVacuum(worker)
                && !worker.isCollectionFull()
                && CollectionAllayToolBehavior.canAttemptTask(worker, level)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBoundCapability(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayCapability capability
    ) {
        if (hasLoadedBoundCapability(level, job, progress, capability)) return true;
        if (!(level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge)) {
            return false;
        }
        for (AllayWorkRecord record : lounge.hosted()) {
            if (record.owner().filter(job.owner()::equals).isEmpty()) continue;
            if (!AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(capability)) continue;
            if (capability == AllayCapability.COLLECT_ITEMS
                && (!CollectionAllayToolBehavior.hasCollectionCapacity(record)
                || !CollectionAllayToolBehavior.canAttemptTask(record, level))) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean hasLoadedBoundCapability(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayCapability capability
    ) {
        return countLoadedBoundCapability(level, job, progress, capability) > 0;
    }

    private static int countLoadedBoundCapability(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayCapability capability
    ) {
        int count = 0;
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!worker.isAlive()) continue;
            if (!isBoundToCoordinator(worker, progress)) continue;
            if (worker.toolDefinition().hasCapability(capability)
                && (capability != AllayCapability.COLLECT_ITEMS
                    || (!worker.isCollectionFull()
                        && CollectionAllayToolBehavior.canAttemptTask(worker, level)))) {
                count++;
            }
        }
        return count;
    }

    private static void evictUnhostedWorkers(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!worker.isAlive()) continue;
            if (isBoundToCoordinator(worker, progress)) continue;
            if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty() && worker.hostedCarry().isEmpty()) {
                continue;
            }
            if (worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS)
                && !worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL)) {
                progress.releaseDebrisLeasesOf(worker.getUUID());
                worker.clearAssignment(false);
                continue;
            }
            if (worker.hostedCarry().isEmpty()) {
                worker.clearAssignment(false);
            } else {
                worker.clearAssignment(false);
            }
        }
        ConstructionJobStore.get(level).markDirty();
    }

    private static Direction pickupSide(ServerLevel level, BlockPos loungePos, UUID allayId) {
        Entity entity = level.getEntity(allayId);
        if (entity == null) return Direction.NORTH;
        Vec3 delta = entity.position().subtract(Vec3.atCenterOf(loungePos));
        Direction side = Direction.getNearest(delta.x, 0.0D, delta.z);
        return side.getAxis().isVertical() ? Direction.NORTH : side;
    }

    private static final class WorkerSnapshot {
        private final long gameTime;
        private final Map<UUID, List<WorkingAllayEntity>> workersByOwner = new HashMap<>();

        private WorkerSnapshot(long gameTime) {
            this.gameTime = gameTime;
        }

        private static WorkerSnapshot capture(ServerLevel level, long gameTime) {
            WorkerSnapshot snapshot = new WorkerSnapshot(gameTime);
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof WorkingAllayEntity worker) {
                    snapshot.add(worker);
                }
            }
            return snapshot;
        }

        private void add(WorkingAllayEntity worker) {
            worker.getOwner().ifPresent(owner -> {
                List<WorkingAllayEntity> workers = this.workersByOwner.computeIfAbsent(
                    owner,
                    ignored -> new ArrayList<>()
                );
                if (!workers.contains(worker)) {
                    workers.add(worker);
                }
            });
        }

        private void remove(WorkingAllayEntity worker) {
            for (List<WorkingAllayEntity> workers : this.workersByOwner.values()) {
                workers.remove(worker);
            }
        }

        private List<WorkingAllayEntity> ownedBy(UUID ownerId) {
            return this.workersByOwner.getOrDefault(ownerId, List.of());
        }
    }

    @Nullable
    public static ServerPlayer findOwner(MinecraftServer server, ServerLevel level, UUID ownerId) {
        ServerPlayer listed = server.getPlayerList().getPlayer(ownerId);
        if (listed != null && listed.level().dimension().equals(level.dimension())) {
            return listed;
        }
        for (ServerPlayer player : level.players()) {
            if (player.getUUID().equals(ownerId)) return player;
        }
        return listed != null && listed.level() == level ? listed : null;
    }

    @Nullable
    private static ServerPlayer ownerInLevel(MinecraftServer server, ConstructionJob job, ServerLevel level) {
        return findOwner(server, level, job.owner());
    }

    private static void clearOwnerDisk(ServerPlayer player, UUID jobId) {
        clearJobId(player.getInventory().getSelected(), jobId);
        clearJobId(player.getOffhandItem(), jobId);
        for (ItemStack stack : player.getInventory().items) {
            clearJobId(stack, jobId);
        }
        clearJobId(player.containerMenu.getCarried(), jobId);
    }

    private static void clearJobId(ItemStack stack, UUID jobId) {
        ConstructionBlueprintData data = ConstructionBlueprintData.get(stack).orElse(null);
        if (data != null && data.jobId().map(jobId::equals).orElse(false)) {
            ConstructionBlueprintData.set(stack, data.withoutJobId());
        }
    }
}
