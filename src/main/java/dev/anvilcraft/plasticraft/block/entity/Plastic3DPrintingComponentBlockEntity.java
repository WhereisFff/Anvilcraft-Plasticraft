package dev.anvilcraft.plasticraft.block.entity;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** 保存单件打印制品所需的塑料熔体，并只允许下方成型舱驱动转移。 */
public final class Plastic3DPrintingComponentBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8192;
    private static final String TAG_TANK = "Tank";

    private final PlasticFluidTank tank = new PlasticFluidTank(CAPACITY) {
        @Override
        public boolean isValid(int index, FluidResource resource) {
            return PlasticMaterial.isMelt(resource.toStack(1));
        }

        @Override
        protected void onContentsChanged(int index, FluidStack previous) {
            Plastic3DPrintingComponentBlockEntity.this.syncContents();
        }
    };

    public Plastic3DPrintingComponentBlockEntity(BlockPos pos, BlockState state) {
        this(PlasticraftBlockEntities.PLASTIC_3D_PRINTING_COMPONENT.get(), pos, state);
    }

    public Plastic3DPrintingComponentBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    PlasticFluidTank tank() {
        return this.tank;
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
    protected void saveAdditional(ValueOutput tag) {
        super.saveAdditional(tag);
        tag.putChild(TAG_TANK, this.tank);
    }

    @Override
    public void loadAdditional(ValueInput tag) {
        super.loadAdditional(tag);
        tag.readChild(TAG_TANK, this.tank);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        return this.saveWithoutMetadata(provider);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void syncContents() {
        this.setChanged();
        if (this.level == null || this.level.isClientSide()) return;
        BlockState state = this.getBlockState();
        this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_CLIENTS);
    }
}
