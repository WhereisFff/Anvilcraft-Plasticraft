package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.drone.tool.DroneCapability;
import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;
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
import net.minecraft.world.entity.ExperienceOrb;
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
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 施工任务服务端协调器:规划、暂停/取消、安静提交、材料台账与单机派发窄接口。
 * 无人机侧自行领取任务,这里不扫描 128 格实体。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ConstructionJobController {
    /** 无站无人机发现范围:到最近可执行目标的直线距离。 */
    public static final double DISCOVERY_RANGE = 128.0D;
    public static final double REACH = 1.0D;

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
            if (ownerInLevel(server, job, level) != null) {
                setState(server, job, nextPhase(progress));
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_MATERIAL) {
            ServerPlayer owner = ownerInLevel(server, job, level);
            if (owner != null && playerHasMaterial(owner, progress.missingMaterial())) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                progress.setMissingMaterial(ItemStack.EMPTY);
                setState(server, job, nextPhase(progress));
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            if (hasAvailableDemolitionDrone(level, job, progress)) {
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
        if (ownerInLevel(server, job, level) == null) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
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
        ConstructionWaitReason reason = currentWait(level, progress);
        progress.setWaitReason(reason);
        reportOnce(server, job, progress, reason);
        store.markDirty();
    }

    public static void plan(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        if (progress.planned() && !progress.operations().isEmpty()) {
            setState(level.getServer(), job, nextPhase(progress));
            return;
        }
        progress.operations().clear();
        try {
            CompoundTag tag = ConstructionStructureLibrary.load(level.getServer(), job.hash());
            StructureSnapshot snapshot = StructureSnapshotCodec.parse(tag, level.registryAccess()).snapshot();
            BlueprintPlacement placement = BlueprintPlacement.of(job);
            boolean incomplete = false;
            Set<BlockPos> declared = new HashSet<>();
            Map<Long, CompoundTag> blockEntities = new HashMap<>();
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
            linkParents(progress);
            incomplete |= extractBlockEntityContents(progress, blockEntities, level.registryAccess());
            incomplete |= extractBlockEntityFluids(progress, blockEntities, level.registryAccess());
            incomplete |= planEntities(level, snapshot, placement, progress);
            FluidSealPlanner.plan(level, declared, progress);
            DemolitionPlanner.plan(level, declared, progress);
            ConstructionAssembler.assignBuildOrder(level, progress);
            ServerPlayer owner = ownerInLevel(level.getServer(), job, level);
            if (owner != null) {
                FluidSealPlanner.applyFill(progress, FluidSealFill.choose(owner, progress));
            }
            progress.setPlanned(true);
            progress.setIncomplete(incomplete);
            setState(level.getServer(), job, nextPhase(progress));
        } catch (ConstructionBlueprintException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Construction planning failed: {}", exception.reason());
            setState(level.getServer(), job, ConstructionJob.STATE_FAILED);
        }
    }

    public static void pause(MinecraftServer server, ConstructionJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ConstructionJobStore store = ConstructionJobStore.get(server);
        ConstructionJobProgress progress = store.get(job.jobId());
        if (progress != null && level != null) {
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
        ConstructionBuildOp best = null;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.SEAL) continue;
            if (op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null) continue;
            BlockState current = level.getBlockState(op.pos());
            if (!canReplaceWithFill(current)) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op);
            if (approach == null) continue;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            if (best == null || op.order() < best.order()) {
                best = op;
            }
        }
        return best;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignableDemolish(ServerLevel level, ConstructionJobProgress progress) {
        List<BlockPos> remaining = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED
                || op.status() == ConstructionBuildOp.Status.LEASED) {
                continue;
            }
            remaining.add(op.pos());
        }
        List<BlockPos> peel = ConstructionAssembler.peelOrder(remaining);
        ConstructionBuildOp best = null;
        int bestIndex = Integer.MAX_VALUE;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
            if (op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            BlockState current = level.getBlockState(op.pos());
            if (current.isAir()) {
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                op.setLeaseDrone(null);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op);
            if (approach == null) continue;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            int index = peel.indexOf(op.pos());
            if (index < 0) index = Integer.MAX_VALUE - 1;
            if (best == null || index < bestIndex) {
                best = op;
                bestIndex = index;
            }
        }
        return best;
    }

    @Nullable
    public static ConstructionBuildOp nextAssignable(ServerLevel level, ConstructionJobProgress progress) {
        ConstructionBuildOp best = null;
        Map<Long, BlockState> overlay = progress.overlayStates();
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.LEASED
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.CONTENT || op.kind() == ConstructionBuildOp.Kind.FLUID) {
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) {
                    continue;
                }
                BlockPos approach = chooseApproach(level, progress, op);
                if (approach == null) continue;
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setApproach(approach);
                if (best == null || op.order() < best.order()) {
                    best = op;
                }
                continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                BlockPos approach = chooseApproach(level, progress, op);
                if (approach == null) continue;
                if (entityOccupied(level, op, null)) {
                    op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
                    continue;
                }
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setApproach(approach);
                if (best == null || op.order() < best.order()) {
                    best = op;
                }
                continue;
            }
            if (!level.getBlockState(op.pos()).isAir()) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
                continue;
            }
            BlockPos approach = chooseApproach(level, progress, op);
            if (approach == null) continue;
            if (groupOccupied(level, progress, op, overlay, view, null)) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
                continue;
            }
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            if (best == null || op.order() < best.order()) {
                best = op;
            }
        }
        return best;
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
        if (groupOccupied(level, progress, op, overlay, view, ignore)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return false;
        }
        if (!ConstructionProjectionIndex.tryDeliver(
            level,
            progress.jobId(),
            op.pos(),
            op.target(),
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
            op.setLeaseDrone(null);
            DemolitionPlanner.skipPlaceAt(progress, op.pos());
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

    public static boolean extractMaterial(Player player, ConstructionJobProgress progress, ConstructionBuildOp op, UUID droneId) {
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial()) return true;
        ItemStack taken = takeBuildMaterial(player, op);
        if (taken.isEmpty()) return false;
        progress.addLedger(op.id(), taken, droneId);
        if (PlasticraftEntityBuildAdapters.returnsResin(taken) && op.returnStack().isEmpty()
            && player.level() instanceof ServerLevel serverLevel) {
            op.setReturnStack(PlasticraftEntityBuildAdapters.resinReturn(serverLevel));
        }
        ConstructionJobStore.get(player.level()).markDirty();
        return true;
    }

    private static ItemStack takeBuildMaterial(Player player, ConstructionBuildOp op) {
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
        DroneShortageStrategy strategy,
        ItemStack missing
    ) {
        if (strategy == DroneShortageStrategy.SKIP) {
            ServerLevel level = server.getLevel(job.dimension());
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                boolean sameItem = missing.isEmpty() || matchesMaterial(op.material(), missing);
                if (op.kind() == ConstructionBuildOp.Kind.SEAL && sameItem) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    op.setLeaseDrone(null);
                    continue;
                }
                if ((op.kind() == ConstructionBuildOp.Kind.PLACE
                    || op.kind() == ConstructionBuildOp.Kind.CONTENT
                    || op.kind() == ConstructionBuildOp.Kind.FLUID
                    || op.kind() == ConstructionBuildOp.Kind.ENTITY)
                    && sameItem) {
                    op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                    op.setLeaseDrone(null);
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
        reportOnce(server, job, progress, ConstructionWaitReason.MATERIAL);
        setState(server, job, ConstructionJob.STATE_WAITING_MATERIAL);
        ConstructionJobStore.get(server).markDirty();
    }

    public static void onShortageStrategyChanged(ServerPlayer player, DroneShortageStrategy strategy) {
        if (strategy != DroneShortageStrategy.SKIP) return;
        ConstructionJob job = ConstructionJobIndex.get(player.server).activeJobOf(player.getUUID()).orElse(null);
        if (job == null) return;
        ConstructionJobProgress progress = ConstructionJobStore.get(player.server).get(job.jobId());
        if (progress == null) return;
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            applyDemolitionShortage(player.server, job, progress, DroneShortageStrategy.SKIP);
            return;
        }
        if (job.state() != ConstructionJob.STATE_WAITING_MATERIAL) return;
        ItemStack missing = progress.missingMaterial();
        if (missing.isEmpty()) return;
        applyShortage(player.server, job, progress, DroneShortageStrategy.SKIP, missing);
    }

    public static void applyDemolitionShortage(
        MinecraftServer server,
        ConstructionJob job,
        ConstructionJobProgress progress,
        DroneShortageStrategy strategy
    ) {
        if (strategy == DroneShortageStrategy.SKIP) {
            ServerLevel level = server.getLevel(job.dimension());
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell()) continue;
                if (op.status() == ConstructionBuildOp.Status.DELIVERED
                    || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                    continue;
                }
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                op.setLeaseDrone(null);
                if (level != null && !level.getBlockState(op.pos()).isAir()) {
                    DemolitionPlanner.skipPlaceAt(progress, op.pos());
                }
            }
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
        if (approach == null) return false;
        if (isReservedBuildCell(progress, approach)) return false;
        if (!fitsDrone(level, approach)) return false;
        Vec3 center = Vec3.atBottomCenterOf(approach);
        AABB droneBox = new AABB(
            center.x - 0.25D,
            center.y,
            center.z - 0.25D,
            center.x + 0.25D,
            center.y + 0.5D,
            center.z + 0.25D
        );
        for (BlockPos target : approachTargets(progress, op)) {
            if (droneBox.intersects(new AABB(target).inflate(REACH))) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static BlockPos chooseApproach(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos target : approachTargets(progress, op)) {
            for (Direction direction : Direction.values()) {
                BlockPos candidate = target.relative(direction);
                if (isReservedBuildCell(progress, candidate)) continue;
                if (!fitsDrone(level, candidate)) continue;
                Vec3 center = Vec3.atBottomCenterOf(candidate);
                AABB droneBox = new AABB(
                    center.x - 0.25D,
                    center.y,
                    center.z - 0.25D,
                    center.x + 0.25D,
                    center.y + 0.5D,
                    center.z + 0.25D
                );
                if (!droneBox.intersects(new AABB(target).inflate(REACH))) continue;
                double score = candidate.getY() + candidate.distManhattan(op.pos()) * 0.01D;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        if (best == null && !op.writesProjection()
            && !isReservedBuildCell(progress, op.pos())
            && fitsDrone(level, op.pos())) {
            return op.pos();
        }
        return best;
    }

    public static boolean playerHasMaterial(Player player, ItemStack needed) {
        if (needed.isEmpty()) return true;
        return countMatching(player, needed) >= needed.getCount();
    }

    private static void deliverAttached(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp parent,
        Map<Long, BlockState> overlay
    ) {
        for (ConstructionBuildOp op : childrenOf(progress, parent)) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (ConstructionProjectionIndex.tryDeliver(level, progress.jobId(), op.pos(), op.target(), overlay)) {
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
            }
        }
    }

    private static void ensureIndex(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) continue;
            if (ConstructionProjectionIndex.has(level, op.pos())) continue;
            ConstructionProjectionIndex.tryDeliver(
                level,
                progress.jobId(),
                op.pos(),
                op.target(),
                progress.overlayStates()
            );
        }
    }

    private static void refreshWorldWaits(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED
                || op.status() == ConstructionBuildOp.Status.LEASED) {
                continue;
            }
            if (!level.getBlockState(op.pos()).isAir()) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
            } else if (op.status() == ConstructionBuildOp.Status.WAITING_WORLD) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
            }
        }
    }

    private static ConstructionWaitReason currentWait(ServerLevel level, ConstructionJobProgress progress) {
        boolean world = false;
        boolean occupied = false;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.WAITING_WORLD) world = true;
            if (op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED) occupied = true;
        }
        if (occupied) return ConstructionWaitReason.OCCUPIED;
        if (world && !progress.hasOpenPlace()) return ConstructionWaitReason.WORLD;
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
        List<DroneEntity> loaded = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof DroneEntity drone)) continue;
            if (drone.assignedJobId().filter(job.jobId()::equals).isEmpty()) continue;
            loaded.add(drone);
        }
        Set<UUID> holding = new HashSet<>();
        for (DroneEntity drone : loaded) {
            drone.navigator().clear();
            if (drone.hostedCarry().isEmpty()) {
                drone.clearAssignment(false);
            } else {
                holding.add(drone.getUUID());
            }
        }
        ServerPlayer owner = findOwner(server, level, job.owner());
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (entry.state() != ConstructionLedgerEntry.State.CARRIED) continue;
            if (entry.droneId() != null && holding.contains(entry.droneId())) continue;
            giveOrDrop(owner, level, job, entry.stack().copy());
            entry.setState(ConstructionLedgerEntry.State.RETURNED);
        }
    }

    /** 无人机飞到所有者触及范围后把托管携带物塞回背包,并勾掉对应台账。 */
    public static void depositHostedCarry(DroneEntity drone, ServerPlayer player) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty()) return;
        player.getInventory().placeItemBackInInventory(carry.copy());
        drone.assignedJobId().ifPresent(jobId -> {
            ConstructionJobProgress progress = ConstructionJobStore.get(player.server).get(jobId);
            if (progress == null) return;
            for (ConstructionLedgerEntry entry : progress.ledger()) {
                if (entry.state() == ConstructionLedgerEntry.State.CARRIED
                    && drone.getUUID().equals(entry.droneId())) {
                    entry.setState(ConstructionLedgerEntry.State.RETURNED);
                }
            }
            ConstructionJobStore.get(player.server).markDirty();
        });
        drone.setHostedCarry(ItemStack.EMPTY);
        drone.clearAssignment(false);
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    private static void giveOrDrop(
        @Nullable ServerPlayer owner,
        ServerLevel level,
        ConstructionJob job,
        ItemStack stack
    ) {
        if (owner != null) {
            owner.getInventory().placeItemBackInInventory(stack);
            return;
        }
        Vec3 drop = Vec3.atCenterOf(job.anchor());
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, stack));
    }

    private static void releaseLeases(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.LEASED) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setLeaseDrone(null);
            }
        }
    }

    private static boolean takeMatching(Player player, ItemStack needed) {
        if (needed.isEmpty()) return true;
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

    private static boolean isReservedBuildCell(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.SEAL) {
                if (op.status() == ConstructionBuildOp.Status.DELIVERED) {
                    continue;
                }
                if (op.pos().equals(pos)) return true;
            }
            if (op.kind() == ConstructionBuildOp.Kind.ATTACHED
                && op.status() != ConstructionBuildOp.Status.DELIVERED
                && op.pos().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockPos> approachTargets(ConstructionJobProgress progress, ConstructionBuildOp op) {
        List<BlockPos> targets = new ArrayList<>();
        targets.add(op.pos());
        if (op.kind() != ConstructionBuildOp.Kind.PLACE) {
            return targets;
        }
        for (ConstructionBuildOp child : childrenOf(progress, op)) {
            if (child.kind() == ConstructionBuildOp.Kind.ATTACHED
                && child.status() != ConstructionBuildOp.Status.SKIPPED) {
                targets.add(child.pos());
            }
        }
        return targets;
    }

    private static boolean fitsDrone(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atBottomCenterOf(pos);
        AABB box = new AABB(
            center.x - 0.25D,
            center.y,
            center.z - 0.25D,
            center.x + 0.25D,
            center.y + 0.5D,
            center.z + 0.25D
        );
        if (!level.noBlockCollision(null, box)) return false;
        for (Entity entity : level.getEntities(null, box)) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            if (entity instanceof DroneEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                continue;
            }
            return false;
        }
        return true;
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

    private static void linkParents(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.ATTACHED || op.parentId() >= 0) {
                continue;
            }
            BlockPos core = MultiblockBuildAdapter.coreOf(op.pos(), op.target());
            for (ConstructionBuildOp candidate : progress.operations()) {
                if (candidate.kind() == ConstructionBuildOp.Kind.PLACE && candidate.pos().equals(core)) {
                    op.setParentId(candidate.id());
                    break;
                }
            }
        }
    }

    private static boolean extractBlockEntityContents(
        ConstructionJobProgress progress,
        Map<Long, CompoundTag> blockEntities,
        HolderLookup.Provider registries
    ) {
        boolean incomplete = false;
        List<ConstructionBuildOp> places = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.PLACE) {
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
            if (op.kind() == ConstructionBuildOp.Kind.PLACE) {
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

    private static List<ConstructionBuildOp> childrenOf(ConstructionJobProgress progress, ConstructionBuildOp parent) {
        List<ConstructionBuildOp> children = new ArrayList<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.parentId() == parent.id()) {
                children.add(op);
            }
        }
        return children;
    }

    private static void skipOrphanedChildren(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.parentId() < 0 || op.status() == ConstructionBuildOp.Status.DELIVERED) {
                continue;
            }
            ConstructionBuildOp parent = progress.parentOf(op);
            if (parent != null && parent.status() == ConstructionBuildOp.Status.SKIPPED) {
                op.setStatus(ConstructionBuildOp.Status.SKIPPED);
                op.setLeaseDrone(null);
            }
        }
    }

    private static boolean groupOccupied(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp parent,
        Map<Long, BlockState> overlay,
        ConstructionOverlayView view,
        @Nullable Entity ignore
    ) {
        if (shapeOccupied(level, parent, view, ignore)) {
            return true;
        }
        for (ConstructionBuildOp child : childrenOf(progress, parent)) {
            if (child.kind() != ConstructionBuildOp.Kind.ATTACHED) continue;
            if (child.status() == ConstructionBuildOp.Status.SKIPPED) continue;
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
        return !worldShape.isEmpty() && ConstructionProjectionIndex.isOccupied(level, worldShape, ignore);
    }

    private static void tickSealing(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        ServerPlayer owner = ownerInLevel(server, job, level);
        if (owner == null) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        if (needsFillMaterial(progress)) {
            FluidSealPlanner.applyFill(progress, FluidSealFill.choose(owner, progress));
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
        if (ownerInLevel(server, job, level) == null) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        if (progress.allDemolishResolved()) {
            reconcileDebris(level, job, progress);
            if (hasWorldDebris(level, job, progress) && hasAvailableCollectionDrone(level, job, progress)) {
                setState(server, job, ConstructionJob.STATE_COLLECTING_DEBRIS);
            } else {
                setState(server, job, ConstructionJob.STATE_BUILDING);
            }
            storeDirty(level);
            return;
        }
        if (!progress.hasLeasedDemolish() && !hasAvailableDemolitionDrone(level, job, progress)) {
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
        if (ownerInLevel(server, job, level) == null) {
            setWait(server, job, progress, ConstructionWaitReason.SOURCE);
            setState(server, job, ConstructionJob.STATE_SOURCE_UNAVAILABLE);
            return;
        }
        reconcileDebris(level, job, progress);
        if (!hasWorldDebris(level, job, progress) || !hasAvailableCollectionDrone(level, job, progress)) {
            setState(server, job, ConstructionJob.STATE_BUILDING);
            storeDirty(level);
            return;
        }
        storeDirty(level);
    }

    public static boolean hasAvailableDemolitionDrone(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search)) {
            if (!drone.isAlive() || drone.getOwner().filter(job.owner()::equals).isEmpty()) continue;
            if (!drone.toolDefinition().hasCapability(DroneCapability.DEMOLISH)) continue;
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

    public static boolean hasAvailableCollectionDrone(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search)) {
            if (!isOwnerCollectionDrone(drone, job) || drone.isCollectionFull()) continue;
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
        for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job).inflate(DISCOVERY_RANGE))) {
            if (!entity.isAlive() || entity.hasPickUpDelay()) continue;
            UUID holder = progress.debrisLease(entity.getUUID());
            if (holder != null && level.getEntity(holder) instanceof DroneEntity leased && leased.isAlive()) {
                continue;
            }
            if (holder != null) {
                progress.releaseDebrisLease(entity.getUUID());
            }
            double distance = from.distanceTo(entity.position());
            if (distance > DISCOVERY_RANGE) continue;
            if (distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static boolean tryCollect(
        DroneEntity drone,
        ItemEntity entity,
        @Nullable ConstructionJobProgress progress
    ) {
        if (!entity.isAlive() || entity.getItem().isEmpty()) return false;
        if (!drone.canAcceptCollection(entity.getItem())) return false;
        ItemStack stack = entity.getItem();
        ConstructionDebris mark = ConstructionDebris.get(stack);
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

    private static boolean isOwnerCollectionDrone(DroneEntity drone, ConstructionJob job) {
        return drone.isAlive()
            && drone.getOwner().filter(job.owner()::equals).isPresent()
            && drone.toolDefinition().hasCapability(DroneCapability.COLLECT_ITEMS);
    }

    private static DroneShortageStrategy ownerShortageStrategy(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        DroneShortageStrategy strategy = DroneShortageStrategy.PAUSE;
        AABB search = worldBox(job).inflate(DISCOVERY_RANGE);
        for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class, search)) {
            if (!drone.isAlive() || drone.getOwner().filter(job.owner()::equals).isEmpty()) continue;
            if (drone.toolDefinition().hasCapability(DroneCapability.DEMOLISH)) {
                return drone.shortageStrategy();
            }
            if (drone.toolDefinition().hasCapability(DroneCapability.PICK_UP_MATERIAL)) {
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
        op.setLeaseDrone(null);
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (entry.operationId() == op.id() && entry.state() == ConstructionLedgerEntry.State.CARRIED) {
                entry.setState(ConstructionLedgerEntry.State.DELIVERED);
            }
        }
        ConstructionJobStore.get(level).markDirty();
    }

    private static void smashRemainingShells(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH || !op.shell()) continue;
            if (op.status() == ConstructionBuildOp.Status.SKIPPED
                || op.status() == ConstructionBuildOp.Status.DELIVERED) {
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
                op.setLeaseDrone(null);
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
        owner.sendSystemMessage(Component.translatable(
            "message.anvilcraftplasticraft.construction.wait." + reason.name().toLowerCase(Locale.ROOT)
        ));
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
