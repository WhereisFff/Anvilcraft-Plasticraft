package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.event.AdhesiveSelectionClientHandler;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** 将服务端完成的树脂牵引路径同步给发起预览的客户端。 */
public record AdhesivePreviewPathPacket(
    int requestId,
    boolean groupClear,
    AdhesivePathPlanner.Plan plan
) implements IClientboundPacket {
    private static final int MAX_PATH_POINTS = 256;

    public static final Type<AdhesivePreviewPathPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("adhesive_preview_path")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, AdhesivePreviewPathPacket> STREAM_CODEC = StreamCodec.of(
        (buffer, packet) -> {
            buffer.writeVarInt(packet.requestId);
            buffer.writeBoolean(packet.groupClear);
            AdhesivePathPlanner.Plan plan = packet.plan;
            buffer.writeVarInt(plan.status().ordinal());
            buffer.writeVarInt(plan.points().size());
            for (Vec3 point : plan.points()) writeVec3(buffer, point);
            writeVec3(buffer, plan.targetPosition());
            BlockPos.STREAM_CODEC.encode(buffer, plan.occupiedPos());
            buffer.writeBoolean(plan.targetOrientation() != null);
            if (plan.targetOrientation() != null) buffer.writeByte(plan.targetOrientation().pack());
            buffer.writeByte(plan.sourceFace().get3DDataValue());
            buffer.writeDouble(plan.directDistance());
        },
        buffer -> {
            int requestId = buffer.readVarInt();
            boolean groupClear = buffer.readBoolean();
            int statusOrdinal = buffer.readVarInt();
            AdhesivePathPlanner.Status[] statuses = AdhesivePathPlanner.Status.values();
            if (statusOrdinal < 0 || statusOrdinal >= statuses.length) {
                throw new IllegalArgumentException("Invalid adhesive preview status");
            }
            int pointCount = buffer.readVarInt();
            if (pointCount < 2 || pointCount > MAX_PATH_POINTS) {
                throw new IllegalArgumentException("Invalid adhesive preview path size");
            }
            List<Vec3> points = new ArrayList<>(pointCount);
            for (int index = 0; index < pointCount; index++) points.add(readVec3(buffer));
            Vec3 targetPosition = readVec3(buffer);
            BlockPos occupiedPos = BlockPos.STREAM_CODEC.decode(buffer);
            @Nullable PlasticEntityOrientation targetOrientation = buffer.readBoolean()
                ? PlasticEntityOrientation.unpack(buffer.readUnsignedByte())
                : null;
            Direction sourceFace = Direction.from3DDataValue(buffer.readUnsignedByte());
            double directDistance = buffer.readDouble();
            return new AdhesivePreviewPathPacket(
                requestId,
                groupClear,
                new AdhesivePathPlanner.Plan(
                    statuses[statusOrdinal],
                    points,
                    targetPosition,
                    occupiedPos,
                    targetOrientation,
                    sourceFace,
                    directDistance
                )
            );
        }
    );

    @Override
    public Type<AdhesivePreviewPathPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        AdhesiveSelectionClientHandler.handlePreviewPath(this.requestId, this.groupClear, this.plan);
    }

    private static void writeVec3(RegistryFriendlyByteBuf buffer, Vec3 point) {
        buffer.writeDouble(point.x);
        buffer.writeDouble(point.y);
        buffer.writeDouble(point.z);
    }

    private static Vec3 readVec3(RegistryFriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }
}
