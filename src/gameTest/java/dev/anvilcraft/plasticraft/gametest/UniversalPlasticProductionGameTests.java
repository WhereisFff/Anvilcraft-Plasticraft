package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.BurningHeaterBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItems;
import dev.dubhe.anvilcraft.recipe.FluidMixingRecipe;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** TODO-00 中保留塑料粒的生产路径及珠宝商交易回归测试。 */
public final class UniversalPlasticProductionGameTests {
    private static final int BUCKET = 1_000;
    private static final int GRANULES = 16;
    private static final int JEWELER_GRANULE_COST = 8;
    private static final int JEWELER_EMERALD_PAYMENT = 2;

    private UniversalPlasticProductionGameTests() {
    }

    @GameTest(timeoutTicks = 500)
    @EmptyTemplate("17x6x14")
    @TestHolder(description = "Cold oil accepts resin materials and recursively crafted processors without consuming catalysts")
    static void resinItemsAndProcessorProductsCatalyzeColdOil(ExtendedGameTestHelper helper) {
        List<ItemStack> catalysts = List.of(
            ModItems.RESIN.asStack(),
            ModItems.HARDEND_RESIN.asStack(),
            ModBlocks.RESIN_BLOCK.asStack(),
            PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack(),
            PlasticraftBlocks.HIGH_VISCOSITY_RESIN_BLOCK.asStack(),
            PlasticraftBlocks.RESIN_ANVIL.asStack(),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticraftItems.RESIN_ANVIL_HAMMER.asStack(),
            ModItems.PROCESSOR.asStack(),
            ModBlocks.SMART_BLOCK_PLACER.asStack(),
            ModBlocks.STRUCTURE_SCANNER.asStack(),
            PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.asStack(),
            ModItems.CAPACITOR.asStack(),
            Items.COMPARATOR.getDefaultInstance()
        );
        List<BlockPos> positions = new ArrayList<>();
        for (int index = 0; index < catalysts.size(); index++) {
            BlockPos pos = new BlockPos(2 + index % 4 * 3, 2, 2 + index / 4 * 3);
            positions.add(pos);
            helper.setBlock(pos.below(), Blocks.ICE);
            helper.setBlock(pos, PlasticraftBlocks.PLASTIC_OIL_CAULDRON.getDefaultState()
                .setValue(Layered4LevelCauldronBlock.LEVEL, 3));
            spawnCatalyst(helper, pos, catalysts.get(index).copy());
        }
        BlockPos copperPos = new BlockPos(14, 2, 11);
        helper.setBlock(copperPos.below(), Blocks.ICE);
        helper.setBlock(copperPos, PlasticraftBlocks.PLASTIC_OIL_CAULDRON.getDefaultState());
        spawnCatalyst(helper, copperPos, Items.COPPER_INGOT.getDefaultInstance());

        helper.runAfterDelay(450, () -> {
            for (int index = 0; index < catalysts.size(); index++) {
                BlockPos pos = positions.get(index);
                ItemStack catalyst = catalysts.get(index);
                BlockState result = helper.getBlockState(pos);
                check(result.is(PlasticMaterial.ELASTIC.meltCauldron()),
                    "cold resin catalyst failed to produce elastic melt: " + catalyst.getItem());
                check(result.getValue(Layered4LevelCauldronBlock.LEVEL) == 3,
                    "elastic catalysis changed the oil amount for " + catalyst.getItem());
                int retained = helper.getLevel().getEntitiesOfClass(
                    ItemEntity.class,
                    new AABB(helper.absolutePos(pos)),
                    item -> item.getItem().is(catalyst.getItem())
                ).stream().mapToInt(item -> item.getItem().getCount()).sum();
                check(retained == catalyst.getCount(), "elastic catalysis consumed " + catalyst.getItem());
            }
            check(helper.getBlockState(copperPos).is(PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get()),
                "resin processing incorrectly turned basic copper into a resin catalyst");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 520)
    @EmptyTemplate("5x6x5")
    @TestHolder(description = "树脂锅仅在底部寒冷时催化自身塑料油，并保持流体数量")
    static void resinContainersCatalyzeTheirOwnOilOnlyOverCold(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 2);
        helper.setBlock(cauldronPos.below(), Blocks.STONE);
        HardenedResinCauldronEntity cauldron = spawnCauldron(helper, cauldronPos);
        check(cauldron.getFluidHandler().fill(
            new FluidStack(PlasticraftFluids.PLASTIC_OIL.get(), 750), IFluidHandler.FluidAction.EXECUTE
        ) == 750, "树脂锅未接受自催化所需的塑料油");

        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(cauldron.getFluidHandler().getFluid().is(PlasticraftFluids.PLASTIC_OIL.get()),
                    "树脂锅在底部不寒冷时催化了塑料油");
                helper.setBlock(cauldronPos.below(), Blocks.ICE);
            })
            .thenExecuteAfter(120, () -> {
                check(cauldron.getFluidHandler().getFluid().is(PlasticraftFluids.PLASTIC_OIL.get()),
                    "树脂锅在催化所需时间前完成了反应");
            })
            .thenExecuteAfter(310, () -> {
                FluidStack result = cauldron.getFluidHandler().getFluid();
                check(result.is(PlasticMaterial.ELASTIC.melt()) && result.getAmount() == 750,
                    "树脂锅自催化未生成等量弹性塑料熔体");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 520)
    @EmptyTemplate("5x6x5")
    @TestHolder(description = "Royal steel converts universal melt only while the block below is cold")
    static void royalSteelEngineeringCatalysisRequiresColdBelow(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 2);
        helper.setBlock(cauldronPos.below(), Blocks.STONE);
        helper.setBlock(cauldronPos, universalMeltCauldron(DyeColor.CYAN));
        spawnCatalyst(helper, cauldronPos, ModItems.ROYAL_STEEL_INGOT.asStack());

        helper.startSequence()
            .thenExecuteAfter(40, () -> {
                check(
                    helper.getBlockState(cauldronPos).is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get()),
                    "royal steel reacted without cold below"
                );
                helper.setBlock(cauldronPos.below(), Blocks.ICE);
            })
            .thenExecuteAfter(430, () -> {
                BlockState result = helper.getBlockState(cauldronPos);
                check(result.is(PlasticraftBlocks.ENGINEERING_PLASTIC_MELT_CAULDRON.get()),
                    "royal steel did not produce engineering melt over cold");
                check(result.getValue(UniversalPlasticMeltCauldronBlock.COLOR)
                    == DyeColor.CYAN, "royal-steel catalysis lost the melt colour");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 860)
    @EmptyTemplate("5x6x5")
    @TestHolder(description = "Frost metal converts universal melt without a cold block")
    static void frostMetalEngineeringCatalysisNeedsNoCold(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 2);
        helper.setBlock(cauldronPos.below(), Blocks.STONE);
        helper.setBlock(cauldronPos, universalMeltCauldron(DyeColor.MAGENTA));
        spawnCatalyst(helper, cauldronPos, ModItems.FROST_METAL_INGOT.asStack());

        helper.startSequence()
            .thenExecuteAfter(820, () -> {
                BlockState result = helper.getBlockState(cauldronPos);
                check(result.is(PlasticraftBlocks.ENGINEERING_PLASTIC_MELT_CAULDRON.get()),
                    "frost metal did not produce engineering melt without cold");
                check(result.getValue(UniversalPlasticMeltCauldronBlock.COLOR)
                    == DyeColor.MAGENTA, "frost-metal catalysis lost the melt colour");
            })
            .thenSucceed();
    }

    @GameTest(timeoutTicks = 940)
    @EmptyTemplate("5x6x5")
    @TestHolder(description = "Frost metal continues directly from heated plastic oil to engineering melt")
    static void frostMetalCatalysisContinuesAcrossBothStages(ExtendedGameTestHelper helper) {
        BlockPos cauldronPos = new BlockPos(2, 2, 2);
        helper.setBlock(cauldronPos.below(), ModBlocks.BURNING_HEATER.getDefaultState());
        check(
            helper.getBlockEntity(cauldronPos.below()) instanceof BurningHeaterBlockEntity heater
                && heater.getItemHandler().insertItem(0, Items.LAVA_BUCKET.getDefaultInstance(), false).isEmpty(),
            "failed to fuel the burning heater"
        );
        helper.setBlock(
            cauldronPos,
            PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get().defaultBlockState()
                .setValue(Layered4LevelCauldronBlock.LEVEL, 4)
        );
        for (ItemStack catalyst : List.of(
            ModItems.FROST_METAL_INGOT.asStack(),
            ModItems.FROST_METAL_NUGGET.asStack(),
            ModItems.FROST_METAL_PICKAXE.asStack(),
            ModItems.FROST_METAL_AXE.asStack(),
            ModItems.FROST_METAL_SHOVEL.asStack(),
            ModItems.FROST_METAL_HOE.asStack(),
            ModItems.FROST_METAL_SWORD.asStack(),
            ModItems.FROST_ANVIL_HAMMER.asStack()
        )) {
            spawnCatalyst(helper, cauldronPos, catalyst);
        }

        helper.startSequence()
            .thenExecuteAfter(450, () -> {
                BlockState intermediate = helper.getBlockState(cauldronPos);
                check(
                    intermediate.is(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get()),
                    "frost catalysts did not finish the plastic-oil stage; found " + intermediate
                );
            })
            .thenExecuteAfter(450, () -> {
                BlockState result = helper.getBlockState(cauldronPos);
                check(
                    result.is(PlasticraftBlocks.ENGINEERING_PLASTIC_MELT_CAULDRON.get()),
                    "frost catalysts did not continue through both reaction stages; found " + result
                );
            })
            .thenSucceed();
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
        check(result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get()), name + " recipe returns the wrong item");
        check(result.getCount() == GRANULES, name + " recipe returns the wrong granule count");
    }

    private static void processLargeCauldron(LargeCauldronBlockEntity cauldron) {
        check(cauldron.getLevel() instanceof ServerLevel, "large cauldron has no server level");
        ServerLevel level = (ServerLevel) cauldron.getLevel();
        BlockPos impactPos = cauldron.getBlockPos().above(3);
        BlockState giantAnvil = ModBlocks.GIANT_ANVIL.getDefaultState()
            .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
            .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER);
        level.setBlock(impactPos, giantAnvil, Block.UPDATE_ALL);
        check(
            ModBlocks.GIANT_ANVIL.get().getMainPartPos(impactPos, giantAnvil).equals(impactPos),
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
        LargeCauldronBlock block = ModBlocks.LARGE_CAULDRON.get();
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
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get().getWidth(),
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get().getHeight()
        );
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
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
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get().getWidth(),
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get().getHeight()
        );
        HardenedResinAnvilEntity anvil = new HardenedResinAnvilEntity(
            PlasticraftEntities.HARDEND_RESIN_ANVIL.get(),
            helper.getLevel(),
            position,
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_ANVIL.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        anvil.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(anvil), "failed to add hardened resin anvil");
        return anvil;
    }

    private static FluidStack coloredMelt(int amount, DyeColor color) {
        FluidStack melt = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), amount);
        PlasticMeltColor.set(melt, color);
        return melt;
    }

    private static BlockState universalMeltCauldron(DyeColor color) {
        return PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get().defaultBlockState()
            .setValue(Layered4LevelCauldronBlock.LEVEL, 4)
            .setValue(UniversalPlasticMeltCauldronBlock.COLOR, color);
    }

    private static void spawnCatalyst(
        ExtendedGameTestHelper helper,
        BlockPos relativePos,
        ItemStack stack
    ) {
        Vec3 center = Vec3.atCenterOf(helper.absolutePos(relativePos)).add(0.0D, 0.2D, 0.0D);
        ItemEntity catalyst = new ItemEntity(helper.getLevel(), center.x, center.y, center.z, stack);
        catalyst.setDeltaMovement(Vec3.ZERO);
        catalyst.setPickUpDelay(Integer.MAX_VALUE);
        check(helper.getLevel().addFreshEntity(catalyst), "failed to spawn catalyst " + stack.getItem());
    }

    private static void assertInventoryGranules(IItemHandler inventory, DyeColor color, String path) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())) continue;
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
        ItemStack granules = new ItemStack(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get(), GRANULES);
        PlasticMeltColor.set(granules, color);
        return granules;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
