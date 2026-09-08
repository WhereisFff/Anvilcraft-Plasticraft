package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeData;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.entity.PlasticCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

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
        IFluidHandler handler = fluidHandler(context, pos);
        if (handler == null) return;
        FluidStack melt = findMelt(handler);
        if (melt.isEmpty()) return;
        PlasticMaterial material = PlasticMaterial.fromMelt(melt).orElse(null);
        if (material == null) return;
        State state = context.computeIfAbsent(DATA);
        state.cauldronPos = pos;
        state.sourceColor = colorAt(context, pos, melt);
        state.material = material;
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
        if (!data.hasMelt || data.dyeColor == null || data.cauldronPos == null || data.material == null) return;
        BlockPos pos = data.cauldronPos;
        BlockState blockState = context.getLevel().getBlockState(pos);
        if (data.material.supportsDyeing()
            && PlasticMaterial.fromMeltCauldron(blockState).filter(data.material::equals).isPresent()
            && blockState.hasProperty(UniversalPlasticMeltCauldronBlock.COLOR)) {
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
                if (!fluid.is(data.material.melt())) continue;
                if (data.material.supportsDyeing()) PlasticMeltColor.set(fluid, data.dyeColor);
                changed = true;
            }
            if (changed) cauldron.getFluids().setFluids(fluids);
            return;
        }
        IFluidHandler handler = fluidHandler(context, pos);
        if (handler != null) recolorFirstMelt(handler, data.material, data.dyeColor);
    }

    private static @Nullable IFluidHandler fluidHandler(InWorldRecipeContext context, BlockPos pos) {
        PlasticCauldron target = CauldronImpactRecipeProcessor.activeRecipeTarget();
        if (target != null && target.level() == context.getLevel()
            && pos.equals(CauldronImpactRecipeProcessor.activeRecipeTargetCell())) return target.getFluidHandler();
        FluidContainerLookup.Result result = FluidContainerLookup.find(context.getLevel(), pos, null);
        return result == null ? null : result.handler();
    }

    private static DyeColor colorAt(InWorldRecipeContext context, BlockPos pos, FluidStack melt) {
        BlockState state = context.getLevel().getBlockState(pos);
        if (state.hasProperty(UniversalPlasticMeltCauldronBlock.COLOR)
            && PlasticMaterial.fromMeltCauldron(state).isPresent()) {
            return state.getValue(UniversalPlasticMeltCauldronBlock.COLOR);
        }
        return PlasticMeltColor.get(melt);
    }

    private static FluidStack findMelt(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (PlasticMaterial.isMelt(fluid)) return fluid.copy();
        }
        return FluidStack.EMPTY;
    }

    private static FluidStack findMelt(IFluidHandler handler, PlasticMaterial material) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.is(material.melt())) return fluid.copy();
        }
        return FluidStack.EMPTY;
    }

    private static void recolorFirstMelt(IFluidHandler handler, PlasticMaterial material, DyeColor color) {
        FluidStack melt = findMelt(handler, material);
        if (melt.isEmpty()) return;
        FluidStack replacement = melt.copy();
        if (material.supportsDyeing()) PlasticMeltColor.set(replacement, color);
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
        private PlasticMaterial material;
        private boolean hasMelt;
    }
}
