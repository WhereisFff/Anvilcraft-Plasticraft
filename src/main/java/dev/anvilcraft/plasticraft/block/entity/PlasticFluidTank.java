package dev.anvilcraft.plasticraft.block.entity;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** 机器单槽储罐允许生产流程独立试算，也允许外部管道在同一事务中搬运。 */
class PlasticFluidTank extends FluidStacksResourceHandler {
    PlasticFluidTank(int capacity) {
        super(1, capacity);
    }

    void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    FluidStack getFluid() {
        return FluidUtil.getStack(this, 0);
    }

    int getFluidAmount() {
        return this.getAmountAsInt(0);
    }

    void setFluid(FluidStack fluid) {
        this.set(0, FluidResource.of(fluid), fluid.getAmount());
    }

    int fill(FluidStack fluid, FluidAction action) {
        if (fluid.isEmpty()) {
            return 0;
        }
        try (var transaction = Transaction.openRoot()) {
            int inserted = this.insert(0, FluidResource.of(fluid), fluid.getAmount(), transaction);
            if (action.execute()) {
                transaction.commit();
            }
            return inserted;
        }
    }

    FluidStack drain(FluidStack requested, FluidAction action) {
        if (requested.isEmpty()) {
            return FluidStack.EMPTY;
        }
        try (var transaction = Transaction.openRoot()) {
            int extracted = this.extract(0, FluidResource.of(requested), requested.getAmount(), transaction);
            if (action.execute()) {
                transaction.commit();
            }
            return requested.copyWithAmount(extracted);
        }
    }

    FluidStack drain(int amount, FluidAction action) {
        return this.drain(this.getFluid().copyWithAmount(amount), action);
    }
}
