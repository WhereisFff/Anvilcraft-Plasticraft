package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintException;
import dev.anvilcraft.plasticraft.blueprint.ConstructionBlueprintService;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 取消一份已放置蓝图;所有者校验由服务层完成。 */
public record BlueprintCancelPacket(UUID jobId) implements IServerboundPacket {
    public static final Type<BlueprintCancelPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_cancel")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintCancelPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> UUIDUtil.STREAM_CODEC.encode(buffer, packet.jobId),
        buffer -> new BlueprintCancelPacket(UUIDUtil.STREAM_CODEC.decode(buffer))
    );

    @Override
    public Type<BlueprintCancelPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        try {
            ConstructionBlueprintService.cancel(serverPlayer, this.jobId);
            BlueprintImportResultPacket.sendSuccess(serverPlayer, "cancelled", "");
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(serverPlayer, exception);
        }
    }
}
