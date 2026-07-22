package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationContext;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationOffer;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationSource;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporStack;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.YukkuriVaporTypes;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Adapts Plasticraft's plasma-jet recipes to Yukkuri's generic source protocol. */
public final class PlasmaJetVaporizationSource implements VaporizationSource {
    public static final PlasmaJetVaporizationSource INSTANCE = new PlasmaJetVaporizationSource();
    public static final ResourceLocation ID = AnvilcraftPlasticraft.of("plasma_jets");

    private PlasmaJetVaporizationSource() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public VaporizationOffer createOffer(
        VaporizationContext context,
        FluidStack availableInput,
        int maxVapor
    ) {
        if (maxVapor <= 0) return null;
        int jets = CondenserTowerProcess.countJetsBelow(context.level(), context.cauldronPos());
        if (jets <= 0) return null;
        int inputRate = Math.multiplyExact(jets, CondenserTowerProcess.VAPORIZATION_PER_JET);

        List<RecipeHolder<PlasmaJetBlastingRecipe>> recipes = new ArrayList<>(
            context.level().getRecipeManager().getAllRecipesFor(
                dev.anvilcraft.plasticraft.init.ModRecipeTypes.PLASMA_JET_BLASTING_TYPE.get()
            )
        );
        recipes.sort(Comparator.comparingInt(
            (RecipeHolder<PlasmaJetBlastingRecipe> holder) -> holder.value().priority()
        ).reversed());
        for (RecipeHolder<PlasmaJetBlastingRecipe> holder : recipes) {
            PlasmaJetBlastingRecipe recipe = holder.value();
            if (!CondenserTowerProcess.isDirectVaporizationRecipe(recipe)) continue;
            HasCauldronSimple definition = recipe.getHasCauldron();
            if (!matchesInput(availableInput, definition) || definition.consume() <= 0 || definition.produce() <= 0) {
                continue;
            }
            int batches = Math.min(
                Math.min(availableInput.getAmount(), inputRate) / definition.consume(),
                maxVapor / definition.produce()
            );
            if (batches <= 0) continue;
            int inputAmount = batches * definition.consume();
            int outputAmount = batches * definition.produce();
            ResourceLocation outputId = CondenserGas.canonicalize(definition.transform());
            if (outputId == null || !YukkuriVaporTypes.isStandard(outputId)) continue;
            return new VaporizationOffer(
                availableInput.copyWithAmount(inputAmount),
                new VaporStack(outputId, outputAmount)
            );
        }
        return null;
    }

    @Override
    public void commit(VaporizationContext context, VaporizationOffer offer) {
        if (!(context.cauldron() instanceof LargeCauldronBlockEntity cauldron)) return;
        CondenserTowerProcess.emitLargeCauldronVaporParticles(
            context.level(),
            CondenserTowerProcess.largeCauldronSurface(cauldron),
            CondenserTowerProcess.countJetsBelow(context.level(), context.cauldronPos()),
            offer.input()
        );
    }

    private static boolean matchesInput(FluidStack available, HasCauldronSimple definition) {
        if (available.isEmpty()) return false;
        if (definition.fluidTag() != null) {
            TagKey<Fluid> tag = TagKey.create(Registries.FLUID, definition.fluidTag());
            return available.is(tag);
        }
        if (!HasCauldron.isNotEmpty(definition.fluid())) return false;
        Fluid expected = BuiltInRegistries.FLUID.get(definition.fluid());
        return expected != null && expected != Fluids.EMPTY && available.is(expected);
    }
}
