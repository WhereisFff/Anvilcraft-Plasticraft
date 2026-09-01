package dev.anvilcraft.plasticraft.network;

import dev.anvilcraft.lib.v2.network.packet.IClientboundPacket;
import dev.anvilcraft.lib.v2.network.packet.IPacket;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 已交付施工投影的区块段差量。clear 为真时客户端删除该任务在该段的全部假方块;
 * 否则用 positions/states 整段替换。record 字段只能追加。
 *
 * <p>编码按区块段压缩:方块状态走段内局部调色板(超大结构里同一段往往只有个别状态),
 * 位置按条目密度在 12 位局部坐标与 4096 位稀疏位集之间二选一。整段 4096 格因此从
 * 每格一个完整 BlockState NBT 降到"一份调色板 + 一张位图 + 每格一个索引"。
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

    /** 一个 16³ 区块段的格数上限,同时是解码时的分配上限:损坏或伪造的长度不得先分配再校验。 */
    private static final int SECTION_CELLS = 16 * 16 * 16;
    /** 位集固定 512 字节,超过这个条目数时逐条写 12 位局部坐标反而更长。 */
    private static final int BITSET_THRESHOLD = SECTION_CELLS / 16;
    private static final int BITSET_BYTES = SECTION_CELLS / 8;

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

    private static int localIndex(long section, BlockPos pos) {
        int x = pos.getX() - SectionPos.sectionToBlockCoord(SectionPos.x(section));
        int y = pos.getY() - SectionPos.sectionToBlockCoord(SectionPos.y(section));
        int z = pos.getZ() - SectionPos.sectionToBlockCoord(SectionPos.z(section));
        return (y << 8) | (z << 4) | x;
    }

    private static BlockPos fromLocalIndex(long section, int index) {
        return new BlockPos(
            SectionPos.sectionToBlockCoord(SectionPos.x(section)) + (index & 15),
            SectionPos.sectionToBlockCoord(SectionPos.y(section)) + ((index >> 8) & 15),
            SectionPos.sectionToBlockCoord(SectionPos.z(section)) + ((index >> 4) & 15)
        );
    }

    private static void write(RegistryFriendlyByteBuf buffer, ConstructionProjectionSectionPacket packet) {
        UUIDUtil.STREAM_CODEC.encode(buffer, packet.jobId);
        buffer.writeLong(packet.section);
        buffer.writeBoolean(packet.clear);
        int count = Math.min(packet.positions.size(), packet.states.size());
        // 段内按局部坐标升序写,解码端因此不必额外携带顺序信息
        List<Integer> order = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            order.add(index);
        }
        order.sort(Comparator.comparingInt(index -> localIndex(packet.section, packet.positions.get(index))));
        List<BlockState> palette = new ArrayList<>();
        Map<BlockState, Integer> paletteIds = new HashMap<>();
        int[] paletteIndices = new int[count];
        for (int slot = 0; slot < count; slot++) {
            BlockState state = packet.states.get(order.get(slot));
            paletteIndices[slot] = paletteIds.computeIfAbsent(state, key -> {
                palette.add(key);
                return palette.size() - 1;
            });
        }
        buffer.writeVarInt(count);
        if (count > 0) {
            buffer.writeVarInt(palette.size());
            for (BlockState state : palette) {
                BLOCK_STATE_CODEC.encode(buffer, state);
            }
            if (count >= BITSET_THRESHOLD) {
                byte[] bits = new byte[BITSET_BYTES];
                for (int index : order) {
                    int local = localIndex(packet.section, packet.positions.get(index));
                    bits[local >> 3] |= (byte) (1 << (local & 7));
                }
                buffer.writeBytes(bits);
            } else {
                for (int index : order) {
                    buffer.writeShort(localIndex(packet.section, packet.positions.get(index)));
                }
            }
            for (int slot = 0; slot < count; slot++) {
                buffer.writeVarInt(paletteIndices[slot]);
            }
        }
        buffer.writeVarInt(packet.signMasks.size());
        for (SignMask mask : packet.signMasks) {
            buffer.writeShort(localIndex(packet.section, mask.pos()));
            buffer.writeVarInt(mask.pending());
        }
    }

    private static ConstructionProjectionSectionPacket read(RegistryFriendlyByteBuf buffer) {
        UUID jobId = UUIDUtil.STREAM_CODEC.decode(buffer);
        long section = buffer.readLong();
        boolean clear = buffer.readBoolean();
        int count = clampSectionCount(buffer.readVarInt());
        List<BlockPos> positions = new ArrayList<>(count);
        List<BlockState> states = new ArrayList<>(count);
        if (count > 0) {
            int paletteSize = clampSectionCount(buffer.readVarInt());
            List<BlockState> palette = new ArrayList<>(paletteSize);
            for (int index = 0; index < paletteSize; index++) {
                palette.add(BLOCK_STATE_CODEC.decode(buffer));
            }
            if (count >= BITSET_THRESHOLD) {
                byte[] bits = new byte[BITSET_BYTES];
                buffer.readBytes(bits);
                for (int local = 0; local < SECTION_CELLS && positions.size() < count; local++) {
                    if ((bits[local >> 3] & (1 << (local & 7))) != 0) {
                        positions.add(fromLocalIndex(section, local));
                    }
                }
            } else {
                for (int index = 0; index < count; index++) {
                    positions.add(fromLocalIndex(section, buffer.readUnsignedShort() & (SECTION_CELLS - 1)));
                }
            }
            for (int index = 0; index < count; index++) {
                int paletteIndex = buffer.readVarInt();
                states.add(paletteIndex >= 0 && paletteIndex < palette.size()
                    ? palette.get(paletteIndex)
                    : null);
            }
            // 位置与状态必须严格一一对应;损坏或伪造的载荷宁可整段作废,也不能把错位状态画到别的格上
            if (positions.size() != states.size() || states.contains(null)) {
                positions.clear();
                states.clear();
            }
        }
        int masks = clampSectionCount(buffer.readVarInt());
        List<SignMask> signMasks = new ArrayList<>(masks);
        for (int index = 0; index < masks; index++) {
            BlockPos pos = fromLocalIndex(section, buffer.readUnsignedShort() & (SECTION_CELLS - 1));
            signMasks.add(new SignMask(pos, buffer.readVarInt()));
        }
        return new ConstructionProjectionSectionPacket(jobId, section, clear, positions, states, signMasks);
    }

    private static int clampSectionCount(int declared) {
        return Math.clamp(declared, 0, SECTION_CELLS);
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
