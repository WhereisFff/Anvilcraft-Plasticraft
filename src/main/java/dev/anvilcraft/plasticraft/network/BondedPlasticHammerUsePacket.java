package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.codec.StreamCodecUtil;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.dubhe.anvilcraft.item.AnvilHammerItem;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/** 固定塑料制品的铁砧锤短按交互。 */
public record BondedPlasticHammerUsePacket(
    BlockPos pos,
    InteractionHand hand,
    Direction interactionFace
) implements IServerboundPacket {
    public static final Type<BondedPlasticHammerUsePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("bonded_plastic_hammer_use")
    );
    public static final StreamCodec<ByteBuf, BondedPlasticHammerUsePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        BondedPlasticHammerUsePacket::pos,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        BondedPlasticHammerUsePacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        BondedPlasticHammerUsePacket::interactionFace,
        BondedPlasticHammerUsePacket::new
    );

    @Override
    public Type<BondedPlasticHammerUsePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player.getItemInHand(this.hand).getItem() instanceof AnvilHammerItem)
            || !player.canInteractWithBlock(this.pos, 0.0D)
            || !player.level().mayInteract(player, this.pos)
            || !(player.level().getBlockEntity(this.pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized()
            || !bonded.isPlastic()) {
            return;
        }
        bonded.useAnvilHammer(player, this.hand, this.interactionFace);
    }
}
