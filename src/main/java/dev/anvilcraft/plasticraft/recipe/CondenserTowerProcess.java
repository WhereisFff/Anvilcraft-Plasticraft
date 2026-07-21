package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.lib.v2.util.predicate.ChanceItemStack;
import dev.anvilcraft.lib.v2.util.predicate.ItemIngredientPredicate;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.api.entity.PlasmaExperienceOrbExtension;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.ModParticles;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.api.fluid.network.FluidContainerLookup;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.block.ModFluidTags;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SuperHeatingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.RoyalPreferenceOutcome;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import dev.dubhe.anvilcraft.recipe.component.HasCauldronSimple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 等离子喷流对容器的气化、持续加工，以及冷凝塔堆叠识别。 */
public final class CondenserTowerProcess {
    public static final int VAPORIZATION_PER_JET = 50;
    public static final int MAX_PRODUCTIVE_TOWERS = 4;
    private static final int EXPERIENCE_PER_OPERATION = 3;
    private static final double LARGE_CAULDRON_MIN_Y = -0.5D + 0.001D;
    private static final double LARGE_CAULDRON_CONTENT_HEIGHT = 2.25D;
    private static final double LARGE_CAULDRON_VAPOR_HALF_WIDTH = 1.15D;
    private static final double SMALL_CONTAINER_VAPOR_HALF_WIDTH = 0.11D;
    private CondenserTowerProcess() {
    }

    /** 每条贴锅喷流每刻处理 50 mB 顶层液体，并把油/水转成虚拟气体。 */
    public static void tickLargeCauldron(ServerLevel level, LargeCauldronBlockEntity cauldron) {
        if (!cauldron.isMainPart()) return;
        int jets = countJetsBelow(level, cauldron.getBlockPos());
        if (jets > 0) {
            processLargeRecipes(level, cauldron, jets);

            FluidStack topFluid = cauldron.getTopFluid();
            if (!topFluid.isEmpty() && (topFluid.is(ModFluidTags.OIL) || topFluid.is(Fluids.WATER))) {
                int amount = Math.min(topFluid.getAmount(), jets * VAPORIZATION_PER_JET);
                if (amount > 0) {
                    ResourceLocation gas = topFluid.is(ModFluidTags.OIL)
                        ? CondenserGas.GASEOUS_OIL
                        : CondenserGas.GASEOUS_WATER;
                    FluidStack request = topFluid.copyWithAmount(amount);
                    FluidStack drained = cauldron.getFluids().drainStoredFluid(
                        request,
                        IFluidHandler.FluidAction.EXECUTE
                    );
                    if (!drained.isEmpty()) {
                        emitLargeCauldronVaporParticles(
                            level,
                            largeCauldronSurface(cauldron),
                            jets,
                            topFluid.is(ModFluidTags.OIL)
                        );
                        collectGas(level, cauldron.getBlockPos(), gas, drained.getAmount());
                    }
                }
            }
        }
        // Cached gas can finish condensing after the spray has disappeared.
        condenseFirstTower(level, cauldron.getBlockPos());
    }

    /** 普通炼药锅、鱼缸和实体锅只气化，不接入冷凝塔。 */
    public static void tickSmallContainerAboveJet(ServerLevel level, BlockPos jetPos) {
        BlockPos targetPos = jetPos.above();
        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.getBlock() instanceof LargeCauldronBlock
            || targetState.getBlock() instanceof CondenserTowerBlock) {
            return;
        }

        FluidContainerLookup.Result endpoint = FluidContainerLookup.find(level, targetPos, null);
        if (endpoint != null && (endpoint.cauldron() || endpoint.entity() instanceof HardenedResinCauldronEntity)) {
            FluidStack stored = findVaporizableFluid(endpoint.handler());
            if (!stored.isEmpty()) {
                FluidStack request = stored.copyWithAmount(Math.min(VAPORIZATION_PER_JET, stored.getAmount()));
                FluidStack drained = endpoint.handler().drain(request, IFluidHandler.FluidAction.EXECUTE);
                if (!drained.isEmpty()) {
                    emitSmallContainerVaporParticles(
                        level,
                        smallContainerSurface(level, targetPos, endpoint),
                        1,
                        true
                    );
                    return;
                }
            }
        }

        if (!(targetState.getBlock() instanceof IIgnitableCauldron cauldron)) return;
        BlockCache cache = new BlockCache(level);
        if (!cauldron.getFluid(cache, targetPos).builtInRegistryHolder().is(ModFluidTags.OIL)) return;
        if (cauldron.consumeOnce(cache, targetPos)) {
            cache.accept();
            emitSmallContainerVaporParticles(
                level,
                ordinaryCauldronSurface(targetPos, targetState),
                1,
                true
            );
        }
    }

    public static boolean isPlasmaPassThrough(BlockState state) {
        return state.getBlock() instanceof LargeCauldronBlock
            || state.getBlock() instanceof CondenserTowerBlock;
    }

    public static List<CondenserTowerBlockEntity> findProductiveTowers(Level level, BlockPos cauldronMain) {
        List<CondenserTowerBlockEntity> towers = new ArrayList<>(MAX_PRODUCTIVE_TOWERS);
        BlockPos towerMain = cauldronMain.above(3);
        for (int layer = 0; layer < MAX_PRODUCTIVE_TOWERS; layer++, towerMain = towerMain.above(3)) {
            BlockState state = level.getBlockState(towerMain);
            boolean firstTowerAligned = layer != 0
                || state.hasProperty(CondenserTowerBlock.SEALED) && state.getValue(CondenserTowerBlock.SEALED)
                || CondenserTowerBlock.isAlignedWithLargeCauldron(level, towerMain.below());
            if (!(state.getBlock() instanceof CondenserTowerBlock)
                || !state.hasProperty(CondenserTowerBlock.HALF)
                || state.getValue(CondenserTowerBlock.HALF) != Cube3x3PartHalf.MID_CENTER
                || !firstTowerAligned) {
                break;
            }
            if (!(level.getBlockEntity(towerMain) instanceof CondenserTowerBlockEntity tower)
                || !tower.isMainPart()) break;
            towers.add(tower);
        }
        return List.copyOf(towers);
    }

    private static int countJetsBelow(Level level, BlockPos cauldronMain) {
        int jets = 0;
        BlockPos center = cauldronMain.below(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (level.getBlockState(center.offset(x, 0, z)).is(ModBlocks.PLASMA_JETS)) jets++;
            }
        }
        return jets;
    }

    private static void collectGas(ServerLevel level, BlockPos cauldronMain, ResourceLocation gas, int amount) {
        boolean condensable = level.getRecipeManager()
            .getAllRecipesFor(ModRecipeTypes.CONDENSER_TYPE.get())
            .stream()
            .anyMatch(holder -> holder.value().gas().equals(gas));
        if (!condensable) return;
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(level, cauldronMain);
        if (towers.isEmpty()) return;
        // 第一层负责当前验证阶段的气体收集，后续层保留给不同产物。
        towers.getFirst().collectGas(gas, amount);
    }

    private static void condenseFirstTower(ServerLevel level, BlockPos cauldronMain) {
        List<CondenserTowerBlockEntity> towers = findProductiveTowers(level, cauldronMain);
        if (towers.isEmpty()) return;
        CondenserTowerBlockEntity tower = towers.getFirst();
        ResourceLocation gasId = tower.getGasId();
        if (gasId == null || tower.getGasAmount() <= 0) return;
        for (RecipeHolder<CondenserRecipe> holder
            : level.getRecipeManager().getAllRecipesFor(ModRecipeTypes.CONDENSER_TYPE.get())) {
            CondenserRecipe recipe = holder.value();
            if (!recipe.matches(gasId, tower.getGasAmount())) continue;
            if (recipe.consume() <= 0 || recipe.produce() <= 0) continue;
            Fluid outputFluid = BuiltInRegistries.FLUID.get(recipe.fluid());
            if (outputFluid == null || outputFluid == Fluids.EMPTY) continue;
            int batches = tower.getGasAmount() / recipe.consume();
            if (batches <= 0) continue;
            int requested = (int) Math.min(Integer.MAX_VALUE, (long) batches * recipe.produce());
            int accepted = tower.getFluidHandler().fill(
                new FluidStack(outputFluid, requested),
                IFluidHandler.FluidAction.SIMULATE
            );
            batches = Math.min(batches, accepted / recipe.produce());
            if (batches <= 0) continue;
            int consumed = batches * recipe.consume();
            int produced = batches * recipe.produce();
            if (tower.drainGas(gasId, consumed) != consumed) continue;
            tower.getFluidHandler().fill(
                new FluidStack(outputFluid, produced),
                IFluidHandler.FluidAction.EXECUTE
            );
            return;
        }
    }

    private static void processLargeRecipes(ServerLevel level, LargeCauldronBlockEntity cauldron, int jets) {
        IItemHandler input = cauldron.getInputHandler();
        IItemHandler output = cauldron.getOutputHandler();
        List<RecipeHolder<PlasmaJetBlastingRecipe>> plasmaRecipes = new ArrayList<>(
            level.getRecipeManager().getAllRecipesFor(
                ModRecipeTypes.PLASMA_JET_BLASTING_TYPE.get()
            )
        );
        plasmaRecipes.sort(Comparator.comparingInt(
            (RecipeHolder<PlasmaJetBlastingRecipe> holder) -> holder.value().priority()
        ).reversed());
        List<RecipeHolder<SuperHeatingRecipe>> superHeatingRecipes = new ArrayList<>(
            level.getRecipeManager().getAllRecipesFor(
                dev.dubhe.anvilcraft.init.recipe.ModRecipeTypes.SUPER_HEATING_TYPE.get()
            )
        );
        superHeatingRecipes.sort(Comparator.comparingInt(
            (RecipeHolder<SuperHeatingRecipe> holder) -> holder.value().priority()
        ).reversed());
        // 每条喷流每 tick 至多推进一次配方，避免单条喷流瞬间清空整个输入栏。
        for (int operation = 0; operation < jets; operation++) {
            if (processPlasmaRecipe(level, cauldron, input, output, plasmaRecipes)) continue;
            if (processSuperHeatingItemRecipe(level, cauldron, input, output, superHeatingRecipes)) continue;
            if (processVanillaItemRecipe(level, input, output)) continue;
            break;
        }
    }

    private static boolean processPlasmaRecipe(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        List<RecipeHolder<PlasmaJetBlastingRecipe>> recipes
    ) {
        for (RecipeHolder<PlasmaJetBlastingRecipe> holder : recipes) {
            PlasmaJetBlastingRecipe recipe = holder.value();
            if (isDirectVaporizationRecipe(recipe)) continue;
            if (applyProcess(level, cauldron, input, output,
                new ItemProcessDefinition(
                    recipe.getInputItems(),
                    recipe.getResultItems(),
                    recipe.getHasCauldron(),
                    false
                ))) {
                return true;
            }
        }
        return false;
    }

    private static boolean processSuperHeatingItemRecipe(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        List<RecipeHolder<SuperHeatingRecipe>> recipes
    ) {
        for (RecipeHolder<SuperHeatingRecipe> holder : recipes) {
            SuperHeatingRecipe recipe = holder.value();
            if (recipe.getInputItems().isEmpty()) continue;
            if (applyProcess(level, cauldron, input, output,
                new ItemProcessDefinition(
                    recipe.getInputItems(),
                    recipe.getResultItems(),
                    recipe.getHasCauldron(),
                    recipe.isHasRoyalPreference() && hasRoyalPreferredInput(level, input)
                ))) {
                return true;
            }
        }
        return false;
    }

    private static boolean processVanillaItemRecipe(
        ServerLevel level,
        IItemHandler input,
        IItemHandler output
    ) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            ItemStack result = findVanillaBlastingResult(level, stack);
            if (result.isEmpty()) result = findVanillaSmeltingResult(level, stack);
            if (result.isEmpty()) continue;
            if (!ItemHandlerUtil.insertItem(output, result.copy(), true).isEmpty()) continue;
            input.extractItem(slot, 1, false);
            ItemHandlerUtil.insertItem(output, result.copy(), false);
            return true;
        }
        return false;
    }

    private static boolean applyProcess(
        ServerLevel level,
        LargeCauldronBlockEntity cauldron,
        IItemHandler input,
        IItemHandler output,
        ItemProcessDefinition definition
    ) {
        List<MatchedItem> matched = matchItems(input, definition.items());
        if (matched == null) return false;

        FluidStack fluidInput = resolveFluidInput(cauldron, definition.cauldron());
        if (definition.hasFluidInput() && fluidInput == null) return false;

        List<ItemStack> results = new ArrayList<>();
        for (ChanceItemStack chance : definition.results()) {
            ItemStack result = chance.getResult(level);
            if (result.isEmpty()) continue;
            results.add(result);
            if (definition.royalPreferenceBonus() && isRoyalSteel(result)) results.add(result.copy());
        }
        ResourceLocation outputId = definition.cauldron().transform();
        int fluidAmount = definition.cauldron().produce();
        Fluid outputFluid = null;
        if (definition.hasFluidOutput() && !CondenserGas.isGas(outputId)
            && !CondenserGas.EXPERIENCE_ORBS.equals(outputId)) {
            outputFluid = BuiltInRegistries.FLUID.get(outputId);
            if (outputFluid == null || outputFluid == Fluids.EMPTY) return false;
            if (!canApplyFluidTransform(
                cauldron,
                fluidInput,
                definition.cauldron().consume(),
                outputFluid,
                fluidAmount
            )) return false;
        }
        if (definition.results().isEmpty() && !definition.hasFluidOutput()) return false;
        if (!canInsertAll(output, results)) return false;

        for (MatchedItem item : matched) input.extractItem(item.slot(), item.amount(), false);
        if (fluidInput != null && definition.cauldron().consume() > 0) {
            FluidStack drained = cauldron.getFluids().drainStoredFluid(
                fluidInput.copyWithAmount(definition.cauldron().consume()),
                IFluidHandler.FluidAction.EXECUTE
            );
            if (drained.getAmount() != definition.cauldron().consume()) return false;
        }
        for (ItemStack result : results) ItemHandlerUtil.insertItem(output, result.copy(), false);
        if (definition.hasFluidOutput()) {
            if (CondenserGas.EXPERIENCE_ORBS.equals(outputId)) {
                spawnExperienceOrbs(
                    level,
                    largeCauldronSurface(cauldron).add(0.0, 0.05, 0.0),
                    Math.max(1, fluidAmount)
                );
            } else if (CondenserGas.isGas(outputId)) {
                emitLargeCauldronVaporParticles(
                    level,
                    largeCauldronSurface(cauldron),
                    1,
                    CondenserGas.GASEOUS_OIL.equals(outputId)
                );
                collectGas(level, cauldron.getBlockPos(), outputId, fluidAmount);
            } else if (outputFluid != null) {
                cauldron.getFluids().fill(
                    new FluidStack(outputFluid, fluidAmount),
                    IFluidHandler.FluidAction.EXECUTE
                );
            }
        }
        return true;
    }

    private static boolean isDirectVaporizationRecipe(PlasmaJetBlastingRecipe recipe) {
        if (!recipe.getInputItems().isEmpty() || !recipe.getResultItems().isEmpty()) return false;
        HasCauldronSimple cauldron = recipe.getHasCauldron();
        if (cauldron.fluidTag() != null) return false;
        return CondenserGas.GASEOUS_OIL.equals(cauldron.transform())
                && ModFluids.OIL.getId().equals(cauldron.fluid())
            || CondenserGas.GASEOUS_WATER.equals(cauldron.transform())
                && BuiltInRegistries.FLUID.getKey(Fluids.WATER).equals(cauldron.fluid());
    }

    private static boolean canApplyFluidTransform(
        LargeCauldronBlockEntity cauldron,
        @org.jetbrains.annotations.Nullable FluidStack input,
        int consume,
        Fluid output,
        int produce
    ) {
        LargeCauldronFluidHandler simulated = new LargeCauldronFluidHandler(() -> {
        });
        simulated.setFluids(cauldron.getFluids().copyFluids());
        if (input != null && consume > 0) {
            FluidStack drained = simulated.drainStoredFluid(
                input.copyWithAmount(consume),
                IFluidHandler.FluidAction.EXECUTE
            );
            if (drained.getAmount() != consume) return false;
        }
        return simulated.fill(new FluidStack(output, produce), IFluidHandler.FluidAction.EXECUTE) == produce;
    }

    private static boolean canInsertAll(IItemHandler output, List<ItemStack> results) {
        List<ItemStack> slots = new ArrayList<>(output.getSlots());
        for (int slot = 0; slot < output.getSlots(); slot++) slots.add(output.getStackInSlot(slot).copy());
        for (ItemStack result : results) {
            if (result.isEmpty()) continue;
            int remaining = result.getCount();
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                ItemStack stored = slots.get(slot);
                if (stored.isEmpty()
                    || !output.isItemValid(slot, result)
                    || !ItemStack.isSameItemSameComponents(stored, result)) continue;
                int limit = Math.min(output.getSlotLimit(slot), stored.getMaxStackSize());
                int inserted = Math.min(remaining, Math.max(0, limit - stored.getCount()));
                if (inserted <= 0) continue;
                slots.set(slot, stored.copyWithCount(stored.getCount() + inserted));
                remaining -= inserted;
            }
            for (int slot = 0; slot < output.getSlots() && remaining > 0; slot++) {
                if (!slots.get(slot).isEmpty() || !output.isItemValid(slot, result)) continue;
                int inserted = Math.min(
                    remaining,
                    Math.min(output.getSlotLimit(slot), result.getMaxStackSize())
                );
                if (inserted <= 0) continue;
                slots.set(slot, result.copyWithCount(inserted));
                remaining -= inserted;
            }
            if (remaining > 0) return false;
        }
        return true;
    }

    private static boolean hasRoyalPreferredInput(ServerLevel level, IItemHandler input) {
        for (int slot = 0; slot < input.getSlots(); slot++) {
            ItemStack stack = input.getStackInSlot(slot);
            if (!stack.isEmpty() && RoyalPreferenceOutcome.RoyalPreference.isRoyalPreferred(level, stack)) return true;
        }
        return false;
    }

    private static boolean isRoyalSteel(ItemStack stack) {
        return stack.is(dev.dubhe.anvilcraft.init.item.ModItems.ROYAL_STEEL_INGOT.get())
            || stack.is(ModBlocks.ROYAL_STEEL_BLOCK.get().asItem());
    }

    private static @org.jetbrains.annotations.Nullable List<MatchedItem> matchItems(
        IItemHandler input,
        List<ItemIngredientPredicate> predicates
    ) {
        if (predicates.isEmpty()) return List.of();
        int[] available = new int[input.getSlots()];
        for (int slot = 0; slot < input.getSlots(); slot++) available[slot] = input.getStackInSlot(slot).getCount();
        List<Integer> order = new ArrayList<>(predicates.size());
        for (int index = 0; index < predicates.size(); index++) order.add(index);
        // Assign the most constrained predicates first; the backtracking fallback handles overlapping tags.
        order.sort(Comparator.comparingInt(index -> countCandidateSlots(input, predicates.get(index), available)));
        List<MatchedItem> matched = new ArrayList<>(predicates.size());
        return matchItems(input, predicates, order, 0, available, matched) ? List.copyOf(matched) : null;
    }

    private static int countCandidateSlots(
        IItemHandler input,
        ItemIngredientPredicate predicate,
        int[] available
    ) {
        int count = 0;
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (available[slot] >= predicate.count()
                && predicate.test(input.getStackInSlot(slot).copyWithCount(available[slot]))) count++;
        }
        return count;
    }

    private static boolean matchItems(
        IItemHandler input,
        List<ItemIngredientPredicate> predicates,
        List<Integer> order,
        int index,
        int[] available,
        List<MatchedItem> matched
    ) {
        if (index >= order.size()) return true;
        ItemIngredientPredicate predicate = predicates.get(order.get(index));
        for (int slot = 0; slot < input.getSlots(); slot++) {
            if (available[slot] < predicate.count()
                || !predicate.test(input.getStackInSlot(slot).copyWithCount(available[slot]))) continue;
            available[slot] -= predicate.count();
            matched.add(new MatchedItem(slot, predicate.count()));
            if (matchItems(input, predicates, order, index + 1, available, matched)) return true;
            matched.removeLast();
            available[slot] += predicate.count();
        }
        return false;
    }

    private static @org.jetbrains.annotations.Nullable FluidStack resolveFluidInput(
        LargeCauldronBlockEntity cauldron,
        HasCauldronSimple definition
    ) {
        if (!HasCauldron.isNotEmpty(definition.fluid()) && definition.fluidTag() == null) return null;
        FluidStack top = cauldron.getTopFluid();
        if (top.isEmpty() || definition.consume() <= 0 || top.getAmount() < definition.consume()) return null;
        if (definition.fluidTag() != null) {
            net.minecraft.tags.TagKey<Fluid> tag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.FLUID,
                definition.fluidTag()
            );
            return top.is(tag) ? top : null;
        }
        Fluid expected = BuiltInRegistries.FLUID.get(definition.fluid());
        return expected != null && expected != Fluids.EMPTY && top.getFluid() == expected ? top : null;
    }

    private record MatchedItem(int slot, int amount) {
    }

    private record ItemProcessDefinition(
        List<ItemIngredientPredicate> items,
        List<ChanceItemStack> results,
        HasCauldronSimple cauldron,
        boolean royalPreferenceBonus
    ) {
        private boolean hasFluidInput() {
            return (HasCauldron.isNotEmpty(this.cauldron.fluid()) || this.cauldron.fluidTag() != null)
                && this.cauldron.consume() > 0;
        }

        private boolean hasFluidOutput() {
            return HasCauldron.isNotEmpty(this.cauldron.transform()) && this.cauldron.produce() > 0;
        }
    }

    private static ItemStack findVanillaBlastingResult(ServerLevel level, ItemStack input) {
        Optional<RecipeHolder<BlastingRecipe>> holder = level.getRecipeManager().getRecipeFor(
            RecipeType.BLASTING,
            new SingleRecipeInput(input.copyWithCount(1)),
            level
        );
        return holder.map(value -> value.value().assemble(
            new SingleRecipeInput(input.copyWithCount(1)),
            level.registryAccess()
        )).orElse(ItemStack.EMPTY);
    }

    private static ItemStack findVanillaSmeltingResult(ServerLevel level, ItemStack input) {
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING,
                new SingleRecipeInput(input.copyWithCount(1)),
                level
            )
            .map(value -> value.value().assemble(
                new SingleRecipeInput(input.copyWithCount(1)),
                level.registryAccess()
            ))
            .orElse(ItemStack.EMPTY);
    }

    private static FluidStack findVaporizableFluid(IFluidHandler handler) {
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack stored = handler.getFluidInTank(tank);
            if (stored.is(ModFluidTags.OIL)) return stored.copy();
        }
        return FluidStack.EMPTY;
    }

    private static Vec3 largeCauldronSurface(LargeCauldronBlockEntity cauldron) {
        float fill = Math.clamp(
            (float) cauldron.getFluids().getTotalAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY,
            0.0F,
            1.0F
        );
        // The renderer's pose is rooted at the main block position, not its center.
        return cauldron.getBlockPos().getCenter().add(
            0.0,
            LARGE_CAULDRON_MIN_Y - 0.5D + LARGE_CAULDRON_CONTENT_HEIGHT * fill,
            0.0
        );
    }

    private static Vec3 smallContainerSurface(
        ServerLevel level,
        BlockPos pos,
        FluidContainerLookup.Result endpoint
    ) {
        if (endpoint.entity() instanceof HardenedResinCauldronEntity pot) {
            Vec3 center = pot.getBoundingBox().getCenter();
            return new Vec3(center.x, pot.getFluidSurfaceY(), center.z);
        }
        if (level.getBlockEntity(pos) instanceof FishTankBlockEntity tank) {
            float fill = Math.clamp(
                (float) tank.getFluidHandler().getFluidAmount() / tank.getFluidHandler().getCapacity(),
                0.0F,
                1.0F
            );
            return pos.getBottomCenter().add(0.0, 1.0 / 16.0 + 0.001 + (1.0 - 2.0 / 16.0 - 0.002) * fill, 0.0);
        }
        return ordinaryCauldronSurface(pos, level.getBlockState(pos));
    }

    private static Vec3 ordinaryCauldronSurface(BlockPos pos, BlockState state) {
        double height = 0.75D;
        if (state.getBlock() instanceof Layered4LevelCauldronBlock
            && state.hasProperty(Layered4LevelCauldronBlock.LEVEL)) {
            height = (6.0D + state.getValue(Layered4LevelCauldronBlock.LEVEL) * 2.0D) / 16.0D;
        }
        return pos.getCenter().add(0.0, height - 0.5, 0.0);
    }

    private static void emitLargeCauldronVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int jets,
        boolean oil
    ) {
        RandomSource random = level.getRandom();
        int gridSize = Math.min(5, 3 + (Math.max(1, jets) - 1) / 3);
        ParticleOptions particle = oil ? ModParticles.OIL_VAPOR.get() : ParticleTypes.CLOUD;
        // 每格各生成一个带抖动的粒子，既覆盖完整液面，也避免随机采样集中在局部。
        for (int cellX = 0; cellX < gridSize; cellX++) {
            for (int cellZ = 0; cellZ < gridSize; cellZ++) {
                double x = surface.x + cellCoordinate(cellX, gridSize, random)
                    * LARGE_CAULDRON_VAPOR_HALF_WIDTH;
                double z = surface.z + cellCoordinate(cellZ, gridSize, random)
                    * LARGE_CAULDRON_VAPOR_HALF_WIDTH;
                sendVaporParticle(level, particle, random, x, surface.y, z);
            }
        }
    }

    private static void emitSmallContainerVaporParticles(
        ServerLevel level,
        Vec3 surface,
        int jets,
        boolean oil
    ) {
        RandomSource random = level.getRandom();
        int count = Math.min(8, Math.max(2, jets * 2));
        ParticleOptions particle = oil ? ModParticles.OIL_VAPOR.get() : ParticleTypes.CLOUD;
        for (int i = 0; i < count; i++) {
            double x = surface.x + (random.nextDouble() * 2.0D - 1.0D) * SMALL_CONTAINER_VAPOR_HALF_WIDTH;
            double z = surface.z + (random.nextDouble() * 2.0D - 1.0D) * SMALL_CONTAINER_VAPOR_HALF_WIDTH;
            sendVaporParticle(level, particle, random, x, surface.y, z);
        }
    }

    private static double cellCoordinate(int cell, int gridSize, RandomSource random) {
        return ((cell + random.nextDouble()) / gridSize) * 2.0D - 1.0D;
    }

    private static void sendVaporParticle(
        ServerLevel level,
        ParticleOptions particle,
        RandomSource random,
        double x,
        double surfaceY,
        double z
    ) {
        // count=0 允许把后三个参数作为粒子初速度，令雾气缓慢向上喷出。
        level.sendParticles(
            particle,
            x,
            surfaceY + 0.015D + random.nextDouble() * 0.025D,
            z,
            0,
            (random.nextDouble() - 0.5D) * 0.018D,
            0.028D + random.nextDouble() * 0.028D,
            (random.nextDouble() - 0.5D) * 0.018D,
            1.0D
        );
    }

    private static void spawnExperienceOrbs(ServerLevel level, Vec3 position, int count) {
        for (int i = 0; i < count; i++) {
            ExperienceOrb orb = new ExperienceOrb(
                level,
                position.x + (level.random.nextDouble() - 0.5D) * 0.35D,
                position.y,
                position.z + (level.random.nextDouble() - 0.5D) * 0.35D,
                EXPERIENCE_PER_OPERATION
            );
            orb.setDeltaMovement(
                (level.random.nextDouble() - 0.5D) * 0.025D,
                0.12D,
                (level.random.nextDouble() - 0.5D) * 0.025D
            );
            if (orb instanceof PlasmaExperienceOrbExtension extension) {
                extension.plasticraft$setPlasmaProduced(true);
            }
            level.addFreshEntity(orb);
        }
    }
}
