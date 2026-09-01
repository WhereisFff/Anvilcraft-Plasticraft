package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 向所有观察者同步当前单写者，用于只读提示和接管后的失效。 */
public record MoldingSessionStatusPacket(
    BlockPos chamberPos,
    @Nullable UUID writerId,
    String writerName
) implements IClientboundPacket {
    public static final Type<MoldingSessionStatusPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_session_status")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingSessionStatusPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeBoolean(packet.writerId != null);
            if (packet.writerId != null) buffer.writeUUID(packet.writerId);
            buffer.writeUtf(packet.writerName, 64);
        },
        buffer -> new MoldingSessionStatusPacket(
            buffer.readBlockPos(),
            buffer.readBoolean() ? buffer.readUUID() : null,
            buffer.readUtf(64)
        )
    );

    @Override
    public Type<MoldingSessionStatusPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.containerMenu instanceof PlasticMoldingChamberMenu menu
            && menu.chamberPos().equals(this.chamberPos)) {
            menu.applyWriterStatus(this.writerId, this.writerName);
        }
    }

    public static void broadcast(ServerLevel level, PlasticMoldingChamberBlockEntity chamber) {
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            new ChunkPos(chamber.getBlockPos()),
            new MoldingSessionStatusPacket(
                chamber.getBlockPos(),
                chamber.writerPlayerId(),
                chamber.writerName()
            )
        );
    }
}
