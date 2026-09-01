package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** 强化喷流燃料识别与消耗的公共游戏逻辑。 */
public final class EnhancedPlasmaJetFuel {
    public static final int CONSUMPTION_PER_TICK = 10;
    public static final int LAYERED_DURATION = 50;

    private EnhancedPlasmaJetFuel() {
    }

    public static FuelMode activate(Level level, BlockPos pos) {
        if (HighHeatFuelCauldronBlock.consumeLayer(level, pos)) return FuelMode.LAYERED;
        return isIgnitedHighHeatFuel(level, pos) ? FuelMode.CONTINUOUS : FuelMode.NONE;
    }

    public static boolean consumeLayeredFuel(Level level, BlockPos pos) {
        return HighHeatFuelCauldronBlock.consumeLayer(level, pos);
    }

    public static boolean isIgnitedHighHeatFuel(Level level, BlockPos pos) {
        Boolean entityResult = HardenedResinCauldronSupport.isIgnitedHighHeatFuel(level, pos);
        if (entityResult != null) return entityResult;
        BlockCache cache = new BlockCache(level);
        if (!(cache.getBlockState(pos).getBlock() instanceof IIgnitableCauldron cauldron)) return false;
        return cauldron.isIgnited(cache, pos) && cauldron.getFluid(cache, pos) == PlasticraftFluids.HIGH_HEAT_FUEL.get();
    }

    public static boolean isValidBase(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get())) {
            return true;
        }
        Boolean entityResult = HardenedResinCauldronSupport.hasHighHeatFuel(level, pos);
        if (entityResult != null) return entityResult;
        FluidContainerLookup.Result endpoint = FluidContainerLookup.find(level, pos, null);
        if (endpoint == null) return false;
        for (int tank = 0; tank < endpoint.handler().getTanks(); tank++) {
            if (endpoint.handler().getFluidInTank(tank).is(PlasticraftFluids.HIGH_HEAT_FUEL.get())) return true;
        }
        return false;
    }

    public static boolean consumeContinuousFuel(Level level, BlockPos pos) {
        Boolean entityResult = HardenedResinCauldronSupport.consumeHighHeatFuel(
            level,
            pos,
            CONSUMPTION_PER_TICK
        );
        if (entityResult != null) return entityResult;
        FluidContainerLookup.Result endpoint = FluidContainerLookup.find(level, pos, null);
        if (endpoint == null) return false;
        FluidStack request = new FluidStack(PlasticraftFluids.HIGH_HEAT_FUEL.get(), CONSUMPTION_PER_TICK);
        FluidStack simulated = endpoint.handler().drain(request, IFluidHandler.FluidAction.SIMULATE);
        if (!FluidStack.matches(simulated, request)) return false;
        FluidStack drained = endpoint.handler().drain(request, IFluidHandler.FluidAction.EXECUTE);
        return FluidStack.matches(drained, request);
    }

    public enum FuelMode {
        NONE,
        CONTINUOUS,
        LAYERED
    }
}
