package dev.anvilcraft.plasticraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** 每个区块内所有已固定普通下落方块的持久化和客户端同步数据。 */
public record BondedFallingChunkData(
    Map<BlockPos, BondedFallingBlockInfo> entries,
    Map<BlockPos, BlockAdhesionState> adhesions
) {
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_ADHESIONS = "Adhesions";
    private static final String TAG_POS = "Pos";
    private static final String TAG_INFO = "Info";

    public static final IAttachmentSerializer<BondedFallingChunkData> SERIALIZER =
        new IAttachmentSerializer<>() {
            @Override
            public BondedFallingChunkData read(
                IAttachmentHolder holder, ValueInput input
            ) {
                return BondedFallingChunkData.load(input.read("value", CompoundTag.CODEC).orElseGet(CompoundTag::new), input.lookup());
            }

            @Override
            public boolean write(BondedFallingChunkData data, ValueOutput output) {
                if (data.entries.isEmpty() && data.adhesions.isEmpty()) {
                    return false;
                }
                output.store("value", CompoundTag.CODEC, data.save());
                return true;
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, BondedFallingChunkData> STREAM_CODEC = StreamCodec.of(
        (buffer, data) -> buffer.writeNbt(data.save()),
        buffer -> {
            CompoundTag tag = buffer.readNbt();
            return tag == null ? empty() : load(tag, buffer.registryAccess());
        }
    );

    public BondedFallingChunkData {
        entries = Map.copyOf(entries);
        adhesions = Map.copyOf(adhesions);
    }

    public BondedFallingChunkData(Map<BlockPos, BondedFallingBlockInfo> entries) {
        this(entries, Map.of());
    }

    public static BondedFallingChunkData empty() {
        return new BondedFallingChunkData(Map.of(), Map.of());
    }

    public @Nullable BondedFallingBlockInfo get(BlockPos pos) {
        return this.entries.get(pos);
    }

    public BondedFallingChunkData with(BlockPos pos, BondedFallingBlockInfo info) {
        Map<BlockPos, BondedFallingBlockInfo> changed = new LinkedHashMap<>(this.entries);
        changed.put(pos.immutable(), info);
        return new BondedFallingChunkData(changed, this.adhesions);
    }

    public BondedFallingChunkData without(BlockPos pos) {
        if (!this.entries.containsKey(pos)) return this;
        Map<BlockPos, BondedFallingBlockInfo> changed = new LinkedHashMap<>(this.entries);
        changed.remove(pos);
        return new BondedFallingChunkData(changed, this.adhesions);
    }

    public @Nullable BlockAdhesionState getAdhesion(BlockPos pos) {
        return this.adhesions.get(pos);
    }

    public BondedFallingChunkData withAdhesion(BlockPos pos, BlockAdhesionState state) {
        Map<BlockPos, BlockAdhesionState> changed = new LinkedHashMap<>(this.adhesions);
        changed.put(pos.immutable(), state);
        return new BondedFallingChunkData(this.entries, changed);
    }

    public BondedFallingChunkData withoutAdhesion(BlockPos pos) {
        if (!this.adhesions.containsKey(pos)) return this;
        Map<BlockPos, BlockAdhesionState> changed = new LinkedHashMap<>(this.adhesions);
        changed.remove(pos);
        return new BondedFallingChunkData(this.entries, changed);
    }

    private CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag entriesTag = new ListTag();
        for (Map.Entry<BlockPos, BondedFallingBlockInfo> entry : this.entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_POS, entry.getKey().asLong());
            entryTag.put(TAG_INFO, entry.getValue().save());
            entriesTag.add(entryTag);
        }
        tag.put(TAG_ENTRIES, entriesTag);
        ListTag adhesionsTag = new ListTag();
        for (Map.Entry<BlockPos, BlockAdhesionState> entry : this.adhesions.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_POS, entry.getKey().asLong());
            entryTag.put(TAG_INFO, entry.getValue().save());
            adhesionsTag.add(entryTag);
        }
        if (!adhesionsTag.isEmpty()) tag.put(TAG_ADHESIONS, adhesionsTag);
        return tag;
    }

    private static BondedFallingChunkData load(CompoundTag tag, HolderLookup.Provider provider) {
        Map<BlockPos, BondedFallingBlockInfo> entries = new LinkedHashMap<>();
        ListTag entriesTag = tag.getListOrEmpty(TAG_ENTRIES);
        for (int i = 0; i < entriesTag.size(); i++) {
            CompoundTag entryTag = entriesTag.getCompoundOrEmpty(i);
            if (!entryTag.contains(TAG_INFO)) continue;
            BlockPos pos = BlockPos.of(entryTag.getLongOr(TAG_POS, 0L));
            entries.put(pos, BondedFallingBlockInfo.load(entryTag.getCompoundOrEmpty(TAG_INFO), provider));
        }
        Map<BlockPos, BlockAdhesionState> adhesions = new LinkedHashMap<>();
        ListTag adhesionsTag = tag.getListOrEmpty(TAG_ADHESIONS);
        for (int i = 0; i < adhesionsTag.size(); i++) {
            CompoundTag entryTag = adhesionsTag.getCompoundOrEmpty(i);
            if (!entryTag.contains(TAG_INFO)) continue;
            BlockPos pos = BlockPos.of(entryTag.getLongOr(TAG_POS, 0L));
            adhesions.put(pos, BlockAdhesionState.load(entryTag.getCompoundOrEmpty(TAG_INFO)));
        }
        return new BondedFallingChunkData(entries, adhesions);
    }
}
