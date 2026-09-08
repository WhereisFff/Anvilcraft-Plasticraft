package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverage;
import dev.anvilcraft.plasticraft.allay.observation.ObservationCoverageService;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolSupply;
import dev.anvilcraft.plasticraft.allay.transfer.ConstructionTransferService;
import dev.anvilcraft.plasticraft.allay.tool.CollectionAllayToolBehavior;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.init.PlasticraftEntityBuildAdapters;
import dev.dubhe.anvilcraft.init.item.ModComponents;
import dev.dubhe.anvilcraft.item.property.component.SavedEntity;
import dev.dubhe.anvilcraft.util.BlockMiningEffect;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
import net.neoforged.neoforge.fluids.FluidStack;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.WeakHashMap;

/**
 * 施工任务服务端协调器:规划、暂停/取消、提交、材料台账与同前沿并行派发
 * 无人机侧自行领取任务
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ConstructionJobController {
    public static final double DISCOVERY_RANGE = 128.0D;
    public static final double REACH = 1.0D;
    public static final int MAX_PARTICIPANTS = 64;
    private static final int FAILED_ASSIGNMENT_RETRY_TICKS = 5;
    /** 就近前沿一次考察的目标数上限;只重排队首一小段,不为每次派发扫描整张操作表。 */
    private static final int FRONTIER_WINDOW = 24;
    /** 不可达退避时长;够长到让别的位置先建完、周围形状发生变化,又不至于让任务停很久。 */
    private static final int UNREACHABLE_RETRY_TICKS = 200;
    private static final Map<ConstructionJobProgress, AssignmentRuntime> ASSIGNMENT_RUNTIME = new WeakHashMap<>();
    private static final Map<ServerLevel, WorkerSnapshot> WORKER_SNAPSHOTS = new WeakHashMap<>();
    private static final ThreadLocal<Integer> SUPPRESSED_WORLD_CHANGE_OBSERVATION =
        ThreadLocal.withInitial(() -> 0);

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

    /** 施工自身的真实写入不应被反应式监听再次解释为玩家改动。 */
    public static boolean isWorldChangeObservationSuppressed() {
        return SUPPRESSED_WORLD_CHANGE_OBSERVATION.get() > 0;
    }

    public static <T> T withoutWorldChangeObservation(Supplier<T> action) {
        int depth = SUPPRESSED_WORLD_CHANGE_OBSERVATION.get();
        SUPPRESSED_WORLD_CHANGE_OBSERVATION.set(depth + 1);
        try {
            return action.get();
        } finally {
            if (depth == 0) {
                SUPPRESSED_WORLD_CHANGE_OBSERVATION.remove();
            } else {
                SUPPRESSED_WORLD_CHANGE_OBSERVATION.set(depth);
            }
        }
    }

    /**
     * 观察真实世界方块变更:建造期间只把蓝图声明格中的不匹配方块转成新的拆除操作。
     * 投影本身不写世界,而拆除/提交产生的空气或终态写入会被阶段过滤掉。
     */
    public static void onRealBlockStateChanged(
        ServerLevel level,
        BlockPos pos,
        BlockState previous,
        BlockState current
    ) {
        if (isWorldChangeObservationSuppressed() || previous.equals(current)) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        ConstructionJobStore store = ConstructionJobStore.get(server);
        for (ConstructionJob job : ConstructionJobIndex.get(server).jobsIn(level)) {
            if (!acceptsRuntimeDemolition(job.state())
                || !worldBox(job).contains(Vec3.atCenterOf(pos))) {
                continue;
            }
            ConstructionJobProgress progress = store.get(job.jobId());
            if (progress == null || !progress.planned()) continue;
            BlockState expected = progress.declaredTarget(pos);
            // 未声明的稀疏空隙不属于清场声明区,即使它落在包围盒内也保持原样
            if (expected == null) continue;
            if (current.isAir()) {
                // 已由真实世界满足的方块被外部移除后,重新进入建设队列;普通拆除写空气由抑制器绕过
                if (hasOpenDemolitionAt(progress, pos) || !hasWorldSatisfiedPlaceAt(progress, pos)) continue;
                boolean changed = resetRuntimeBuildOperations(level, progress, pos, pos);
                if (!ConstructionPermission.canModify(level, pos, job.owner())) {
                    if (changed) store.markDirty();
                    enterPermissionWait(level, job, progress);
                    continue;
                }
                if (changed) {
                    store.markDirty();
                    reconcileRuntimeState(server, level, job, progress);
                }
                continue;
            }
            BlockPos operationPos = DemolitionPlanner.runtimeCore(level, pos, progress);
            if (expected.equals(current)) {
                // 外部方块正好满足目标时,先结清正在途中的建设台账,避免留下 WAITING_WORLD
                boolean changed = false;
                if (hasActiveBuildWork(progress)) {
                    interruptBuildWorkers(server, level, job, progress);
                    changed = true;
                }
                if (cancelDemolitionsAt(level, job, progress, operationPos)) {
                    resetRuntimeBuildOperations(level, progress, operationPos, pos);
                    changed = true;
                }
                if (satisfyExternalPlace(level, progress, pos, current)) {
                    changed = true;
                }
                if (changed) {
                    store.markDirty();
                }
                if (!ConstructionPermission.canModify(level, pos, job.owner())
                    || !ConstructionPermission.canModify(level, operationPos, job.owner())) {
                    // 先记录真实世界已满足的状态,但禁止在权限恢复前继续派发或提交
                    enterPermissionWait(level, job, progress);
                } else if (changed) {
                    reconcileRuntimeState(server, level, job, progress);
                }
                continue;
            }
            if (expected.isAir()
                && progress.clearanceStrategy() == AllayClearanceStrategy.KEEP_BLANK
                && !hasEntityTargetAt(progress, pos)) {
                continue;
            }
            if (hasOpenDemolitionAt(progress, operationPos)) continue;
            BlockState operationState = level.getBlockState(operationPos);
            if (StonecutterSmashAdapter.isPermanentObstacle(level, operationPos, operationState)) {
                if (hasActiveBuildWork(progress)) {
                    interruptBuildWorkers(server, level, job, progress);
                }
                if (!ConstructionPermission.canModify(level, pos, job.owner())
                    || !ConstructionPermission.canModify(level, operationPos, job.owner())) {
                    // 永久障碍也不能借反应式路径绕过保护区权限
                    DemolitionPlanner.addRuntimeDemolition(level, pos, progress);
                    resetRuntimeBuildOperations(level, progress, operationPos, pos);
                    progress.setWaitReason(ConstructionWaitReason.NONE);
                    progress.setMissingMaterial(ItemStack.EMPTY);
                    store.markDirty();
                    enterPermissionWait(level, job, progress);
                    continue;
                }
                skipPlaceAtIncludingDelivered(level, progress, operationPos);
                progress.setIncomplete(true);
                store.markDirty();
                reconcileRuntimeState(server, level, job, progress);
                continue;
            }
            boolean enteringDemolition = job.state() != ConstructionJob.STATE_DEMOLISHING
                && job.state() != ConstructionJob.STATE_WAITING_DEMOLITION;
            if (enteringDemolition) {
                interruptBuildWorkers(server, level, job, progress);
            }
            DemolitionPlanner.addRuntimeDemolition(level, pos, progress);
            resetRuntimeBuildOperations(level, progress, operationPos);
            progress.setWaitReason(ConstructionWaitReason.NONE);
            progress.setMissingMaterial(ItemStack.EMPTY);
            store.markDirty();
            if (enteringDemolition) {
                setState(server, job, ConstructionJob.STATE_DEMOLISHING);
            }
        }
    }

    private static boolean hasEntityTargetAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operationsAt(pos)) {
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY
                && op.status() != ConstructionBuildOp.Status.SKIPPED) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasWorldSatisfiedPlaceAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operationsAt(pos)) {
            if ((op.kind() == ConstructionBuildOp.Kind.PLACE
                    || op.kind() == ConstructionBuildOp.Kind.ATTACHED)
                && op.worldSatisfied()
                && op.status() == ConstructionBuildOp.Status.DELIVERED) {
                return true;
            }
        }
        return false;
    }

    /** 外部变更可能结清等待项,也可能新增拆除项;把任务状态收敛到实际开放相位。 */
    private static void reconcileRuntimeState(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob sourceJob,
        ConstructionJobProgress progress
    ) {
        ConstructionJob job = ConstructionJobIndex.get(server).job(sourceJob.jobId());
        if (job == null) return;
        if (progress.hasOpenDemolish()) {
            if (job.state() != ConstructionJob.STATE_DEMOLISHING
                && job.state() != ConstructionJob.STATE_WAITING_DEMOLITION) {
                setState(server, job, ConstructionJob.STATE_DEMOLISHING);
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            progress.setWaitReason(ConstructionWaitReason.NONE);
            setState(server, job, nextPhase(progress));
            return;
        }
        if (job.state() != ConstructionJob.STATE_WAITING_MATERIAL) return;
        ConstructionBuildOp missing = progress.operation(progress.missingOperationId());
        if (missing != null && missing.isOpen()) return;
        if (missing == null && !progress.missingMaterial().isEmpty()) return;
        progress.setWaitReason(ConstructionWaitReason.NONE);
        progress.setMissingMaterial(ItemStack.EMPTY);
        setState(server, job, nextPhase(progress));
    }

    private static boolean acceptsRuntimeDemolition(byte state) {
        return state == ConstructionJob.STATE_BUILDING
            || state == ConstructionJob.STATE_WAITING_MATERIAL
            || state == ConstructionJob.STATE_WAITING_DEMOLITION
            // 等观察窗口期间玩家仍可能改动已加载的那一部分,这些改动照样要排进运行期拆除
            || state == ConstructionJob.STATE_WAITING_OBSERVER
            || state == ConstructionJob.STATE_DEMOLISHING
            || state == ConstructionJob.STATE_COLLECTING_DEBRIS;
    }

    private static boolean hasOpenDemolitionAt(ConstructionJobProgress progress, BlockPos pos) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH
                || op.shell()
                || !op.pos().equals(pos)) {
                continue;
            }
            if (op.isOpen() || op.leaseAllay().isPresent()) return true;
        }
        return false;
    }

    private static boolean hasActiveBuildWork(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (!op.isBuildMaterial()) continue;
            if (op.leaseAllay().isPresent() || progress.hasCarriedMaterial(op.id())) return true;
        }
        return false;
    }

    private static void resetRuntimeBuildOperations(
        ServerLevel level,
        ConstructionJobProgress progress,
        BlockPos operationPos
    ) {
        resetRuntimeBuildOperations(level, progress, operationPos, operationPos);
    }

    private static boolean resetRuntimeBuildOperations(
        ServerLevel level,
        ConstructionJobProgress progress,
        BlockPos operationPos,
        BlockPos requestedPos
    ) {
        Set<Integer> resetIds = new LinkedHashSet<>();
        Set<BlockPos> affectedPositions = new LinkedHashSet<>();
        affectedPositions.addAll(DemolitionPlanner.affectedPositions(level, operationPos));
        affectedPositions.addAll(DemolitionPlanner.affectedPositions(level, requestedPos));
        for (BlockPos affected : affectedPositions) {
            ConstructionProjectionIndex.remove(level, progress.jobId(), affected);
            for (ConstructionBuildOp op : List.copyOf(progress.operationsAt(affected))) {
                // 外部方块覆盖实体或其内容子操作时,也要撤掉对应投影并重新交付
                if (op.kind() == ConstructionBuildOp.Kind.PLACE
                    || op.kind() == ConstructionBuildOp.Kind.ATTACHED
                    || op.kind() == ConstructionBuildOp.Kind.CONTENT
                    || op.kind() == ConstructionBuildOp.Kind.FLUID
                    || op.kind() == ConstructionBuildOp.Kind.ENTITY
                    || op.kind() == ConstructionBuildOp.Kind.DECORATE) {
                    resetIds.add(op.id());
                }
            }
        }
        // 父方块被外部替换时,其内容/流体/实体子操作也必须回到待交付状态
        boolean expanded;
        do {
            expanded = false;
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.parentId() >= 0
                    && resetIds.contains(op.parentId())
                    && resetIds.add(op.id())) {
                    expanded = true;
                }
            }
        } while (expanded);
        for (int operationId : resetIds) {
            ConstructionBuildOp op = progress.operation(operationId);
            if (op == null || op.status() == ConstructionBuildOp.Status.SKIPPED) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) {
                returnRuntimeMaterial(level, progress, op);
            }
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                ConstructionEntityProjectionIndex.removeOperation(level, progress.jobId(), op.id());
            }
            op.setWorldSatisfied(false);
            if (op.isOpen() || op.status() == ConstructionBuildOp.Status.DELIVERED) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
                op.setLeaseAllay(null);
                op.setApproach(null);
            }
        }
        progress.setProjectionIndexReady(false);
        return !resetIds.isEmpty();
    }

    /** 精确目标到达后,旧的拆除租约也必须释放,否则拆除悦灵会继续执行过期操作。 */
    private static boolean cancelDemolitionsAt(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        BlockPos operationPos
    ) {
        Set<Integer> cancelledIds = new HashSet<>();
        Set<UUID> cancelledAllays = new HashSet<>();
        boolean changed = false;
        for (ConstructionBuildOp op : List.copyOf(progress.operationsAt(operationPos))) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH
                || op.shell()
                || !op.isOpen()) {
                continue;
            }
            cancelledIds.add(op.id());
            op.leaseAllay().ifPresent(cancelledAllays::add);
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            op.setLeaseAllay(null);
            op.setApproach(null);
            changed = true;
        }
        if (!cancelledAllays.isEmpty()) {
            for (WorkingAllayEntity worker : loadedWorkers(level)) {
                if (!cancelledAllays.contains(worker.getUUID())
                    || worker.assignedJobId().filter(job.jobId()::equals).isEmpty()) {
                    continue;
                }
                worker.navigator().clear();
                worker.clearAssignment(false);
                worker.setActionState((byte) 0);
                worker.setWaitReason(ConstructionWaitReason.NONE);
                ConstructionTraffic.release(level, worker.getUUID());
            }
        }
        // 失联悦灵的租约已清掉,下次加载时按操作状态重新领取,不会继续执行旧目标
        for (ConstructionBuildOp op : progress.operations()) {
            if (cancelledIds.contains(op.id())) op.setLeaseAllay(null);
        }
        return changed;
    }

    private static boolean satisfyExternalPlace(
        ServerLevel level,
        ConstructionJobProgress progress,
        BlockPos pos,
        BlockState current
    ) {
        boolean changed = false;
        for (ConstructionBuildOp op : List.copyOf(progress.operationsAt(pos))) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.ATTACHED
                || !op.target().equals(current)
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            if (op.leaseAllay().isPresent() || progress.hasCarriedMaterial(op.id())) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) {
                returnRuntimeMaterial(level, progress, op);
            }
            ConstructionProjectionIndex.remove(level, progress.jobId(), op.pos());
            op.setWorldSatisfied(true);
            op.setStatus(ConstructionBuildOp.Status.DELIVERED);
            op.setLeaseAllay(null);
            progress.markOperationDelivered(op.id());
            changed = true;
        }
        return changed;
    }

    private static void returnRuntimeMaterial(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        if (job == null || ConstructionCommitService.hasCommittedMaterial(progress, op)) return;
        returnOperationLedger(level, job, progress, op);
    }

    /** 外部方块插入时先结清建设悦灵的在途材料,再让拆除悦灵接管任务。 */
    private static void interruptBuildWorkers(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        returnInTransit(server, level, job, progress);
        for (WorkingAllayEntity worker : loadedWorkers(level)) {
            if (worker.assignedJobId().filter(job.jobId()::equals).isEmpty()) continue;
            ConstructionBuildOp op = progress.operation(worker.taskOpId());
            if (op == null || op.kind() == ConstructionBuildOp.Kind.DEMOLISH) continue;
            returnWorkerCarry(server, level, job, progress, worker);
            worker.navigator().clear();
            worker.clearAssignment(false);
            worker.setActionState((byte) 0);
            worker.setWaitReason(ConstructionWaitReason.NONE);
            ConstructionTraffic.release(level, worker.getUUID());
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH || !op.isOpen()) continue;
            if (op.leaseAllay().isEmpty()) continue;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setLeaseAllay(null);
            op.setApproach(null);
        }
    }

    private static void skipPlaceAtIncludingDelivered(
        ServerLevel level,
        ConstructionJobProgress progress,
        BlockPos pos
    ) {
        for (ConstructionBuildOp op : List.copyOf(progress.operationsAt(pos))) {
            if (op.kind() != ConstructionBuildOp.Kind.PLACE
                && op.kind() != ConstructionBuildOp.Kind.ATTACHED
                && op.kind() != ConstructionBuildOp.Kind.CONTENT
                && op.kind() != ConstructionBuildOp.Kind.FLUID
                && op.kind() != ConstructionBuildOp.Kind.ENTITY
                && op.kind() != ConstructionBuildOp.Kind.DECORATE) {
                continue;
            }
            if (op.status() == ConstructionBuildOp.Status.SKIPPED) continue;
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) {
                returnRuntimeMaterial(level, progress, op);
            }
            ConstructionProjectionIndex.remove(level, progress.jobId(), op.pos());
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                ConstructionEntityProjectionIndex.removeOperation(level, progress.jobId(), op.id());
            }
            op.setWorldSatisfied(false);
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            op.setLeaseAllay(null);
            op.setApproach(null);
            progress.setIncomplete(true);
        }
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
        if (job.state() != ConstructionJob.STATE_WAITING_PERMISSION
            && !enterIfCoordinatorDenied(level, progress)) {
            return;
        }
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
        if (job.state() == ConstructionJob.STATE_WAITING_OBSERVER) {
            tickWaitingObserver(server, level, job, progress);
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_DEMOLITION) {
            // 缺拆除工时不能在这里直接返回:远程休息室借调必须先有机会把拆除工送来
            tryLaunchForJob(level, job, progress);
            if (hasAvailableDemolitionAllay(level, job, progress)) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                setState(server, job, ConstructionJob.STATE_DEMOLISHING);
            }
            return;
        }
        if (job.state() == ConstructionJob.STATE_WAITING_PERMISSION) {
            if (permissionRestored(level, job, progress)) {
                progress.setWaitReason(ConstructionWaitReason.NONE);
                setState(server, job, resumeAfterPermission(progress));
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
        // 提交只写已交付操作,不需要新的作业面;施工阶段则必须先有实体刻窗口才谈得上派发
        if (enterIfObserverMissing(server, level, job, progress, job.state())) return;
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
        if (!ensureIndex(level, progress)) return;
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
        ConstructionWaitReason reason = currentWait(progress, level.getGameTime());
        progress.setWaitReason(reason);
        reportOnce(server, job, progress, reason);
        store.markDirty();
    }

    /**
     * 施工阶段的观察窗口闸。规划每刻都要跑:观察者飞行、玩家路过、他人加载器出现都会改变覆盖,
     * 停在等待观察者的任务同样得靠它才能重新算出还缺几个窗口。
     *
     * @param phase 用于判定相关操作的阶段;等待观察者时传入将要恢复的阶段而不是等待状态本身
     * @return true 表示这一刻已经无窗口可用,调用方应当停下
     */
    private static boolean enterIfObserverMissing(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        byte phase
    ) {
        ObservationCoverageService.tick(server, level, job, progress, phase);
        if (!ObservationCoverageService.isWaitingObserver(server, job)) return false;
        // 观察者出库和远程借调都得先有机会推进,否则停在等待状态就再也没人去建立窗口
        tryLaunchForJob(level, job, progress);
        if (job.state() != ConstructionJob.STATE_WAITING_OBSERVER) {
            setWait(server, job, progress, ConstructionWaitReason.OBSERVER);
            setState(server, job, ConstructionJob.STATE_WAITING_OBSERVER);
        }
        return true;
    }

    /**
     * 等待观察者:按恢复目标阶段继续规划,窗口一建立就回到该阶段。
     * 恢复阶段必须由进度而不是等待状态推导,否则相关操作集合会与真正要干的活错位。
     */
    private static void tickWaitingObserver(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        byte phase = resumePhase(level, job, progress);
        if (enterIfObserverMissing(server, level, job, progress, phase)) return;
        progress.setWaitReason(ConstructionWaitReason.NONE);
        setState(server, job, phase);
        ConstructionJobStore.get(server).markDirty();
    }

    public static void plan(ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        try {
            ConstructionBlueprintService.validatePlacement(level, job);
            if (progress.planned() && !progress.operations().isEmpty()) {
                if (!permissionRestored(level, job, progress)) {
                    enterPermissionWait(level, job, progress);
                    return;
                }
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
            // 原版结构通常省略空气条目,但实体的所在格仍是蓝图声明范围的一部分
            for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
                BlockPos entityPos = placement.worldOf(entry.blockPos());
                worldTargets.putIfAbsent(entityPos.asLong(), Blocks.AIR.defaultBlockState());
            }
            progress.setDeclaredTargets(worldTargets);
            if (!progress.clearanceStrategyRecorded()) {
                progress.setClearanceStrategy(ownerClearanceStrategy(level, progress));
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
                if (IgnitionBuildAdapter.supports(target)) {
                    IgnitionBuildAdapter.plan(progress, worldPos, target, worldTargets);
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
            for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
                declared.add(placement.worldOf(entry.blockPos()));
            }
            incomplete |= linkParents(progress);
            incomplete |= extractBlockEntityContents(progress, blockEntities, level.registryAccess());
            incomplete |= extractBlockEntityFluids(progress, blockEntities, level.registryAccess());
            incomplete |= planEntities(level, snapshot, placement, progress);
            FluidSealPlanner.plan(level, declared, progress);
            DemolitionPlanner.plan(level, declared, demolishableCells(level, progress, declared, worldTargets), progress);
            skipOrphanedChildren(progress);
            ConstructionAssembler.assignBuildOrder(level, progress);
            ServerPlayer owner = ownerInLevel(level.getServer(), job, level);
            if (owner != null) {
                FluidSealPlanner.applyFill(progress, FluidSealFill.choose(owner, progress));
            }
            progress.setPlanned(true);
            progress.setIncomplete(incomplete || progress.incomplete());
            if (!permissionRestored(level, job, progress)) {
                enterPermissionWait(level, job, progress);
                return;
            }
            setState(level.getServer(), job, nextPhase(progress));
        } catch (ConstructionBlueprintException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Construction planning failed: {}", exception.reason());
            setState(level.getServer(), job, ConstructionJob.STATE_FAILED);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Construction planning failed unexpectedly", exception);
            setState(level.getServer(), job, ConstructionJob.STATE_FAILED);
        }
    }

    /**
     * 协调室选择保留空白时,只允许拆除与蓝图实体格重合的世界方块;
     * 蓝图里的空气格原样留下,未认领协调室时沿用整片清场。
     */
    private static Set<BlockPos> demolishableCells(
        ServerLevel level,
        ConstructionJobProgress progress,
        Set<BlockPos> declared,
        Map<Long, BlockState> worldTargets
    ) {
        if (progress.clearanceStrategy() != AllayClearanceStrategy.KEEP_BLANK) return declared;
        Set<BlockPos> occupied = new HashSet<>();
        for (BlockPos pos : declared) {
            BlockState target = worldTargets.get(pos.asLong());
            if (target == null || (target.isAir() && !hasEntityTargetAt(progress, pos))) continue;
            occupied.add(pos);
        }
        return occupied;
    }

    private static AllayClearanceStrategy ownerClearanceStrategy(
        ServerLevel level,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            return lounge.clearanceStrategy();
        }
        return AllayClearanceStrategy.CLEAR_AREA;
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
        ObservationCoverageService.releaseJob(server, job.jobId());
        ConstructionTransferService.onJobEnded(server, job.jobId());
    }

    public static boolean cancel(MinecraftServer server, ConstructionJob job) {
        ServerLevel level = server.getLevel(job.dimension());
        ConstructionJobStore store = ConstructionJobStore.get(server);
        ConstructionJobProgress progress = store.get(job.jobId());
        if (progress != null && level != null) {
            progress.clearDebrisLeases();
            Set<Integer> committedOperations = new HashSet<>();
            Set<Integer> rolledBackOperations = new HashSet<>();
            prepareCancelledDelivered(level, job, progress, committedOperations, rolledBackOperations);
            // 取消是清理操作，不能因为权限在提交前失效而把任务和投影遗留在世界里。
            if (!ConstructionCommitService.commitDelivered(level, progress)) {
                prepareCancelledDelivered(level, job, progress, committedOperations, rolledBackOperations);
                if (!ConstructionCommitService.commitDelivered(level, progress)) {
                    abandonCancelledDelivered(
                        level,
                        job,
                        progress,
                        committedOperations,
                        rolledBackOperations
                    );
                } else {
                    rememberCommittedDelivered(progress, committedOperations);
                }
            } else {
                rememberCommittedDelivered(progress, committedOperations);
            }
            settleCancelledCarries(
                server,
                level,
                job,
                progress,
                committedOperations,
                rolledBackOperations
            );
            releaseLeases(progress);
            ConstructionProjectionIndex.clearJob(level, job.jobId());
            ConstructionEntityProjectionIndex.clearJob(level, job.jobId());
            clearCoordinatorDisk(level, progress, job.jobId());
        } else if (level != null) {
            ConstructionProjectionIndex.clearJob(level, job.jobId());
            ConstructionEntityProjectionIndex.clearJob(level, job.jobId());
        }
        ConstructionTransferService.onJobEnded(server, job.jobId());
        clearOnlineDisks(server, job.jobId());
        store.remove(job.jobId());
        ConstructionJobIndex.get(server).remove(job.jobId());
        BlueprintJobSync.syncRemove(server, job.jobId());
        ObservationCoverageService.releaseJob(server, job.jobId());
        return true;
    }

    /**
     * 一次性把剩余提交跑完再收尾。玩法路径走 {@link #tickJob} 的 COMMITTING 分支按 tick 预算分区写入,
     * 这条同步入口只服务"必须当场结束"的调用方(取消收尾与 GameTest 的终态断言),不是玩家入口。
     */
    public static boolean finish(MinecraftServer server, ServerLevel level, ConstructionJob job, ConstructionJobProgress progress) {
        if (!ConstructionCommitService.commitDelivered(level, progress)) return false;
        return complete(server, level, job, progress);
    }

    private static boolean complete(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!smashRemainingShells(level, progress)) return false;
        if (!ConstructionCommitService.restoreWirePorts(level, progress)) return false;
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
        }
        ConstructionTransferService.onJobEnded(server, job.jobId());
        clearOnlineDisks(server, job.jobId());
        ObservationCoverageService.releaseJob(server, job.jobId());
        return true;
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
        if (worker != null && !worker.toolDefinition().hasCapability(AllayCapability.SEAL_FLUID)) return null;
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
            if (!isAssignable(level, progress, op.pos())) continue;
            if (!enterIfDenied(level, progress, op)) return null;
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
        for (ConstructionBuildOp op : progress.openOperations(ConstructionBuildOp.Kind.SEAL)) {
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
            if (!isAssignable(level, progress, op.pos())) continue;
            if (!enterIfDenied(level, progress, op)) return null;
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
        AssignmentScan scan = worker == null ? AssignmentScan.BUILD_ANY
            : worker.toolDefinition().hasCapability(AllayCapability.IGNITE) ? AssignmentScan.BUILD_IGNITION
            : !canPlaceLongReach(worker) ? AssignmentScan.BUILD_SHORT : AssignmentScan.BUILD_LONG;
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
                || op.isDeferred(level.getGameTime())
                || op.leaseAllay().isPresent()
                || progress.hasCarriedMaterial(op.id())
                || !isAssignable(level, progress, op.pos())
                || !ConstructionPlacementLimits.canDeliver(worker, op)
                || !level.getBlockState(op.pos()).isAir()) {
                continue;
            }
            if (!enterIfDenied(level, progress, op)) break;
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
        List<ConstructionBuildOp> frontier = worker == null
            ? List.of()
            : nearestFrontier(candidates, worker, level.getGameTime());
        for (ConstructionBuildOp op : frontier) {
            AssignVerdict verdict = evaluateAssignable(level, progress, worker, carriedBy, op, overlay, view);
            if (verdict == AssignVerdict.DENIED) return null;
            if (verdict == AssignVerdict.ACCEPT) return op;
        }
        for (ConstructionBuildOp op : candidates) {
            if (frontier.contains(op)) continue;
            AssignVerdict verdict = evaluateAssignable(level, progress, worker, carriedBy, op, overlay, view);
            if (verdict == AssignVerdict.DENIED) return null;
            if (verdict == AssignVerdict.ACCEPT) return op;
        }
        return null;
    }

    /**
     * 取队首同一层的待办目标,按离悦灵的距离重排。剥离顺序在一层之内只是逐行扫描,
     * 成环结构会让悦灵放一块就飞到对面再飞回来;同层目标之间不存在支撑先后,
     * 拓扑安全仍由每个候选各自的接近位与封闭复核把关,因此就近领取安全且能绕着圈施工。
     * 跨层不重排,以保住"先铺下层、再压上层"的剥离顺序。
     */
    private static List<ConstructionBuildOp> nearestFrontier(
        List<ConstructionBuildOp> candidates,
        WorkingAllayEntity worker,
        long gameTime
    ) {
        List<ConstructionBuildOp> frontier = new ArrayList<>();
        int layer = 0;
        for (ConstructionBuildOp op : candidates) {
            if (op.leaseAllay().isPresent() || !isFrontierOpen(op) || op.isDeferred(gameTime)) continue;
            if (frontier.isEmpty()) {
                layer = op.pos().getY();
            } else if (op.pos().getY() != layer) {
                break;
            }
            frontier.add(op);
            if (frontier.size() >= FRONTIER_WINDOW) break;
        }
        if (frontier.size() < 2) return List.of();
        Vec3 from = worker.position();
        frontier.sort(Comparator
            .comparingDouble((ConstructionBuildOp op) -> from.distanceToSqr(Vec3.atCenterOf(op.pos())))
            .thenComparingInt(ConstructionBuildOp::order)
            .thenComparingInt(ConstructionBuildOp::id));
        return frontier;
    }

    private static boolean isFrontierOpen(ConstructionBuildOp op) {
        return op.status() == ConstructionBuildOp.Status.PENDING
            || op.status() == ConstructionBuildOp.Status.WAITING_WORLD
            || op.status() == ConstructionBuildOp.Status.WAITING_OCCUPIED;
    }

    /** {@link AssignVerdict#DENIED} 表示权限被拒,整轮派发必须中止而不是换个目标继续。 */
    private enum AssignVerdict {
        REJECT,
        DENIED,
        ACCEPT
    }

    private static AssignVerdict evaluateAssignable(
        ServerLevel level,
        ConstructionJobProgress progress,
        @Nullable WorkingAllayEntity worker,
        @Nullable UUID carriedBy,
        ConstructionBuildOp op,
        Map<Long, BlockState> overlay,
        ConstructionOverlayView view
    ) {
        if (worker != null && !IgnitionBuildAdapter.canDeliver(worker, op)) return AssignVerdict.REJECT;
        if (op.kind() != ConstructionBuildOp.Kind.PLACE
            && op.kind() != ConstructionBuildOp.Kind.CONTENT
            && op.kind() != ConstructionBuildOp.Kind.FLUID
            && op.kind() != ConstructionBuildOp.Kind.ENTITY
            && op.kind() != ConstructionBuildOp.Kind.DECORATE) {
            return AssignVerdict.REJECT;
        }
        if (worker != null
            && (op.kind() == ConstructionBuildOp.Kind.PLACE || op.kind() == ConstructionBuildOp.Kind.ENTITY)
            && !ConstructionPlacementLimits.canDeliver(worker, op)) {
            return AssignVerdict.REJECT;
        }
        if (op.leaseAllay().isPresent()
            || op.status() == ConstructionBuildOp.Status.LEASED
            || op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.status() == ConstructionBuildOp.Status.SKIPPED) {
            return AssignVerdict.REJECT;
        }
        if (op.isDeferred(level.getGameTime())) return AssignVerdict.REJECT;
        if (carriedBy == null) {
            if (progress.hasCarriedMaterial(op.id())) return AssignVerdict.REJECT;
        } else if (!progress.isCarriedBy(carriedBy, op.id())) {
            return AssignVerdict.REJECT;
        }
        if (!isAssignable(level, progress, op.pos())) return AssignVerdict.REJECT;
        if (!enterIfDenied(level, progress, op)) return AssignVerdict.DENIED;
        if (op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.DECORATE) {
            ConstructionBuildOp parent = progress.parentOf(op);
            if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) {
                return AssignVerdict.REJECT;
            }
            BlockPos approach = chooseApproach(level, progress, op, worker);
            if (approach == null) return AssignVerdict.REJECT;
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            return AssignVerdict.ACCEPT;
        }
        if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
            if (hasOpenSupportPlace(progress, op.pos())) {
                return AssignVerdict.REJECT;
            }
            BlockPos approach = chooseApproach(level, progress, op, worker);
            if (approach == null) return AssignVerdict.REJECT;
            if (entityOccupied(level, op, null)) {
                op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
                return AssignVerdict.REJECT;
            }
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setApproach(approach);
            return AssignVerdict.ACCEPT;
        }
        if (!level.getBlockState(op.pos()).isAir()) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_WORLD);
            return AssignVerdict.REJECT;
        }
        BlockPos approach = chooseApproach(level, progress, op, worker);
        if (approach == null) return AssignVerdict.REJECT;
        if (groupOccupied(level, progress, op, overlay, view, null)) {
            op.setStatus(ConstructionBuildOp.Status.WAITING_OCCUPIED);
            return AssignVerdict.REJECT;
        }
        op.setStatus(ConstructionBuildOp.Status.PENDING);
        op.setApproach(approach);
        return AssignVerdict.ACCEPT;
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
        if (!enterIfDenied(level, progress, op)) return false;
        if (ignore instanceof WorkingAllayEntity worker && !IgnitionBuildAdapter.canDeliver(worker, op)) return false;
        if (IgnitionBuildAdapter.supports(op.target())
            && (op.status() == ConstructionBuildOp.Status.DELIVERED
                || !(ignore instanceof WorkingAllayEntity worker) || !IgnitionBuildAdapter.canDeliver(worker, op))) {
            return false;
        }
        if (op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.DECORATE) {
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
        refreshSignDecoration(level, progress, op);
        deliverAttached(level, progress, op, overlay);
        ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
        if (IgnitionBuildAdapter.supports(op.target()) && ignore instanceof WorkingAllayEntity worker) {
            IgnitionBuildAdapter.consumeUse(level, worker, op.pos());
        }
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
        if (op.kind() == ConstructionBuildOp.Kind.DECORATE) {
            refreshSignDecoration(level, progress, parent);
        }
        return true;
    }

    /**
     * 已交付假告示牌先空白,再随每条 DECORATE 交付逐项显示。
     * 只有 DELIVERED 才清掉掩码位:跳过的加工项永远不会写进最终方块实体,
     * 假方块也必须一直空着,否则会预告一个提交后并不存在的效果。
     */
    private static void refreshSignDecoration(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp parent
    ) {
        int pending = 0;
        for (ConstructionBuildOp child : progress.childrenOf(parent)) {
            if (child.kind() != ConstructionBuildOp.Kind.DECORATE) continue;
            if (child.status() == ConstructionBuildOp.Status.DELIVERED) continue;
            pending |= SignDecorationAdapter.maskOf(child.slot());
        }
        ConstructionProjectionIndex.setPendingDecorations(level, progress.jobId(), parent.pos(), pending);
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
        if (ignore instanceof WorkingAllayEntity worker
            && !worker.toolDefinition().hasCapability(AllayCapability.SEAL_FLUID)) return false;
        if (op.kind() != ConstructionBuildOp.Kind.SEAL) return false;
        if (!enterIfDenied(level, progress, op)) return false;
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
        withoutWorldChangeObservation(() -> {
            level.setBlockAndUpdate(op.pos(), fill);
            return null;
        });
        markOpDone(level, progress, op);
        return true;
    }

    public static boolean tryDemolish(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        return tryDemolish(level, progress, op, BlockMiningEffect.NORMAL);
    }

    public static boolean tryDemolish(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        BlockMiningEffect miningEffect
    ) {
        if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH) return false;
        if (!enterIfDenied(level, progress, op)) return false;
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
        if (!StonecutterSmashAdapter.smash(
            level,
            smashPos,
            progress.jobId(),
            op.id(),
            progress,
            miningEffect
        )) {
            return false;
        }
        DemolitionPlanner.clearAttachedResidue(level, smashPos);
        markOpDone(level, progress, op);
        return true;
    }

    public static boolean extractMaterial(Player player, ConstructionJobProgress progress, ConstructionBuildOp op, UUID allayId) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        ConstructionJob job = ConstructionJobIndex.get(serverPlayer.server).job(progress.jobId());
        if (job == null
            || !job.dimension().equals(level.dimension())
            || progress.operation(op.id()) != op
            || !ConstructionPermission.canManageJob(serverPlayer, job)
            || !ConstructionPermission.canModify(level, player.blockPosition(), job.owner())) {
            return false;
        }
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
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        Entity workerEntity = level.getEntity(allayId);
        UUID workerOwner = workerEntity instanceof WorkingAllayEntity worker
            ? worker.getOwner().orElse(null)
            : null;
        if (job == null
            || !job.dimension().equals(level.dimension())
            || progress.operation(op.id()) != op
            || workerOwner == null
            || workerEntity instanceof WorkingAllayEntity worker
                && op.needsMaterial() && !worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL)
            || !ConstructionPermission.canManageWorker(level.getServer(), workerOwner, job.owner())) {
            return false;
        }
        if (op.kind() == ConstructionBuildOp.Kind.SEAL
            && (op.material().isEmpty() || FluidSealFill.stateOf(op.material()) == null)) {
            return false;
        }
        if (!op.needsMaterial()) return true;
        BlockPos loungePos = progress.coordinatorLounge();
        if (loungePos == null) return false;
        if (!enterIfCoordinatorDenied(level, progress)) return false;
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, loungePos);
        if (!access.isAvailable()) return false;
        ItemStack taken = access.extract(op);
        if (taken.isEmpty()) return false;
        progress.addLedger(op.id(), taken, allayId);
        if (PlasticraftEntityBuildAdapters.returnsResin(taken) && op.returnStack().isEmpty()) {
            op.setReturnStack(PlasticraftEntityBuildAdapters.resinReturn(level));
        }
        if (level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.setPickupDisplay(taken, allayId);
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
                    || op.kind() == ConstructionBuildOp.Kind.ENTITY
                    || op.kind() == ConstructionBuildOp.Kind.DECORATE)
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

    /**
     * 反复确认飞不到的目标只做退避,绝不作废。只释放租约会被托管台账立刻重领同一操作、
     * 悦灵原地空转,所以这里把材料退回料源、把操作退回待办并压一段冷却:
     * 冷却期内派发轮空,别的位置照常施工;冷却结束后由任意悦灵重新挑接近位再试。
     * 未建成的位置永远留在施工阶段,不计入残缺,也不让任务提前收敛到完成。
     */
    public static void deferUnreachable(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op,
        WorkingAllayEntity worker
    ) {
        if (op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.status() == ConstructionBuildOp.Status.SKIPPED) {
            return;
        }
        op.deferUntil(level.getGameTime() + UNREACHABLE_RETRY_TICKS);
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        if (job != null) {
            // 材料留在悦灵手上会一直锁住这个位置,先按台账退回休息室或所有者,让别的悦灵能重新取料
            returnWorkerCarry(level.getServer(), level, job, progress, worker);
        }
        op.setLeaseAllay(null);
        op.setApproach(null);
        if (op.status() == ConstructionBuildOp.Status.LEASED) {
            op.setStatus(ConstructionBuildOp.Status.PENDING);
        }
        ConstructionJobStore.get(level).markDirty();
    }

    public static void onShortageStrategyChanged(ServerPlayer player, AllayShortageStrategy strategy) {
        ConstructionJob job = ConstructionJobIndex.get(player.server)
            .activeJobOf(player.server, player.getUUID())
            .orElse(null);
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

    /** 清场策略改动后同步已认领任务，避免任务仍沿用首次规划时的旧值。 */
    public static void onLoungeClearanceStrategyChanged(
        ServerLevel level,
        BlockPos loungePos,
        AllayClearanceStrategy strategy
    ) {
        AllayClearanceStrategy selected = strategy == null
            ? AllayClearanceStrategy.CLEAR_AREA
            : strategy;
        MinecraftServer server = level.getServer();
        ConstructionJobStore store = ConstructionJobStore.get(server);
        for (ConstructionJob job : ConstructionJobIndex.get(server).jobsIn(level)) {
            ConstructionJobProgress progress = store.get(job.jobId());
            if (progress == null
                || !loungePos.equals(progress.coordinatorLounge())) {
                continue;
            }
            progress.setClearanceStrategy(selected);
            if (!progress.planned()) {
                store.markDirty();
                continue;
            }
            boolean changed = reconcileClearanceDemolitions(level, job, progress, selected);
            ConstructionJob current = ConstructionJobIndex.get(server).job(job.jobId());
            if (current != null && current.isActive()) {
                if (selected == AllayClearanceStrategy.CLEAR_AREA
                    && progress.hasOpenDemolish()
                    && current.state() != ConstructionJob.STATE_DEMOLISHING
                    && current.state() != ConstructionJob.STATE_WAITING_DEMOLITION) {
                    interruptBuildWorkers(server, level, current, progress);
                    setState(server, current, ConstructionJob.STATE_DEMOLISHING);
                } else if (selected == AllayClearanceStrategy.KEEP_BLANK
                    && changed
                    && (current.state() == ConstructionJob.STATE_DEMOLISHING
                        || current.state() == ConstructionJob.STATE_WAITING_DEMOLITION)
                    && progress.allDemolishResolved()) {
                    setState(server, current, nextPhase(progress));
                }
            }
            store.markDirty();
        }
    }

    private static boolean reconcileClearanceDemolitions(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayClearanceStrategy strategy
    ) {
        Set<BlockPos> declared = new HashSet<>();
        for (long packed : progress.declaredTargets().keySet()) {
            declared.add(BlockPos.of(packed));
        }
        if (strategy == AllayClearanceStrategy.CLEAR_AREA) {
            Set<BlockPos> blankCells = new HashSet<>();
            for (BlockPos pos : declared) {
                if (isExplicitBlankCell(progress, pos)) blankCells.add(pos);
            }
            boolean changed = reactivateSkippedBlankDemolitions(level, progress, blankCells);
            int before = progress.operations().size();
            // 这里只补扫显式空白格;非空白格的运行时冲突已经由世界变更观察器追加,
            // 不能把已经满足的最终目标方块误当成需要重新拆除的旧方块。
            DemolitionPlanner.plan(level, declared, blankCells, progress);
            if (progress.operations().size() != before) changed = true;
            if (changed) {
                ConstructionAssembler.assignBuildOrder(level, progress);
            }
            return changed;
        }

        Set<UUID> cancelledAllays = new HashSet<>();
        boolean changed = false;
        for (ConstructionBuildOp op : List.copyOf(progress.operations())) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH
                || op.shell()
                || !isExplicitBlankCell(progress, op.pos())
                || op.status() == ConstructionBuildOp.Status.DELIVERED
                || op.status() == ConstructionBuildOp.Status.SKIPPED) {
                continue;
            }
            op.leaseAllay().ifPresent(cancelledAllays::add);
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            op.setLeaseAllay(null);
            op.setApproach(null);
            changed = true;
        }
        if (!cancelledAllays.isEmpty()) {
            for (WorkingAllayEntity worker : loadedWorkers(level)) {
                if (!cancelledAllays.contains(worker.getUUID())
                    || worker.assignedJobId().filter(job.jobId()::equals).isEmpty()) {
                    continue;
                }
                worker.navigator().clear();
                worker.clearAssignment(false);
                worker.setActionState((byte) 0);
                worker.setWaitReason(ConstructionWaitReason.NONE);
                ConstructionTraffic.release(level, worker.getUUID());
            }
        }
        return changed;
    }

    /** CLEAR_AREA 重新启用此前因 KEEP_BLANK 跳过、但世界中仍存在的空白格拆除。 */
    private static boolean reactivateSkippedBlankDemolitions(
        ServerLevel level,
        ConstructionJobProgress progress,
        Set<BlockPos> blankCells
    ) {
        boolean changed = false;
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH
                || op.shell()
                || op.status() != ConstructionBuildOp.Status.SKIPPED
                || !blankCells.contains(op.pos())) {
                continue;
            }
            BlockState current = level.getBlockState(op.pos());
            if (current.isAir()
                || FluidSealPlanner.isSealableFluid(current)
                || StonecutterSmashAdapter.isPermanentObstacle(level, op.pos(), current)) {
                continue;
            }
            op.setWorldSatisfied(false);
            op.setStatus(ConstructionBuildOp.Status.PENDING);
            op.setLeaseAllay(null);
            op.setApproach(null);
            changed = true;
        }
        return changed;
    }

    private static boolean isExplicitBlankCell(ConstructionJobProgress progress, BlockPos pos) {
        BlockState target = progress.declaredTarget(pos);
        return target != null && target.isAir() && !hasEntityTargetAt(progress, pos);
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
        // 先跑便宜的占位与触及判定,再谈防自封分析:洪泛是这条每刻复查里最贵的一步,
        // 接近位本来就被占住或已经够不到时不该为它付这个代价
        UUID except = worker == null ? null : worker.getUUID();
        if (!ConstructionWorkerSpace.fitsWorker(level, approach, except)) return false;
        AABB workerBox = ConstructionWorkerSpace.boxAt(approach);
        boolean inReach = false;
        for (BlockPos target : approachTargets(progress, op)) {
            if (workerBox.intersects(new AABB(target).inflate(REACH))) {
                inReach = true;
                break;
            }
        }
        if (!inReach) return false;
        if (op.kind() != ConstructionBuildOp.Kind.DEMOLISH) {
            ConstructionEnclosure.Analysis analysis = ConstructionEnclosure.analyze(level, progress, op, worker);
            if (analysis.wouldEnclose() || !analysis.approachSafe(approach)) return false;
        }
        return true;
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
            case BUILD_SHORT -> !isBuildAssignment(op) || ConstructionPlacementLimits.requiresLongReach(op)
                || IgnitionBuildAdapter.supports(op.target());
            case BUILD_LONG -> !isBuildAssignment(op) || IgnitionBuildAdapter.supports(op.target());
            case BUILD_IGNITION -> op.kind() != ConstructionBuildOp.Kind.PLACE || !IgnitionBuildAdapter.supports(op.target());
            case BUILD_ANY -> !isBuildAssignment(op);
            case SEAL -> op.kind() != ConstructionBuildOp.Kind.SEAL;
            case DEMOLISH -> op.kind() != ConstructionBuildOp.Kind.DEMOLISH || op.shell();
        };
    }

    private static boolean isBuildAssignment(ConstructionBuildOp op) {
        return op.kind() == ConstructionBuildOp.Kind.PLACE
            || op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.ENTITY
            || op.kind() == ConstructionBuildOp.Kind.DECORATE;
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
        BUILD_IGNITION,
        BUILD_ANY,
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
            if (!enterIfDenied(level, progress, op)) return;
            BlockState stored = OrdinaryBlockAdapter.projectionState(op.target(), op.pos(), overlay);
            if (ConstructionProjectionIndex.tryDeliver(level, progress.jobId(), op.pos(), stored, overlay)) {
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
            }
        }
    }

    private static boolean ensureIndex(ServerLevel level, ConstructionJobProgress progress) {
        if (progress.projectionIndexReady()) return true;
        Map<Long, BlockState> overlay = progress.overlayStates();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !op.writesProjection()) continue;
            if (ConstructionProjectionIndex.isOwnedBy(level, op.pos(), progress.jobId())) continue;
            if (!enterIfDenied(level, progress, op)) return false;
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
        return true;
    }

    private static void refreshWorldWaits(ServerLevel level, ConstructionJobProgress progress) {
        // 只有等待世界让位的 PLACE 需要复查,按索引取这一小撮,不再每刻复制整份操作表
        for (ConstructionBuildOp op : progress.waitingWorldPlaces()) {
            if (op.status() != ConstructionBuildOp.Status.WAITING_WORLD) continue;
            // 世界等待复查同样要读方块状态,未加载区块只能等窗口推进过去再复查
            if (!ObservationCoverageService.isWorkable(level, op.pos())) continue;
            BlockState current = level.getBlockState(op.pos());
            if (current.isAir()) {
                op.setStatus(ConstructionBuildOp.Status.PENDING);
            } else if (current.equals(op.target())
                && op.leaseAllay().isEmpty()
                && !progress.hasCarriedMaterial(op.id())) {
                ConstructionProjectionIndex.remove(level, progress.jobId(), op.pos());
                op.setWorldSatisfied(true);
                op.setStatus(ConstructionBuildOp.Status.DELIVERED);
                progress.markOperationDelivered(op.id());
            }
        }
    }

    private static ConstructionWaitReason currentWait(ConstructionJobProgress progress, long gameTime) {
        if (progress.hasWaitingOccupied()) return ConstructionWaitReason.OCCUPIED;
        if (progress.hasWaitingWorld() && !progress.hasOpenPlace()) return ConstructionWaitReason.WORLD;
        if (progress.stalledByUnreachable(gameTime)) return ConstructionWaitReason.UNREACHABLE;
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
        for (WorkingAllayEntity drone : loadedWorkers(level)) {
            if (!drone.isAlive()) continue;
            boolean assigned = drone.assignedJobId().filter(job.jobId()::equals).isPresent();
            boolean carrying = !progress.carriedEntries(drone.getUUID()).isEmpty();
            if (!assigned && !carrying) continue;
            loaded.add(drone);
        }
        Set<UUID> holding = new HashSet<>();
        for (WorkingAllayEntity drone : loaded) {
            boolean assigned = drone.assignedJobId().filter(job.jobId()::equals).isPresent();
            if (assigned) {
                drone.navigator().clear();
                ConstructionTraffic.release(level, drone.getUUID());
            }
            if (drone.hostedCarry().isEmpty()) {
                if (assigned) drone.clearAssignment(false);
            } else {
                if (canWorkerAccessJob(drone, level, job)) {
                    holding.add(drone.getUUID());
                } else {
                    returnWorkerCarry(server, level, job, progress, drone);
                    if (assigned) drone.clearAssignment(false);
                }
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

    /** 取消时按实体实物和全部任务台账结清，不能把同一悦灵为其他任务携带的物品一并返还。 */
    private static void settleCancelledCarries(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        Set<Integer> committedOperations,
        Set<Integer> rolledBackOperations
    ) {
        Set<UUID> seenWorkers = new HashSet<>();
        Map<Integer, Integer> returnedOutputs = new HashMap<>();
        for (WorkingAllayEntity worker : loadedWorkers(level)) {
            if (!worker.isAlive()) continue;
            boolean assigned = worker.assignedJobId().filter(job.jobId()::equals).isPresent();
            boolean hasLedger = progress.ledger().stream().anyMatch(entry ->
                worker.getUUID().equals(entry.allayId())
                    && (entry.state() != ConstructionLedgerEntry.State.RETURNED
                        || committedOperations.contains(entry.operationId())
                        || rolledBackOperations.contains(entry.operationId()))
            );
            if (!assigned && !hasLedger) continue;
            seenWorkers.add(worker.getUUID());
            settleCancelledWorkerCarry(
                server,
                level,
                job,
                progress,
                worker,
                committedOperations,
                rolledBackOperations,
                returnedOutputs,
                assigned
            );
            if (assigned) {
                worker.navigator().clear();
                ConstructionTraffic.release(level, worker.getUUID());
                worker.clearAssignment(false);
                worker.setActionState((byte) 0);
                worker.setWaitReason(ConstructionWaitReason.NONE);
            }
        }

        ServerPlayer owner = findOwner(server, level, job.owner());
        for (ConstructionLedgerEntry entry : List.copyOf(progress.ledger())) {
            if (entry.state() != ConstructionLedgerEntry.State.CARRIED) continue;
            if (entry.allayId() != null && seenWorkers.contains(entry.allayId())) {
                progress.markCarryReturned(entry);
                continue;
            }
            giveOrDrop(owner, level, job, progress, entry.stack().copy());
            progress.markCarryReturned(entry);
        }
        for (ConstructionBuildOp op : progress.operations()) {
            if (!committedOperations.contains(op.id()) || op.returnStack().isEmpty()) continue;
            List<ConstructionLedgerEntry> delivered = progress.ledger().stream()
                .filter(entry -> entry.operationId() == op.id())
                .filter(entry -> entry.state() == ConstructionLedgerEntry.State.DELIVERED)
                .toList();
            if (delivered.isEmpty()) continue;
            int returned = returnedOutputs.getOrDefault(op.id(), 0);
            boolean hasLoadedCarrier = delivered.stream()
                .map(ConstructionLedgerEntry::allayId)
                .anyMatch(id -> id != null && seenWorkers.contains(id));
            if (!hasLoadedCarrier && returned < op.returnStack().getCount()) {
                giveOrDrop(
                    owner,
                    level,
                    job,
                    progress,
                    op.returnStack().copyWithCount(op.returnStack().getCount() - returned)
                );
            }
            progress.markDeliveredReturned(op.id());
        }
        if (progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            lounge.clearPickupDisplays();
        }
        ConstructionJobStore.get(server).markDirty();
    }

    /** 按台账物品与实体实际携带物做一次差额结算，防止任务号清空后重复返还。 */
    private static void returnWorkerCarry(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker
    ) {
        ItemStack physical = worker.hostedCarry().copy();
        int reservedForOtherJobs = Math.min(
            physical.getCount(),
            otherTaskClaimCount(server, progress, worker.getUUID(), physical)
        );
        int available = Math.max(0, physical.getCount() - reservedForOtherJobs);
        ServerPlayer owner = findOwner(server, level, job.owner());
        List<ConstructionLedgerEntry> entries = progress.carriedEntries(worker.getUUID());
        boolean assigned = worker.assignedJobId().filter(job.jobId()::equals).isPresent();
        if (!assigned && !entries.isEmpty()) {
            // 旧版本在无租约时会把同一批材料落在悦灵脚边;先回收入库再结算台账,避免再次供料时复制。
            returnNearbyOrphanDrops(level, job, progress, worker, entries);
        }
        for (ConstructionLedgerEntry entry : entries) {
            ItemStack accounted = entry.stack().copy();
            int matching = matchingCount(physical.copyWithCount(available), accounted);
            if (matching > 0) {
                ItemStack returned = accounted.copyWithCount(matching);
                giveOrDrop(owner, level, job, progress, returned);
                available -= matching;
            }
            progress.markCarryReturned(entry);
            resetReturnedOperation(progress, entry.operationId());
        }
        Set<Integer> returnedOperations = new HashSet<>();
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (!worker.getUUID().equals(entry.allayId())
                || entry.state() != ConstructionLedgerEntry.State.DELIVERED
                || !returnedOperations.add(entry.operationId())) {
                continue;
            }
            ConstructionBuildOp op = progress.operation(entry.operationId());
            if (op == null
                || op.returnStack().isEmpty()
                || available < op.returnStack().getCount()
                || !ItemStack.isSameItemSameComponents(physical, op.returnStack())) {
                continue;
            }
            giveOrDrop(owner, level, job, progress, op.returnStack().copy());
            available -= op.returnStack().getCount();
            progress.markDeliveredReturned(op.id());
        }
        if ((assigned || !entries.isEmpty()) && available > 0) {
            giveOrDrop(owner, level, job, progress, physical.copyWithCount(available));
            available = 0;
        }
        int retained = Math.min(physical.getCount(), reservedForOtherJobs + available);
        worker.setHostedCarry(retained == 0 ? ItemStack.EMPTY : physical.copyWithCount(retained));
        if (!entries.isEmpty()) ConstructionJobStore.get(server).markDirty();
    }

    private static void resetReturnedOperation(ConstructionJobProgress progress, int operationId) {
        if (progress.hasCarriedMaterial(operationId)) return;
        ConstructionBuildOp op = progress.operation(operationId);
        if (op == null || op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.status() == ConstructionBuildOp.Status.SKIPPED) {
            return;
        }
        op.setLeaseAllay(null);
        op.setApproach(null);
        if (op.status() == ConstructionBuildOp.Status.LEASED) {
            op.setStatus(ConstructionBuildOp.Status.PENDING);
        }
    }

    private static void returnNearbyOrphanDrops(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker,
        List<ConstructionLedgerEntry> entries
    ) {
        AABB search = worker.getBoundingBox().inflate(1.25D);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, search)) {
            if (!entity.isAlive() || ConstructionDebris.isMarked(entity.getItem())) continue;
            if (entity.getAge() <= 100 && entity.hasPickUpDelay()) continue;
            ItemStack stack = entity.getItem();
            boolean matches = entries.stream().anyMatch(entry ->
                ItemStack.isSameItemSameComponents(stack, entry.stack()));
            if (!matches) continue;
            entity.discard();
            giveOrDrop(
                findOwner(level.getServer(), level, job.owner()),
                level,
                job,
                progress,
                stack.copy()
            );
        }
    }

    private static void settleCancelledWorkerCarry(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker,
        Set<Integer> committedOperations,
        Set<Integer> rolledBackOperations,
        Map<Integer, Integer> returnedOutputs,
        boolean assigned
    ) {
        ItemStack physical = worker.hostedCarry().copy();
        int reservedForOtherJobs = otherTaskClaimCount(server, progress, worker.getUUID(), physical);
        int available = physical.getCount() - reservedForOtherJobs;
        ServerPlayer owner = findOwner(server, level, job.owner());
        for (ConstructionLedgerEntry entry : progress.carriedEntries(worker.getUUID())) {
            int matching = matchingCount(physical.copyWithCount(available), entry.stack());
            if (matching > 0) {
                giveOrDrop(owner, level, job, progress, entry.stack().copyWithCount(matching));
                available -= matching;
            }
            progress.markCarryReturned(entry);
        }
        Set<Integer> seenOutputs = new HashSet<>();
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (!worker.getUUID().equals(entry.allayId())
                || entry.state() == ConstructionLedgerEntry.State.CARRIED
                || !seenOutputs.add(entry.operationId())) {
                continue;
            }
            ConstructionBuildOp op = progress.operation(entry.operationId());
            if (op == null || op.returnStack().isEmpty()) continue;
            int matching = matchingCount(physical.copyWithCount(available), op.returnStack());
            if (matching == 0) continue;
            if (committedOperations.contains(op.id())) {
                giveOrDrop(owner, level, job, progress, op.returnStack().copyWithCount(matching));
                returnedOutputs.merge(op.id(), matching, Integer::sum);
                available -= matching;
            } else if (rolledBackOperations.contains(op.id())) {
                available -= matching;
            }
        }
        if (assigned && available > 0) {
            giveOrDrop(owner, level, job, progress, physical.copyWithCount(available));
            available = 0;
        }
        int retained = reservedForOtherJobs + available;
        worker.setHostedCarry(retained == 0 ? ItemStack.EMPTY : physical.copyWithCount(retained));
    }

    private static int otherTaskClaimCount(
        MinecraftServer server,
        ConstructionJobProgress current,
        UUID workerId,
        ItemStack physical
    ) {
        if (physical.isEmpty()) return 0;
        int claimed = 0;
        for (ConstructionJobProgress progress : ConstructionJobStore.get(server).progresses()) {
            if (progress.jobId().equals(current.jobId())) continue;
            for (ConstructionLedgerEntry entry : progress.carriedEntries(workerId)) {
                if (ItemStack.isSameItemSameComponents(physical, entry.stack())) {
                    claimed = Math.min(physical.getCount(), claimed + entry.stack().getCount());
                }
            }
            Set<Integer> outputOperations = new HashSet<>();
            for (ConstructionLedgerEntry entry : progress.ledger()) {
                if (!workerId.equals(entry.allayId())
                    || entry.state() != ConstructionLedgerEntry.State.DELIVERED
                    || !outputOperations.add(entry.operationId())) {
                    continue;
                }
                ConstructionBuildOp op = progress.operation(entry.operationId());
                if (op != null && ItemStack.isSameItemSameComponents(physical, op.returnStack())) {
                    claimed = Math.min(physical.getCount(), claimed + op.returnStack().getCount());
                }
            }
            if (claimed == physical.getCount()) return claimed;
        }
        return claimed;
    }

    private static int matchingCount(ItemStack available, ItemStack expected) {
        if (available.isEmpty()
            || expected.isEmpty()
            || !ItemStack.isSameItemSameComponents(available, expected)) {
            return 0;
        }
        return Math.min(available.getCount(), expected.getCount());
    }

    /** 取消前过滤已失去世界权限的已交付操作，并移除对应投影和台账。 */
    private static void prepareCancelledDelivered(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        Set<Integer> committedOperations,
        Set<Integer> rolledBackOperations
    ) {
        Set<Integer> denied = new HashSet<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED || !requiresCurrentPermission(op)) continue;
            if (!hasOperationPermission(level, job, progress, op)) denied.add(op.id());
        }
        if (denied.isEmpty()) return;

        // 父方块失权时，内容、流体和实体子操作也必须一起撤销，不能把内容写入陌生区域。
        boolean changed;
        do {
            changed = false;
            for (ConstructionBuildOp op : progress.operations()) {
                if (op.status() != ConstructionBuildOp.Status.DELIVERED || op.parentId() < 0) continue;
                ConstructionBuildOp parent = progress.parentOf(op);
                if (parent != null && denied.contains(parent.id()) && denied.add(op.id())) changed = true;
            }
        } while (changed);

        ConstructionCommitService.adjustCursorForRemoval(progress, denied);
        for (ConstructionBuildOp op : progress.operations()) {
            if (!denied.contains(op.id())) continue;
            if (op.writesProjection()) {
                ConstructionProjectionIndex.remove(level, job.jobId(), op.pos());
            }
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                ConstructionEntityProjectionIndex.removeOperation(level, job.jobId(), op.id());
            }
            if (ConstructionCommitService.hasCommittedMaterial(progress, op)) {
                committedOperations.add(op.id());
            } else {
                rolledBackOperations.add(op.id());
                returnOperationLedger(level, job, progress, op);
            }
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            progress.setIncomplete(true);
        }
        Map<Long, BlockState> overlay = progress.overlayStates();
        for (ConstructionBuildOp op : progress.operations()) {
            if (!op.writesProjection() || op.status() != ConstructionBuildOp.Status.DELIVERED) continue;
            ConstructionProjectionIndex.refreshNeighbors(level, op.pos(), overlay);
        }
        ConstructionJobStore.get(level).markDirty();
    }

    private static boolean hasOperationPermission(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        for (BlockPos pos : operationPositions(level, progress, op)) {
            if (!ConstructionPermission.canModify(level, pos, job.owner())) return false;
        }
        return true;
    }

    private static void returnOperationLedger(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        ServerPlayer owner = findOwner(level.getServer(), level, job.owner());
        boolean delivered = false;
        for (ConstructionLedgerEntry entry : progress.unsettledEntries(op.id())) {
            if (entry.state() != ConstructionLedgerEntry.State.DELIVERED) continue;
            giveOrDrop(owner, level, job, progress, entry.stack().copy());
            delivered = true;
        }
        if (delivered) progress.markDeliveredReturned(op.id());
    }

    /** 提交仍因动态权限失败时的最后清理路径；取消不能留下投影或任务租约。 */
    private static void abandonCancelledDelivered(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        Set<Integer> committedOperations,
        Set<Integer> rolledBackOperations
    ) {
        Set<Integer> abandoned = new HashSet<>();
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) abandoned.add(op.id());
        }
        ConstructionCommitService.adjustCursorForRemoval(progress, abandoned);
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() != ConstructionBuildOp.Status.DELIVERED) continue;
            if (op.writesProjection()) ConstructionProjectionIndex.remove(level, job.jobId(), op.pos());
            if (op.kind() == ConstructionBuildOp.Kind.ENTITY) {
                ConstructionEntityProjectionIndex.removeOperation(level, job.jobId(), op.id());
            }
            if (ConstructionCommitService.hasCommittedMaterial(progress, op)) {
                committedOperations.add(op.id());
            } else {
                rolledBackOperations.add(op.id());
                returnOperationLedger(level, job, progress, op);
            }
            op.setStatus(ConstructionBuildOp.Status.SKIPPED);
            progress.setIncomplete(true);
        }
    }

    private static void rememberCommittedDelivered(
        ConstructionJobProgress progress,
        Set<Integer> committedOperations
    ) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (op.status() == ConstructionBuildOp.Status.DELIVERED) {
                committedOperations.add(op.id());
            }
        }
    }

    /**
     * 托管携带物是否登记在任一施工台账里。自由收集来的物品没有台账,
     * 只能走收集卸货路径;调用方是携带物变化时的一次性判定,不适合每刻调用。
     */
    public static boolean hasEscrowCarry(WorkingAllayEntity drone) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty() || !(drone.level() instanceof ServerLevel level)) return false;
        UUID droneId = drone.getUUID();
        for (ConstructionJobProgress progress : ConstructionJobStore.get(level).progresses()) {
            if (!progress.carriedEntries(droneId).isEmpty()) return true;
            if (hasPendingReturnCarry(progress, droneId, carry)) return true;
        }
        return false;
    }

    /** 台账仍记着悦灵在途,即使实体重载后手上的物品暂时为空也不能把它当作普通悦灵。 */
    public static boolean hasEscrowLedger(WorkingAllayEntity drone) {
        if (!(drone.level() instanceof ServerLevel level)) return false;
        UUID droneId = drone.getUUID();
        for (ConstructionJobProgress progress : ConstructionJobStore.get(level).progresses()) {
            if (!progress.carriedEntries(droneId).isEmpty()) return true;
        }
        return false;
    }

    /** 判断实体当前实物是否足以兑现至少一条在途材料,避免按台账凭空装载。 */
    public static boolean hasPhysicalCarriedMaterial(
        MinecraftServer server,
        ConstructionJobProgress progress,
        WorkingAllayEntity worker
    ) {
        ItemStack physical = worker.hostedCarry();
        if (physical.isEmpty()) return false;
        int available = physical.getCount() - otherTaskClaimCount(server, progress, worker.getUUID(), physical);
        if (available <= 0) return false;
        ItemStack availableStack = physical.copyWithCount(available);
        for (ConstructionLedgerEntry entry : progress.carriedEntries(worker.getUUID())) {
            if (matchingCount(availableStack, entry.stack()) >= entry.stack().getCount()) return true;
        }
        return false;
    }

    /** 重载后补回协调室绑定;只有绑定恢复后才允许建设悦灵重新领取在途操作。 */
    public static boolean restoreWorkerBinding(
        WorkingAllayEntity worker,
        ConstructionJobProgress progress
    ) {
        if (!(worker.level() instanceof ServerLevel level)) return false;
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        UUID workerOwner = worker.getOwner().orElse(null);
        if (job == null
            || !job.isActive()
            || !job.dimension().equals(level.dimension())
            || workerOwner == null
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, job.owner())) {
            return false;
        }
        if (progress.hasCoordinator()) {
            BlockPos loungePos = progress.coordinatorLounge();
            if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)
                || !lounge.canHost(worker)) {
                return false;
            }
            if (!loungePos.equals(worker.homeLoungePos())) {
                worker.setHomeLounge(loungePos);
            }
            if (!isBoundToCoordinator(worker, progress)) return false;
        }
        recoverNearbyCarry(worker, level, progress);
        if (worker.assignedJobId().isPresent() || !hasPhysicalCarriedMaterial(level.getServer(), progress, worker)) {
            return true;
        }
        ConstructionBuildOp op = nextAssignableCarried(level, progress, worker);
        if (op == null) return true;
        op.setStatus(ConstructionBuildOp.Status.LEASED);
        op.setLeaseAllay(worker.getUUID());
        worker.assign(progress.jobId(), op.id());
        op.approach().ifPresent(approach -> ConstructionTraffic.reserveApproach(level, worker.getUUID(), approach));
        ConstructionJobStore.get(level).markDirty();
        return true;
    }

    /** 回收重载前同一悦灵在附近留下的无标记旧掉落,只认老化且与在途台账完全相同的物品。 */
    private static void recoverNearbyCarry(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJobProgress progress
    ) {
        ItemStack carry = worker.hostedCarry().copy();
        AABB search = worker.getBoundingBox().inflate(2.0D);
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, search)) {
            if (!entity.isAlive() || entity.getAge() <= 100 || ConstructionDebris.isMarked(entity.getItem())) continue;
            ItemStack ground = entity.getItem();
            boolean matches = progress.carriedEntries(worker.getUUID()).stream().anyMatch(entry ->
                ItemStack.isSameItemSameComponents(ground, entry.stack()));
            if (!matches || !ItemStack.isSameItemSameComponents(carry, ground)
                && !carry.isEmpty()) {
                continue;
            }
            int room = carry.isEmpty() ? ground.getMaxStackSize() : carry.getMaxStackSize() - carry.getCount();
            if (room <= 0) break;
            int taken = Math.min(room, ground.getCount());
            if (carry.isEmpty()) carry = ground.copyWithCount(taken);
            else carry.grow(taken);
            ground.shrink(taken);
            if (ground.isEmpty()) entity.discard();
            if (carry.getCount() >= carry.getMaxStackSize()) break;
        }
        if (!ItemStack.matches(carry, worker.hostedCarry())) worker.setHostedCarry(carry);
    }

    /** 交付后手上剩下的空桶、树脂球等返还物同样归台账管,凭已交付条目登记的返还物匹配识别。 */
    private static boolean hasPendingReturnCarry(ConstructionJobProgress progress, UUID droneId, ItemStack carry) {
        for (ConstructionLedgerEntry entry : progress.ledger()) {
            if (!droneId.equals(entry.allayId())
                || entry.state() != ConstructionLedgerEntry.State.DELIVERED) {
                continue;
            }
            ConstructionBuildOp op = progress.operation(entry.operationId());
            if (op != null
                && !op.returnStack().isEmpty()
                && ItemStack.isSameItemSameComponents(carry, op.returnStack())) {
                return true;
            }
        }
        return false;
    }

    /** 无人机飞到所有者触及范围后把托管携带物塞回背包,并勾掉对应台账。 */
    public static void depositHostedCarry(WorkingAllayEntity drone, ServerPlayer player) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty()) return;
        if (!canDepositCarryToPlayer(drone, player)) return;
        player.getInventory().placeItemBackInInventory(carry.copy());
        markCarryReturned(drone, player.server, carry.copy());
        drone.setHostedCarry(ItemStack.EMPTY);
        drone.clearAssignment(false);
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    public static void depositHostedCarryToLounge(WorkingAllayEntity drone, ServerLevel level, BlockPos loungePos) {
        ItemStack carry = drone.hostedCarry();
        if (carry.isEmpty()) return;
        if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)
            || !canDepositCarryToLounge(drone, level, loungePos, lounge)) {
            return;
        }
        ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, loungePos);
        access.insertOrDrop(carry.copy());
        markCarryReturned(drone, level.getServer(), carry.copy());
        lounge.clearPickupDisplay(drone.getUUID());
        drone.setHostedCarry(ItemStack.EMPTY);
        drone.clearAssignment(false);
        drone.setActionState((byte) 0);
        drone.setWaitReason(ConstructionWaitReason.NONE);
    }

    static void markCarryReturned(WorkingAllayEntity drone, MinecraftServer server, ItemStack deposited) {
        ConstructionJobStore store = ConstructionJobStore.get(server);
        boolean changed = false;
        int remaining = deposited.getCount();
        List<ConstructionJobProgress> progresses = new ArrayList<>();
        store.progresses().forEach(progresses::add);
        UUID assignedJobId = drone.assignedJobId().orElse(null);
        if (assignedJobId != null) {
            for (int index = 0; index < progresses.size(); index++) {
                if (!progresses.get(index).jobId().equals(assignedJobId)) continue;
                progresses.addFirst(progresses.remove(index));
                break;
            }
        }
        for (ConstructionJobProgress progress : progresses) {
            for (ConstructionLedgerEntry entry : progress.carriedEntries(drone.getUUID())) {
                if (progress.operation(entry.operationId()) == null) {
                    changed |= progress.markCarryReturned(entry);
                    continue;
                }
                if (remaining < entry.stack().getCount()
                    || !ItemStack.isSameItemSameComponents(deposited, entry.stack())) {
                    continue;
                }
                remaining -= entry.stack().getCount();
                changed |= progress.markCarryReturned(entry);
            }
            Set<Integer> returnedOperations = new HashSet<>();
            for (ConstructionLedgerEntry entry : progress.ledger()) {
                if (!drone.getUUID().equals(entry.allayId())
                    || entry.state() != ConstructionLedgerEntry.State.DELIVERED
                    || !returnedOperations.add(entry.operationId())) {
                    continue;
                }
                ConstructionBuildOp op = progress.operation(entry.operationId());
                if (op == null
                    || op.returnStack().isEmpty()
                    || remaining < op.returnStack().getCount()
                    || !ItemStack.isSameItemSameComponents(deposited, op.returnStack())) {
                    continue;
                }
                remaining -= op.returnStack().getCount();
                changed |= progress.markDeliveredReturned(op.id());
            }
            if (remaining == 0) break;
        }
        if (changed) store.markDirty();
    }

    private static boolean canDepositCarryToPlayer(WorkingAllayEntity drone, ServerPlayer player) {
        UUID workerOwner = drone.getOwner().orElse(null);
        if (workerOwner == null
            || !ConstructionPermission.areCollaborators(player.server, workerOwner, player.getUUID())
            || !(player.level() instanceof ServerLevel level)) {
            return false;
        }
        ConstructionJobIndex index = ConstructionJobIndex.get(player.server);
        for (ConstructionJobProgress progress : ConstructionJobStore.get(player.server).progresses()) {
            boolean carriedByWorker = progress.ledger().stream().anyMatch(entry ->
                drone.getUUID().equals(entry.allayId())
                    && (entry.state() == ConstructionLedgerEntry.State.CARRIED
                        || entry.state() == ConstructionLedgerEntry.State.DELIVERED)
            );
            if (!carriedByWorker) continue;
            ConstructionJob job = index.job(progress.jobId());
            if (job == null
                || !ConstructionPermission.canManageWorker(player.server, workerOwner, job.owner())
                || !job.dimension().equals(level.dimension())
                || !ConstructionPermission.canModify(level, player.blockPosition(), job.owner())) {
                return false;
            }
        }
        // 没有任何台账的携带物是自由收集物,只要协作权限允许就能交还,否则会永远卡在悦灵身上
        return true;
    }

    private static boolean canDepositCarryToLounge(
        WorkingAllayEntity drone,
        ServerLevel level,
        BlockPos loungePos,
        AllayLoungeBlockEntity lounge
    ) {
        UUID workerOwner = drone.getOwner().orElse(null);
        if (workerOwner == null
            || !lounge.canHost(drone)
            || lounge.owner() == null
            || !ConstructionPermission.canModify(level, loungePos, lounge.owner())
            || !ConstructionPermission.canModify(level, loungePos.below(), lounge.owner())) {
            return false;
        }
        ConstructionJobIndex index = ConstructionJobIndex.get(level.getServer());
        for (ConstructionJobProgress progress : ConstructionJobStore.get(level.getServer()).progresses()) {
            boolean carriedByWorker = progress.ledger().stream().anyMatch(entry ->
                drone.getUUID().equals(entry.allayId())
                    && (entry.state() == ConstructionLedgerEntry.State.CARRIED
                        || entry.state() == ConstructionLedgerEntry.State.DELIVERED)
            );
            if (!carriedByWorker) continue;
            ConstructionJob job = index.job(progress.jobId());
            if (job == null
                || !progress.hasCoordinator()
                || !loungePos.equals(progress.coordinatorLounge())
                || !ConstructionPermission.canManageWorker(level.getServer(), workerOwner, job.owner())
                || !ConstructionPermission.areCollaborators(level.getServer(), lounge.owner(), job.owner())) {
                return false;
            }
        }
        // 没有任何台账的携带物是自由收集物,只要休息室权限允许就能入库,否则会永远塞不进容器
        return true;
    }

    private static void giveOrDrop(
        @Nullable ServerPlayer owner,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ItemStack stack
    ) {
        if (stack.isEmpty()) return;
        if (canAccessCoordinatorStorage(level, job, progress)) {
            ConstructionMaterialAccess access = ConstructionMaterialAccess.below(level, progress.coordinatorLounge());
            // 创造板条箱的供料没有消耗真实库存，取消时不能把虚拟材料再生成一份。
            if (access.isInfinite()) return;
            access.insertOrDrop(stack);
            return;
        }
        if (owner != null
            && ConstructionPermission.canModify(level, owner.blockPosition(), job.owner())) {
            if (owner.isCreative()) return;
            owner.getInventory().placeItemBackInInventory(stack);
            return;
        }
        Vec3 drop = Vec3.atCenterOf(job.anchor());
        level.addFreshEntity(new ItemEntity(level, drop.x, drop.y, drop.z, stack));
    }

    private static void releaseLeases(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : List.copyOf(progress.leasedOperations())) {
            if (op.isOpen()) {
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
                if (canUseOpenBuildCell(current, operation)) continue;
                return true;
            }
            if (operation.kind() == ConstructionBuildOp.Kind.ATTACHED
                && operation.status() != ConstructionBuildOp.Status.DELIVERED) {
                ConstructionBuildOp parent = progress.parentOf(operation);
                if (parent != null && canUseOpenBuildCell(current, parent)) continue;
                return true;
            }
        }
        return false;
    }

    /** 接近位在租约建立后立即预约，可借用任意未租用的目标格，封闭分析仍会拒绝会困住悦灵的选择。 */
    private static boolean canUseOpenBuildCell(
        ConstructionBuildOp current,
        ConstructionBuildOp reserved
    ) {
        if (current.kind() != ConstructionBuildOp.Kind.PLACE || !reserved.isOpen()) return false;
        if (reserved.status() == ConstructionBuildOp.Status.LEASED || current.id() == reserved.id()) return false;
        return true;
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
            if (worker.transitJobId().filter(job.jobId()::equals).isPresent()) continue;
            UUID assigned = worker.assignedJobId().orElse(null);
            if (assigned != null && !assigned.equals(job.jobId())) continue;
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
        return participantCount(level, job, progress)
            + ConstructionTransferService.inTransitCount(level.getServer(), job.jobId()) < MAX_PARTICIPANTS;
    }

    private static boolean hasSkippedPlace(ConstructionJobProgress progress) {
        return progress.hasSkippedPlaceMaterial() || progress.incomplete();
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
            SignDecorationAdapter.Extracted decorated =
                SignDecorationAdapter.extract(place.target(), extracted.config(), registries);
            place.setBlockEntity(decorated.config());
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
            for (SignDecorationAdapter.Decoration decoration : decorated.decorations()) {
                ConstructionBuildOp child = progress.addOperation(
                    place.pos(),
                    place.target(),
                    decoration.material(),
                    ConstructionBuildOp.Kind.DECORATE,
                    ConstructionBuildOp.Status.PENDING
                );
                child.setParentId(place.id());
                child.setSlot(decoration.slot());
                child.setBlockEntity(decoration.payload());
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
        // 拆除悦灵不取料;来源不可用只应阻断封堵/建设,不能让反应式拆除永远等箱子恢复
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
        if (!progress.hasLeasedDemolish()
            && ConstructionTransferService.inTransitCount(level.getServer(), job.jobId(), AllayCapability.DEMOLISH) == 0
            && !hasAvailableDemolitionAllay(level, job, progress)) {
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
            if (nextAssignableDemolish(level, progress) == null) return false;
            return hasBoundCapability(level, job, progress, AllayCapability.DEMOLISH)
                || ConstructionTransferService.inTransitCount(level.getServer(), job.jobId(), AllayCapability.DEMOLISH) > 0;
        }
        for (WorkingAllayEntity drone : loadedWorkers(level, job.owner())) {
            if (!isWorkerAvailableForJob(level, drone, job, progress)) continue;
            if (!drone.toolDefinition().hasCapability(AllayCapability.DEMOLISH)) continue;
            for (ConstructionBuildOp op : progress.openDemolitionCores()) {
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
        for (WorkingAllayEntity drone : loadedWorkers(level, job.owner())) {
            if (!isOwnerCollectionAllay(drone, job)
                || !isWorkerAvailableForJob(level, drone, job, progress)
                || drone.isCollectionFull()
                || !CollectionAllayToolBehavior.canAttemptTask(drone, level)) {
                continue;
            }
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
            if (!ConstructionPermission.canModify(level, BlockPos.containing(entity.position()), job.owner())) {
                enterPermissionWait(level, job, progress);
                return null;
            }
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
        if (!(drone.level() instanceof ServerLevel level)) return false;
        UUID workerOwner = drone.getOwner().orElse(null);
        if (workerOwner == null) return false;
        ConstructionJob job = progress == null
            ? null
            : ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        UUID permissionOwner = job == null ? workerOwner : job.owner();
        if (job != null
            && !ConstructionPermission.canManageWorker(level.getServer(), workerOwner, job.owner())) {
            enterPermissionWait(level, job, progress);
            return false;
        }
        if (!ConstructionPermission.canModify(level, BlockPos.containing(entity.position()), permissionOwner)) {
            if (job != null) enterPermissionWait(level, job, progress);
            return false;
        }
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
        UUID droneOwner = drone.getOwner().orElse(null);
        return drone.isAlive()
            && droneOwner != null
            && ConstructionPermission.areCollaborators(drone.level().getServer(), droneOwner, job.owner())
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
        for (WorkingAllayEntity drone : loadedWorkers(level, job.owner())) {
            UUID droneOwner = drone.getOwner().orElse(null);
            if (!drone.isAlive()
                || droneOwner == null
                || !ConstructionPermission.areCollaborators(level.getServer(), droneOwner, job.owner())
                || !isWorkerAvailableForJob(level, drone, job, progress)) {
                continue;
            }
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
        return coordinatorPermission(level, job, progress)
            && firstDeniedPosition(level, job, progress) == null
            && firstDeniedDebrisPosition(level, job, progress) == null;
    }

    @Nullable
    private static BlockPos firstDeniedPosition(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        for (ConstructionBuildOp op : progress.operations()) {
            if (!requiresCurrentPermission(op)) continue;
            BlockPos denied = firstDeniedOperationPosition(level, job, progress, op);
            if (denied != null) return denied;
        }
        return null;
    }

    @Nullable
    private static BlockPos firstDeniedDebrisPosition(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        for (ItemEntity entity : markedDebrisIn(level, job, progress, worldBox(job).inflate(DISCOVERY_RANGE))) {
            BlockPos pos = BlockPos.containing(entity.position());
            if (!ConstructionPermission.canModify(level, pos, job.owner())) return pos;
        }
        return null;
    }

    private static boolean requiresCurrentPermission(ConstructionBuildOp op) {
        if (op.status() == ConstructionBuildOp.Status.SKIPPED
            || op.kind() == ConstructionBuildOp.Kind.UNSUPPORTED) {
            return false;
        }
        if (op.isOpen()) return true;
        return op.status() == ConstructionBuildOp.Status.DELIVERED
            && (op.writesProjection()
                || op.kind() == ConstructionBuildOp.Kind.CONTENT
                || op.kind() == ConstructionBuildOp.Kind.FLUID
                || op.kind() == ConstructionBuildOp.Kind.ENTITY
                || op.kind() == ConstructionBuildOp.Kind.DECORATE);
    }

    private static byte resumeAfterPermission(ConstructionJobProgress progress) {
        return progress.commitLog().phase() == ConstructionCommitLog.Phase.NONE
            ? nextPhase(progress)
            : ConstructionJob.STATE_COMMITTING;
    }

    static boolean enterIfDenied(ServerLevel level, ConstructionJobProgress progress, BlockPos pos) {
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        if (job == null) return true;
        if (ConstructionPermission.canModify(level, pos, job.owner())) return true;
        enterPermissionWait(level, job, progress);
        return false;
    }

    static boolean enterIfDenied(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        if (job == null) return true;
        for (BlockPos pos : operationPositions(level, progress, op)) {
            if (!ConstructionPermission.canModify(level, pos, job.owner())) {
                enterPermissionWait(level, job, progress);
                return false;
            }
        }
        return true;
    }

    private static @Nullable BlockPos firstDeniedOperationPosition(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        for (BlockPos pos : operationPositions(level, progress, op)) {
            if (!ConstructionPermission.canModify(level, pos, job.owner())) return pos;
        }
        return null;
    }

    /** 计算一项操作真正可能写入、清理或触发状态变更的所有位置。 */
    private static List<BlockPos> operationPositions(
        ServerLevel level,
        ConstructionJobProgress progress,
        ConstructionBuildOp op
    ) {
        Set<BlockPos> positions = new LinkedHashSet<>();
        switch (op.kind()) {
            case DEMOLISH -> positions.addAll(DemolitionPlanner.affectedPositions(level, op.pos()));
            case ENTITY -> {
                positions.add(op.pos());
                CompoundTag nbt = op.entityNbt();
                if (nbt != null) positions.add(BlockPos.containing(posOf(nbt, op.pos())));
            }
            default -> positions.add(op.pos());
        }
        if (op.kind() == ConstructionBuildOp.Kind.PLACE) {
            for (ConstructionBuildOp child : progress.childrenOf(op)) {
                if (child.kind() == ConstructionBuildOp.Kind.ATTACHED) positions.add(child.pos());
            }
        }
        if (AnvilCraftRedstoneWirePorts.isWire(op.target())
            || AnvilCraftRedstoneWirePorts.isWire(level.getBlockState(op.pos()))) {
            for (Direction direction : Direction.values()) {
                BlockPos neighbor = op.pos().relative(direction);
                if (level.isInWorldBounds(neighbor)) positions.add(neighbor);
            }
        }
        return List.copyOf(positions);
    }

    private static boolean coordinatorPermission(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (!progress.hasCoordinator()) return true;
        BlockPos loungePos = progress.coordinatorLounge();
        if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            return true;
        }
        UUID loungeOwner = lounge.owner();
        return loungeOwner != null
            && ConstructionPermission.areCollaborators(level.getServer(), loungeOwner, job.owner())
            && ConstructionPermission.canModify(level, loungePos, loungeOwner)
            && ConstructionPermission.canModify(level, loungePos.below(), loungeOwner);
    }

    static boolean enterIfCoordinatorDenied(ServerLevel level, ConstructionJobProgress progress) {
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        if (job == null || !progress.hasCoordinator()) return true;
        BlockPos loungePos = progress.coordinatorLounge();
        if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) {
            return true;
        }
        if (coordinatorPermission(level, job, progress)) return true;
        enterPermissionWait(level, job, progress);
        return false;
    }

    private static void enterPermissionWait(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        progress.setWaitReason(ConstructionWaitReason.PERMISSION);
        reportOnce(level.getServer(), job, progress, ConstructionWaitReason.PERMISSION);
        setState(level.getServer(), job, ConstructionJob.STATE_WAITING_PERMISSION);
        ConstructionJobStore.get(level).markDirty();
    }

    private static void markOpDone(ServerLevel level, ConstructionJobProgress progress, ConstructionBuildOp op) {
        op.setStatus(ConstructionBuildOp.Status.DELIVERED);
        op.setLeaseAllay(null);
        progress.markOperationDelivered(op.id());
        ConstructionJobStore.get(level).markDirty();
    }

    private static boolean smashRemainingShells(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : List.copyOf(progress.openDemolitionShells())) {
            if (!enterIfDenied(level, progress, op)) return false;
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
        return true;
    }

    private static boolean canReplaceWithFill(BlockState state) {
        return state.isAir()
            || state.getBlock() instanceof LiquidBlock
            || state.canBeReplaced()
            || FluidSealPlanner.isSealableFluid(state);
    }

    private static boolean needsFillMaterial(ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : progress.openOperations(ConstructionBuildOp.Kind.SEAL)) {
            if (op.material().isEmpty()) return true;
        }
        return false;
    }

    private static void skipPlaceOnRemainingFluids(ServerLevel level, ConstructionJobProgress progress) {
        for (ConstructionBuildOp op : List.copyOf(progress.openOperations(ConstructionBuildOp.Kind.PLACE))) {
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
    public static boolean claimLounge(ServerLevel level, BlockPos loungePos, UUID jobId) {
        MinecraftServer server = level.getServer();
        ConstructionJob job = ConstructionJobIndex.get(server).job(jobId);
        if (job == null || !job.dimension().equals(level.dimension())) return false;
        if (!(level.getBlockEntity(loungePos) instanceof AllayLoungeBlockEntity lounge)) return false;
        UUID loungeOwner = lounge.owner();
        if (loungeOwner == null || !ConstructionPermission.areCollaborators(server, loungeOwner, job.owner())) {
            return false;
        }
        if (!ConstructionPermission.canModify(level, loungePos, loungeOwner)
            || !ConstructionPermission.canModify(level, loungePos.below(), loungeOwner)) {
            return false;
        }
        ConstructionJobProgress progress = ConstructionJobStore.get(server).getOrCreate(jobId);
        progress.setCoordinatorLounge(loungePos.immutable());
        evictUnhostedWorkers(level, job, progress);
        ConstructionJobStore.get(server).markDirty();
        return true;
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
            // 协调室被撤销后任务再无落脚点,清空在途台账,让已借调的悦灵沿链返程
            ConstructionTransferService.onJobEnded(server, jobId);
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
        return progress.hasCoordinator()
            && progress.coordinatorLounge().equals(worker.homeLoungePos())
            && worker.level() instanceof ServerLevel level
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge
            && lounge.canHost(worker);
    }

    /** 当前工人没有被其他任务占用，且协调站绑定与目标任务兼容。 */
    public static boolean isWorkerAvailableForJob(
        ServerLevel level,
        WorkingAllayEntity worker,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        UUID workerOwner = worker.getOwner().orElse(null);
        UUID assigned = worker.assignedJobId().orElse(null);
        if (!worker.isAlive()
            || !job.isActive()
            || !job.dimension().equals(level.dimension())
            || workerOwner == null
            || assigned != null && !assigned.equals(job.jobId())
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, job.owner())) {
            return false;
        }
        if (progress.hasCoordinator()) return isBoundToCoordinator(worker, progress);
        return !hasActiveCoordinatorBindingToOtherJob(level, worker, job.jobId());
    }

    private static boolean hasActiveCoordinatorBindingToOtherJob(
        ServerLevel level,
        WorkingAllayEntity worker,
        UUID candidateJobId
    ) {
        BlockPos home = worker.homeLoungePos();
        if (home == null || !(level.getBlockEntity(home) instanceof AllayLoungeBlockEntity lounge)) return false;
        UUID coordinatedJobId = lounge.diskJobId();
        if (coordinatedJobId == null || coordinatedJobId.equals(candidateJobId) || !lounge.canHost(worker)) {
            return false;
        }
        ConstructionJob coordinated = ConstructionJobIndex.get(level).job(coordinatedJobId);
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(coordinatedJobId);
        return coordinated != null
            && coordinated.isActive()
            && coordinated.dimension().equals(level.dimension())
            && progress != null
            && home.equals(progress.coordinatorLounge());
    }

    public static boolean canClaimJob(WorkingAllayEntity worker, ConstructionJobProgress progress) {
        if (worker.requestedTool() != null && worker.requestedTool() != worker.toolDefinition()
            && worker.hostedCarry().isEmpty() && !worker.hasCollectionItems()) return false;
        if (!(worker.level() instanceof ServerLevel level)) return false;
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        UUID workerOwner = worker.getOwner().orElse(null);
        UUID assigned = worker.assignedJobId().orElse(null);
        if (job == null || workerOwner == null
            || assigned != null && !assigned.equals(job.jobId())
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, job.owner())) {
            return false;
        }
        if (!enterIfCoordinatorDenied(level, progress)) return false;
        if (progress.hasCoordinator()) return isBoundToCoordinator(worker, progress);
        if (hasActiveCoordinatorBindingToOtherJob(level, worker, job.jobId())) return false;
        if (worker.assignedJobId().filter(progress.jobId()::equals).isPresent()) return true;
        return participantCount(level, job, progress)
            + ConstructionTransferService.inTransitCount(level.getServer(), job.jobId()) < MAX_PARTICIPANTS;
    }

    /** 返回悦灵当前已持有租约对应的任务，避免团队关系变化后切到另一份活动任务。 */
    @Nullable
    public static ConstructionJob jobForWorker(ServerLevel level, WorkingAllayEntity worker) {
        ConstructionJobIndex index = ConstructionJobIndex.get(level);
        UUID assigned = worker.assignedJobId().orElse(null);
        if (assigned != null) {
            ConstructionJob assignedJob = index.job(assigned);
            if (assignedJob != null) return assignedJob;
        }
        UUID owner = worker.getOwner().orElse(null);
        if (owner == null) return null;

        ConstructionJob carried = carriedJobForWorker(level, worker, index, owner);
        if (carried != null) return carried;

        ConstructionJob coordinated = coordinatorJobForWorker(level, worker, index);
        if (coordinated != null) return coordinated;

        ConstructionJob own = null;
        double ownDistance = Double.MAX_VALUE;
        ConstructionJob teammate = null;
        double teammateDistance = Double.MAX_VALUE;
        ConstructionJobStore store = ConstructionJobStore.get(level);
        for (ConstructionJob candidate : index.jobsIn(level)) {
            ConstructionJobProgress progress = store.get(candidate.jobId());
            if (progress == null
                || progress.hasCoordinator()
                || !isWorkerAvailableForJob(level, worker, candidate, progress)
                || !canServeCurrentPhase(level, worker, candidate, progress)) {
                continue;
            }
            double distance = distanceToJobSqr(worker.position(), candidate);
            if (distance > DISCOVERY_RANGE * DISCOVERY_RANGE) continue;
            if (candidate.owner().equals(owner)) {
                if (own == null || distance < ownDistance
                    || distance == ownDistance && candidate.jobId().compareTo(own.jobId()) < 0) {
                    own = candidate;
                    ownDistance = distance;
                }
            } else if (teammate == null || distance < teammateDistance
                || distance == teammateDistance && candidate.jobId().compareTo(teammate.jobId()) < 0) {
                teammate = candidate;
                teammateDistance = distance;
            }
        }
        return own != null ? own : teammate;
    }

    @Nullable
    private static ConstructionJob carriedJobForWorker(
        ServerLevel level,
        WorkingAllayEntity worker,
        ConstructionJobIndex index,
        UUID owner
    ) {
        ConstructionJobStore store = ConstructionJobStore.get(level);
        ConstructionJob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ConstructionJobProgress progress : store.progresses()) {
            if (progress.carriedEntries(worker.getUUID()).isEmpty()) continue;
            ConstructionJob candidate = index.job(progress.jobId());
            if (candidate == null
                || !candidate.isActive()
                || !candidate.dimension().equals(level.dimension())) {
                continue;
            }
            double distance = distanceToJobSqr(worker.position(), candidate);
            if (best == null
                || candidate.owner().equals(owner) && !best.owner().equals(owner)
                || candidate.owner().equals(owner) == best.owner().equals(owner)
                    && (distance < bestDistance
                        || distance == bestDistance && candidate.jobId().compareTo(best.jobId()) < 0)) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Nullable
    private static ConstructionJob coordinatorJobForWorker(
        ServerLevel level,
        WorkingAllayEntity worker,
        ConstructionJobIndex index
    ) {
        BlockPos home = worker.homeLoungePos();
        if (home == null || !(level.getBlockEntity(home) instanceof AllayLoungeBlockEntity lounge)) return null;
        UUID jobId = lounge.diskJobId();
        if (jobId == null) return null;
        ConstructionJob job = index.job(jobId);
        ConstructionJobProgress progress = ConstructionJobStore.get(level).get(jobId);
        if (job == null || progress == null || !home.equals(progress.coordinatorLounge())) return null;
        return isWorkerAvailableForJob(level, worker, job, progress) ? job : null;
    }

    private static boolean canServeCurrentPhase(
        ServerLevel level,
        WorkingAllayEntity worker,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        return switch (job.state()) {
            case ConstructionJob.STATE_SEALING_FLUID ->
                worker.toolDefinition().hasCapability(AllayCapability.SEAL_FLUID)
                    && progress.unleasedMaterialCount() > 0;
            case ConstructionJob.STATE_BUILDING ->
                worker.toolDefinition().hasCapability(AllayCapability.DELIVER_PROJECTION)
                    && (worker.toolDefinition().hasCapability(AllayCapability.IGNITE)
                        ? IgnitionBuildAdapter.hasWork(progress) : progress.unleasedMaterialCount() > 0);
            case ConstructionJob.STATE_DEMOLISHING ->
                worker.toolDefinition().hasCapability(AllayCapability.DEMOLISH)
                    && progress.unleasedDemolishCount() > 0
                    || worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS)
                    && hasWorldDebris(level, job, progress);
            case ConstructionJob.STATE_COLLECTING_DEBRIS ->
                worker.toolDefinition().hasCapability(AllayCapability.COLLECT_ITEMS)
                    && hasWorldDebris(level, job, progress);
            default -> false;
        };
    }

    private static double distanceToJobSqr(Vec3 position, ConstructionJob job) {
        AABB box = worldBox(job);
        double x = Math.max(box.minX - position.x, Math.max(0.0D, position.x - box.maxX));
        double y = Math.max(box.minY - position.y, Math.max(0.0D, position.y - box.maxY));
        double z = Math.max(box.minZ - position.z, Math.max(0.0D, position.z - box.maxZ));
        return x * x + y * y + z * z;
    }

    /** 每个执行 tick 复核已持有租约的团队、协调站和实际操作权限。 */
    public static boolean canContinueJob(WorkingAllayEntity worker, ConstructionJobProgress progress) {
        if (!(worker.level() instanceof ServerLevel level)) return false;
        ConstructionJob job = ConstructionJobIndex.get(level).job(progress.jobId());
        UUID workerOwner = worker.getOwner().orElse(null);
        if (job == null
            || !job.dimension().equals(level.dimension())
            || workerOwner == null
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, job.owner())) {
            return false;
        }
        if (!enterIfCoordinatorDenied(level, progress)) return false;
        if (progress.hasCoordinator() && !isBoundToCoordinator(worker, progress)) return false;
        if (worker.assignedJobId().filter(progress.jobId()::equals).isPresent() && worker.taskOpId() >= 0) {
            ConstructionBuildOp op = progress.operation(worker.taskOpId());
            if (op != null && !enterIfDenied(level, progress, op)) return false;
        }
        return true;
    }

    /** 收回失权悦灵的租约、交通预约和在途材料，不把资源交给失权者个人。 */
    private static boolean canWorkerAccessJob(
        WorkingAllayEntity worker,
        ServerLevel level,
        ConstructionJob job
    ) {
        UUID workerOwner = worker.getOwner().orElse(null);
        return workerOwner != null
            && job.dimension().equals(level.dimension())
            && ConstructionPermission.canManageWorker(level.getServer(), workerOwner, job.owner());
    }

    public static void revokeWorker(WorkingAllayEntity worker, ServerLevel level, ConstructionJobProgress progress) {
        UUID workerId = worker.getUUID();
        ConstructionJob job = ConstructionJobIndex.get(level.getServer()).job(progress.jobId());
        progress.releaseDebrisLeasesOf(workerId);
        if (job != null) {
            returnWorkerCarry(level.getServer(), level, job, progress, worker);
        } else {
            worker.dropCollectionAt(worker.position());
            progress.markCarriesReturned(workerId);
        }
        if (worker.toolDefinition().inventorySize() > 0 && worker.hasCollectionItems()) {
            worker.dropCollectionAt(worker.position());
        }
        worker.clearAssignment(false);
        if (shouldDetachFromLounge(worker, level, job)) worker.setHomeLounge(null);
        ConstructionTraffic.release(level, workerId);
        ConstructionJobStore.get(level).markDirty();
    }

    /** 收回失权收集悦灵的任务掉落；不能把它们继续卸给失权者或陌生容器。 */
    public static void revokeCollectionWorker(
        WorkingAllayEntity worker,
        ServerLevel level,
        @Nullable ConstructionJob job,
        @Nullable ConstructionJobProgress progress
    ) {
        if (progress != null) progress.releaseDebrisLeasesOf(worker.getUUID());
        worker.clearAssignment(false);
        if (job != null
            && progress != null
            && canWorkerAccessJob(worker, level, job)
            && canAccessCoordinatorStorage(level, job, progress)) {
            worker.unloadCollectionTo(ConstructionMaterialAccess.below(level, progress.coordinatorLounge()));
        } else {
            worker.dropCollectionAt(worker.position());
        }
        if (shouldDetachFromLounge(worker, level, job)) worker.setHomeLounge(null);
        ConstructionTraffic.release(level, worker.getUUID());
        if (progress != null) ConstructionJobStore.get(level).markDirty();
    }

    private static boolean shouldDetachFromLounge(
        WorkingAllayEntity worker,
        ServerLevel level,
        @Nullable ConstructionJob job
    ) {
        UUID workerOwner = worker.getOwner().orElse(null);
        if (job == null || workerOwner == null
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, job.owner())) {
            return true;
        }
        BlockPos home = worker.homeLoungePos();
        if (home == null) return false;
        if (!(level.getBlockEntity(home) instanceof AllayLoungeBlockEntity lounge)) return true;
        UUID loungeOwner = lounge.owner();
        return loungeOwner == null
            || !ConstructionPermission.areCollaborators(level.getServer(), workerOwner, loungeOwner);
    }

    static boolean canAccessCoordinatorStorage(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        return progress.hasCoordinator()
            && level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge
            && coordinatorPermission(level, job, progress)
            && lounge.owner() != null
            && ConstructionPermission.canModify(level, progress.coordinatorLounge().below(), lounge.owner());
    }

    private static void returnToJobSource(
        ServerLevel level,
        @Nullable ConstructionJob job,
        ConstructionJobProgress progress,
        ItemStack stack,
        Vec3 fallback
    ) {
        if (stack.isEmpty()) return;
        if (job != null && canAccessCoordinatorStorage(level, job, progress)) {
            ConstructionMaterialAccess.below(level, progress.coordinatorLounge()).insertOrDrop(stack);
            return;
        }
        if (job != null) {
            ServerPlayer owner = findOwner(level.getServer(), level, job.owner());
            if (owner != null
                && ConstructionPermission.canModify(level, owner.blockPosition(), job.owner())) {
                owner.getInventory().placeItemBackInInventory(stack);
                return;
            }
        }
        level.addFreshEntity(new ItemEntity(level, fallback.x, fallback.y, fallback.z, stack));
    }

    public static boolean isWithinLoungeRange(ConstructionJobProgress progress, BlockPos target) {
        if (!progress.hasCoordinator()) return true;
        return Vec3.atCenterOf(progress.coordinatorLounge()).distanceTo(Vec3.atCenterOf(target)) <= DISCOVERY_RANGE;
    }

    /**
     * 目标是否可派发。除了协调室发现半径,还必须已经处在实体刻窗口内:
     * 未加载区块里没有工人能到场,而 {@code getBlockState} 会把该区块同步拽进内存,
     * 因此这道闸必须挡在任何读取世界状态之前。
     */
    static boolean isAssignable(ServerLevel level, ConstructionJobProgress progress, BlockPos target) {
        return isWithinLoungeRange(progress, target) && ObservationCoverageService.isWorkable(level, target);
    }

    public static boolean isSourceAvailable(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        if (progress.hasCoordinator()) {
            if (!enterIfCoordinatorDenied(level, progress)) return false;
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
        AllayToolSupply.plan(level, job, progress, lounge);
        if (!canAcceptMoreParticipants(level, job, progress)) return;
        launchObserverIfNeeded(level, job, progress, lounge);
        byte state = job.state();
        if (state == ConstructionJob.STATE_SEALING_FLUID || state == ConstructionJob.STATE_BUILDING) {
            if (state == ConstructionJob.STATE_BUILDING
                && IgnitionBuildAdapter.hasWork(progress)
                && !hasLoadedBoundCapability(level, job, progress, AllayCapability.IGNITE)) {
                lounge.tryLaunch(record -> hostedRecordAvailableForJob(level, record, job)
                    && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(AllayCapability.IGNITE));
            }
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
        if (state == ConstructionJob.STATE_WAITING_DEMOLITION) {
            launchIfNeeded(level, job, progress, lounge, AllayCapability.DEMOLISH);
            return;
        }
        if (state == ConstructionJob.STATE_COLLECTING_DEBRIS && hasWorldDebris(level, job, progress)) {
            launchIfNeeded(level, job, progress, lounge, AllayCapability.COLLECT_ITEMS);
        }
    }

    /**
     * 观察悦灵出库与借调。缺口由覆盖规划给出:现场闲置的观察悦灵已经在规划里指派过租约,
     * 这里只补真正需要从托管位放出或从外室借来的那几只。
     *
     * <p>协调室自身那一柱没进入实体刻时不能出库:出入库要读方块实体,窗口得先由它自己
     * 托管的观察者或别处的自由观察者建立起来。
     */
    private static void launchObserverIfNeeded(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayLoungeBlockEntity lounge
    ) {
        int wanted = ObservationCoverageService.wantedObservers(level.getServer(), job);
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!isWorkerAvailableForJob(level, worker, job, progress)) continue;
            if (worker.requestedTool() == AllayToolDefinitions.OBSERVATION
                && worker.toolDefinition() != AllayToolDefinitions.OBSERVATION) wanted--;
        }
        if (wanted <= 0) return;
        if (!ObservationCoverageService.isCoordinatorTicking(level, progress)) return;
        int inTransit = ConstructionTransferService.inTransitCount(
            level.getServer(),
            job.jobId(),
            AllayCapability.CHUNK_LOADING
        );
        // 规划结论最多滞后一个校验周期,已在途的必须先扣掉,否则会一路超借
        if (wanted <= inTransit) return;
        Predicate<AllayWorkRecord> recordMatch = record -> hostedRecordAvailableForJob(level, record, job)
            && ObservationCoverage.isEligible(record);
        if (!lounge.isBayBusy()) lounge.tryLaunch(recordMatch);
        ConstructionTransferService.considerBorrow(
            level,
            job,
            lounge,
            AllayCapability.CHUNK_LOADING,
            recordMatch,
            wanted,
            0
        );
    }

    private static void launchIfNeeded(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        AllayLoungeBlockEntity lounge,
        AllayCapability capability
    ) {
        if (!canAcceptMoreParticipants(level, job, progress)) return;
        // 协调室出库通道忙只挡本室这一刻出库,不能挡住远程借调决策:
        // 每个休息室各有一条 20 gt 出库通道,外室必须与协调室同步起飞而不是排在它后面
        boolean bayBusy = lounge.isBayBusy();
        if (capability == AllayCapability.COLLECT_ITEMS) {
            if (!bayBusy) tryLaunchCollector(level, job, progress, lounge);
            return;
        }
        boolean building = capability == AllayCapability.PICK_UP_MATERIAL
            && job.state() == ConstructionJob.STATE_BUILDING;
        boolean shortWork = building && hasMorePotentialBuildWork(level, progress, false, 0);
        boolean longWork = building && hasMorePotentialBuildWork(level, progress, true, 0);
        if (building && !shortWork && !longWork) {
            return;
        }
        if (!bayBusy
            && building
            && longWork
            && hasMorePotentialBuildWork(
                level,
                progress,
                true,
                countLoadedLongReachBuilders(level, job, progress)
            )
            && lounge.tryLaunch(record -> hostedRecordAvailableForJob(level, record, job)
                && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(capability)
                && canPlaceLongReach(record))) {
            return;
        }
        if (!building && !hasAssignableForLaunch(level, job, progress, capability)) return;
        int workers = countLoadedBoundCapability(level, job, progress, capability);
        int inTransit = ConstructionTransferService.inTransitCount(level.getServer(), job.jobId(), capability);
        if (!hasMorePotentiallyOpen(level, progress, capability, workers + inTransit)) return;
        Predicate<AllayWorkRecord> recordMatch = record -> hostedRecordAvailableForJob(level, record, job)
            && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(capability)
            && (!building || canPlaceLongReach(record) || shortWork);
        if (!bayBusy) lounge.tryLaunch(recordMatch);
        // 本室出库与远程借调同时推进:是否借调只由预计完工时间判定,不再等本室托管记录耗尽
        int remaining = capability == AllayCapability.DEMOLISH
            ? progress.unleasedDemolishCount()
            : progress.unleasedMaterialCount();
        ConstructionTransferService.considerBorrow(
            level,
            job,
            lounge,
            capability,
            recordMatch,
            remaining,
            workers
        );
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
            for (ConstructionBuildOp op : progress.openDemolitionCores()) {
                if (op.status() == ConstructionBuildOp.Status.LEASED) continue;
                if (!isAssignable(level, progress, op.pos())) continue;
                if (level.getBlockState(op.pos()).isAir()) continue;
                if (++count > workers) return true;
            }
            return false;
        }
        for (ConstructionBuildOp.Kind kind : ConstructionJobProgress.LAUNCH_MATERIAL_KINDS) {
            for (ConstructionBuildOp op : progress.openOperations(kind)) {
                if (IgnitionBuildAdapter.supports(op.target())) continue;
                if (op.status() == ConstructionBuildOp.Status.LEASED) continue;
                if (!isAssignable(level, progress, op.pos())) continue;
                if (kind == ConstructionBuildOp.Kind.CONTENT
                    || kind == ConstructionBuildOp.Kind.FLUID
                    || kind == ConstructionBuildOp.Kind.DECORATE) {
                    ConstructionBuildOp parent = progress.parentOf(op);
                    if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) continue;
                }
                if (kind == ConstructionBuildOp.Kind.PLACE && !level.getBlockState(op.pos()).isAir()) {
                    continue;
                }
                if (++count > workers) return true;
            }
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
        return snapshot.ownedBy(level.getServer(), ownerId);
    }

    /** 返回本维度当前 tick 已加载的全部施工悦灵；取消/失权返还不能按当前团队关系过滤。 */
    public static List<WorkingAllayEntity> loadedWorkers(ServerLevel level) {
        long gameTime = level.getGameTime();
        WorkerSnapshot snapshot = WORKER_SNAPSHOTS.get(level);
        if (snapshot == null || snapshot.gameTime != gameTime) {
            snapshot = WorkerSnapshot.capture(level, gameTime);
            WORKER_SNAPSHOTS.put(level, snapshot);
        }
        return snapshot.all();
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
        for (ConstructionBuildOp.Kind kind : ConstructionJobProgress.PLACE_MATERIAL_KINDS) {
            for (ConstructionBuildOp op : progress.openOperations(kind)) {
                if (IgnitionBuildAdapter.supports(op.target())) continue;
                if (ConstructionPlacementLimits.requiresLongReach(op) != requiresLongReach) continue;
                if (op.status() == ConstructionBuildOp.Status.LEASED) continue;
                if (!isAssignable(level, progress, op.pos())) continue;
                if (kind == ConstructionBuildOp.Kind.CONTENT
                    || kind == ConstructionBuildOp.Kind.FLUID
                    || kind == ConstructionBuildOp.Kind.DECORATE) {
                    ConstructionBuildOp parent = progress.parentOf(op);
                    if (parent == null || parent.status() != ConstructionBuildOp.Status.DELIVERED) continue;
                }
                if (kind == ConstructionBuildOp.Kind.PLACE && !level.getBlockState(op.pos()).isAir()) continue;
                if (kind == ConstructionBuildOp.Kind.ENTITY
                    && (hasOpenSupportPlace(progress, op.pos()) || entityOccupied(level, op, null))) {
                    continue;
                }
                if (chooseApproach(level, progress, op) != null && ++count > workers) return true;
            }
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
            if (worker.transitJobId().filter(job.jobId()::equals).isPresent()) continue;
            if (!isWorkerAvailableForJob(level, worker, job, progress)) continue;
            if (!worker.toolDefinition().hasCapability(AllayCapability.PICK_UP_MATERIAL)) continue;
            if (canPlaceLongReach(worker)) count++;
        }
        return count;
    }

    private static boolean canPlaceLongReach(AllayWorkRecord record) {
        return AllayToolDefinitions.fromHeldItem(record.heldTool()).reachDistance()
            >= AllayToolDefinitions.CONSTRUCTION.reachDistance();
    }

    private static boolean hostedRecordAvailableForJob(
        ServerLevel level,
        AllayWorkRecord record,
        ConstructionJob job
    ) {
        return record.assignedJobId().filter(assigned -> !assigned.equals(job.jobId())).isEmpty()
            && record.owner()
                .map(owner -> ConstructionPermission.areCollaborators(level.getServer(), owner, job.owner()))
                .orElse(false);
    }

    /** 收集出库先放磁铁;只有没有可用磁铁时才放空手收集工. */
    public static boolean tryLaunchCollector(AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level)
            || lounge.owner() == null
            || !ConstructionPermission.canModify(level, lounge.getBlockPos(), lounge.owner())
            || !ConstructionPermission.canModify(level, lounge.getBlockPos().below(), lounge.owner())) {
            return false;
        }
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
        if (lounge.tryLaunch(record -> hostedRecordAvailableForJob(level, record, job)
            && CollectionAllayToolBehavior.isVacuumTool(record.heldTool())
            && CollectionAllayToolBehavior.hasCollectionCapacity(record)
            && CollectionAllayToolBehavior.canAttemptTask(record, level))) {
            return;
        }
        if (hasLoadedBoundCapability(level, job, progress, AllayCapability.COLLECT_ITEMS)) {
            return;
        }
        Predicate<AllayWorkRecord> collector = record -> hostedRecordAvailableForJob(level, record, job)
            && AllayToolDefinitions.fromHeldItem(record.heldTool()).hasCapability(AllayCapability.COLLECT_ITEMS)
            && CollectionAllayToolBehavior.hasCollectionCapacity(record)
            && CollectionAllayToolBehavior.canAttemptTask(record, level);
        if (!lounge.tryLaunch(collector)) {
            // 本地无收集记录,考虑远程借调收集工。上面已确认没有到场的收集工,故已到场数恒为 0;
            // 收集同时只需要一只,已有在途收集工时不再重复借调
            if (ConstructionTransferService.inTransitCount(
                level.getServer(),
                job.jobId(),
                AllayCapability.COLLECT_ITEMS
            ) > 0) {
                return;
            }
            ConstructionTransferService.considerBorrow(
                level,
                job,
                lounge,
                AllayCapability.COLLECT_ITEMS,
                collector,
                1,
                0
            );
        }
    }

    private static boolean hasLoadedVacuum(
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress
    ) {
        for (WorkingAllayEntity worker : loadedWorkers(level, job.owner())) {
            if (!isWorkerAvailableForJob(level, worker, job, progress)) continue;
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
            if (!hostedRecordAvailableForJob(level, record, job)) continue;
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
            if (worker.transitJobId().filter(job.jobId()::equals).isPresent()) continue;
            if (!isWorkerAvailableForJob(level, worker, job, progress)) continue;
            if ((worker.requestedTool() == null ? worker.toolDefinition() : worker.requestedTool()).hasCapability(capability)
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

    private static final class WorkerSnapshot {
        private final long gameTime;
        private final Map<UUID, List<WorkingAllayEntity>> workersByOwner = new HashMap<>();
        /** 同一刻内按调用方所有者缓存合并结果:64 只悦灵下每刻会问上百次,不能每次都重建并复制。 */
        private final Map<UUID, List<WorkingAllayEntity>> collaboratorCache = new HashMap<>();
        @Nullable
        private List<WorkingAllayEntity> allCache;

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
                    this.invalidate();
                }
            });
        }

        private void remove(WorkingAllayEntity worker) {
            for (List<WorkingAllayEntity> workers : this.workersByOwner.values()) {
                if (workers.remove(worker)) this.invalidate();
            }
        }

        private void invalidate() {
            this.collaboratorCache.clear();
            this.allCache = null;
        }

        private List<WorkingAllayEntity> ownedBy(MinecraftServer server, UUID ownerId) {
            List<WorkingAllayEntity> cached = this.collaboratorCache.get(ownerId);
            if (cached != null) return cached;
            List<WorkingAllayEntity> result = new ArrayList<>();
            for (Map.Entry<UUID, List<WorkingAllayEntity>> entry : this.workersByOwner.entrySet()) {
                if (ConstructionPermission.areCollaborators(server, ownerId, entry.getKey())) {
                    result.addAll(entry.getValue());
                }
            }
            List<WorkingAllayEntity> workers = List.copyOf(result);
            this.collaboratorCache.put(ownerId, workers);
            return workers;
        }

        private List<WorkingAllayEntity> all() {
            if (this.allCache != null) return this.allCache;
            List<WorkingAllayEntity> result = new ArrayList<>();
            for (List<WorkingAllayEntity> workers : this.workersByOwner.values()) {
                result.addAll(workers);
            }
            this.allCache = List.copyOf(result);
            return this.allCache;
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

    /** 任务结束时清除所有在线玩家仍持有的旧任务磁盘引用，避免同队成员留下失效 jobId。 */
    public static void clearOnlineDisks(MinecraftServer server, UUID jobId) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearOwnerDisk(player, jobId);
        }
    }

    private static void clearOwnerDisk(ServerPlayer player, UUID jobId) {
        boolean changed = clearJobId(player.getInventory().getSelected(), jobId);
        changed |= clearJobId(player.getOffhandItem(), jobId);
        for (ItemStack stack : player.getInventory().items) {
            changed |= clearJobId(stack, jobId);
        }
        for (Slot slot : player.containerMenu.slots) {
            if (!clearJobId(slot.getItem(), jobId)) continue;
            slot.setChanged();
            changed = true;
        }
        changed |= clearJobId(player.containerMenu.getCarried(), jobId);
        if (changed) {
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
        }
    }

    private static boolean clearJobId(ItemStack stack, UUID jobId) {
        ConstructionBlueprintData data = ConstructionBlueprintData.get(stack).orElse(null);
        if (data == null || data.jobId().filter(jobId::equals).isEmpty()) return false;
        ConstructionBlueprintData.set(stack, data.withoutJobId());
        return true;
    }
}
