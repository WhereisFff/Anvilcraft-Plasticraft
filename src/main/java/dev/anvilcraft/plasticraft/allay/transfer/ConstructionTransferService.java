package dev.anvilcraft.plasticraft.allay.transfer;

import dev.anvilcraft.plasticraft.allay.AllayFlightState;
import dev.anvilcraft.plasticraft.allay.AllayWorkRecord;
import dev.anvilcraft.plasticraft.allay.tool.AllayCapability;
import dev.anvilcraft.plasticraft.allay.tool.AllayToolDefinitions;
import dev.anvilcraft.plasticraft.allay.transfer.AllayLoungeNetwork.LoungeRoute;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobStore;
import dev.anvilcraft.plasticraft.blueprint.ConstructionTraffic;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * 远程休息室转运与综合工种调度服务(TODO 14)。
 *
 * 借调悦灵沿同维度休息室链逐跳移动:源室出库占 20 gt 通道,飞向下一跳休息室并入栈托管,
 * 中转室在下一次转发时把它继续送往任务协调室。最后一跳不入栈——协调室只有一条 20 gt 通道和
 * 16 个托管位,外室悦灵挤进去会堵住本室悦灵的出库与入栈,所以借调悦灵到达后以“客工”身份留在世界上:
 * homeLounge 指向协调室从而取得参与资格与取放点,却始终不占用协调室的通道与托管位。
 * 真正的家只记在 {@link AllayWorkRecord#originLounge()} 与实体转运字段上,不受宿主休息室改写 homeLounge 影响。
 * 只有能缩短完工时间才借调(见 {@link ConstructionTransferOptimizer}),任务结束或协调室撤销后沿链返程。
 * 借调与协调室本室出库同刻进行,节流只靠各室自己那条 20 gt 出库通道,因此多室是并行的 1 s 一只,
 * 而不是全局 1 s 一只。
 *
 * 在途台账是纯运行时状态,按服务器实例分桶且键为弱引用,存档退出重进不会沿用旧计数。
 */
public final class ConstructionTransferService {
    /** 世界内转运兜底的重试冷却(游戏刻)。 */
    static final long WORLD_TRANSIT_RETRY_TICKS = 20L;

    private static final Map<MinecraftServer, ServerTransit> SERVERS = new WeakHashMap<>();

    private ConstructionTransferService() {
    }

    /** 单个服务器实例的转运运行时状态。 */
    private static final class ServerTransit {
        /** 在途台账:jobId → allayId → 该悦灵可承担的能力集合。 */
        private final Map<UUID, Map<UUID, Set<AllayCapability>>> inTransit = new HashMap<>();
    }

    /** 一个可出借的源室及其路由与预计转运耗时。 */
    private record BorrowCandidate(
        AllayLoungeBlockEntity source,
        Predicate<AllayWorkRecord> match,
        LoungeRoute route,
        double transferTicks
    ) {
    }

    /** 一条待转发的在途记录及其目标;{@code homebound} 为 true 表示返程而非奔赴协调室。 */
    private record TransitHop(AllayWorkRecord record, BlockPos target, UUID collaborator, boolean homebound) {
    }

    public static int inTransitCount(MinecraftServer server, UUID jobId) {
        synchronized (SERVERS) {
            Map<UUID, Set<AllayCapability>> entries = state(server).inTransit.get(jobId);
            return entries == null ? 0 : entries.size();
        }
    }

    public static int inTransitCount(MinecraftServer server, UUID jobId, AllayCapability capability) {
        synchronized (SERVERS) {
            Map<UUID, Set<AllayCapability>> entries = state(server).inTransit.get(jobId);
            if (entries == null || entries.isEmpty()) return 0;
            int count = 0;
            for (Set<AllayCapability> capabilities : entries.values()) {
                if (capabilities.contains(capability)) count++;
            }
            return count;
        }
    }

    /** 任务结束(完成/取消/暂停/解除认领)时清理在途台账。 */
    public static void onJobEnded(MinecraftServer server, UUID jobId) {
        synchronized (SERVERS) {
            state(server).inTransit.remove(jobId);
        }
    }

    /**
     * 悦灵真正离开世界(死亡、被清除)时不再计入在途上限。
     * 逐跳入栈托管属于正常转运环节,调用方必须避开,否则每一跳都会清空台账。
     */
    public static void onEntityRemove(MinecraftServer server, UUID allayId) {
        forgetTransit(server, allayId);
    }

    /** 玩家从休息室手动放出时解除转运台账，随后该悦灵按普通无室实体运行。 */
    public static void detachManualRelease(WorkingAllayEntity worker) {
        worker.setOriginLounge(null);
        worker.setTransitJob(null);
        worker.setNextTransitRetryTick(Long.MIN_VALUE);
        if (worker.level() instanceof ServerLevel level) {
            forgetTransit(level.getServer(), worker.getUUID());
        }
    }

    /**
     * 借调决策入口:由中央任务调度器在“可并行前沿仍开放”时调用,与协调室本室出库同刻推进。
     * 一次反向路由给出全部候选源室的跳数与沿链累计距离,近室优先,凡按预计完工时间仍有收益的源室
     * 本轮都各出库一只——每室自带 20 gt 出库通道,因此各室天然保持 1 s 一只的节奏并彼此并行,
     * 不再由单一全局冷却把多室出库按距离串成一条队。
     *
     * @param remainingOps  该能力当前剩余未租操作数
     * @param loadedWorkers 已到场且可承担该能力的悦灵数(不含在途,在途由本服务自行计入)
     */
    public static void considerBorrow(
        ServerLevel level,
        ConstructionJob job,
        AllayLoungeBlockEntity coordinator,
        AllayCapability capability,
        Predicate<AllayWorkRecord> recordPredicate,
        int remainingOps,
        int loadedWorkers
    ) {
        if (remainingOps <= 0) return;
        MinecraftServer server = level.getServer();
        UUID jobId = job.jobId();
        BlockPos coordinatorPos = coordinator.getBlockPos();
        Map<BlockPos, LoungeRoute> routes = AllayLoungeNetwork.routesTo(level, coordinatorPos, job.owner(), true);
        if (routes.isEmpty()) return;
        List<BorrowCandidate> candidates = new ArrayList<>();
        for (AllayLoungeBlockEntity source : AllayLoungeNetwork.lounges(level)) {
            BlockPos from = source.getBlockPos();
            if (from.equals(coordinatorPos)) continue;
            LoungeRoute route = routes.get(from);
            if (route == null || route.hops() <= 0 || route.next() == null) continue;
            // 出库通道忙的源室这一刻借不出来,直接看下一个候选
            if (source.isBayBusy()) continue;
            if (isCoordinatingActiveJob(server, source)) continue;
            Predicate<AllayWorkRecord> match = borrowable(recordPredicate, from);
            if (!source.hasHosted(match)) continue;
            candidates.add(new BorrowCandidate(
                source,
                match,
                route,
                ConstructionTransferOptimizer.transferTicks(route.hops(), route.distance())
            ));
        }
        if (candidates.isEmpty()) return;
        // 近室先出:收益判据随转运耗时与工人数单调变严,一旦某室无收益,更远的室也不必再看
        candidates.sort(Comparator.comparingDouble(BorrowCandidate::transferTicks));
        int inTransit = inTransitCount(server, jobId, capability);
        for (BorrowCandidate candidate : candidates) {
            if (!ConstructionTransferOptimizer.isBeneficial(
                loadedWorkers,
                inTransit,
                remainingOps,
                candidate.transferTicks()
            )) {
                break;
            }
            WorkingAllayEntity worker = candidate.source().tryLaunchFor(candidate.match());
            if (worker == null) continue;
            inTransit++;
            worker.setOriginLounge(candidate.source().getBlockPos());
            worker.setTransitJob(jobId);
            worker.setNextTransitRetryTick(Long.MIN_VALUE);
            noteInTransit(server, jobId, worker);
            if (!advanceTo(level, worker, candidate.route().next(), coordinatorPos)) {
                // 下一跳此刻无法接收:不能继续把源室当作当前宿主,否则世界兜底会误判为已在家而永不重试
                worker.setHomeLounge(null);
            }
        }
    }

    /**
     * 休息室托管侧的转运推进,挂在休息室 serverTick。先按记录状态做台账维护:
     * 到家清两字段、任务结束或协调室撤销则转返程;再挑一条待转发记录出库,
     * 每 tick 最多一只,由 20 gt 单出库通道自然节流。
     *
     * 带 origin 且不在家的记录一律尽快转发回家,不因宿主室正在协调任务而留用——
     * 借调悦灵在协调室是客工而非托管,任何滞留在别室栈里的外来记录都只是玩家召回等意外路径。
     */
    public static void tickLoungeTransit(ServerLevel level, AllayLoungeBlockEntity lounge) {
        List<AllayWorkRecord> records = lounge.hosted();
        if (records.isEmpty()) return;
        MinecraftServer server = level.getServer();
        BlockPos here = lounge.getBlockPos();
        List<TransitHop> pending = new ArrayList<>();
        for (AllayWorkRecord record : records) {
            if (record.originLounge().isEmpty() && record.transitJob().isEmpty()) continue;
            UUID transitJob = record.transitJob().orElse(null);
            if (transitJob != null) {
                ConstructionJob job = ConstructionJobIndex.get(server).job(transitJob);
                BlockPos target = job != null && job.isActive() ? coordinatorOf(server, transitJob) : null;
                if (target == null || target.equals(here)) {
                    // 任务已结束、协调室被撤销,或已抵达协调室:清 transitJob 但保留 origin,后续按普通托管记录处理
                    dropTransitJob(lounge, record);
                    forgetTransit(server, record.entityId());
                    continue;
                }
                // 沿链每一站都补登记,热重载或异常路径丢台账后可自愈
                noteInTransit(server, transitJob, record);
                pending.add(new TransitHop(record, target, job.owner(), false));
                continue;
            }
            BlockPos origin = record.originLounge().map(BlockPos::of).orElse(null);
            if (origin == null) continue;
            if (origin.equals(here)) {
                // 到家:恢复普通托管记录
                clearTransitFields(lounge, record);
                forgetTransit(server, record.entityId());
                continue;
            }
            if (originGone(level, origin)) {
                // 原休息室已被破坏或失去认领:就地落户,免得记录永远标着回不去的家
                clearTransitFields(lounge, record);
                forgetTransit(server, record.entityId());
                continue;
            }
            UUID workerOwner = record.owner().orElse(null);
            if (workerOwner == null) continue;
            pending.add(new TransitHop(record, origin, workerOwner, true));
        }
        if (pending.isEmpty() || lounge.isBayBusy()) return;
        for (TransitHop hop : pending) {
            LoungeRoute route = AllayLoungeNetwork
                .routesTo(level, hop.target(), hop.collaborator(), true)
                .get(here);
            if (route == null || route.next() == null) {
                if (!hop.homebound()) {
                    // 协调室这一刻不可达:撤销借调转为返程,避免在途台账长期占着参与上限与缺工判定
                    dropTransitJob(lounge, hop.record());
                    forgetTransit(server, hop.record().entityId());
                }
                continue;
            }
            WorkingAllayEntity worker = lounge.tryLaunchFor(
                candidate -> candidate.entityId().equals(hop.record().entityId())
            );
            if (worker == null) continue;
            // 返程终点是自己的家,可以入栈;奔赴协调室的最后一跳只登记为客工,不占协调室通道
            BlockPos coordinator = hop.homebound() ? null : hop.target();
            if (!advanceTo(level, worker, route.next(), coordinator)) {
                // 出库已经完成但下一跳暂时不可接收,清掉旧宿主标记以启用世界内重试
                worker.setHomeLounge(null);
                worker.setNextTransitRetryTick(Long.MIN_VALUE);
            }
            return;
        }
    }

    /**
     * 世界内转运悦灵的空闲判定:带转运字段、无租约与携带物、未被驱逐且不在入栈飞行中,
     * 并且当前宿主不可用(宿主缺失或已无法接收)——只有这种掉队状态才需要世界内兜底接链。
     */
    public static boolean isIdleTransitWorker(ServerLevel level, WorkingAllayEntity worker) {
        if (worker.originLoungePos() == null && worker.transitJobId().isEmpty()) return false;
        if (worker.assignedJobId().isPresent()
            || !worker.hostedCarry().isEmpty()
            || worker.hasCollectionItems()
            || worker.isEvacuating()
            || worker.flightState() != AllayFlightState.HOVERING) {
            return false;
        }
        BlockPos home = worker.homeLoungePos();
        if (home == null) return true;
        return !(level.getBlockEntity(home) instanceof AllayLoungeBlockEntity lounge && lounge.canHost(worker));
    }

    /** 世界内转运兜底:入栈失败、宿主被破坏或失权后,借调/返程悦灵沿转运图重新接链。 */
    public static void routeWorldWorkerHome(ServerLevel level, WorkingAllayEntity worker) {
        MinecraftServer server = level.getServer();
        long gameTime = level.getGameTime();
        if (worker.transitRetryCooldownActive(gameTime)) return;
        worker.setNextTransitRetryTick(gameTime + WORLD_TRANSIT_RETRY_TICKS);
        UUID collaborator = worker.getOwner().orElse(null);
        BlockPos target = transitTarget(level, worker);
        if (collaborator == null || target == null) {
            // 任务与原休息室都已不存在:清掉转运字段,恢复普通悦灵行为
            abandonTransit(server, worker);
            return;
        }
        // 仍在奔赴协调室时终点是任务现场而非家,抵达后转为客工
        BlockPos coordinator = worker.transitJobId().isPresent() ? target : null;
        if (coordinator == null && originGone(level, target)) {
            abandonTransit(server, worker);
            return;
        }
        if (withinHop(worker, target) && advanceTo(level, worker, target, coordinator)) return;
        BlockPos entry = null;
        int bestPriority = Integer.MAX_VALUE;
        int bestHops = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, LoungeRoute> candidate
            : AllayLoungeNetwork.routesTo(level, target, collaborator, true).entrySet()) {
            double distance = distanceSqr(worker, candidate.getKey());
            if (distance > AllayLoungeNetwork.HOP_RANGE_SQR) continue;
            int priority = relayPriority(server, level, candidate.getKey());
            int hops = candidate.getValue().hops();
            if (priority > bestPriority) continue;
            if (priority == bestPriority
                && (hops > bestHops || hops == bestHops && distance >= bestDistance)) {
                continue;
            }
            bestPriority = priority;
            bestHops = hops;
            bestDistance = distance;
            entry = candidate.getKey();
        }
        if (entry != null && advanceTo(level, worker, entry, coordinator)) return;
        // 图上暂时没有可用入口:先就近入栈存起来,之后由休息室继续转发,好过在世界里空转
        BlockPos nearest = nearestHostableNode(level, worker);
        if (nearest != null) advanceTo(level, worker, nearest, coordinator);
    }

    /**
     * 推进一跳。目标正是任务协调室时只登记为客工,其余情况入栈托管。
     *
     * @param coordinator 本次转运的任务协调室;返程或无任务时为 null
     */
    private static boolean advanceTo(
        ServerLevel level,
        WorkingAllayEntity worker,
        BlockPos next,
        @Nullable BlockPos coordinator
    ) {
        if (coordinator != null && coordinator.equals(next)) {
            return checkInAsGuest(level, worker, coordinator);
        }
        return worker.startDockingTo(next);
    }

    /**
     * 借调悦灵抵达协调室:不入栈、不排队、不占 20 gt 通道,只把 homeLounge 指向协调室,
     * 从而取得参与资格并以协调室下方容器为取放点。清 transitJob 让它由在途转为已到场,
     * 避免调度器把同一只悦灵既算在途又算到场而重复借调。
     */
    public static boolean checkInAsGuest(ServerLevel level, WorkingAllayEntity worker, BlockPos coordinator) {
        if (!(level.getBlockEntity(coordinator) instanceof AllayLoungeBlockEntity lounge)
            || !lounge.canHost(worker)) {
            return false;
        }
        worker.setHomeLounge(coordinator);
        worker.setTransitJob(null);
        worker.setNextTransitRetryTick(Long.MIN_VALUE);
        forgetTransit(level.getServer(), worker.getUUID());
        return true;
    }

    /** 客工:借自其他休息室、当前挂靠在别处的悦灵。它不属于当前宿主室,不能入栈占位。 */
    public static boolean isGuest(WorkingAllayEntity worker) {
        BlockPos origin = worker.originLoungePos();
        return origin != null && !origin.equals(worker.homeLoungePos());
    }

    /**
     * 客工无活可做时改为返程,而不是挤进协调室。原休息室已消失的客工就地落户,
     * 返回 false 让调用方按普通悦灵入栈当前宿主室。
     */
    public static boolean sendGuestHome(ServerLevel level, WorkingAllayEntity worker) {
        if (!isGuest(worker)) return false;
        MinecraftServer server = level.getServer();
        BlockPos origin = worker.originLoungePos();
        if (origin == null || originGone(level, origin)) {
            abandonTransit(server, worker);
            return false;
        }
        worker.clearAssignment(false);
        ConstructionTraffic.release(level, worker.getUUID());
        worker.setHomeLounge(null);
        worker.setTransitJob(null);
        worker.setNextTransitRetryTick(Long.MIN_VALUE);
        forgetTransit(server, worker.getUUID());
        routeWorldWorkerHome(level, worker);
        return true;
    }

    /** 只借调待在自己家里的记录:已带转运字段的悦灵不能被二次借调,否则再也回不到真正的家。 */
    private static Predicate<AllayWorkRecord> borrowable(Predicate<AllayWorkRecord> base, BlockPos source) {
        return record -> base.test(record)
            && record.transitJob().isEmpty()
            && record.originLounge().map(home -> BlockPos.of(home).equals(source)).orElse(true);
    }

    /**
     * 解析在途悦灵此刻应飞往何处:任务仍有效则去协调室,否则撤销借调转为返程。
     * 顺带补登在途台账,让世界内掉队的悦灵重新计入参与上限。
     */
    @Nullable
    private static BlockPos transitTarget(ServerLevel level, WorkingAllayEntity worker) {
        MinecraftServer server = level.getServer();
        UUID transitJob = worker.transitJobId().orElse(null);
        if (transitJob != null) {
            ConstructionJob job = ConstructionJobIndex.get(server).job(transitJob);
            BlockPos coordinator = job != null && job.isActive() ? coordinatorOf(server, transitJob) : null;
            if (coordinator != null) {
                noteInTransit(server, transitJob, worker);
                return coordinator;
            }
            worker.setTransitJob(null);
            forgetTransit(server, worker.getUUID());
        }
        return worker.originLoungePos();
    }

    @Nullable
    private static BlockPos coordinatorOf(MinecraftServer server, UUID jobId) {
        ConstructionJobProgress progress = ConstructionJobStore.get(server).get(jobId);
        return progress == null ? null : progress.coordinatorLounge();
    }

    private static boolean isCoordinatingActiveJob(MinecraftServer server, AllayLoungeBlockEntity source) {
        UUID diskJob = source.diskJobId();
        if (diskJob == null) return false;
        ConstructionJob job = ConstructionJobIndex.get(server).job(diskJob);
        // 自身正在协调活动任务的休息室不出借,避免抽干仍在施工的现场
        return job != null && job.isActive();
    }

    /** 原休息室是否已经不在了:只有该位置确实装载、且不再是可接收的休息室才算消失。 */
    private static boolean originGone(ServerLevel level, BlockPos origin) {
        if (!level.isLoaded(origin)) return false;
        return !(level.getBlockEntity(origin) instanceof AllayLoungeBlockEntity lounge) || lounge.owner() == null;
    }

    private static void abandonTransit(MinecraftServer server, WorkingAllayEntity worker) {
        worker.setTransitJob(null);
        worker.setOriginLounge(null);
        forgetTransit(server, worker.getUUID());
    }

    private static boolean withinHop(WorkingAllayEntity worker, BlockPos target) {
        return distanceSqr(worker, target) <= AllayLoungeNetwork.HOP_RANGE_SQR;
    }

    private static double distanceSqr(WorkingAllayEntity worker, BlockPos pos) {
        return worker.position().distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    @Nullable
    private static BlockPos nearestHostableNode(ServerLevel level, WorkingAllayEntity worker) {
        MinecraftServer server = level.getServer();
        BlockPos nearest = null;
        int bestPriority = Integer.MAX_VALUE;
        double bestDistance = Double.MAX_VALUE;
        for (AllayLoungeBlockEntity lounge : AllayLoungeNetwork.lounges(level)) {
            if (!lounge.canHost(worker) || !lounge.canAcceptDocking()) continue;
            BlockPos pos = lounge.getBlockPos();
            double distance = distanceSqr(worker, pos);
            if (distance > AllayLoungeNetwork.HOP_RANGE_SQR) continue;
            int priority = isCoordinatingActiveJob(server, lounge) ? 1 : 0;
            if (priority > bestPriority || priority == bestPriority && distance >= bestDistance) continue;
            bestPriority = priority;
            bestDistance = distance;
            nearest = pos;
        }
        return nearest;
    }

    /** 中转优先级:正在协调活动任务的休息室排在后面,免得借调悦灵挤占施工现场的通道与托管位。 */
    private static int relayPriority(MinecraftServer server, ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof AllayLoungeBlockEntity lounge
            && isCoordinatingActiveJob(server, lounge) ? 1 : 0;
    }

    private static void dropTransitJob(AllayLoungeBlockEntity lounge, AllayWorkRecord record) {
        lounge.updateHostedRecord(
            record.entityId(),
            current -> rebuild(current, current.originLounge(), Optional.empty())
        );
    }

    private static void clearTransitFields(AllayLoungeBlockEntity lounge, AllayWorkRecord record) {
        lounge.updateHostedRecord(
            record.entityId(),
            current -> rebuild(current, Optional.empty(), Optional.empty())
        );
    }

    private static AllayWorkRecord rebuild(AllayWorkRecord record, Optional<Long> origin, Optional<UUID> transit) {
        return new AllayWorkRecord(
            record.entityId(),
            record.hardHat(),
            record.heldTool(),
            record.owner(),
            record.shortageStrategy(),
            record.collectionInventory(),
            record.assignedJobId(),
            record.hostedCarry(),
            record.customName(),
            origin,
            transit
        );
    }

    private static ServerTransit state(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, ignored -> new ServerTransit());
    }

    private static void noteInTransit(MinecraftServer server, UUID jobId, WorkingAllayEntity worker) {
        noteInTransit(server, jobId, worker.getUUID(), worker.toolDefinition().capabilities());
    }

    private static void noteInTransit(MinecraftServer server, UUID jobId, AllayWorkRecord record) {
        noteInTransit(
            server,
            jobId,
            record.entityId(),
            AllayToolDefinitions.fromHeldItem(record.heldTool()).capabilities()
        );
    }

    private static void noteInTransit(
        MinecraftServer server,
        UUID jobId,
        UUID allayId,
        Set<AllayCapability> capabilities
    ) {
        synchronized (SERVERS) {
            ServerTransit transit = state(server);
            // 同一只悦灵只能算一份在途,先从其他任务的台账里摘掉,避免重复占用参与上限
            for (Map.Entry<UUID, Map<UUID, Set<AllayCapability>>> entry : transit.inTransit.entrySet()) {
                if (!entry.getKey().equals(jobId)) entry.getValue().remove(allayId);
            }
            transit.inTransit
                .computeIfAbsent(jobId, ignored -> new HashMap<>())
                .put(allayId, Set.copyOf(capabilities));
        }
    }

    private static void forgetTransit(MinecraftServer server, UUID allayId) {
        synchronized (SERVERS) {
            for (Map<UUID, Set<AllayCapability>> entries : state(server).inTransit.values()) {
                entries.remove(allayId);
            }
        }
    }
}
