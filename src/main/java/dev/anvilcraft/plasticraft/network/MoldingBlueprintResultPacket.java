package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** 回送蓝图操作的权威结果和有界具体原因。 */
public record MoldingBlueprintResultPacket(
    BlockPos chamberPos,
    String reason,
    String detail
) implements IClientboundPacket {
    public static final int MAX_REASON_LENGTH = 64;
    public static final int MAX_DETAIL_LENGTH = 512;
    public static final Type<MoldingBlueprintResultPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("molding_blueprint_result")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, MoldingBlueprintResultPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeBlockPos(packet.chamberPos);
            buffer.writeUtf(packet.reason, MAX_REASON_LENGTH);
            buffer.writeUtf(packet.detail, MAX_DETAIL_LENGTH);
        },
        buffer -> new MoldingBlueprintResultPacket(
            buffer.readBlockPos(),
            buffer.readUtf(MAX_REASON_LENGTH),
            buffer.readUtf(MAX_DETAIL_LENGTH)
        )
    );

    public static void send(ServerPlayer player, BlockPos chamberPos, String reason, String detail) {
        String safeDetail = detail == null ? "" : detail.substring(0, Math.min(detail.length(), MAX_DETAIL_LENGTH));
        PacketDistributor.sendToPlayer(player, new MoldingBlueprintResultPacket(chamberPos, reason, safeDetail));
    }

    @Override
    public Type<MoldingBlueprintResultPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        Component message = Component.translatable("message.anvilcraftplasticraft.molding." + this.reason);
        if (!this.detail.isBlank()) {
            message = message.copy().append(Component.literal(": " + this.detail));
        }
        PlasticMoldingChamberMenu.showTitleMessage(player, this.chamberPos, message);
    }
}
