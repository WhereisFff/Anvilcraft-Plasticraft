package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.init.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 为高热燃料炼药锅提供动态火焰渲染锚点。 */
public final class HighHeatFuelCauldronBlockEntity extends BlockEntity {
    private static final String TAG_SPENT = "spent";
    private boolean spent;

    public HighHeatFuelCauldronBlockEntity(
        BlockEntityType<? extends HighHeatFuelCauldronBlockEntity> type,
        BlockPos pos,
        BlockState state
    ) {
        super(type, pos, state);
    }

    public HighHeatFuelCauldronBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.HIGH_HEAT_FUEL_CAULDRON.get(), pos, state);
    }

    public boolean isSpent() {
        return this.spent;
    }

    public void setSpent(boolean spent) {
        if (this.spent == spent) return;
        this.spent = spent;
        this.setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(
                this.getBlockPos(),
                this.getBlockState(),
                this.getBlockState(),
                Block.UPDATE_CLIENTS
            );
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(TAG_SPENT, this.spent);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.spent = tag.contains(TAG_SPENT)
            ? tag.getBoolean(TAG_SPENT)
            : this.getBlockState().hasProperty(dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock.IGNITED)
                && this.getBlockState().getValue(dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock.IGNITED)
                && this.getBlockState().getValue(dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock.LEVEL) == 1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
