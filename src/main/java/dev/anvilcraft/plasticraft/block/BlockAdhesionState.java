package dev.anvilcraft.plasticraft.block;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** 记录一个真实方块六个面上的裸胶、方块粘合和实体粘合。 */
public record BlockAdhesionState(
    ResourceLocation blockId,
    int patchMask,
    int blockBondMask,
    int entityBondMask
) {
    private static final String TAG_BLOCK = "Block";
    private static final String TAG_PATCHES = "Patches";
    private static final String TAG_BLOCK_BONDS = "BlockBonds";
    private static final String TAG_ENTITY_BONDS = "EntityBonds";
    private static final int FACE_MASK = 0x3F;

    public BlockAdhesionState {
        Objects.requireNonNull(blockId, "blockId");
        patchMask &= FACE_MASK;
        blockBondMask &= FACE_MASK;
        entityBondMask &= FACE_MASK;
        patchMask &= ~blockBondMask;
        entityBondMask &= ~blockBondMask;
    }

    public static BlockAdhesionState empty(BlockState state) {
        return new BlockAdhesionState(BuiltInRegistries.BLOCK.getKey(state.getBlock()), 0, 0, 0);
    }

    public boolean matches(BlockState state) {
        return this.blockId.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public boolean hasPatch(Direction face) {
        return (this.patchMask & bit(face)) != 0;
    }

    public boolean hasBlockBond(Direction face) {
        return (this.blockBondMask & bit(face)) != 0;
    }

    public boolean hasEntityBond(Direction face) {
        return (this.entityBondMask & bit(face)) != 0;
    }

    public boolean hasAnyBond() {
        return (this.blockBondMask | this.entityBondMask) != 0;
    }

    public boolean isEmpty() {
        return (this.patchMask | this.blockBondMask | this.entityBondMask) == 0;
    }

    public BlockAdhesionState withPatch(Direction face) {
        int bit = bit(face);
        if ((this.blockBondMask & bit) != 0) return this;
        return new BlockAdhesionState(this.blockId, this.patchMask | bit, this.blockBondMask, this.entityBondMask);
    }

    public BlockAdhesionState withoutPatch(Direction face) {
        return new BlockAdhesionState(
            this.blockId,
            this.patchMask & ~bit(face),
            this.blockBondMask,
            this.entityBondMask
        );
    }

    public BlockAdhesionState withBlockBond(Direction face) {
        int bit = bit(face);
        return new BlockAdhesionState(
            this.blockId,
            this.patchMask & ~bit,
            this.blockBondMask | bit,
            this.entityBondMask & ~bit
        );
    }

    public BlockAdhesionState withoutBlockBond(Direction face) {
        return new BlockAdhesionState(
            this.blockId,
            this.patchMask,
            this.blockBondMask & ~bit(face),
            this.entityBondMask
        );
    }

    public BlockAdhesionState withEntityBond(Direction face) {
        int bit = bit(face);
        return new BlockAdhesionState(
            this.blockId,
            this.patchMask,
            this.blockBondMask & ~bit,
            this.entityBondMask | bit
        );
    }

    public BlockAdhesionState withoutEntityBond(Direction face) {
        return new BlockAdhesionState(
            this.blockId,
            this.patchMask,
            this.blockBondMask,
            this.entityBondMask & ~bit(face)
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_BLOCK, this.blockId.toString());
        tag.putByte(TAG_PATCHES, (byte) this.patchMask);
        tag.putByte(TAG_BLOCK_BONDS, (byte) this.blockBondMask);
        tag.putByte(TAG_ENTITY_BONDS, (byte) this.entityBondMask);
        return tag;
    }

    public static BlockAdhesionState load(CompoundTag tag) {
        ResourceLocation blockId = ResourceLocation.tryParse(tag.getString(TAG_BLOCK));
        if (blockId == null) blockId = BuiltInRegistries.BLOCK.getDefaultKey();
        return new BlockAdhesionState(
            blockId,
            tag.getByte(TAG_PATCHES),
            tag.getByte(TAG_BLOCK_BONDS),
            tag.getByte(TAG_ENTITY_BONDS)
        );
    }

    private static int bit(Direction face) {
        return 1 << face.get3DDataValue();
    }
}
