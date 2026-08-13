package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 已交付施工投影的区块段差量。clear 为真时客户端删除该任务在该段的全部假方块;
 * 否则用 positions/states 整段替换。字段只能追加。
 */
public record ConstructionProjectionSectionPacket(
    UUID jobId,
    long section,
    boolean clear,
    List<BlockPos> positions,
    List<BlockState> states
) implements IClientboundPacket {
    public static final Type<ConstructionProjectionSectionPacket> TYPE = IPacket.type(
        AnvilcraftPlasticraft.of("construction_projection_section")
    );
    private static final StreamCodec<RegistryFriendlyByteBuf, BlockState> BLOCK_STATE_CODEC =
        ByteBufCodecs.fromCodecWithRegistries(BlockState.CODEC);
    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionProjectionSectionPacket> STREAM_CODEC =
        StreamCodec.of(
            ConstructionProjectionSectionPacket::write,
            ConstructionProjectionSectionPacket::read
        );

    public static ConstructionProjectionSectionPacket clear(UUID jobId, long section) {
        return new ConstructionProjectionSectionPacket(jobId, section, true, List.of(), List.of());
    }

    private static void write(RegistryFriendlyByteBuf buffer, ConstructionProjectionSectionPacket packet) {
        UUIDUtil.STREAM_CODEC.encode(buffer, packet.jobId);
        buffer.writeLong(packet.section);
        buffer.writeBoolean(packet.clear);
        buffer.writeVarInt(packet.positions.size());
        for (int index = 0; index < packet.positions.size(); index++) {
            buffer.writeBlockPos(packet.positions.get(index));
            BLOCK_STATE_CODEC.encode(buffer, packet.states.get(index));
        }
    }

    private static ConstructionProjectionSectionPacket read(RegistryFriendlyByteBuf buffer) {
        UUID jobId = UUIDUtil.STREAM_CODEC.decode(buffer);
        long section = buffer.readLong();
        boolean clear = buffer.readBoolean();
        int count = buffer.readVarInt();
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> states = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(buffer.readBlockPos());
            states.add(BLOCK_STATE_CODEC.decode(buffer));
        }
        return new ConstructionProjectionSectionPacket(jobId, section, clear, positions, states);
    }

    @Override
    public Type<ConstructionProjectionSectionPacket> type() {
        return TYPE;
    }

    @Override
    public void handleOnClient(Player player) {
        if (player.level() == null) return;
        if (this.clear) {
            ConstructionProjectionIndex.clearClientSection(player.level(), this.jobId, this.section);
            return;
        }
        ConstructionProjectionIndex.applyClientSection(
            player.level(),
            this.jobId,
            this.section,
            this.positions,
            this.states
        );
    }
}
