package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.dubhe.anvilcraft.block.cauldron.Layered4LevelCauldronBlock;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
        this(PlasticraftBlockEntities.HIGH_HEAT_FUEL_CAULDRON.get(), pos, state);
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
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_SPENT, this.spent);
    }

    @Override
    protected void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        this.spent = tag.keySet().contains(TAG_SPENT)
            ? tag.getBooleanOr(TAG_SPENT, false)
            : this.getBlockState().hasProperty(HighHeatFuelCauldronBlock.IGNITED)
                && this.getBlockState().getValue(HighHeatFuelCauldronBlock.IGNITED)
                && this.getBlockState().getValue(Layered4LevelCauldronBlock.LEVEL) == 1;
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
