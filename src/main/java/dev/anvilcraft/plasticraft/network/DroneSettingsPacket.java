package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.drone.DroneShortageStrategy;
import dev.anvilcraft.plasticraft.inventory.DroneMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 在无人机设置菜单中切换缺料与缺拆除能力策略。 */
public record DroneSettingsPacket(
    int containerId,
    DroneShortageStrategy strategy
) implements IServerboundPacket {
    public static final Type<DroneSettingsPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("drone_settings")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneSettingsPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeVarInt(packet.containerId);
            DroneShortageStrategy.STREAM_CODEC.encode(buffer, packet.strategy);
        },
        buffer -> new DroneSettingsPacket(buffer.readVarInt(), DroneShortageStrategy.STREAM_CODEC.decode(buffer))
    );

    @Override
    public Type<DroneSettingsPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (player.containerMenu.containerId != this.containerId) return;
        if (!(player.containerMenu instanceof DroneMenu menu)) return;
        if (!menu.stillValid(player)) return;
        menu.applyStrategy(this.strategy);
    }
}
