package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.InWorldRecipe;
import dev.anvilcraft.lib.v2.recipe.event.InWorldRecipeCommittedEvent;
import dev.anvilcraft.lib.v2.recipe.event.InWorldRecipeEvent;
import dev.anvilcraft.lib.v2.recipe.predicate.IRecipePredicate;
import dev.anvilcraft.lib.v2.recipe.predicate.item.HasItemIngredient;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.entity.MoldedLargeCauldronInteraction;
import dev.anvilcraft.plasticraft.entity.PlasticCauldronWorkBlockFinder;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.api.recipe.CauldronProcessing;
import dev.dubhe.anvilcraft.block.entity.BurningHeaterBlockEntity;
import dev.dubhe.anvilcraft.block.workstation.GiantAnvilBlock;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTriggers;
import dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import dev.dubhe.anvilcraft.recipe.LiquidEnchantmentCauldronRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.DamageAnvil;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.ItemCompressRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/** 本体大锅的九组调度，工作方块位置由塑料锅的实际底面提供。 */
public final class MoldedLargeCauldronRecipes {
    private MoldedLargeCauldronRecipes() {
    }

    public static boolean process(ServerLevel level, FallingBlockEntity anvil, UniversalPlasticEntity pot, Vec3 position) {
        List<BlockPos> helpers = PlasticCauldronWorkBlockFinder.findWorkBlockPositions(pot, position).stream()
            .filter(level::hasChunkAt).filter(pos -> activeHelper(level.getBlockState(pos))).toList();
        List<Pass> passes = helpers.isEmpty() ? List.of(Pass.ALL) : List.of(Pass.NON_COMPRESSION, Pass.COMPRESSION);
        List<RecipeHolder<InWorldRecipe>> recipes = new ArrayList<>(level.recipeAccess()
            .anvillib$getInWorldRecipeManager().recipeHolders.get(ModRecipeTriggers.ON_ANVIL_FALL_ON.get()));
        ResourceHandler<ItemResource> input = pot.getMoldedItemHandler().inputView();
        ResourceHandler<ItemResource> output = pot.getMoldedItemHandler().outputView();
        List<FluidStack> initialFluids = pot.getMoldedFluidHandler().copyFluids();
        List<Integer> initialOutputs = occupied(output);
        Set<Integer> specialSlots = new HashSet<>();
        int limit = AnvilCraft.CONFIG.machines.largeCauldronMaxProcessEfficiency;
        int processed = 0;
        boolean damaged = false;
        for (int slot = 0; slot < input.size() && processed < limit; slot++) {
            if (!enchant(level, pot, slot, helpers)) continue;
            specialSlots.add(slot);
            processed++;
        }
        for (Pass pass : passes) {
            while (processed < limit) {
                boolean progress = false;
                List<Integer> slots = occupied(input);
                slots.sort(Comparator.comparingInt((Integer slot) -> priority(recipes, ItemUtil.getStack(input, slot), pass))
                    .reversed().thenComparingInt(i -> i));
                for (int slot : slots) {
                    if (specialSlots.contains(slot)) continue;
                    Execution result = group(level, anvil, pot, position, helpers, recipes, slot, false, pass);
                    if (!result.executed()) continue;
                    damaged |= result.damaged();
                    processed++;
                    progress = true;
                    // 原料分布不能压过配方优先级，每成功一组都重新排序。
                    break;
                }
                if (!progress) break;
            }
        }
        for (Pass pass : passes) {
            for (int slot : initialOutputs) {
                if (processed >= limit) break;
                Execution result = group(level, anvil, pot, position, helpers, recipes, slot, true, pass);
                if (!result.executed()) continue;
                damaged |= result.damaged();
                processed++;
            }
        }
        pot.selectLargeRecipeInput(-1, false);
        if (sameFluids(initialFluids, pot.getMoldedFluidHandler().copyFluids())) {
            while (processed < limit) {
                if (mix(level, pot)) {
                    processed++;
                    continue;
                }
                boolean progress = false;
                for (BlockPos cell : cells(pot, position, helpers, -1)) {
                    Execution result = one(level, anvil, pot, cell, recipes, ItemStack.EMPTY, Pass.NON_COMPRESSION);
                    if (!result.executed()) continue;
                    damaged |= result.damaged();
                    processed++;
                    progress = true;
                    break;
                }
                if (!progress) break;
            }
        }
        return damaged;
    }

    private static Execution group(
        ServerLevel level, FallingBlockEntity anvil, UniversalPlasticEntity pot, Vec3 position,
        List<BlockPos> helpers, List<RecipeHolder<InWorldRecipe>> recipes, int slot, boolean outputs, Pass pass
    ) {
        ResourceHandler<ItemResource> source = outputs ? pot.getMoldedItemHandler().outputView() : pot.getMoldedItemHandler().inputView();
        if (ItemUtil.getStack(source, slot).isEmpty()) return Execution.EMPTY;
        for (BlockPos cell : cells(pot, position, helpers, outputs ? -1 : slot)) {
            pot.selectLargeRecipeInput(slot, outputs);
            try {
                int budget = ItemUtil.getStack(source, slot).getMaxStackSize();
                int consumed = 0;
                boolean executed = false;
                boolean damaged = false;
                while (consumed < budget) {
                    ItemStack stack = ItemUtil.getStack(pot.getInput(), 0).copy();
                    if (stack.isEmpty()) break;
                    Execution result = one(level, anvil, pot, cell, recipes, stack, pass);
                    if (!result.executed()) break;
                    executed = true;
                    damaged |= result.damaged();
                    int used = stack.getCount() - ItemUtil.getStack(pot.getInput(), 0).getCount();
                    if (used <= 0) break;
                    consumed += used;
                }
                if (executed) return new Execution(true, damaged);
            } finally {
                pot.selectLargeRecipeInput(-1, false);
            }
        }
        return Execution.EMPTY;
    }

    private static Execution one(
        ServerLevel level, FallingBlockEntity anvil, UniversalPlasticEntity pot, BlockPos cell,
        List<RecipeHolder<InWorldRecipe>> recipes, ItemStack stack, Pass pass
    ) {
        CauldronImpactRecipeProcessor.beginTargetedRecipe(pot, cell);
        try {
            InWorldRecipeContext context = new InWorldRecipeContext(level, cell.getCenter().add(0, 0.5, 0), anvil);
            for (RecipeHolder<InWorldRecipe> holder : recipes) {
                InWorldRecipe recipe = holder.value();
                if (pass.rejects(recipe) || (stack.isEmpty() ? !fluidOnly(recipe) : !anchored(recipe, stack))) continue;
                if (!recipe.matches(context, level)) continue;
                recipe.assemble(context);
                NeoForge.EVENT_BUS.post(new InWorldRecipeEvent(recipe.getType(), holder.id().identifier(), recipe, context));
                long batchId = context.getBatchId();
                context.afterCommit(() -> NeoForge.EVENT_BUS.post(new InWorldRecipeCommittedEvent(
                    recipe.getType(), holder.id().identifier(), recipe, context, batchId
                )));
                boolean damaged = context.get(DamageAnvil.DAMAGE_ANVIL);
                boolean suppress = GiantAnvilBlock.SUPPRESS_DROPS.get();
                GiantAnvilBlock.SUPPRESS_DROPS.set(true);
                try {
                    context.accept();
                } finally {
                    GiantAnvilBlock.SUPPRESS_DROPS.set(suppress);
                }
                return new Execution(true, damaged);
            }
            return Execution.EMPTY;
        } finally {
            CauldronImpactRecipeProcessor.finishTargetedRecipe();
        }
    }

    private static List<BlockPos> cells(UniversalPlasticEntity pot, Vec3 position, List<BlockPos> helpers, int slot) {
        if (helpers.isEmpty()) return List.of(CauldronImpactRecipeProcessor.defaultRecipePotCell(pot, position));
        Vec3 input = MoldedLargeCauldronInteraction.inputPosition(pot, slot).add(position.subtract(pot.position()));
        return helpers.stream().sorted(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(input)))
            .map(BlockPos::above).toList();
    }

    private static boolean activeHelper(BlockState state) {
        return CauldronProcessing.isActive(state);
    }

    private static boolean heating(BlockState state) {
        return CauldronProcessing.matches(CauldronProcessing.SUPER_HEATING, state);
    }

    private static Stream<IRecipePredicate<?>> predicates(InWorldRecipe recipe) {
        return Stream.concat(recipe.nonConflicting().stream(), recipe.conflicting().stream());
    }

    private static boolean anchored(InWorldRecipe recipe, ItemStack stack) {
        return predicates(recipe).filter(HasItemIngredient.class::isInstance).map(HasItemIngredient.class::cast)
            .findFirst().map(ingredient -> ingredient.getItem().test(stack)).orElse(false);
    }

    private static boolean fluidOnly(InWorldRecipe recipe) {
        return predicates(recipe).noneMatch(HasItemIngredient.class::isInstance)
            && predicates(recipe).anyMatch(HasCauldron.class::isInstance);
    }

    private static int priority(List<RecipeHolder<InWorldRecipe>> recipes, ItemStack stack, Pass pass) {
        return recipes.stream().map(RecipeHolder::value).filter(recipe -> !pass.rejects(recipe) && anchored(recipe, stack))
            .mapToInt(InWorldRecipe::priority).max().orElse(Integer.MIN_VALUE);
    }

    private static List<Integer> occupied(ResourceHandler<ItemResource> handler) {
        List<Integer> result = new ArrayList<>();
        for (int slot = 0; slot < handler.size(); slot++) if (!ItemUtil.getStack(handler, slot).isEmpty()) result.add(slot);
        return result;
    }

    private static boolean enchant(ServerLevel level, UniversalPlasticEntity pot, int slot, List<BlockPos> helpers) {
        ResourceHandler<ItemResource> input = pot.getMoldedItemHandler().inputView();
        ItemStack starting = ItemUtil.getStack(input, slot);
        if (starting.isEmpty()) return false;
        int consumed = 0;
        while (consumed < starting.getMaxStackSize()) {
            BlockPos heater = helpers.stream().filter(pos -> heating(level.getBlockState(pos)))
                .min(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(MoldedLargeCauldronInteraction.inputPosition(pot, slot))))
                .orElse(null);
            var matched = LiquidEnchantmentCauldronRecipe.match(pot.getMoldedFluidHandler().copyFluids(), ItemUtil.getStack(input, slot), heater != null);
            if (matched.isEmpty()) break;
            LiquidEnchantmentCauldronRecipe.Result result = matched.orElseThrow();
            try (var tx = Transaction.openRoot()) {
                var output = pot.getMoldedItemHandler().outputView();
                ItemStack product = result.itemResult();
                if (input.extract(slot, input.getResource(slot), result.itemCost(), tx) != result.itemCost()
                    || !product.isEmpty() && output.insert(ItemResource.of(product), product.getCount(), tx) != product.getCount()) break;
                tx.commit();
            }
            pot.getMoldedFluidHandler().setFluids(result.fluids());
            if (result.consumesHeat() && heater != null && level.getBlockEntity(heater) instanceof BurningHeaterBlockEntity burning) {
                burning.consumeBurnTime(240 * 20);
            }
            consumed += result.itemCost();
            if (result.itemCost() <= 0) break;
        }
        return consumed > 0;
    }

    private static boolean mix(ServerLevel level, UniversalPlasticEntity pot) {
        List<FluidStack> stored = pot.getMoldedFluidHandler().copyFluids();
        for (RecipeHolder<FluidMixingRecipe> holder : level.recipeAccess().recipeMap().byType(ModRecipeTypes.FLUID_MIXING.get())) {
            FluidMixingRecipe recipe = holder.value();
            int batches = recipe.getMaximumBatches(stored);
            if (batches <= 0) continue;
            List<FluidStack> mixed = null;
            if (recipe.consumesMaximum()) {
                int low = 1;
                int high = batches;
                while (low <= high) {
                    int mid = low + (high - low) / 2;
                    List<FluidStack> candidate = mixFluids(recipe, stored, mid);
                    if (candidate == null) high = mid - 1;
                    else {
                        mixed = candidate;
                        low = mid + 1;
                    }
                }
            } else mixed = mixFluids(recipe, stored, 1);
            if (mixed == null) continue;
            ResourceHandler<ItemResource> output = pot.getMoldedItemHandler().outputView();
            try (var tx = Transaction.openRoot()) {
                boolean fits = true;
                for (ItemStack stack : recipe.getItemResults()) {
                    ItemStack result = color(stack.copy(), stored);
                    if (output.insert(ItemResource.of(result), result.getCount(), tx) != result.getCount()) {
                        fits = false;
                        break;
                    }
                }
                if (!fits) continue;
                tx.commit();
            }
            pot.getMoldedFluidHandler().setFluids(mixed);
            return true;
        }
        return false;
    }

    private static @Nullable List<FluidStack> mixFluids(FluidMixingRecipe recipe, List<FluidStack> stored, int batches) {
        var remaining = recipe.consume(stored, batches);
        var results = recipe.getFluidResults(batches);
        if (remaining.isEmpty() || results.isEmpty()) return null;
        LargeCauldronFluidHandler simulated = new LargeCauldronFluidHandler(() -> {});
        simulated.setFluids(remaining.orElseThrow());
        for (FluidStack result : results.orElseThrow()) {
            try (var tx = Transaction.openRoot()) {
                if (simulated.insert(FluidResource.of(result), result.getAmount(), tx) != result.getAmount()) return null;
                tx.commit();
            }
        }
        return simulated.copyFluids();
    }

    private static ItemStack color(ItemStack output, List<FluidStack> fluids) {
        PlasticMaterial material = PlasticMaterial.fromGranule(output).orElse(null);
        if (material != null && material.supportsDyeing()) {
            fluids.stream().filter(fluid -> fluid.is(material.melt())).findFirst()
                .ifPresent(fluid -> PlasticMeltColor.set(output, PlasticMeltColor.get(fluid)));
        }
        return output;
    }

    private static boolean sameFluids(List<FluidStack> first, List<FluidStack> second) {
        if (first.size() != second.size()) return false;
        for (int index = 0; index < first.size(); index++) if (!FluidStack.matches(first.get(index), second.get(index))) return false;
        return true;
    }

    private enum Pass {
        ALL, NON_COMPRESSION, COMPRESSION;

        private boolean rejects(InWorldRecipe recipe) {
            return this == NON_COMPRESSION && recipe instanceof ItemCompressRecipe
                || this == COMPRESSION && !(recipe instanceof ItemCompressRecipe);
        }
    }

    private record Execution(boolean executed, boolean damaged) {
        private static final Execution EMPTY = new Execution(false, false);
    }
}
