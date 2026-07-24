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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;

/** 六向轮盘改变固定塑料制品朝向时先解除胶粘。 */
public record BondedPlasticHammerRotatePacket(
    BlockPos pos,
    InteractionHand hand,
    Direction attachmentFace
) implements IServerboundPacket {
    public static final Type<BondedPlasticHammerRotatePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("bonded_plastic_hammer_rotate")
    );
    public static final StreamCodec<ByteBuf, BondedPlasticHammerRotatePacket> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC,
        BondedPlasticHammerRotatePacket::pos,
        StreamCodecUtil.enumStreamCodec(InteractionHand.class),
        BondedPlasticHammerRotatePacket::hand,
        StreamCodecUtil.enumStreamCodec(Direction.class),
        BondedPlasticHammerRotatePacket::attachmentFace,
        BondedPlasticHammerRotatePacket::new
    );

    @Override
    public Type<BondedPlasticHammerRotatePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player.getItemInHand(this.hand).getItem() instanceof AnvilHammerItem)
            || player.isShiftKeyDown()
            || !player.getAbilities().mayBuild
            || !player.canInteractWithBlock(this.pos, 0.0D)
            || !player.level().mayInteract(player, this.pos)
            || !(player.level().getBlockEntity(this.pos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.isInitialized()
            || !bonded.isPlastic()
            || bonded.getPlasticOrientation().attachmentFace() == this.attachmentFace) {
            return;
        }

        Entity restored = bonded.releaseEntity(this.attachmentFace);
        if (restored == null) return;
        player.level().playSound(
            null,
            this.pos,
            SoundEvents.HONEY_BLOCK_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            1.0F
        );
        player.gameEvent(GameEvent.ENTITY_INTERACT);
    }
}
