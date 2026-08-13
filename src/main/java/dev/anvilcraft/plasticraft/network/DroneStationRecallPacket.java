package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.lib.v2.network.packet.IServerboundPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.entity.DroneStationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/** 站点界面的召回指令:让站点周围的世界无人机依次返站入库。 */
public record DroneStationRecallPacket(BlockPos stationPos) implements IServerboundPacket {
    public static final Type<DroneStationRecallPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("drone_station_recall")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DroneStationRecallPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> buffer.writeBlockPos(packet.stationPos),
        buffer -> new DroneStationRecallPacket(buffer.readBlockPos())
    );

    @Override
    public Type<DroneStationRecallPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnServer(Player player) {
        if (player.distanceToSqr(
            this.stationPos.getX() + 0.5D,
            this.stationPos.getY() + 0.5D,
            this.stationPos.getZ() + 0.5D
        ) > 64.0D) {
            return;
        }
        if (player.level().getBlockEntity(this.stationPos) instanceof DroneStationBlockEntity station) {
            station.recallNearbyDrones();
        }
    }
}
