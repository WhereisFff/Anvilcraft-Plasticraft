package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 气化运行时所需的大型炼药锅最小契约。 */
public interface VaporizationCauldron {
    BlockPos vaporizationPos();

    boolean isMainVaporizationPart();

    FluidStack getTopVaporizationFluid();

    /** 按报价精确抽取顶层流体；模拟与执行语义与 {@link IFluidHandler.FluidAction} 一致。 */
    FluidStack drainVaporizationFluid(FluidStack resource, IFluidHandler.FluidAction action);
}
