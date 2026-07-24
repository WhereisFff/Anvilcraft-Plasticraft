package dev.anvilcraft.plasticraft.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/** 普通下落方块恢复原方块状态后保留的胶粘关系。 */
public record BondedFallingBlockInfo(
    BlockState blockState,
    BlockPos supportPos,
    ResourceLocation supportBlockId,
    boolean pistonMovable
) {
    private static final String TAG_STATE = "State";
    private static final String TAG_SUPPORT_POS = "SupportPos";
    private static final String TAG_SUPPORT_BLOCK = "SupportBlock";
    private static final String TAG_PISTON_MOVABLE = "PistonMovable";

    public BondedFallingBlockInfo {
        Objects.requireNonNull(blockState, "blockState");
        supportPos = Objects.requireNonNull(supportPos, "supportPos").immutable();
        Objects.requireNonNull(supportBlockId, "supportBlockId");
    }

    public boolean hasSupport(Level level) {
        if (!level.hasChunkAt(this.supportPos)) return true;
        BlockState supportState = level.getBlockState(this.supportPos);
        if (supportState.is(Blocks.MOVING_PISTON)) return true;
        return !supportState.isAir()
            && this.supportBlockId.equals(BuiltInRegistries.BLOCK.getKey(supportState.getBlock()));
    }

    public BondedFallingBlockInfo moved(net.minecraft.core.Direction direction) {
        return this.translated(new BlockPos(
            direction.getStepX(),
            direction.getStepY(),
            direction.getStepZ()
        ));
    }

    public BondedFallingBlockInfo translated(BlockPos offset) {
        return new BondedFallingBlockInfo(
            this.blockState,
            this.supportPos.offset(offset),
            this.supportBlockId,
            this.pistonMovable
        );
    }

    public BondedFallingBlockInfo withBlockState(BlockState state) {
        return new BondedFallingBlockInfo(
            state,
            this.supportPos,
            this.supportBlockId,
            this.pistonMovable
        );
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.put(TAG_STATE, NbtUtils.writeBlockState(this.blockState));
        tag.putLong(TAG_SUPPORT_POS, this.supportPos.asLong());
        tag.putString(TAG_SUPPORT_BLOCK, this.supportBlockId.toString());
        tag.putBoolean(TAG_PISTON_MOVABLE, this.pistonMovable);
        return tag;
    }

    public static BondedFallingBlockInfo load(CompoundTag tag, HolderLookup.Provider provider) {
        BlockState state = NbtUtils.readBlockState(
            provider.lookupOrThrow(Registries.BLOCK),
            tag.getCompound(TAG_STATE)
        );
        ResourceLocation supportBlock = ResourceLocation.tryParse(tag.getString(TAG_SUPPORT_BLOCK));
        if (supportBlock == null) supportBlock = BuiltInRegistries.BLOCK.getKey(Blocks.AIR);
        return new BondedFallingBlockInfo(
            state,
            BlockPos.of(tag.getLong(TAG_SUPPORT_POS)),
            supportBlock,
            tag.getBoolean(TAG_PISTON_MOVABLE)
        );
    }
}
