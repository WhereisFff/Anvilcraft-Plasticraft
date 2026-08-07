package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/** 保存单件打印制品所需的塑料熔体，并只允许下方成型舱驱动转移。 */
public final class Plastic3DPrintingComponentBlockEntity extends BlockEntity {
    public static final int CAPACITY = MoldingVolumeMask.CELL_COUNT / 4;
    private static final String TAG_TANK = "Tank";

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        public boolean isFluidValid(FluidStack stack) {
            return !stack.isEmpty()
                && stack.getFluid().getFluidType() == PlasticraftFluids.UNIVERSAL_PLASTIC_MELT_TYPE.get();
        }

        @Override
        protected void onContentsChanged() {
            Plastic3DPrintingComponentBlockEntity.this.syncContents();
        }
    };

    public Plastic3DPrintingComponentBlockEntity(BlockPos pos, BlockState state) {
        this(PlasticraftBlockEntities.PLASTIC_3D_PRINTING_COMPONENT.get(), pos, state);
    }

    public Plastic3DPrintingComponentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public FluidStack fluid() {
        return this.tank.getFluid().copy();
    }

    public int fluidAmount() {
        return this.tank.getFluidAmount();
    }

    public int fillFromChamber(FluidStack resource, IFluidHandler.FluidAction action) {
        return this.tank.fill(resource, action);
    }

    public FluidStack consumeForPrinting(int amount, IFluidHandler.FluidAction action) {
        return this.tank.drain(amount, action);
    }

    public void setFluidFromChamber(FluidStack fluid) {
        this.tank.setFluid(fluid.copy());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.put(TAG_TANK, this.tank.writeToNBT(provider, new CompoundTag()));
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.tank.readFromNBT(provider, tag.getCompound(TAG_TANK));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        tag.put(TAG_TANK, this.tank.writeToNBT(provider, new CompoundTag()));
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncContents() {
        this.setChanged();
        if (this.level == null || this.level.isClientSide) return;
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }
}
