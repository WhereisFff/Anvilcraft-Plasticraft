package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.network.BlueprintJobSyncPacket;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端施工任务缓存:登录全量、变更增量,由任务同步包驱动。
 * 槽底颜色、部署会话与投影渲染只读取这里,不自行推断服务端状态。
 */
public final class ClientBlueprintJobCache {
    private static final Map<UUID, ConstructionJob> JOBS = new LinkedHashMap<>();
    private static List<ConstructionJob> snapshot = List.of();

    private ClientBlueprintJobCache() {
    }

    public static void apply(BlueprintJobSyncPacket packet) {
        Set<UUID> invalidated = new HashSet<>();
        synchronized (ClientBlueprintJobCache.class) {
            if (packet.fullReplace()) {
                invalidated.addAll(JOBS.keySet());
                JOBS.clear();
            }
            for (ConstructionJob job : packet.updates()) {
                ConstructionJob previous = JOBS.put(job.jobId(), job);
                if (previous != null && !sameOverlay(previous, job)) invalidated.add(job.jobId());
            }
            for (UUID removed : packet.removals()) {
                if (JOBS.remove(removed) != null) invalidated.add(removed);
            }
            snapshot = List.copyOf(JOBS.values());
        }
        for (UUID jobId : invalidated) {
            ClientConstructionOverlayLookup.invalidate(jobId);
        }
    }

    @Nullable
    public static synchronized ConstructionJob job(UUID jobId) {
        return JOBS.get(jobId);
    }

    public static synchronized List<ConstructionJob> jobs() {
        return snapshot;
    }

    /** 断开连接时清空,避免跨存档展示过期任务状态。 */
    public static synchronized void clear() {
        JOBS.clear();
        snapshot = List.of();
        ClientConstructionOverlayLookup.clear();
    }

    private static boolean sameOverlay(ConstructionJob first, ConstructionJob second) {
        return first.hash().equals(second.hash())
            && first.dimension().equals(second.dimension())
            && first.anchor().equals(second.anchor())
            && first.rotation() == second.rotation()
            && first.mirror() == second.mirror();
    }
}
