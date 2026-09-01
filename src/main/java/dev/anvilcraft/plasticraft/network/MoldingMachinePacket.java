package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.molding.machine.MoldingMachineAction;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 提交带菜单会话和模型修订前提的成型舱机器操作。 */
public record MoldingMachinePacket(
    BlockPos chamberPos,
    UUID sessionId,
    long baseRevision,
    MoldingMachineAction action,
    int value
) implements IServerboundPacket {
    public static final Type<MoldingMachinePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_machine")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingMachinePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.baseRevision);
            buffer.writeByte(packet.action.protocolId());
            buffer.writeVarInt(packet.value);
        },
        buffer -> new MoldingMachinePacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readVarLong(),
            MoldingMachineAction.fromProtocolId(buffer.readUnsignedByte()),
            buffer.readVarInt()
        )
    );

    @Override
    public Type<MoldingMachinePacket> type() {
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
        PlasticMoldingChamberBlockEntity.MachineOutcome outcome = chamber.applyMachineAction(
            serverPlayer,
            this.sessionId,
            this.baseRevision,
            this.action,
            this.value
        );
        MoldingSessionSnapshot snapshot = chamber.sessionSnapshot(serverPlayer, this.sessionId);
        PacketDistributor.sendToPlayer(
            serverPlayer,
            MoldingSessionSnapshotPacket.of(this.chamberPos, snapshot, outcome.reason())
        );
        serverPlayer.containerMenu.broadcastChanges();
    }
}
