package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionEntityProjectionIndex;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已交付实体施工投影差量。clear 为真时删除该任务的全部实体投影;
 * 否则用 entries 整表替换。不要改 ConstructionProjectionSectionPacket 既有字段。
 */
public record ConstructionEntityProjectionPacket(
    UUID jobId,
    boolean clear,
    List<ConstructionEntityProjectionIndex.Entry> entries
) implements IClientboundPacket {
    public static final Type<ConstructionEntityProjectionPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("construction_entity_projection")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionEntityProjectionPacket> STREAM_CODEC =
        StreamCodec.of(
            ConstructionEntityProjectionPacket::write,
            ConstructionEntityProjectionPacket::read
        );

    public static ConstructionEntityProjectionPacket clear(UUID jobId) {
        return new ConstructionEntityProjectionPacket(jobId, true, List.of());
    }

    public static ConstructionEntityProjectionPacket replace(
        UUID jobId,
        List<ConstructionEntityProjectionIndex.Entry> entries
    ) {
        return new ConstructionEntityProjectionPacket(jobId, false, List.copyOf(entries));
    }

    private static void write(RegistryFriendlyByteBuf buffer, ConstructionEntityProjectionPacket packet) {
        UUIDUtil.STREAM_CODEC.encode(buffer, packet.jobId);
        buffer.writeBoolean(packet.clear);
        buffer.writeVarInt(packet.entries.size());
        for (ConstructionEntityProjectionIndex.Entry entry : packet.entries) {
            buffer.writeVarInt(entry.opId());
            buffer.writeDouble(entry.pos().x);
            buffer.writeDouble(entry.pos().y);
            buffer.writeDouble(entry.pos().z);
            ByteBufCodecs.COMPOUND_TAG.encode(buffer, entry.nbt());
        }
    }

    private static ConstructionEntityProjectionPacket read(RegistryFriendlyByteBuf buffer) {
        UUID jobId = UUIDUtil.STREAM_CODEC.decode(buffer);
        boolean clear = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<ConstructionEntityProjectionIndex.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int opId = buffer.readVarInt();
            Vec3 pos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            CompoundTag nbt = ByteBufCodecs.COMPOUND_TAG.decode(buffer);
            entries.add(new ConstructionEntityProjectionIndex.Entry(opId, pos, nbt));
        }
        return new ConstructionEntityProjectionPacket(jobId, clear, entries);
    }

    @Override
    public Type<ConstructionEntityProjectionPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level() == null) {
            return;
        }
        ConstructionEntityProjectionIndex.applyClient(player.level(), this.jobId, this.clear, this.entries);
    }
}
