package dev.anvilcraft.plasticraft.api;

import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 为压盖的瞬时整量挤出提供管网事务接口。 */
public interface FluidPipeNetworkExtension {
    boolean plasticraft$pushAll(
        IFluidHandler source,
        BlockPos sourcePos,
        BlockPos entryPipePos,
        int sourceEffectiveHeight,
        FluidStack fluid
    );
}
