package dev.anvilcraft.plasticraft.allay.observation;

import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBuildOp;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobProgress;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 施工调度与观察覆盖之间的接口层:把"哪些目标能派发"、"还缺几个观察窗口"和"窗口租给了谁"
 * 收在一处,调度器只读结论。
 *
 * <p>规划沿剥离顺序增量推进:已经处在实体刻的区块直接复用,不占观察悦灵;
 * 前沿上第一个尚未进入实体刻的区块就是下一个窗口中心,观察者先把窗口建立起来,
 * 其它工种才进得去。窗口不足时不失败,只是能派发的目标变少,前沿自然收缩。
 */
public final class ObservationCoverageService {
    /**
     * 单份任务同时持有的观察窗口上限。一个九柱窗口水平已有 48×48,远超单轮派发前沿的规模,
     * 窗口再多只是白占观察悦灵;超出上限的作业区排队等前沿推进过去。
     */
    public static final int MAX_WINDOWS = 4;
    /** 规划节流间隔:一次规划要走一遍未完成操作表,与区块票校验同频。 */
    private static final int PLAN_INTERVAL_TICKS = ObservationChunkLoader.VERIFY_INTERVAL_TICKS;

    private static final Map<MinecraftServer, Map<UUID, JobPlan>> PLANS = new WeakHashMap<>();

    private ObservationCoverageService() {
    }

    /**
     * 一次规划的结论。
     *
     * @param phase    规划所依据的施工阶段,决定哪些操作算相关
     * @param workable 是否还有相关操作落在实体刻窗口内,false 表示这一刻派谁去都无活可干
     * @param missing  仍然没有观察者认领的窗口中心,按剥离顺序排列
     * @param leased   已指派观察者的窗口数,包含观察者还在飞往目标柱的窗口
     */
    private record JobPlan(long tick, byte phase, boolean workable, List<ChunkPos> missing, int leased) {
        private boolean needsWindow() {
            return !this.missing.isEmpty() || this.leased > 0;
        }
    }

    /**
     * 目标所在区块柱当前是否处在实体刻窗口内。任何派发扫描都必须先过这道闸:
     * 未加载区块既没有工人能到场,读取方块状态又会把大面积世界隐式拽进内存。
     */
    public static boolean isWorkable(ServerLevel level, BlockPos target) {
        return ObservationCoverage.isTicking(level, target);
    }

    /** 协调室自身所在柱是否处在实体刻窗口内;库存访问、协调、入库、出库和远程转运都以此为前提。 */
    public static boolean isCoordinatorTicking(ServerLevel level, ConstructionJobProgress progress) {
        if (!progress.hasCoordinator()) return true;
        return ObservationCoverage.isTicking(level, new ChunkPos(progress.coordinatorLounge()));
    }

    /**
     * 按节流间隔重算一份任务的窗口需求并指派观察者。调度器每刻调用,内部自行限频。
     *
     * @param phase 相关操作集合所依据的阶段;等待观察者时必须传入将要恢复到的阶段,
     *              否则相关集合会随状态来回切换,任务在两个状态间反复跳。
     */
    public static void tick(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        byte phase
    ) {
        Map<UUID, JobPlan> plans = plans(server);
        JobPlan cached = plans.get(job.jobId());
        long gameTime = level.getGameTime();
        // 阶段一变,相关操作集合就整体换了,必须立刻重算而不是等节流窗口
        if (cached != null && cached.phase() == phase && gameTime - cached.tick() < PLAN_INTERVAL_TICKS) {
            return;
        }
        plans.put(job.jobId(), plan(server, level, job, progress, phase, gameTime));
    }

    /** 是否应当停在等待观察者:确实需要窗口,而且当前一个能干的目标都没有。 */
    public static boolean isWaitingObserver(MinecraftServer server, ConstructionJob job) {
        JobPlan plan = plans(server).get(job.jobId());
        return plan != null && !plan.workable() && plan.needsWindow();
    }

    /** 还缺几名观察者。现场闲置的观察悦灵在规划时已经指派完,这里只剩需要出库或借调的缺口。 */
    public static int wantedObservers(MinecraftServer server, ConstructionJob job) {
        JobPlan plan = plans(server).get(job.jobId());
        return plan == null ? 0 : plan.missing().size();
    }

    /**
     * 是否还有窗口在等这只观察者接手。规划按 {@link #PLAN_INTERVAL_TICKS} 节流,刚出库或刚到场的
     * 观察者要等下一次规划才拿到租约;这段空窗里若让它当刻返库,出库与入库会各占一次休息室 20 gt
     * 通道,任务只会在出库/入库之间空转,永远等不到观察者飞到窗口上。
     */
    public static boolean awaitsWindow(ServerLevel level, WorkingAllayEntity worker) {
        MinecraftServer server = level.getServer();
        if (ObservationLoadingIndex.get(server).leaseOf(worker.getUUID()) != null) return false;
        UUID owner = worker.getOwner().orElse(null);
        if (owner == null) return false;
        ConstructionJob job = ConstructionJobIndex.get(server).activeJobOf(server, owner).orElse(null);
        if (job == null) return false;
        JobPlan plan = plans(server).get(job.jobId());
        return plan != null && !plan.missing().isEmpty() && isAvailableObserver(level, job, worker);
    }

    /** 任务结束或暂停时清空租约与规划缓存,观察者下一刻各自返航。 */
    public static void releaseJob(MinecraftServer server, UUID jobId) {
        plans(server).remove(jobId);
        ObservationLoadingIndex.get(server).removeLeases(jobId);
    }

    /**
     * 该工人应当结队跟随的观察悦灵。窗口还没完全进入实体刻时跟着观察者走,
     * 比回库再出库复工更快;跟在观察者身后也始终待在它自己的九柱里,不会飞进未加载区块断刻。
     */
    @Nullable
    public static WorkingAllayEntity convoyObserver(
        ServerLevel level,
        ConstructionJob job,
        WorkingAllayEntity worker
    ) {
        WorkingAllayEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ObservationLease lease : ObservationLoadingIndex.get(level).leases(job.jobId())) {
            if (!lease.dimension().equals(level.dimension())) continue;
            if (ObservationChunkLoader.isCoverageTicking(level, lease.center())) continue;
            if (!(level.getEntity(lease.observer()) instanceof WorkingAllayEntity observer)) continue;
            if (!ObservationCoverage.isEligible(observer)) continue;
            double distance = worker.position().distanceToSqr(observer.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = observer;
            }
        }
        return best;
    }

    private static JobPlan plan(
        MinecraftServer server,
        ServerLevel level,
        ConstructionJob job,
        ConstructionJobProgress progress,
        byte phase,
        long gameTime
    ) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(server);
        UUID jobId = progress.jobId();
        List<ObservationLease> leases = index.leases(jobId);
        List<ChunkPos> wanted = new ArrayList<>();
        Set<ChunkPos> useful = new HashSet<>();
        boolean coordinatorTicking = true;
        if (progress.hasCoordinator()) {
            ChunkPos coordinator = new ChunkPos(progress.coordinatorLounge());
            if (!ObservationCoverage.isTicking(level, coordinator)) {
                selfLoadCoordinator(level, progress);
            }
            coordinatorTicking = ObservationCoverage.isTicking(level, coordinator);
            markUseful(leases, coordinator, useful);
            // 协调室这一柱是硬需求,不占窗口预算:没有它连库存都读不到,整份任务动不了
            if (!coordinatorTicking && !coveredByLease(leases, coordinator)) {
                wanted.add(coordinator);
            }
        }
        boolean workable = false;
        int budget = Math.max(0, MAX_WINDOWS - leases.size() - wanted.size());
        for (ConstructionBuildOp op : progress.operations()) {
            if (!isRelevant(phase, op)) continue;
            ChunkPos chunk = new ChunkPos(op.pos());
            markUseful(leases, chunk, useful);
            if (ObservationCoverage.isTicking(level, chunk)) {
                workable = true;
            } else if (budget > 0 && !coveredByLease(leases, chunk) && !coveredByCenter(wanted, chunk)) {
                wanted.add(chunk);
                budget--;
            }
            // 已持有的窗口全部确认还在罩着待办之前不能提前收尾:漏掉一条 markUseful 就会把
            // 正在被工人依赖的窗口当成冗余撤掉,那一柱当刻停刻,里面的悦灵连带冻住
            if (workable && budget <= 0 && useful.size() == leases.size()) break;
        }
        // 不再罩住任何未完成操作的租约立即退掉,让观察者跟着前沿走而不是钉在原地
        for (ObservationLease lease : leases) {
            if (!useful.contains(lease.center())) index.removeLease(jobId, lease.center());
        }
        List<ChunkPos> missing = new ArrayList<>();
        for (ChunkPos center : wanted) {
            WorkingAllayEntity observer = pickObserver(level, job, index, center);
            if (observer == null) {
                missing.add(center);
                continue;
            }
            index.addLease(jobId, new ObservationLease(level.dimension(), center, observer.getUUID()));
        }
        return new JobPlan(
            gameTime,
            phase,
            coordinatorTicking && workable,
            List.copyOf(missing),
            index.leases(jobId).size()
        );
    }

    /**
     * 协调室在非实体刻区块里方块实体根本不会 tick,只能由规划代它重算一次覆盖。
     * 少了这一步,托管着有效观察者的协调室会永远等不到唤醒自己的那一刻。
     */
    private static void selfLoadCoordinator(ServerLevel level, ConstructionJobProgress progress) {
        if (level.getBlockEntity(progress.coordinatorLounge()) instanceof AllayLoungeBlockEntity lounge) {
            ObservationChunkLoader.syncLounge(lounge);
        }
    }

    /** 挑一只离目标柱最近的闲置观察悦灵。已经接了别的窗口的不再重复指派。 */
    @Nullable
    private static WorkingAllayEntity pickObserver(
        ServerLevel level,
        ConstructionJob job,
        ObservationLoadingIndex index,
        ChunkPos center
    ) {
        WorkingAllayEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (WorkingAllayEntity worker : ConstructionJobController.loadedWorkers(level, job.owner())) {
            if (!isAvailableObserver(level, job, worker)) continue;
            if (index.leaseOf(worker.getUUID()) != null) continue;
            double dx = worker.getX() - (center.getMiddleBlockX() + 0.5D);
            double dz = worker.getZ() - (center.getMiddleBlockZ() + 0.5D);
            double distance = dx * dx + dz * dz;
            if (distance < bestDistance || distance == bestDistance && isEarlier(worker, best)) {
                bestDistance = distance;
                best = worker;
            }
        }
        return best;
    }

    /**
     * 观察者的可用性比普通工种宽松:窗口不经过协调室出库通道,也不需要绑定协调室,
     * 否则协调室自己那一柱一旦停刻,就再没有任何一只悦灵有资格去覆盖它。
     */
    private static boolean isAvailableObserver(ServerLevel level, ConstructionJob job, WorkingAllayEntity worker) {
        if (!ObservationCoverage.isEligible(worker)) return false;
        if (worker.isEvacuating() || worker.transitJobId().isPresent()) return false;
        if (!job.dimension().equals(level.dimension())) return false;
        return worker.assignedJobId().filter(assigned -> !assigned.equals(job.jobId())).isEmpty();
    }

    private static boolean isEarlier(WorkingAllayEntity worker, @Nullable WorkingAllayEntity current) {
        return current == null || worker.getUUID().compareTo(current.getUUID()) < 0;
    }

    /** 当前阶段真正要靠观察窗口才能推进的操作。 */
    private static boolean isRelevant(byte phase, ConstructionBuildOp op) {
        if (op.status() == ConstructionBuildOp.Status.DELIVERED
            || op.status() == ConstructionBuildOp.Status.SKIPPED) {
            return false;
        }
        if (phase == ConstructionJob.STATE_SEALING_FLUID) {
            return op.kind() == ConstructionBuildOp.Kind.SEAL;
        }
        if (phase == ConstructionJob.STATE_DEMOLISHING
            || phase == ConstructionJob.STATE_WAITING_DEMOLITION
            || phase == ConstructionJob.STATE_COLLECTING_DEBRIS) {
            return op.kind() == ConstructionBuildOp.Kind.DEMOLISH && !op.shell();
        }
        if (op.kind() == ConstructionBuildOp.Kind.DEMOLISH) return !op.shell();
        return op.kind() == ConstructionBuildOp.Kind.PLACE
            || op.kind() == ConstructionBuildOp.Kind.CONTENT
            || op.kind() == ConstructionBuildOp.Kind.FLUID
            || op.kind() == ConstructionBuildOp.Kind.ENTITY
            || op.kind() == ConstructionBuildOp.Kind.DECORATE
            || op.kind() == ConstructionBuildOp.Kind.SEAL;
    }

    private static void markUseful(List<ObservationLease> leases, ChunkPos chunk, Set<ChunkPos> useful) {
        for (ObservationLease lease : leases) {
            if (ObservationCoverage.covers(lease.center(), chunk)) useful.add(lease.center());
        }
    }

    private static boolean coveredByLease(List<ObservationLease> leases, ChunkPos chunk) {
        for (ObservationLease lease : leases) {
            if (ObservationCoverage.covers(lease.center(), chunk)) return true;
        }
        return false;
    }

    private static boolean coveredByCenter(List<ChunkPos> centers, ChunkPos chunk) {
        for (ChunkPos center : centers) {
            if (ObservationCoverage.covers(center, chunk)) return true;
        }
        return false;
    }

    private static Map<UUID, JobPlan> plans(MinecraftServer server) {
        return PLANS.computeIfAbsent(server, key -> new HashMap<>());
    }
}
