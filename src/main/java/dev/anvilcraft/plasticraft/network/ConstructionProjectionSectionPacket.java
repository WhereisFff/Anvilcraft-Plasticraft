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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    List<BlockState> states,
    List<SignMask> signMasks
) implements IClientboundPacket {
    /** 告示牌未加工掩码只在极少数位置非零,单独走稀疏表,常规整段只多付一个长度字节。 */
    public record SignMask(BlockPos pos, int pending) {
    }

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
        return new ConstructionProjectionSectionPacket(jobId, section, true, List.of(), List.of(), List.of());
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
        buffer.writeVarInt(packet.signMasks.size());
        for (SignMask mask : packet.signMasks) {
            buffer.writeBlockPos(mask.pos());
            buffer.writeVarInt(mask.pending());
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
        int masks = buffer.readVarInt();
        List<SignMask> signMasks = new ArrayList<>(masks);
        for (int index = 0; index < masks; index++) {
            BlockPos pos = buffer.readBlockPos();
            signMasks.add(new SignMask(pos, buffer.readVarInt()));
        }
        return new ConstructionProjectionSectionPacket(jobId, section, clear, positions, states, signMasks);
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
        Map<Long, Integer> pending = new HashMap<>(this.signMasks.size());
        for (SignMask mask : this.signMasks) {
            pending.put(mask.pos().asLong(), mask.pending());
        }
        ConstructionProjectionIndex.applyClientSection(
            player.level(),
            this.jobId,
            this.section,
            this.positions,
            this.states,
            pending
        );
    }
}
