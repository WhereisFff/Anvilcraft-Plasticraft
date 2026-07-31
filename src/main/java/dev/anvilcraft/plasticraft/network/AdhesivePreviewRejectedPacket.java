package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.event.AdhesiveSelectionClientHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 通知客户端对应的树脂牵引预览请求已失效。 */
public record AdhesivePreviewRejectedPacket(int requestId) implements IClientboundPacket {
    public static final Type<AdhesivePreviewRejectedPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_preview_rejected")
    );
    public static final StreamCodec<ByteBuf, AdhesivePreviewRejectedPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AdhesivePreviewRejectedPacket::requestId,
        AdhesivePreviewRejectedPacket::new
    );

    @Override
    public Type<AdhesivePreviewRejectedPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        AdhesiveSelectionClientHandler.handlePreviewRejected(this.requestId);
    }
}
