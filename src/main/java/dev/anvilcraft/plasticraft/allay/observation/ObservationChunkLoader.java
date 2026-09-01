package dev.anvilcraft.plasticraft.allay.observation;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.common.world.chunk.TicketHelper;
import net.neoforged.neoforge.common.world.chunk.TicketSet;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 观察加载服务:把观察覆盖翻译成九张完整实体刻区块票,并维护跨区块交接、重启恢复与资格校验。
 *
 * <p>一张距离 2 的票只把自己那一柱抬到实体刻等级,邻区会逐级衰减,所以九柱必须各发一张票。
 * 世界观察悦灵按 UUID 持票、休息室按方块坐标持票,两种持票人落在互不影响的票据集合里,
 * 因此重叠覆盖天然按持票人引用计数,交接只要"先加新票再撤旧票"就不会断刻。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ObservationChunkLoader {
    /** 资格校验周期,同时用于休息室托管记录扫描的节流。 */
    public static final int VERIFY_INTERVAL_TICKS = 20;
    /** 重启后等待实体或休息室方块实体加载的宽限期,超时仍未出现即视为失效记录并撤票。 */
    private static final int VERIFY_GRACE_TICKS = 200;
    /** 旧票最长扣留时间。正常情况下新覆盖一两刻内就进入实体刻,该上限只防止异常时旧票永久滞留。 */
    private static final int RELEASE_CONFIRM_TICKS = 100;

    private static final TicketController CONTROLLER = new TicketController(
        AnvilcraftPlasticraft.of("observation"),
        ObservationChunkLoader::validateTickets
    );

    private static final Map<MinecraftServer, ServerState> STATES = new WeakHashMap<>();

    private ObservationChunkLoader() {
    }

    public static void registerTicketController(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerState state = state(server);
        if (!state.reconciled) {
            state.reconciled = true;
            reconcile(server, state);
            state.nextVerifyTick = server.overworld().getGameTime() + VERIFY_INTERVAL_TICKS;
        }
        processPendingRelease(server, state);
        if (server.overworld().getGameTime() >= state.nextVerifyTick) {
            state.nextVerifyTick = server.overworld().getGameTime() + VERIFY_INTERVAL_TICKS;
            verifyAll(server, state);
        }
    }

    /** 按观察悦灵当前位置同步覆盖;资格一旦丢失立即撤票。 */
    public static void syncObserver(WorkingAllayEntity worker) {
        if (!(worker.level() instanceof ServerLevel level)) return;
        ObservationOwner owner = ObservationOwner.observer(level.dimension(), worker.getUUID());
        if (!ObservationCoverage.isEligible(worker)) {
            revoke(level, owner);
            return;
        }
        apply(level, owner, ObservationCoverage.centerOf(worker));
    }

    public static void revokeObserver(ServerLevel level, UUID entityId) {
        revoke(level, ObservationOwner.observer(level.dimension(), entityId));
    }

    /** 立即同步休息室覆盖,用于出入库交接等不能等节流的时刻。 */
    public static void syncLounge(AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level)) return;
        ObservationOwner owner = ObservationOwner.lounge(level.dimension(), lounge.getBlockPos());
        if (!ObservationCoverage.isEligible(lounge)) {
            revoke(level, owner);
            return;
        }
        apply(level, owner, ObservationCoverage.centerOf(lounge.getBlockPos()));
    }

    /**
     * 节流版休息室同步。托管记录扫描每刻都做没有意义,按坐标错峰分摊到校验周期里;
     * 真正要求即时生效的出入库交接另有直接调用。
     */
    public static void syncLoungeThrottled(AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level)) return;
        long phase = Math.floorMod(lounge.getBlockPos().asLong(), VERIFY_INTERVAL_TICKS);
        if (Math.floorMod(level.getGameTime(), VERIFY_INTERVAL_TICKS) != phase) return;
        syncLounge(lounge);
    }

    public static void revokeLounge(ServerLevel level, BlockPos pos) {
        revoke(level, ObservationOwner.lounge(level.dimension(), pos));
    }

    /** 该区块柱当前是否处于实体刻,不区分是观察覆盖还是玩家等其它加载源。 */
    public static boolean isTicking(ServerLevel level, ChunkPos chunk) {
        return ObservationCoverage.isTicking(level, chunk);
    }

    /** 以 center 为中心的九柱是否全部进入实体刻。 */
    public static boolean isCoverageTicking(ServerLevel level, ChunkPos center) {
        for (ChunkPos column : ObservationCoverage.columns(center)) {
            if (!ObservationCoverage.isTicking(level, column)) return false;
        }
        return true;
    }

    /** 该区块柱是否落在任意一份有效观察覆盖内。 */
    public static boolean hasObservationCoverage(ServerLevel level, ChunkPos chunk) {
        return ObservationLoadingIndex.get(level).covered(level.dimension(), chunk);
    }

    private static void apply(ServerLevel level, ObservationOwner owner, ChunkPos desired) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        ServerState state = state(level.getServer());
        ChunkPos stored = index.center(owner);
        if (stored == null) {
            dropStaleDimensionRecords(level, index, owner);
            for (ChunkPos column : ObservationCoverage.columns(desired)) {
                force(level, owner, column, true);
            }
            index.putCoverage(owner, desired);
            state.verifyDeadline.remove(owner);
            return;
        }
        state.verifyDeadline.remove(owner);
        if (stored.equals(desired)) return;
        // 先申请并持有新中心的九柱,旧柱转入待撤集,确认新覆盖实体刻后才释放,
        // 否则跨区块瞬间会出现空窗,让跟随的工人和自己一起掉出实体刻。
        Set<ChunkPos> held = new LinkedHashSet<>(ObservationCoverage.columns(stored));
        PendingRelease pending = state.pending.get(owner);
        if (pending != null) held.addAll(pending.columns);
        List<ChunkPos> target = ObservationCoverage.columns(desired);
        for (ChunkPos column : target) {
            if (!held.contains(column)) force(level, owner, column, true);
        }
        held.removeAll(target);
        index.putCoverage(owner, desired);
        if (held.isEmpty()) {
            state.pending.remove(owner);
            return;
        }
        PendingRelease next = pending == null ? new PendingRelease() : pending;
        next.columns.clear();
        next.columns.addAll(held);
        // 连续移动不重新计时,否则一路飞行会把上限拖成无限期扣留
        if (next.deadline == 0L) next.deadline = level.getGameTime() + RELEASE_CONFIRM_TICKS;
        state.pending.put(owner, next);
    }

    /** 同一只悦灵换维度后旧维度的记录不能留着,否则会在两个维度各占九柱。 */
    private static void dropStaleDimensionRecords(ServerLevel level, ObservationLoadingIndex index, ObservationOwner owner) {
        if (!(owner instanceof ObservationOwner.Observer observer)) return;
        MinecraftServer server = level.getServer();
        for (ObservationOwner existing : index.owners()) {
            if (!(existing instanceof ObservationOwner.Observer other)) continue;
            if (!other.entityId().equals(observer.entityId())) continue;
            if (other.dimension().equals(observer.dimension())) continue;
            ServerLevel staleLevel = server.getLevel(other.dimension());
            if (staleLevel == null) {
                index.removeCoverage(existing);
                continue;
            }
            revoke(staleLevel, existing);
        }
    }

    private static void revoke(ServerLevel level, ObservationOwner owner) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level);
        ServerState state = state(level.getServer());
        ChunkPos center = index.center(owner);
        PendingRelease pending = state.pending.remove(owner);
        state.verifyDeadline.remove(owner);
        if (center != null) {
            for (ChunkPos column : ObservationCoverage.columns(center)) {
                force(level, owner, column, false);
            }
        }
        if (pending != null) {
            for (ChunkPos column : pending.columns) {
                force(level, owner, column, false);
            }
        }
        if (!index.removeCoverage(owner)) return;
        // 观察者失效后任务必须立刻重新规划,不能守着一份没有持票人的租约
        if (owner instanceof ObservationOwner.Observer observer) {
            index.releaseObserverLeases(observer.entityId());
        }
    }

    private static void processPendingRelease(MinecraftServer server, ServerState state) {
        if (state.pending.isEmpty()) return;
        ObservationLoadingIndex index = ObservationLoadingIndex.get(server);
        Iterator<Map.Entry<ObservationOwner, PendingRelease>> iterator = state.pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ObservationOwner, PendingRelease> entry = iterator.next();
            ObservationOwner owner = entry.getKey();
            ServerLevel level = server.getLevel(owner.dimension());
            ChunkPos center = index.center(owner);
            if (level == null || center == null) {
                iterator.remove();
                continue;
            }
            boolean expired = level.getGameTime() >= entry.getValue().deadline;
            if (!expired && !isCoverageTicking(level, center)) continue;
            for (ChunkPos column : entry.getValue().columns) {
                force(level, owner, column, false);
            }
            iterator.remove();
        }
    }

    /**
     * 服务器启动后的首刻按索引重新发票。NeoForge 自身也会恢复持久化的票,这里的补发对未发生分叉的
     * 记录是空操作;真正的作用是让恢复只以世界级索引为准,而不是等悦灵自己唤醒自己——它所在的区块
     * 恰恰要靠这张票才会加载。
     */
    private static void reconcile(MinecraftServer server, ServerState state) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(server);
        long grace = server.overworld().getGameTime() + VERIFY_GRACE_TICKS;
        for (ObservationOwner owner : index.owners()) {
            ServerLevel level = server.getLevel(owner.dimension());
            ChunkPos center = index.center(owner);
            if (level == null || center == null) {
                index.removeCoverage(owner);
                continue;
            }
            for (ChunkPos column : ObservationCoverage.columns(center)) {
                force(level, owner, column, true);
            }
            state.verifyDeadline.put(owner, grace);
        }
    }

    private static void verifyAll(MinecraftServer server, ServerState state) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(server);
        for (ObservationOwner owner : index.owners()) {
            ServerLevel level = server.getLevel(owner.dimension());
            if (level == null) {
                index.removeCoverage(owner);
                state.pending.remove(owner);
                state.verifyDeadline.remove(owner);
                continue;
            }
            if (isEligible(level, owner)) {
                state.verifyDeadline.remove(owner);
                continue;
            }
            // 持票人尚未加载出来时给宽限期;已经加载但资格不再成立(例如换掉了主手望远镜)立即撤票
            if (!isPresent(level, owner)) {
                Long deadline = state.verifyDeadline.get(owner);
                if (deadline != null && level.getGameTime() < deadline) continue;
            }
            revoke(level, owner);
        }
    }

    private static boolean isPresent(ServerLevel level, ObservationOwner owner) {
        if (owner instanceof ObservationOwner.Observer observer) {
            return level.getEntity(observer.entityId()) instanceof WorkingAllayEntity;
        }
        if (owner instanceof ObservationOwner.Lounge lounge) {
            return level.isLoaded(lounge.pos()) && level.getBlockEntity(lounge.pos()) instanceof AllayLoungeBlockEntity;
        }
        return false;
    }

    private static boolean isEligible(ServerLevel level, ObservationOwner owner) {
        if (owner instanceof ObservationOwner.Observer observer) {
            return level.getEntity(observer.entityId()) instanceof WorkingAllayEntity worker
                && ObservationCoverage.isEligible(worker);
        }
        if (owner instanceof ObservationOwner.Lounge lounge) {
            return level.isLoaded(lounge.pos())
                && level.getBlockEntity(lounge.pos()) instanceof AllayLoungeBlockEntity hosted
                && ObservationCoverage.isEligible(hosted);
        }
        return false;
    }

    private static void force(ServerLevel level, ObservationOwner owner, ChunkPos column, boolean add) {
        if (owner instanceof ObservationOwner.Observer observer) {
            CONTROLLER.forceChunk(level, observer.entityId(), column.x, column.z, add, true);
            return;
        }
        if (owner instanceof ObservationOwner.Lounge lounge) {
            CONTROLLER.forceChunk(level, lounge.pos(), column.x, column.z, add, true);
        }
    }

    /**
     * 存档恢复期回调。索引之外的票一律裁掉:崩溃或索引分叉留下的残票必须在这里消失,
     * 否则重启后会出现没有持票人的永久加载区。
     */
    private static void validateTickets(ServerLevel level, TicketHelper helper) {
        ObservationLoadingIndex index = ObservationLoadingIndex.get(level.getServer());
        for (Map.Entry<UUID, TicketSet> entry : helper.getEntityTickets().entrySet()) {
            UUID entityId = entry.getKey();
            trimTickets(
                index,
                ObservationOwner.observer(level.dimension(), entityId),
                entry.getValue(),
                (chunk, ticking) -> helper.removeTicket(entityId, chunk, ticking)
            );
        }
        for (Map.Entry<BlockPos, TicketSet> entry : helper.getBlockTickets().entrySet()) {
            BlockPos pos = entry.getKey();
            trimTickets(
                index,
                ObservationOwner.lounge(level.dimension(), pos),
                entry.getValue(),
                (chunk, ticking) -> helper.removeTicket(pos, chunk, ticking)
            );
        }
    }

    private static void trimTickets(ObservationLoadingIndex index, ObservationOwner owner, TicketSet tickets, TicketRemover remover) {
        ChunkPos center = index.center(owner);
        Set<Long> allowed = new LinkedHashSet<>();
        if (center != null) {
            for (ChunkPos column : ObservationCoverage.columns(center)) {
                allowed.add(column.toLong());
            }
        }
        // 观察覆盖只发实体刻票,非实体刻的残票一律不留
        for (long chunk : tickets.nonTicking().toLongArray()) {
            remover.remove(chunk, false);
        }
        for (long chunk : tickets.ticking().toLongArray()) {
            if (!allowed.contains(chunk)) remover.remove(chunk, true);
        }
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, key -> new ServerState());
    }

    private interface TicketRemover {
        void remove(long chunk, boolean ticking);
    }

    /** 待撤的旧覆盖柱。deadline 为 0 表示尚未开始计时。 */
    private static final class PendingRelease {
        private final Set<ChunkPos> columns = new LinkedHashSet<>();
        private long deadline;
    }

    private static final class ServerState {
        private final Map<ObservationOwner, PendingRelease> pending = new LinkedHashMap<>();
        private final Map<ObservationOwner, Long> verifyDeadline = new HashMap<>();
        private boolean reconciled;
        private long nextVerifyTick;
    }
}
