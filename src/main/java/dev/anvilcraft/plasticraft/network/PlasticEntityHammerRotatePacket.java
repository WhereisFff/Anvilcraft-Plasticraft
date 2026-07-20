package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 应用铁砧锤环形菜单选择的实体附着面。 */
public record PlasticEntityHammerRotatePacket(
    int entityId,
    InteractionHand hand,
    Direction attachmentFace
) implements IServerboundPacket {
    public static final Type<PlasticEntityHammerRotatePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("plastic_entity_hammer_rotate")
    );
    public static final StreamCodec<ByteBuf, PlasticEntityHammerRotatePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        PlasticEntityHammerRotatePacket::entityId,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        PlasticEntityHammerRotatePacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        PlasticEntityHammerRotatePacket::attachmentFace,
        PlasticEntityHammerRotatePacket::new
    );

    @Override
    public Type<PlasticEntityHammerRotatePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        Entity target = player.level().getEntity(this.entityId);
        if (!(target instanceof AbstractPlasticEntity plasticEntity)
            || !target.isAlive()
            || !plasticEntity.supportsAnvilHammerOrientationMenu()
            || !player.canInteractWithEntity(target, 0.0D)
            || !(player.getItemInHand(this.hand).getItem() instanceof AnvilHammerItem)) {
            return;
        }
        plasticEntity.plasticraft$changeAttachmentFace(player, this.hand, this.attachmentFace);
    }
}
