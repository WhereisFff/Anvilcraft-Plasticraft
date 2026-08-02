package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 撤销、重做或增量失配时用于重建世界投影的完整快照。 */
public record MoldingProjectionSnapshotPacket(
    BlockPos chamberPos,
    long revision,
    EditableMoldingModel model
) implements IClientboundPacket {
    public static final Type<MoldingProjectionSnapshotPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_projection_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingProjectionSnapshotPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeVarLong(packet.revision);
            MoldingModelStreams.writeModel(buffer, packet.model);
        },
        buffer -> new MoldingProjectionSnapshotPacket(
            buffer.readBlockPos(),
            buffer.readVarLong(),
            MoldingModelStreams.readModel(buffer)
        )
    );

    @Override
    public Type<MoldingProjectionSnapshotPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level().getBlockEntity(this.chamberPos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.applyClientSnapshot(this.revision, this.model);
        }
    }
}
