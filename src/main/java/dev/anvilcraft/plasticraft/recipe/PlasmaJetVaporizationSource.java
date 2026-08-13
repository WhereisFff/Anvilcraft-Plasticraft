package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporAction;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporStack;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationContext;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationManager;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationOffer;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationSource;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

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
        if (VaporizationManager.findConsumer(context) == null
            && CondenserTowerProcess.isPhysicalOutletBlocked(context)) {
            return null;
        }
        int inputRate = CondenserTowerProcess.getVaporizationRateBelow(
            context.level(),
            context.cauldronPos()
        );
        if (inputRate <= 0) return null;

        List<RecipeHolder<PlasmaJetBlastingRecipe>> recipes = new ArrayList<>(
            context.level().getRecipeManager().getAllRecipesFor(
                PlasticraftRecipeTypes.PLASMA_JET_BLASTING_TYPE.get()
            )
        );
        recipes.sort(Comparator.comparingInt(
            (RecipeHolder<PlasmaJetBlastingRecipe> holder) -> holder.value().priority()
        ).reversed());
        for (RecipeHolder<PlasmaJetBlastingRecipe> holder : recipes) {
            PlasmaJetBlastingRecipe recipe = holder.value();
            if (!CondenserTowerProcess.isDirectVaporizationRecipe(recipe)) continue;
            HasCauldronSimple definition = recipe.getHasCauldron();
            PlasmaJetBlastingRecipe.GasOutput gas = recipe.gasOutput().orElse(null);
            if (gas == null || gas.amount() <= 0 || definition.consume() <= 0) continue;
            if (!definition.hasFluid() || !definition.fluid().test(availableInput)) continue;
            int divisor = greatestCommonDivisor(definition.consume(), gas.amount());
            int inputUnit = definition.consume() / divisor;
            int outputUnit = gas.amount() / divisor;
            int units = Math.min(
                Math.min(availableInput.getAmount(), inputRate) / inputUnit,
                maxVapor / outputUnit
            );
            if (units <= 0) continue;
            int inputAmount = units * inputUnit;
            int outputAmount = units * outputUnit;
            ResourceLocation outputId = CondenserGas.canonicalize(gas.id());
            if (outputId == null || !CondenserGas.isGas(outputId)) continue;
            return new VaporizationOffer(
                availableInput.copyWithAmount(inputAmount),
                new VaporStack(outputId, outputAmount)
            );
        }
        return null;
    }

    private static int greatestCommonDivisor(int first, int second) {
        while (second != 0) {
            int remainder = first % second;
            first = second;
            second = remainder;
        }
        return first;
    }

    @Override
    public void commit(VaporizationContext context, VaporizationOffer offer) {
        if (!(context.cauldron() instanceof LargeCauldronBlockEntity cauldron)) return;
        ResourceLocation vaporType = CondenserGas.canonicalize(offer.output().type());
        if (CondenserGas.GASEOUS_EXPERIENCE.equals(vaporType)) {
            CondenserTowerProcess.emitLargeCauldronExperienceVaporParticles(
                context.level(),
                CondenserTowerProcess.largeCauldronSurface(cauldron),
                offer.input().getAmount()
            );
        } else {
            CondenserTowerProcess.emitLargeCauldronVaporParticles(
                context.level(),
                CondenserTowerProcess.largeCauldronSurface(cauldron),
                offer.input().getAmount(),
                offer.input()
            );
        }
        int accepted = VaporizationManager.findConsumer(context) == null
            ? 0
            : VaporizationManager.receiveVapor(context, offer.output(), VaporAction.SIMULATE);
        if (accepted < offer.output().amount()) {
            CondenserTowerProcess.releaseEscapingVapor(
                context,
                offer.output().withAmount(offer.output().amount() - accepted),
                offer.input()
            );
        }
    }

}
