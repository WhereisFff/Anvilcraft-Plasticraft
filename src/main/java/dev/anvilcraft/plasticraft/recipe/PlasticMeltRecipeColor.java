package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeData;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;

/** 在一次世界内配方中传递熔体的源颜色和染料目标色。 */
public final class PlasticMeltRecipeColor {
    private static final InWorldRecipeData<State> DATA = InWorldRecipeData.of(
        AnvilcraftPlasticraft.of("plastic_melt_recipe_color"),
        (context, key) -> new State()
    );

    private PlasticMeltRecipeColor() {
    }

    public static void captureCauldron(InWorldRecipeContext context, Vec3 offset) {
        BlockPos pos = BlockPos.containing(context.getPos().add(offset));
        FluidContainerLookup.Result result = FluidContainerLookup.find(context.getLevel(), pos, null);
        if (result == null) return;
        FluidStack melt = findMelt(result.handler());
        if (melt.isEmpty()) return;
        State state = context.computeIfAbsent(DATA);
        state.cauldronPos = pos;
        state.sourceColor = colorAt(context, pos, melt);
        state.hasMelt = true;
    }

    public static void captureDye(InWorldRecipeContext context, DyeColor color) {
        context.computeIfAbsent(DATA).dyeColor = color;
    }

    public static DyeColor resultColor(InWorldRecipeContext context) {
        State state = context.computeIfAbsent(DATA);
        return state.dyeColor == null ? state.sourceColor : state.dyeColor;
    }

    public static void apply(InWorldRecipeContext context) {
        State data = context.computeIfAbsent(DATA);
        if (!data.hasMelt || data.dyeColor == null || data.cauldronPos == null) return;
        BlockPos pos = data.cauldronPos;
        BlockState blockState = context.getLevel().getBlockState(pos);
        if (blockState.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())) {
            context.getLevel().setBlock(
                pos,
                blockState.setValue(UniversalPlasticMeltCauldronBlock.COLOR, data.dyeColor),
                Block.UPDATE_CLIENTS
            );
        }
        if (context.getLevel().getBlockEntity(pos) instanceof LargeCauldronBlockEntity cauldron) {
            List<FluidStack> fluids = cauldron.getFluids().copyFluids();
            boolean changed = false;
            for (FluidStack fluid : fluids) {
                if (!fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) continue;
                PlasticMeltColor.set(fluid, data.dyeColor);
                changed = true;
            }
            if (changed) cauldron.getFluids().setFluids(fluids);
            return;
        }
        FluidContainerLookup.Result result = FluidContainerLookup.find(context.getLevel(), pos, null);
        if (result != null) recolorFirstMelt(result.handler(), data.dyeColor);
    }

    private static DyeColor colorAt(InWorldRecipeContext context, BlockPos pos, FluidStack melt) {
        BlockState state = context.getLevel().getBlockState(pos);
        if (state.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())) {
            return state.getValue(UniversalPlasticMeltCauldronBlock.COLOR);
        }
        return PlasticMeltColor.get(melt);
    }

    private static FluidStack findMelt(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.is(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get())) return fluid.copy();
        }
        return FluidStack.EMPTY;
    }

    private static void recolorFirstMelt(IFluidHandler handler, DyeColor color) {
        FluidStack melt = findMelt(handler);
        if (melt.isEmpty()) return;
        FluidStack replacement = melt.copy();
        PlasticMeltColor.set(replacement, color);
        FluidStack drained = handler.drain(melt, IFluidHandler.FluidAction.EXECUTE);
        if (drained.getAmount() != melt.getAmount()) {
            if (!drained.isEmpty()) handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
            return;
        }
        int filled = handler.fill(replacement, IFluidHandler.FluidAction.EXECUTE);
        if (filled != replacement.getAmount()) {
            if (filled > 0) handler.drain(replacement.copyWithAmount(filled), IFluidHandler.FluidAction.EXECUTE);
            handler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        }
    }

    private static final class State {
        private BlockPos cauldronPos;
        private DyeColor sourceColor = DyeColor.WHITE;
        private DyeColor dyeColor;
        private boolean hasMelt;
    }
}
