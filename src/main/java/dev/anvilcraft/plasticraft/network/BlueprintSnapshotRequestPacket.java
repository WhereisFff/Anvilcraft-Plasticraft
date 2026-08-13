package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJobIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionStructureLibrary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * 客户端请求一份结构快照内容。服务端只回应任务索引中真实引用的哈希,
 * 按块下发库文件的压缩字节,避免陌生客户端枚举世界结构库。
 */
public record BlueprintSnapshotRequestPacket(String hash) implements IServerboundPacket {
    public static final Type<BlueprintSnapshotRequestPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_snapshot_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintSnapshotRequestPacket> STREAM_CODEC =
        StreamCodec.of(
            (buffer, packet) -> buffer.writeUtf(packet.hash),
            buffer -> new BlueprintSnapshotRequestPacket(buffer.readUtf())
        );

    @Override
    public Type<BlueprintSnapshotRequestPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!ConstructionStructureLibrary.isValidHash(this.hash)) return;
        boolean referenced = false;
        for (ConstructionJob job : ConstructionJobIndex.get(serverPlayer.server).jobs()) {
            if (job.hash().equals(this.hash)) {
                referenced = true;
                break;
            }
        }
        if (!referenced) return;
        byte[] bytes;
        try {
            Path root = ConstructionStructureLibrary.libraryRoot(serverPlayer.server);
            Path file = root.resolve(this.hash + ".nbt").toAbsolutePath().normalize();
            if (!file.getParent().equals(root.toAbsolutePath().normalize())
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return;
            }
            bytes = Files.readAllBytes(file);
        } catch (IOException | ConstructionBlueprintException exception) {
            AnvilcraftPlasticraft.LOGGER.warn("Failed to read structure {}: {}", this.hash, exception.getMessage());
            return;
        }
        int chunkSize = BlueprintSnapshotChunkPacket.CHUNK_BYTES;
        int totalChunks = Math.max(1, Math.ceilDiv(bytes.length, chunkSize));
        for (int index = 0; index < totalChunks; index++) {
            int from = index * chunkSize;
            int to = Math.min(bytes.length, from + chunkSize);
            PacketDistributor.sendToPlayer(serverPlayer, new BlueprintSnapshotChunkPacket(
                this.hash,
                index,
                totalChunks,
                bytes.length,
                Arrays.copyOfRange(bytes, from, to)
            ));
        }
    }
}
