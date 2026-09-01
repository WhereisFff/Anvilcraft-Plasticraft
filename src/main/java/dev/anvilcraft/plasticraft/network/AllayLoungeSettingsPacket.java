package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayShortageStrategy;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 休息室界面切换缺料与缺拆除策略,室内悦灵共用这一份设置。 */
public record AllayLoungeSettingsPacket(
    BlockPos loungePos,
    AllayShortageStrategy strategy
) implements IServerboundPacket {
    public static final Type<AllayLoungeSettingsPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("allay_lounge_settings")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AllayLoungeSettingsPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.loungePos);
            AllayShortageStrategy.STREAM_CODEC.encode(buffer, packet.strategy);
        },
        buffer -> new AllayLoungeSettingsPacket(
            buffer.readBlockPos(),
            AllayShortageStrategy.STREAM_CODEC.decode(buffer)
        )
    );

    @Override
    public Type<AllayLoungeSettingsPacket> type() {
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
                lounge.setShortageStrategy(serverPlayer, this.strategy);
            }
        }
    }
}
