package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelStreams;
import dev.anvilcraft.plasticraft.molding.session.MoldingSessionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 向一个菜单会话回送权威模型、修订和写权限。 */
public record MoldingSessionSnapshotPacket(
    BlockPos chamberPos,
    UUID sessionId,
    boolean writable,
    String writerName,
    long revision,
    EditableMoldingModel model,
    String reason
) implements IClientboundPacket {
    public static final Type<MoldingSessionSnapshotPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_session_snapshot")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingSessionSnapshotPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeBoolean(packet.writable);
            buffer.writeUtf(packet.writerName, 64);
            buffer.writeVarLong(packet.revision);
            MoldingModelStreams.writeModel(buffer, packet.model);
            buffer.writeUtf(packet.reason, 64);
        },
        buffer -> new MoldingSessionSnapshotPacket(
            buffer.readBlockPos(),
            buffer.readUUID(),
            buffer.readBoolean(),
            buffer.readUtf(64),
            buffer.readVarLong(),
            MoldingModelStreams.readModel(buffer),
            buffer.readUtf(64)
        )
    );

    public static MoldingSessionSnapshotPacket of(
        BlockPos pos,
        MoldingSessionSnapshot snapshot,
        String reason
    ) {
        return new MoldingSessionSnapshotPacket(
            pos,
            snapshot.sessionId(),
            snapshot.writable(),
            snapshot.writerName(),
            snapshot.revision(),
            snapshot.model(),
            reason
        );
    }

    @Override
    public Type<MoldingSessionSnapshotPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level().getBlockEntity(this.chamberPos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.applyClientSnapshot(this.revision, this.model);
        }
        if (player.containerMenu instanceof PlasticMoldingChamberMenu menu
            && menu.chamberPos().equals(this.chamberPos)) {
            menu.applySnapshot(new MoldingSessionSnapshot(
                this.sessionId,
                this.writable,
                this.writerName,
                this.revision,
                this.model
            ));
        }
        if (!this.reason.isEmpty()) {
            PlasticMoldingChamberMenu.showTitleMessage(
                player,
                this.chamberPos,
                Component.translatable("message.anvilcraftplasticraft.molding." + this.reason)
            );
        }
    }
}
