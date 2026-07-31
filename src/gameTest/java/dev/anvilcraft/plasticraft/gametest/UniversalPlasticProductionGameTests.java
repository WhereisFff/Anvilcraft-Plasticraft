package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.event.PlasticVillagerTrades;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.recipe.CatalyticPressProcess;
import dev.anvilcraft.plasticraft.recipe.PlasticGranuleCauldronOutput;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.entity.ModVillagers;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import dev.dubhe.anvilcraft.recipe.anvil.wrap.SolidLiquidRecipe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON;
import static dev.dubhe.anvilcraft.init.block.ModFluids.POWDER_SNOW;
import static dev.dubhe.anvilcraft.init.item.ModItems.ANVIL_HAMMER;

/** TODO-00 中保留塑料粒的生产路径及珠宝商交易回归测试。 */
public final class UniversalPlasticProductionGameTests {
    private static final int BUCKET = 1_000;
    private static final int GRANULES = 16;
    private static final int JEWELER_GRANULE_COST = 8;
    private static final int JEWELER_EMERALD_PAYMENT = 2;

    private UniversalPlasticProductionGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "A catalytic outlet consumes one full water cauldron and one bucket of coloured melt")
    static void catalyticOutletProducesColoredGranules(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 3);
        BlockPos outputPos = cauldronPos.east();
        HardenedResinCauldronEntity cauldron = spawnCauldron(helper, cauldronPos);
        CatalyticPressLidEntity lid = spawnReadyLid(helper, cauldronPos.above());
        FluidStack melt = coloredMelt(BUCKET, DyeColor.PURPLE);
        check(
            cauldron.getFluidHandler().fill(melt, IFluidHandler.FluidAction.EXECUTE) == BUCKET,
            "source cauldron rejected the plastic melt"
        );

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setPos(cauldron.position().add(0.0D, 0.5D, -2.0D));
        player.setItemInHand(InteractionHand.MAIN_HAND, ANVIL_HAMMER.asStack());
        check(
            cauldron.plasticraft$useAnvilHammer(player, InteractionHand.MAIN_HAND, Direction.EAST).consumesAction(),
            "anvil hammer did not open the catalytic outlet"
        );
        check(cauldron.getOutletDirection() == Direction.EAST, "catalytic outlet faced the wrong direction");
        helper.setBlock(outputPos, fullWaterCauldron());

        CatalyticPressProcess.press(lid);

        check(cauldron.getFluidHandler().getFluid().isEmpty(), "catalytic outlet did not consume exactly one bucket");
        check(helper.getBlockState(outputPos).is(Blocks.CAULDRON), "catalytic outlet did not consume the full water");
        ItemStack output = singleGranuleDrop(helper, outputPos);
        check(output.getCount() == GRANULES, "catalytic outlet produced " + output.getCount() + " granules");
        check(PlasticMeltColor.get(output) == DyeColor.PURPLE, "catalytic outlet lost the melt colour");
        check(!helper.getBlockState(outputPos).is(ModBlocks.UNIVERSAL_PLASTIC.get()), "outlet created a plastic block");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "The water-cauldron transaction consumes exactly one bucket and preserves components")
    static void directCauldronTransactionConsumesExactAmount(ExtendedGameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 2, 3);
        helper.setBlock(outputPos, fullWaterCauldron());
        FluidStack melt = coloredMelt(1_500, DyeColor.CYAN);
        CompoundTag marker = melt.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        marker.putBoolean("Preserved", true);
        melt.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        FluidTank source = new FluidTank(2_000);
        check(source.fill(melt, IFluidHandler.FluidAction.EXECUTE) == 1_500, "test tank rejected melt");

        check(
            PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(outputPos),
                source,
                source.getFluid().copy()
            ),
            "valid full-water transaction was rejected"
        );

        check(source.getFluidAmount() == 500, "transaction consumed a non-bucket amount of melt");
        check(PlasticMeltColor.get(source.getFluid()) == DyeColor.CYAN, "remaining melt lost its colour");
        check(
            source.getFluid().getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("Preserved"),
            "remaining melt lost an unrelated component"
        );
        ItemStack output = singleGranuleDrop(helper, outputPos);
        check(output.getCount() == GRANULES, "direct transaction produced the wrong granule count");
        check(PlasticMeltColor.get(output) == DyeColor.CYAN, "direct transaction lost the melt colour");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("9x5x7")
    @TestHolder(description = "Insufficient melt and partial water leave both sides unchanged")
    static void cauldronPreflightFailuresPreserveResources(ExtendedGameTestHelper helper) {
        BlockPos insufficientPos = new BlockPos(2, 2, 3);
        BlockPos partialWaterPos = new BlockPos(6, 2, 3);
        helper.setBlock(insufficientPos, fullWaterCauldron());
        helper.setBlock(
            partialWaterPos,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 2)
        );
        FluidTank insufficient = filledTank(coloredMelt(999, DyeColor.RED));
        FluidTank partialWater = filledTank(coloredMelt(BUCKET, DyeColor.BLUE));

        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(insufficientPos),
                insufficient,
                insufficient.getFluid().copy()
            ),
            "insufficient melt unexpectedly completed the transaction"
        );
        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(partialWaterPos),
                partialWater,
                partialWater.getFluid().copy()
            ),
            "partial water unexpectedly completed the transaction"
        );

        check(insufficient.getFluidAmount() == 999, "insufficient transaction consumed melt");
        check(partialWater.getFluidAmount() == BUCKET, "partial-water transaction consumed melt");
        check(helper.getBlockState(insufficientPos).equals(fullWaterCauldron()), "failed transaction consumed water");
        check(
            helper.getBlockState(partialWaterPos).getValue(LayeredCauldronBlock.LEVEL) == 2,
            "failed transaction changed the partial water level"
        );
        check(granuleDrops(helper, new AABB(
            helper.absoluteVec(Vec3.ZERO),
            helper.absoluteVec(new Vec3(9.0D, 5.0D, 7.0D))
        )).isEmpty(), "preflight failure created granules");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "A simulation-only fluid handler failure rolls back water, items, and partial drainage")
    static void cauldronExecutionFailureRollsBack(ExtendedGameTestHelper helper) {
        BlockPos outputPos = new BlockPos(3, 2, 3);
        helper.setBlock(outputPos, fullWaterCauldron());
        PartialExecutionTank source = new PartialExecutionTank(BUCKET);
        FluidStack melt = coloredMelt(BUCKET, DyeColor.LIME);
        check(source.fill(melt, IFluidHandler.FluidAction.EXECUTE) == BUCKET, "test tank rejected melt");

        check(
            !PlasticGranuleCauldronOutput.tryProcess(
                helper.getLevel(),
                helper.absolutePos(outputPos),
                source,
                source.getFluid().copy()
            ),
            "partial execution unexpectedly committed"
        );

        check(source.getFluidAmount() == BUCKET, "rollback did not restore partially drained melt");
        check(PlasticMeltColor.get(source.getFluid()) == DyeColor.LIME, "rollback changed the melt colour");
        check(helper.getBlockState(outputPos).equals(fullWaterCauldron()), "rollback did not restore full water");
        check(
            granuleDrops(helper, new AABB(helper.absolutePos(outputPos)).inflate(1.0D)).isEmpty(),
            "rollback left a granule item entity"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("15x9x7")
    @TestHolder(description = "Both retained fluid-mixing recipes produce sixteen same-colour granules")
    static void retainedFluidMixingRecipesPreserveColor(ExtendedGameTestHelper helper) {
        FluidMixingRecipe waterRecipe = fluidMixingRecipe(helper, "universal_plastic_melt_with_water");
        FluidMixingRecipe snowRecipe = fluidMixingRecipe(helper, "universal_plastic_melt_with_powder_snow");
        assertFluidMixingDefinition(waterRecipe, Fluids.WATER, "water", helper.getLevel());
        assertFluidMixingDefinition(snowRecipe, POWDER_SNOW.get(), "powder snow", helper.getLevel());

        LargeCauldronBlockEntity waterCauldron = placeLargeCauldron(helper, new BlockPos(3, 1, 3));
        waterCauldron.getFluids().setFluids(List.of(
            coloredMelt(BUCKET, DyeColor.ORANGE),
            new FluidStack(Fluids.WATER, BUCKET)
        ));
        processLargeCauldron(waterCauldron);
        assertInventoryGranules(waterCauldron.getOutputHandler(), DyeColor.ORANGE, "water mixing");

        LargeCauldronBlockEntity snowCauldron = placeLargeCauldron(helper, new BlockPos(11, 1, 3));
        snowCauldron.getFluids().setFluids(List.of(
            coloredMelt(BUCKET, DyeColor.MAGENTA),
            new FluidStack(POWDER_SNOW.get(), BUCKET)
        ));
        processLargeCauldron(snowCauldron);
        assertInventoryGranules(snowCauldron.getOutputHandler(), DyeColor.MAGENTA, "powder-snow mixing");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "The retained solid-liquid cooling recipe produces sixteen same-colour granules")
    static void retainedSolidLiquidRecipePreservesColor(ExtendedGameTestHelper helper) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("solid_liquid/cool_universal_plastic_melt"))
            .orElseThrow(() -> new GameTestAssertException("solid-liquid cooling recipe was not loaded"));
        check(holder.value() instanceof SolidLiquidRecipe, "cooling recipe did not load as solid-liquid");
        SolidLiquidRecipe recipe = (SolidLiquidRecipe) holder.value();
        check(recipe.getHasCauldron().consume() == BUCKET, "solid-liquid recipe consumes the wrong melt amount");
        check(
            recipe.getHasCauldron().fluid().equals(ModFluids.UNIVERSAL_PLASTIC_MELT.getId()),
            "solid-liquid recipe targets the wrong fluid"
        );
        check(recipe.getResultItems().size() == 1, "solid-liquid recipe has an unexpected result count");
        check(
            recipe.getResultItems().getFirst().getItem() == ModItems.UNIVERSAL_PLASTIC_GRANULE.get(),
            "solid-liquid recipe returns the wrong item"
        );
        check(recipe.getResultItems().getFirst().getMaxCount() == GRANULES, "solid-liquid recipe returns the wrong count");

        HardenedResinCauldronEntity cauldron = spawnCauldron(helper, new BlockPos(3, 2, 3));
        HardenedResinAnvilEntity anvil = spawnAnvil(helper, new BlockPos(3, 3, 3));
        FluidStack melt = coloredMelt(BUCKET, DyeColor.LIGHT_BLUE);
        check(
            cauldron.getFluidHandler().fill(melt, IFluidHandler.FluidAction.EXECUTE) == BUCKET,
            "solid-liquid test cauldron rejected melt"
        );
        check(
            cauldron.getInput().insertItem(0, new ItemStack(Items.SNOWBALL), false).isEmpty(),
            "solid-liquid test cauldron rejected its cold item"
        );

        cauldron.processAnvilImpact(anvil, Direction.DOWN);

        check(cauldron.getFluidHandler().getFluid().isEmpty(), "solid-liquid recipe did not consume the melt");
        check(cauldron.getInput().getStackInSlot(0).isEmpty(), "solid-liquid recipe did not consume the cold item");
        assertInventoryGranules(cauldron.getOutput(), DyeColor.LIGHT_BLUE, "solid-liquid cooling");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Each novice-jeweler offer randomly requests exactly one of sixteen granule colors")
    static void jewelerTradeRandomlyRequestsOneColor(ExtendedGameTestHelper helper) {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> jewelerTrades = emptyTradeMap();
        PlasticVillagerTrades.addTrades(new VillagerTradesEvent(
            jewelerTrades,
            ModVillagers.JEWELER.get(),
            helper.getLevel().registryAccess()
        ));
        check(jewelerTrades.get(1).size() == 1, "jeweler did not receive exactly one Plasticraft trade");

        Int2ObjectMap<List<VillagerTrades.ItemListing>> farmerTrades = emptyTradeMap();
        PlasticVillagerTrades.addTrades(new VillagerTradesEvent(
            farmerTrades,
            VillagerProfession.FARMER,
            helper.getLevel().registryAccess()
        ));
        check(farmerTrades.get(1).isEmpty(), "non-jeweler profession received the plastic trade");

        VillagerTrades.ItemListing listing = jewelerTrades.get(1).getFirst();
        RandomSource random = RandomSource.create(1L);
        EnumSet<DyeColor> requestedColors = EnumSet.noneOf(DyeColor.class);
        for (int attempt = 0; attempt < 256; attempt++) {
            MerchantOffer offer = listing.getOffer(null, random);
            check(offer != null, "jeweler listing returned no offer");
            check(offer.getBaseCostA().is(ModItems.UNIVERSAL_PLASTIC_GRANULE.get()), "trade buys the wrong item");
            check(offer.getBaseCostA().getCount() == JEWELER_GRANULE_COST, "trade buys the wrong granule count");
            check(offer.getResult().is(Items.EMERALD) && offer.getResult().getCount() == JEWELER_EMERALD_PAYMENT,
                "trade pays the wrong amount");
            check(offer.getMaxUses() == 16, "trade has the wrong maximum uses");
            check(offer.getXp() == 2, "trade grants the wrong villager experience");
            check(Math.abs(offer.getPriceMultiplier() - 0.05F) < 1.0E-6F,
                "trade has the wrong price multiplier");

            // 成本栈携带本次随机颜色；同色可以成交，任一其他颜色必须被拒绝。
            DyeColor requestedColor = PlasticMeltColor.get(offer.getBaseCostA());
            requestedColors.add(requestedColor);
            ItemStack matching = coloredGranules(requestedColor);
            DyeColor otherColor = DyeColor.byId((requestedColor.getId() + 1) % DyeColor.values().length);
            check(offer.satisfiedBy(matching, ItemStack.EMPTY),
                "trade rejected its requested " + requestedColor.getName() + " granules");
            check(!offer.satisfiedBy(coloredGranules(otherColor), ItemStack.EMPTY),
                "trade accepted " + otherColor.getName() + " instead of " + requestedColor.getName());
            check(!offer.satisfiedBy(matching.copyWithCount(JEWELER_GRANULE_COST - 1), ItemStack.EMPTY),
                "trade accepted fewer than eight granules");
        }
        check(requestedColors.size() == DyeColor.values().length,
            "random jeweler offers did not cover all sixteen granule colors: " + requestedColors);
        helper.succeed();
    }

    private static FluidMixingRecipe fluidMixingRecipe(ExtendedGameTestHelper helper, String name) {
        RecipeHolder<?> holder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("fluid_mixing/" + name))
            .orElseThrow(() -> new GameTestAssertException(name + " recipe was not loaded"));
        check(holder.value() instanceof FluidMixingRecipe, name + " did not load as fluid mixing");
        return (FluidMixingRecipe) holder.value();
    }

    private static void assertFluidMixingDefinition(
        FluidMixingRecipe recipe,
        Fluid coolant,
        String name,
        Level level
    ) {
        check(recipe.getFluidIngredients().size() == 2, name + " recipe has the wrong ingredient count");
        check(
            recipe.getFluidIngredients().stream().allMatch(ingredient -> ingredient.amount() == BUCKET),
            name + " recipe does not consume two one-bucket ingredients"
        );
        check(
            recipe.matches(new FluidMixingRecipe.Input(List.of(
                coloredMelt(BUCKET, DyeColor.BROWN),
                new FluidStack(coolant, BUCKET)
            )), level),
            name + " recipe rejected coloured melt and its coolant"
        );
        check(recipe.getItemResults().size() == 1, name + " recipe has the wrong result count");
        ItemStack result = recipe.getItemResults().getFirst();
        check(result.is(ModItems.UNIVERSAL_PLASTIC_GRANULE.get()), name + " recipe returns the wrong item");
        check(result.getCount() == GRANULES, name + " recipe returns the wrong granule count");
    }

    private static void processLargeCauldron(LargeCauldronBlockEntity cauldron) {
        check(cauldron.getLevel() instanceof ServerLevel, "large cauldron has no server level");
        ServerLevel level = (ServerLevel) cauldron.getLevel();
        BlockPos impactPos = cauldron.getBlockPos().above(3);
        BlockState giantAnvil = GIANT_ANVIL.getDefaultState()
            .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        level.setBlock(impactPos, giantAnvil, Block.UPDATE_ALL);
        check(
            GIANT_ANVIL.get().getMainPartPos(impactPos, giantAnvil).equals(impactPos),
            "synthetic giant-anvil impact was not placed at its main part"
        );
        FallingBlockEntity impactEntity = new FallingBlockEntity(EntityType.FALLING_BLOCK, level);
        check(
            cauldron.handleGiantAnvilImpact(new AnvilEvent.OnLand(level, impactPos, impactEntity, 1.0F)),
            "large cauldron rejected its giant-anvil impact"
        );
    }

    private static LargeCauldronBlockEntity placeLargeCauldron(
        ExtendedGameTestHelper helper,
        BlockPos relativeBase
    ) {
        Level level = helper.getLevel();
        LargeCauldronBlock block = LARGE_CAULDRON.get();
        BlockPos base = helper.absolutePos(relativeBase);
        BlockState state = block.defaultBlockState();
        level.setBlock(base, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, base, state, null, ItemStack.EMPTY);
        check(
            level.getBlockEntity(base.above()) instanceof LargeCauldronBlockEntity,
            "large cauldron main block entity was not created"
        );
        return (LargeCauldronBlockEntity) level.getBlockEntity(base.above());
    }

    private static HardenedResinCauldronEntity spawnCauldron(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            ModEntities.HARDEND_RESIN_CAULDRON.get().getWidth(),
            ModEntities.HARDEND_RESIN_CAULDRON.get().getHeight()
        );
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            position,
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(cauldron), "failed to add hardened resin cauldron");
        return cauldron;
    }

    private static HardenedResinAnvilEntity spawnAnvil(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            ModEntities.HARDEND_RESIN_ANVIL.get().getWidth(),
            ModEntities.HARDEND_RESIN_ANVIL.get().getHeight()
        );
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            ModEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            ModBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        anvil.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(anvil), "failed to add hardened resin anvil");
        return anvil;
    }

    private static CatalyticPressLidEntity spawnReadyLid(
        ExtendedGameTestHelper helper,
        BlockPos relativePos
    ) {
        BlockPos pos = helper.absolutePos(relativePos);
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            CatalyticPressLidEntity.WIDTH,
            CatalyticPressLidEntity.HEIGHT
        );
        CatalyticPressLidEntity lid = new CatalyticPressLidEntity(
            ModEntities.CATALYTIC_PRESS_LID.get(),
            helper.getLevel(),
            position,
            ModBlocks.CATALYTIC_PRESS_LID.get().defaultBlockState(),
            ModBlocks.CATALYTIC_PRESS_LID.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        lid.completeCatalysis();
        check(helper.getLevel().addFreshEntity(lid), "failed to add catalytic press lid");
        return lid;
    }

    private static FluidStack coloredMelt(int amount, DyeColor color) {
        FluidStack melt = new FluidStack(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, color);
        return melt;
    }

    private static FluidTank filledTank(FluidStack fluid) {
        FluidTank tank = new FluidTank(Math.max(BUCKET, fluid.getAmount()));
        check(tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE) == fluid.getAmount(), "test tank rejected fluid");
        return tank;
    }

    private static BlockState fullWaterCauldron() {
        return Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
    }

    private static ItemStack singleGranuleDrop(ExtendedGameTestHelper helper, BlockPos relativePos) {
        List<ItemStack> drops = granuleDrops(
            helper,
            new AABB(helper.absolutePos(relativePos)).inflate(1.0D)
        );
        check(drops.size() == 1, "expected one granule stack, found " + drops.size());
        return drops.getFirst();
    }

    private static List<ItemStack> granuleDrops(ExtendedGameTestHelper helper, AABB bounds) {
        return helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            bounds,
            item -> item.isAlive() && item.getItem().is(ModItems.UNIVERSAL_PLASTIC_GRANULE.get())
        ).stream().map(ItemEntity::getItem).toList();
    }

    private static void assertInventoryGranules(IItemHandler inventory, DyeColor color, String path) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.is(ModItems.UNIVERSAL_PLASTIC_GRANULE.get())) continue;
            check(PlasticMeltColor.get(stack) == color, path + " produced a wrong-colour stack");
            count += stack.getCount();
        }
        check(count == GRANULES, path + " produced " + count + " granules");
    }

    private static Int2ObjectMap<List<VillagerTrades.ItemListing>> emptyTradeMap() {
        Int2ObjectMap<List<VillagerTrades.ItemListing>> trades = new Int2ObjectOpenHashMap<>();
        for (int level = 1; level <= 5; level++) trades.put(level, new ArrayList<>());
        return trades;
    }

    private static ItemStack coloredGranules(DyeColor color) {
        ItemStack granules = new ItemStack(ModItems.UNIVERSAL_PLASTIC_GRANULE.get(), GRANULES);
        PlasticMeltColor.set(granules, color);
        return granules;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** 模拟阶段守约、执行阶段只排出一半，用于验证跨方块与能力的补偿回滚。 */
    private static final class PartialExecutionTank extends FluidTank {
        private PartialExecutionTank(int capacity) {
            super(capacity);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (action.simulate()) return super.drain(resource, action);
            return super.drain(resource.copyWithAmount(Math.min(resource.getAmount(), BUCKET / 2)), action);
        }
    }
}
