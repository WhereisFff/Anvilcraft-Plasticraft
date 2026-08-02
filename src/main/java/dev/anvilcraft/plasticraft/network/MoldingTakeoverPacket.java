package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 只读观察者明确请求接管当前成型舱写租约。 */
public record MoldingTakeoverPacket(
    BlockPos chamberPos,
    UUID sessionId,
    long revision
) implements IServerboundPacket {
    public static final Type<MoldingTakeoverPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_takeover")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingTakeoverPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.revision);
        },
        buffer -> new MoldingTakeoverPacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong())
    );

    @Override
    public Type<MoldingTakeoverPacket> type() {
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
        if (chamber.revision() != this.revision) {
            PacketDistributor.sendToPlayer(
                serverPlayer,
                MoldingSessionSnapshotPacket.of(
                    this.chamberPos,
                    chamber.sessionSnapshot(serverPlayer, this.sessionId),
                    "stale_revision"
                )
            );
            return;
        }
        MoldingSessionSnapshot snapshot = chamber.openSession(serverPlayer, true);
        if (serverPlayer.containerMenu instanceof PlasticMoldingChamberMenu menu) menu.applySnapshot(snapshot);
        PacketDistributor.sendToPlayer(
            serverPlayer,
            MoldingSessionSnapshotPacket.of(this.chamberPos, snapshot, "session_taken_over")
        );
        MoldingSessionStatusPacket.broadcast(serverPlayer.serverLevel(), chamber);
    }
}
