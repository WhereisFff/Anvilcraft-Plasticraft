package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePreviewService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 请求服务端为当前树脂牵引目标生成权威预览路径。 */
public record AdhesivePreviewRequestPacket(
    int requestId,
    Mode mode,
    int selectedEntityId,
    int targetEntityId,
    BlockPos supportPos,
    Direction targetFace
) implements IServerboundPacket {
    public static final Type<AdhesivePreviewRequestPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_preview_request")
    );
    public static final StreamCodec<ByteBuf, AdhesivePreviewRequestPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AdhesivePreviewRequestPacket::requestId,
        StreamCodecUtil.enumStreamCodec(Mode.class),
        AdhesivePreviewRequestPacket::mode,
        ByteBufCodecs.VAR_INT,
        AdhesivePreviewRequestPacket::selectedEntityId,
        ByteBufCodecs.VAR_INT,
        AdhesivePreviewRequestPacket::targetEntityId,
        BlockPos.STREAM_CODEC,
        AdhesivePreviewRequestPacket::supportPos,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        AdhesivePreviewRequestPacket::targetFace,
        AdhesivePreviewRequestPacket::new
    );

    @Override
    public Type<AdhesivePreviewRequestPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        switch (this.mode) {
            case BLOCK -> AdhesivePreviewService.requestBlock(
                player,
                this.requestId,
                this.selectedEntityId,
                this.supportPos,
                this.targetFace
            );
            case ENTITY -> AdhesivePreviewService.requestEntity(
                player,
                this.requestId,
                this.selectedEntityId,
                this.targetEntityId,
                this.targetFace
            );
            case CANCEL -> AdhesivePreviewService.cancel(player);
        }
    }

    public enum Mode {
        BLOCK,
        ENTITY,
        CANCEL
    }
}
