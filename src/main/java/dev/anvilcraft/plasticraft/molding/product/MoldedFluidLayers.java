package dev.anvilcraft.plasticraft.molding.product;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** 同一组有序流体层供自动化、配方与按高度交互读取。 */
public interface MoldedFluidLayers extends ResourceHandler<FluidResource>, IFluidHandler {
    @Override
    default int size() {
        return this.getTanks();
    }

    @Override
    default FluidResource getResource(int index) {
        return FluidResource.of(this.getFluidInTank(index));
    }

    @Override
    default long getAmountAsLong(int index) {
        return this.getFluidInTank(index).getAmount();
    }

    @Override
    default long getCapacityAsLong(int index, FluidResource resource) {
        return this.getTankCapacity(index);
    }

    @Override
    default boolean isValid(int index, FluidResource resource) {
        return this.isFluidValid(index, resource.toStack(1));
    }

    @Override
    default int fill(FluidStack resource, FluidAction action) {
        return IFluidHandler.of(this).fill(resource, action);
    }

    @Override
    default FluidStack drain(FluidStack resource, FluidAction action) {
        return IFluidHandler.of(this).drain(resource, action);
    }

    @Override
    default FluidStack drain(int amount, FluidAction action) {
        return IFluidHandler.of(this).drain(amount, action);
    }
}
