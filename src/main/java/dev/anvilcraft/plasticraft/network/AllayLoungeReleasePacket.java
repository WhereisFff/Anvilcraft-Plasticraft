package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 休息室卡片点选后把对应托管悦灵放到站顶。 */
public record AllayLoungeReleasePacket(BlockPos loungePos, int index, UUID entityId) implements IServerboundPacket {
    public static final Type<AllayLoungeReleasePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("allay_lounge_release")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AllayLoungeReleasePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.loungePos);
            buffer.writeVarInt(packet.index);
            buffer.writeUUID(packet.entityId);
        },
        buffer -> new AllayLoungeReleasePacket(buffer.readBlockPos(), buffer.readVarInt(), buffer.readUUID())
    );

    @Override
    public Type<AllayLoungeReleasePacket> type() {
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
            if (player instanceof ServerPlayer serverPlayer) {
                lounge.releaseHosted(serverPlayer, this.index, this.entityId);
            }
        }
    }
}
