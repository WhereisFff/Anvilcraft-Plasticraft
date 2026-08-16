package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 休息室界面的召回指令:让周围戴帽悦灵依次入库。 */
public record AllayLoungeRecallPacket(BlockPos loungePos) implements IServerboundPacket {
    public static final Type<AllayLoungeRecallPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("allay_lounge_recall")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AllayLoungeRecallPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeBlockPos(packet.loungePos),
        buffer -> new AllayLoungeRecallPacket(buffer.readBlockPos())
    );

    @Override
    public Type<AllayLoungeRecallPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (player.distanceToSqr(
            this.loungePos.getX() + 0.5D,
            this.loungePos.getY() + 0.5D,
            this.loungePos.getZ() + 0.5D
        ) > 64.0D) {
            return;
        }
        if (player.level().getBlockEntity(this.loungePos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.recallNearbyWorkers(player.getUUID());
        }
    }
}
