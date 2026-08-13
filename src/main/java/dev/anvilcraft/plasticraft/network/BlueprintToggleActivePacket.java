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

/**
 * 菜单内空手右击已部署磁盘:独立于原版槽位点击包,避免创造物品栏槽位编号
 * 与服务端 InventoryMenu 对不上时右击被静默丢弃。
 */
public record BlueprintToggleActivePacket(UUID jobId) implements IServerboundPacket {
    public static final Type<BlueprintToggleActivePacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("blueprint_toggle_active")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintToggleActivePacket> STREAM_CODEC =
        StreamCodec.of(
            (buffer, packet) -> UUIDUtil.STREAM_CODEC.encode(buffer, packet.jobId),
            buffer -> new BlueprintToggleActivePacket(UUIDUtil.STREAM_CODEC.decode(buffer))
        );

    @Override
    public Type<BlueprintToggleActivePacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        try {
            boolean active = ConstructionBlueprintService.toggleActive(serverPlayer, this.jobId);
            BlueprintImportResultPacket.sendSuccess(serverPlayer, active ? "started" : "stopped", "");
        } catch (ConstructionBlueprintException exception) {
            BlueprintImportResultPacket.sendFailure(serverPlayer, exception);
        }
    }
}
