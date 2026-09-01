package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.allay.AllayClearanceStrategy;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** 休息室界面切换空白格清场策略;未规划任务下次规划读取,已规划任务立即同步未完成拆除台账 */
public record AllayLoungeClearancePacket(
    BlockPos loungePos,
    AllayClearanceStrategy strategy
) implements IServerboundPacket {
    public static final Type<AllayLoungeClearancePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("allay_lounge_clearance")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AllayLoungeClearancePacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.loungePos);
            AllayClearanceStrategy.STREAM_CODEC.encode(buffer, packet.strategy);
        },
        buffer -> new AllayLoungeClearancePacket(
            buffer.readBlockPos(),
            AllayClearanceStrategy.STREAM_CODEC.decode(buffer)
        )
    );

    @Override
    public Type<AllayLoungeClearancePacket> type() {
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
                lounge.setClearanceStrategy(serverPlayer, this.strategy);
            }
        }
    }
}
