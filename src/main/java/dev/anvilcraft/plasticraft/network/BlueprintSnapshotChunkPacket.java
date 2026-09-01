package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintSnapshotCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 结构快照内容的一个分块;客户端按哈希聚合,全部到齐后解析进快照缓存。 */
public record BlueprintSnapshotChunkPacket(
    String hash,
    int chunkIndex,
    int totalChunks,
    int totalBytes,
    byte[] bytes
) implements IClientboundPacket {
    /** 单个分块的字节上限。 */
    public static final int CHUNK_BYTES = 30_000;

    public static final Type<BlueprintSnapshotChunkPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_snapshot_chunk")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintSnapshotChunkPacket> STREAM_CODEC =
        StreamCodec.of(BlueprintSnapshotChunkPacket::write, BlueprintSnapshotChunkPacket::read);

    private static void write(RegistryFriendlyByteBuf buffer, BlueprintSnapshotChunkPacket packet) {
        buffer.writeUtf(packet.hash);
        buffer.writeVarInt(packet.chunkIndex);
        buffer.writeVarInt(packet.totalChunks);
        buffer.writeVarInt(packet.totalBytes);
        buffer.writeByteArray(packet.bytes);
    }

    private static BlueprintSnapshotChunkPacket read(RegistryFriendlyByteBuf buffer) {
        return new BlueprintSnapshotChunkPacket(
            buffer.readUtf(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readVarInt(),
            buffer.readByteArray(CHUNK_BYTES)
        );
    }

    @Override
    public Type<BlueprintSnapshotChunkPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        ClientBlueprintSnapshotCache.acceptChunk(
            this.hash,
            this.chunkIndex,
            this.totalChunks,
            this.totalBytes,
            this.bytes
        );
    }
}
