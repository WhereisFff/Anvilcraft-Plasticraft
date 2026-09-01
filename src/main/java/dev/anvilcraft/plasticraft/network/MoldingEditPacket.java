package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** 提交一个以服务端修订号为前提的语义建模命令。 */
public record MoldingEditPacket(
    BlockPos chamberPos,
    UUID sessionId,
    long baseRevision,
    MoldingCommand command
) implements IServerboundPacket {
    public static final Type<MoldingEditPacket> TYPE = IPacket.type(AnvilcraftPlasticraft.of("molding_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingEditPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.baseRevision);
            MoldingModelStreams.writeCommand(buffer, packet.command);
        },
        buffer -> new MoldingEditPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readVarLong(),
            MoldingModelStreams.readCommand(buffer)
        )
    );

    @Override
    public Type<MoldingEditPacket> type() {
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
        PlasticMoldingChamberBlockEntity.EditOutcome outcome = chamber.applyEditorCommand(
            serverPlayer,
            this.sessionId,
            this.baseRevision,
            this.command
        );
        MoldingSessionSnapshot snapshot = chamber.sessionSnapshot(serverPlayer, this.sessionId);
        PacketDistributor.sendToPlayer(
            serverPlayer,
            MoldingSessionSnapshotPacket.of(this.chamberPos, snapshot, outcome.reason())
        );
        if (!outcome.accepted()) return;
        if (this.command instanceof MoldingCommand.Undo || this.command instanceof MoldingCommand.Redo) {
            PacketDistributor.sendToPlayersTrackingChunk(
                serverPlayer.serverLevel(),
                new ChunkPos(this.chamberPos),
                new MoldingProjectionSnapshotPacket(this.chamberPos, outcome.revision(), outcome.model())
            );
        } else {
            PacketDistributor.sendToPlayersTrackingChunk(
                serverPlayer.serverLevel(),
                new ChunkPos(this.chamberPos),
                new MoldingProjectionDeltaPacket(
                    this.chamberPos,
                    this.baseRevision,
                    outcome.revision(),
                    this.command
                )
            );
        }
    }
}
