package dev.anvilcraft.plasticraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

import javax.annotation.Nullable;
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

    public static final IAttachmentSerializer<CompoundTag, BondedFallingChunkData> SERIALIZER =
        new IAttachmentSerializer<>() {
            @Override
            public BondedFallingChunkData read(
                IAttachmentHolder holder,
                CompoundTag tag,
                HolderLookup.Provider provider
            ) {
                return BondedFallingChunkData.load(tag, provider);
            }

            @Override
            public @Nullable CompoundTag write(BondedFallingChunkData data, HolderLookup.Provider provider) {
                return data.entries.isEmpty() && data.adhesions.isEmpty() ? null : data.save(provider);
            }
        };

    public static final StreamCodec<RegistryFriendlyByteBuf, BondedFallingChunkData> STREAM_CODEC = StreamCodec.of(
        (buffer, data) -> buffer.writeNbt(data.save(buffer.registryAccess())),
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

    private CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        ListTag entriesTag = new ListTag();
        for (Map.Entry<BlockPos, BondedFallingBlockInfo> entry : this.entries.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putLong(TAG_POS, entry.getKey().asLong());
            entryTag.put(TAG_INFO, entry.getValue().save(provider));
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
        ListTag entriesTag = tag.getList(TAG_ENTRIES, Tag.TAG_COMPOUND);
        for (int i = 0; i < entriesTag.size(); i++) {
            CompoundTag entryTag = entriesTag.getCompound(i);
            if (!entryTag.contains(TAG_INFO, Tag.TAG_COMPOUND)) continue;
            BlockPos pos = BlockPos.of(entryTag.getLong(TAG_POS));
            entries.put(pos, BondedFallingBlockInfo.load(entryTag.getCompound(TAG_INFO), provider));
        }
        Map<BlockPos, BlockAdhesionState> adhesions = new LinkedHashMap<>();
        ListTag adhesionsTag = tag.getList(TAG_ADHESIONS, Tag.TAG_COMPOUND);
        for (int i = 0; i < adhesionsTag.size(); i++) {
            CompoundTag entryTag = adhesionsTag.getCompound(i);
            if (!entryTag.contains(TAG_INFO, Tag.TAG_COMPOUND)) continue;
            BlockPos pos = BlockPos.of(entryTag.getLong(TAG_POS));
            adhesions.put(pos, BlockAdhesionState.load(entryTag.getCompound(TAG_INFO)));
        }
        return new BondedFallingChunkData(entries, adhesions);
    }
}
