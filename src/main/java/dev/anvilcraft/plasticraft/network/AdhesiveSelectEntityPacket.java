package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 请求服务端记录树脂桶当前选中的实体。 */
public record AdhesiveSelectEntityPacket(
    int entityId,
    InteractionHand hand,
    Direction hitFace
) implements IServerboundPacket {
    public static final Type<AdhesiveSelectEntityPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_select_entity")
    );
    public static final StreamCodec<ByteBuf, AdhesiveSelectEntityPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AdhesiveSelectEntityPacket::entityId,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        AdhesiveSelectEntityPacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        AdhesiveSelectEntityPacket::hitFace,
        AdhesiveSelectEntityPacket::new
    );

    @Override
    public Type<AdhesiveSelectEntityPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        Entity target = player.level().getEntity(this.entityId);
        if (target != null && !AdhesiveBondingService.reclaimEntity(player, this.hand, target, this.hitFace)) {
            AdhesiveBondingService.select(player, this.hand, target, this.hitFace);
        }
    }
}
