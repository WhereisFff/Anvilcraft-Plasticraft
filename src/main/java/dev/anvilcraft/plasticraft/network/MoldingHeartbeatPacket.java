package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 续租单写者会话，并在修订失配时重发权威快照。 */
public record MoldingHeartbeatPacket(
    BlockPos chamberPos,
    UUID sessionId,
    long revision
) implements IServerboundPacket {
    public static final Type<MoldingHeartbeatPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_heartbeat")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingHeartbeatPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.revision);
        },
        buffer -> new MoldingHeartbeatPacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong())
    );

    @Override
    public Type<MoldingHeartbeatPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PlasticMoldingChamberBlockEntity chamber = MoldingPacketAccess.chamber(
            serverPlayer,
            this.chamberPos,
            this.sessionId
        );
        if (chamber == null) return;
        boolean valid = chamber.heartbeat(serverPlayer, this.sessionId);
        if (valid && chamber.revision() == this.revision) return;
        PacketDistributor.sendToPlayer(
            serverPlayer,
            MoldingSessionSnapshotPacket.of(
                this.chamberPos,
                chamber.sessionSnapshot(serverPlayer, this.sessionId),
                valid ? "stale_revision" : "invalid_session"
            )
        );
    }
}
