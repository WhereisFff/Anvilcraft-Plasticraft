package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePreviewService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 请求把已选实体粘到另一实体的指定碰撞箱面。 */
public record AdhesiveBondEntitiesPacket(
    int selectedEntityId,
    int targetEntityId,
    InteractionHand hand,
    Direction targetFace
) implements IServerboundPacket {
    public static final Type<AdhesiveBondEntitiesPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_bond_entities")
    );
    public static final StreamCodec<ByteBuf, AdhesiveBondEntitiesPacket> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT,
        AdhesiveBondEntitiesPacket::selectedEntityId,
        ByteBufCodecs.VAR_INT,
        AdhesiveBondEntitiesPacket::targetEntityId,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        AdhesiveBondEntitiesPacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        AdhesiveBondEntitiesPacket::targetFace,
        AdhesiveBondEntitiesPacket::new
    );

    @Override
    public Type<AdhesiveBondEntitiesPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        Entity target = player.level().getEntity(this.targetEntityId);
        if (selected == null || selected.getId() != this.selectedEntityId || target == null) return;
        AdhesivePreviewService.confirmEntity(player, this.hand, target, this.targetFace);
    }
}
