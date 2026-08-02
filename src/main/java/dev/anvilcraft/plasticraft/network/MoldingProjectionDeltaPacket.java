package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 把一个已确认语义命令增量应用到世界投影。 */
public record MoldingProjectionDeltaPacket(
    BlockPos chamberPos,
    long baseRevision,
    long newRevision,
    MoldingCommand command
) implements IClientboundPacket {
    public static final Type<MoldingProjectionDeltaPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_projection_delta")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingProjectionDeltaPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeVarLong(packet.baseRevision);
            buffer.writeVarLong(packet.newRevision);
            MoldingModelStreams.writeCommand(buffer, packet.command);
        },
        buffer -> new MoldingProjectionDeltaPacket(
            buffer.readBlockPos(),
            buffer.readVarLong(),
            buffer.readVarLong(),
            MoldingModelStreams.readCommand(buffer)
        )
    );

    @Override
    public Type<MoldingProjectionDeltaPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level().getBlockEntity(this.chamberPos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.applyClientDelta(this.baseRevision, this.newRevision, this.command);
        }
        if (player.containerMenu instanceof PlasticMoldingChamberMenu menu
            && menu.chamberPos().equals(this.chamberPos)) {
            menu.applyDelta(this.baseRevision, this.newRevision, this.command);
        }
    }
}
