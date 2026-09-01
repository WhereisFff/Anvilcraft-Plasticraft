package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 请求当前玩家排序后的共享蓝图摘要。 */
public record MoldingBlueprintListRequestPacket(
    BlockPos chamberPos,
    UUID sessionId
) implements IServerboundPacket {
    public static final Type<MoldingBlueprintListRequestPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_list_request")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintListRequestPacket> STREAM_CODEC =
        StreamCodec.of(
            (buffer, packet) -> {
                buffer.writeBlockPos(packet.chamberPos);
                buffer.writeUUID(packet.sessionId);
            },
            buffer -> new MoldingBlueprintListRequestPacket(buffer.readBlockPos(), buffer.readUUID())
        );

    @Override
    public Type<MoldingBlueprintListRequestPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)
            || MoldingPacketAccess.chamber(serverPlayer, this.chamberPos, this.sessionId) == null) {
            return;
        }
        MoldingBlueprintListPacket.send(serverPlayer, this.chamberPos, "");
    }
}
