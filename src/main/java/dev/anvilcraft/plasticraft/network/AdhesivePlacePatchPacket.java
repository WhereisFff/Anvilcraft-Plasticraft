package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** 请求在指定方块面留下一个等待粘合的高粘性树脂胶点。 */
public record AdhesivePlacePatchPacket(
    InteractionHand hand,
    BlockPos supportPos,
    Direction face
) implements IServerboundPacket {
    public static final Type<AdhesivePlacePatchPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_place_patch")
    );
    public static final StreamCodec<ByteBuf, AdhesivePlacePatchPacket> STREAM_CODEC = StreamCodec.composite(
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        AdhesivePlacePatchPacket::hand,
        BlockPos.STREAM_CODEC,
        AdhesivePlacePatchPacket::supportPos,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        AdhesivePlacePatchPacket::face,
        AdhesivePlacePatchPacket::new
    );

    @Override
    public Type<AdhesivePlacePatchPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        AdhesiveBondingService.placePatch(player, this.hand, this.supportPos, this.face);
    }
}
