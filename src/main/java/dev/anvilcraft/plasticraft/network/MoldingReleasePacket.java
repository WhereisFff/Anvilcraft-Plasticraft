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

import java.util.UUID;

/** 正常关闭界面时立即释放租约；异常退出仍由超时回收。 */
public record MoldingReleasePacket(
    BlockPos chamberPos,
    UUID sessionId,
    long revision
) implements IServerboundPacket {
    public static final Type<MoldingReleasePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_release")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingReleasePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUUID(packet.sessionId);
            buffer.writeVarLong(packet.revision);
        },
        buffer -> new MoldingReleasePacket(buffer.readBlockPos(), buffer.readUUID(), buffer.readVarLong())
    );

    @Override
    public Type<MoldingReleasePacket> type() {
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
        if (chamber != null && chamber.releaseSession(serverPlayer, this.sessionId)) {
            MoldingSessionStatusPacket.broadcast(serverPlayer.serverLevel(), chamber);
        }
    }
}
