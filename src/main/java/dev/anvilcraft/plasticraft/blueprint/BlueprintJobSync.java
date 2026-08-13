package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.network.BlueprintJobSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.UUID;

/**
 * 任务索引到客户端的同步:登录时全量下发,变更时增量广播。
 * 数据量只有任务摘要,客户端用它绘制槽底颜色与投影放置。
 */
public final class BlueprintJobSync {
    private BlueprintJobSync() {
    }

    public static void syncAllTo(ServerPlayer player) {
        ConstructionJobIndex index = ConstructionJobIndex.get(player.server);
        PacketDistributor.sendToPlayer(player, BlueprintJobSyncPacket.fullReplace(index.jobs()));
    }

    public static void syncPut(MinecraftServer server, ConstructionJob job) {
        PacketDistributor.sendToAllPlayers(BlueprintJobSyncPacket.update(List.of(job)));
    }

    public static void syncRemove(MinecraftServer server, UUID jobId) {
        PacketDistributor.sendToAllPlayers(BlueprintJobSyncPacket.remove(List.of(jobId)));
    }
}
