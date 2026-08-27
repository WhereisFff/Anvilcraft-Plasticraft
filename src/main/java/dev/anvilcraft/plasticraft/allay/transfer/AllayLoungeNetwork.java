package dev.anvilcraft.plasticraft.allay.transfer;

import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.blueprint.ConstructionPermission;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 同维度休息室运行时转运网。节点 = 世界内已装载并登记的休息室,
 * 边 = 欧氏距离不超过 {@link #HOP_RANGE} 且节点所有者与调用方相互协作。
 *
 * 纯运行时结构,不落盘:节点表按世界分桶且键为弱引用,存档退出重进后旧世界连同旧方块实体一起回收,
 * 不会留下失效节点。路由查询按“跳数优先、同跳数比累计距离”一次性给出各节点的最优下一跳。
 */
public final class AllayLoungeNetwork {
    /** 转运单段最大距离,与任务发现范围一致(设计文档 §309:每段最多 128 格)。 */
    public static final double HOP_RANGE = 128.0D;
    static final double HOP_RANGE_SQR = HOP_RANGE * HOP_RANGE;

    private static final Map<ServerLevel, Map<BlockPos, AllayLoungeBlockEntity>> NODES = new WeakHashMap<>();

    private AllayLoungeNetwork() {
    }

    /**
     * 通往某个目标休息室的路由。
     *
     * @param next     朝目标前进的下一跳,目标节点自身为 null
     * @param hops     到目标的剩余跳数
     * @param distance 沿链累计欧氏距离(格),用于估算飞行时间
     */
    public record LoungeRoute(@Nullable BlockPos next, int hops, double distance) {
    }

    private record Pending(BlockPos pos, @Nullable BlockPos next, int hops, double distance) {
    }

    public static void register(AllayLoungeBlockEntity lounge) {
        if (!(lounge.getLevel() instanceof ServerLevel level) || lounge.isRemoved()) return;
        synchronized (NODES) {
            NODES.computeIfAbsent(level, ignored -> new LinkedHashMap<>())
                .put(lounge.getBlockPos().immutable(), lounge);
        }
    }

    public static void unregister(ServerLevel level, BlockPos pos) {
        synchronized (NODES) {
            Map<BlockPos, AllayLoungeBlockEntity> nodes = NODES.get(level);
            if (nodes == null) return;
            nodes.remove(pos);
            if (nodes.isEmpty()) NODES.remove(level);
        }
    }

    /** 当前世界仍然有效的全部注册休息室,顺带剔除已失效的方块实体。 */
    public static List<AllayLoungeBlockEntity> lounges(ServerLevel level) {
        synchronized (NODES) {
            Map<BlockPos, AllayLoungeBlockEntity> nodes = NODES.get(level);
            if (nodes == null || nodes.isEmpty()) return List.of();
            List<AllayLoungeBlockEntity> alive = new ArrayList<>(nodes.size());
            Iterator<Map.Entry<BlockPos, AllayLoungeBlockEntity>> iterator = nodes.entrySet().iterator();
            while (iterator.hasNext()) {
                AllayLoungeBlockEntity lounge = iterator.next().getValue();
                if (lounge.isRemoved() || lounge.getLevel() != level) {
                    iterator.remove();
                    continue;
                }
                alive.add(lounge);
            }
            return List.copyOf(alive);
        }
    }

    public static boolean isRegistered(ServerLevel level, BlockPos pos) {
        synchronized (NODES) {
            Map<BlockPos, AllayLoungeBlockEntity> nodes = NODES.get(level);
            AllayLoungeBlockEntity lounge = nodes == null ? null : nodes.get(pos);
            return lounge != null && !lounge.isRemoved() && lounge.getLevel() == level;
        }
    }

    /** 该坐标能否作为落脚点:必须处于实体刻活跃区,否则悦灵飞不动、休息室也无法继续转发。 */
    public static boolean isLive(ServerLevel level, BlockPos pos) {
        return level.isPositionEntityTicking(pos);
    }

    /**
     * 一次反向搜索给出所有节点通往 {@code target} 的最优路由。图的边是无向的,
     * 因此从目标出发的搜索结果可直接当作各节点的下一跳,避免每个候选各跑一遍 BFS。
     *
     * @param requireTicking 只允许落在实体刻活跃的节点上(实际调度用 true,纯路由查询用 false)
     */
    public static Map<BlockPos, LoungeRoute> routesTo(
        ServerLevel level,
        BlockPos target,
        @Nullable UUID collaborator,
        boolean requireTicking
    ) {
        if (collaborator == null) return Map.of();
        BlockPos destination = target.immutable();
        List<BlockPos> nodes = eligibleNodes(level, collaborator, requireTicking);
        if (!nodes.contains(destination)) return Map.of();
        Map<BlockPos, LoungeRoute> settled = new HashMap<>();
        PriorityQueue<Pending> queue = new PriorityQueue<>(
            Comparator.comparingInt(Pending::hops).thenComparingDouble(Pending::distance)
        );
        queue.add(new Pending(destination, null, 0, 0.0D));
        while (!queue.isEmpty()) {
            Pending current = queue.poll();
            if (settled.containsKey(current.pos())) continue;
            settled.put(current.pos(), new LoungeRoute(current.next(), current.hops(), current.distance()));
            for (BlockPos candidate : nodes) {
                if (settled.containsKey(candidate)) continue;
                double distanceSqr = current.pos().distSqr(candidate);
                if (distanceSqr <= 0.0D || distanceSqr > HOP_RANGE_SQR) continue;
                queue.add(new Pending(
                    candidate,
                    current.pos(),
                    current.hops() + 1,
                    current.distance() + Math.sqrt(distanceSqr)
                ));
            }
        }
        return settled;
    }

    /** 同维度从 {@code fromPos} 走向 {@code toPos} 的下一跳。起终点相同、不可达或不在图中时返回 null。 */
    @Nullable
    public static BlockPos nextHop(ServerLevel level, BlockPos fromPos, BlockPos toPos, @Nullable UUID collaborator) {
        LoungeRoute route = routesTo(level, toPos, collaborator, false).get(fromPos);
        return route == null ? null : route.next();
    }

    /** 从停留处到目标的完整跳数,不可达返回 -1;起终点相同为 0。 */
    public static int hopCount(ServerLevel level, BlockPos fromPos, BlockPos toPos, @Nullable UUID collaborator) {
        LoungeRoute route = routesTo(level, toPos, collaborator, false).get(fromPos);
        return route == null ? -1 : route.hops();
    }

    private static List<BlockPos> eligibleNodes(ServerLevel level, UUID collaborator, boolean requireTicking) {
        MinecraftServer server = level.getServer();
        List<BlockPos> eligible = new ArrayList<>();
        for (AllayLoungeBlockEntity lounge : lounges(level)) {
            UUID owner = lounge.owner();
            if (owner == null || !ConstructionPermission.areCollaborators(server, collaborator, owner)) continue;
            BlockPos pos = lounge.getBlockPos();
            if (requireTicking && !isLive(level, pos)) continue;
            eligible.add(pos);
        }
        return eligible;
    }
}
