package dev.anvilcraft.plasticraft.vapor;

import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/** 把铁砧工艺大型炼药锅适配到塑料工艺气化运行时。 */
public record LargeCauldronVaporHost(LargeCauldronBlockEntity cauldron) implements VaporizationCauldron {
    @Override
    public BlockPos vaporizationPos() {
        return this.cauldron.getBlockPos();
    }

    @Override
    public boolean isMainVaporizationPart() {
        return this.cauldron.isMainPart();
    }

    @Override
    public FluidStack getTopVaporizationFluid() {
        return this.cauldron.getTopFluid();
    }

    @Override
    public FluidStack drainVaporizationFluid(FluidStack resource, IFluidHandler.FluidAction action) {
        return this.cauldron.getFluids().drainStoredFluid(resource, action);
    }

    public static @Nullable LargeCauldronBlockEntity unwrap(VaporizationCauldron cauldron) {
        return cauldron instanceof LargeCauldronVaporHost host ? host.cauldron() : null;
    }
}
