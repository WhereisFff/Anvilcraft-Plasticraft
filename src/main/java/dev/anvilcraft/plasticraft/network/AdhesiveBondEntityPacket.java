package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 请求把已选实体固定到指定方块表面。 */
public record AdhesiveBondEntityPacket(
    int entityId,
    InteractionHand hand,
    BlockPos supportPos,
    Direction attachmentFace
) implements IServerboundPacket {
    public static final Type<AdhesiveBondEntityPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_bond_entity")
    );
    public static final StreamCodec<ByteBuf, AdhesiveBondEntityPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AdhesiveBondEntityPacket::entityId,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        AdhesiveBondEntityPacket::hand,
        BlockPos.STREAM_CODEC,
        AdhesiveBondEntityPacket::supportPos,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        AdhesiveBondEntityPacket::attachmentFace,
        AdhesiveBondEntityPacket::new
    );

    @Override
    public Type<AdhesiveBondEntityPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null || selected.getId() != this.entityId) return;
        AdhesiveBondingService.bondSelected(player, this.hand, this.supportPos, this.attachmentFace);
    }
}
