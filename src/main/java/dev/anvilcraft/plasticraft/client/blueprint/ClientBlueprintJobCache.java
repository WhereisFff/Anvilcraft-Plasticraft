package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.network.BlueprintJobSyncPacket;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户端施工任务缓存:登录全量、变更增量,由任务同步包驱动。
 * 槽底颜色、部署会话与投影渲染只读取这里,不自行推断服务端状态。
 */
public final class ClientBlueprintJobCache {
    private static final Map<UUID, ConstructionJob> JOBS = new LinkedHashMap<>();

    private ClientBlueprintJobCache() {
    }

    public static synchronized void apply(BlueprintJobSyncPacket packet) {
        if (packet.fullReplace()) JOBS.clear();
        for (ConstructionJob job : packet.updates()) {
            JOBS.put(job.jobId(), job);
        }
        for (UUID removed : packet.removals()) {
            JOBS.remove(removed);
        }
    }

    @Nullable
    public static synchronized ConstructionJob job(UUID jobId) {
        return JOBS.get(jobId);
    }

    public static synchronized List<ConstructionJob> jobs() {
        return List.copyOf(JOBS.values());
    }

    /** 断开连接时清空,避免跨存档展示过期任务状态。 */
    public static synchronized void clear() {
        JOBS.clear();
    }
}
