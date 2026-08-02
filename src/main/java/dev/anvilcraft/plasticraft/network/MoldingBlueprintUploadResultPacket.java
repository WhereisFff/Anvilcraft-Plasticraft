package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.molding.blueprint.MoldingBlueprintClientFiles;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintImporter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 确认一次本地源文件上传是否已由服务端持久化。 */
public record MoldingBlueprintUploadResultPacket(
    BlockPos chamberPos,
    UUID sessionId,
    String filename,
    long sourceSize,
    long sourceModifiedAt,
    boolean accepted
) implements IClientboundPacket {
    public static final Type<MoldingBlueprintUploadResultPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_upload_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintUploadResultPacket> STREAM_CODEC =
        StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.chamberPos);
                buffer.writeUUID(packet.sessionId);
                buffer.writeUtf(packet.filename, MoldingBlueprintImporter.MAX_UPLOAD_NAME_LENGTH);
                buffer.writeVarLong(packet.sourceSize);
                buffer.writeVarLong(packet.sourceModifiedAt);
                buffer.writeBoolean(packet.accepted);
            },
            buffer -> new MoldingBlueprintUploadResultPacket(
                buffer.readBlockPos(),
                buffer.readUUID(),
                buffer.readUtf(MoldingBlueprintImporter.MAX_UPLOAD_NAME_LENGTH),
                buffer.readVarLong(),
                buffer.readVarLong(),
                buffer.readBoolean()
            )
        );

    public static void send(
        ServerPlayer player,
        BlockPos chamberPos,
        UUID sessionId,
        String filename,
        long sourceSize,
        long sourceModifiedAt,
        boolean accepted
    ) {
        PacketDistributor.sendToPlayer(player, new MoldingBlueprintUploadResultPacket(
            chamberPos,
            sessionId,
            filename,
            sourceSize,
            sourceModifiedAt,
            accepted
        ));
    }

    @Override
    public Type<MoldingBlueprintUploadResultPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        MoldingBlueprintClientFiles.handleUploadResult(
            player,
            this.chamberPos,
            this.sessionId,
            this.filename,
            this.sourceSize,
            this.sourceModifiedAt,
            this.accepted
        );
    }
}
