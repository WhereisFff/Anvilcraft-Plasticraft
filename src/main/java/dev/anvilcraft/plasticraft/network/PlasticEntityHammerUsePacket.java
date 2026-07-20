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

/** 铁砧锤在环形菜单出现前快速释放时执行实体的默认交互。 */
public record PlasticEntityHammerUsePacket(
    int entityId,
    InteractionHand hand,
    Direction interactionFace
) implements IServerboundPacket {
    public static final Type<PlasticEntityHammerUsePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("plastic_entity_hammer_use")
    );
    public static final StreamCodec<ByteBuf, PlasticEntityHammerUsePacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        PlasticEntityHammerUsePacket::entityId,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        PlasticEntityHammerUsePacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        PlasticEntityHammerUsePacket::interactionFace,
        PlasticEntityHammerUsePacket::new
    );

    @Override
    public Type<PlasticEntityHammerUsePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        Entity target = player.level().getEntity(this.entityId);
        if (!(target instanceof AbstractPlasticEntity plasticEntity)
            || !target.isAlive()
            || !player.canInteractWithEntity(target, 0.0D)
            || !(player.getItemInHand(this.hand).getItem() instanceof AnvilHammerItem)) {
            return;
        }
        plasticEntity.plasticraft$useAnvilHammer(player, this.hand, this.interactionFace);
    }
}
