package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.entity.PlasmaExperienceOrbExtension;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.IVaporConsumer;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporAction;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationContext;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporStack;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.YukkuriCapabilities;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import dev.dubhe.anvilcraft.recipe.anvil.outcome.RoyalPreferenceOutcome;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPattern;
import dev.dubhe.anvilcraft.recipe.multiblock.BlockPredicateWithState;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockConversionRecipe;
import dev.dubhe.anvilcraft.recipe.multiblock.MultiblockRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** 冷凝塔、喷流气化和硬化树脂锅的服务器端回归测试。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID + "_tests")
public final class CondenserTowerGameTests {
    private static final int TOWER_CAPACITY = 64 * FluidType.BUCKET_VOLUME;
    private static final IVaporConsumer FULL_SEALED_CONSUMER = new IVaporConsumer() {
        @Override
        public int receiveVapor(VaporStack vapor, VaporAction action, VaporizationContext context) {
            return 0;
        }

        @Override
        public boolean sealsOutlet(VaporizationContext context) {
            return true;
        }
    };

    private CondenserTowerGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Large cauldrons vaporize only their top oil layer and scale with jet count")
    static void largeCauldronVaporizationUsesTopLayer(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        FluidStack oil = new FluidStack(ModFluids.OIL.get(), 1_000);
        cauldron.getFluids().setFluids(List.of(oil, new FluidStack(Fluids.WATER, 1_000)));

        BlockPos main = helper.absolutePos(base).above();
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));
        BlockPos jetCenter = main.below(2);
        setJet(helper.getLevel(), jetCenter);
        setJet(helper.getLevel(), jetCenter.west());
        setJet(helper.getLevel(), jetCenter.east());

        CondenserTowerProcess.tickLargeCauldron((net.minecraft.server.level.ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getFluids().getFluidInTank(0).getAmount() == 1_000,
            "a non-oil top layer was incorrectly vaporized");
        tower.dropContents();

        cauldron.getFluids().setFluids(List.of(oil));
        CondenserTowerProcess.tickLargeCauldron((net.minecraft.server.level.ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 850,
            "three jets did not vaporize 150 mB from the top oil layer");
        check(CondenserGas.GASEOUS_OIL.equals(tower.getGasId()) && tower.getGasAmount() == 150,
            "the condenser tower did not receive 150 mB of gaseous oil through Yukkuri");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "A missing vapor consumer vents to the atmosphere while consuming crude oil")
    static void largeCauldronVaporizationVentsWithoutConsumer(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 950,
            "an open cauldron did not vaporize 50 mB of crude oil into the atmosphere");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "An open condenser vents partial and full overflow without stopping vaporization")
    static void fullOpenCondenserDoesNotApplyBackpressure(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));
        check(tower.collectGas(CondenserGas.GASEOUS_OIL, TOWER_CAPACITY - 25) == TOWER_CAPACITY - 25,
            "failed to leave 25 mB free in the condenser's vapor buffer");
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 950,
            "a nearly full open condenser incorrectly limited crude-oil vaporization");
        check(tower.getGasAmount() == TOWER_CAPACITY,
            "an open condenser did not accept exactly its remaining 25 mB of capacity");

        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 900,
            "a full open condenser incorrectly stopped crude-oil vaporization");
        check(tower.getGasAmount() == TOWER_CAPACITY,
            "a full open condenser accepted vapor beyond its capacity");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "A full sealed consumer applies backpressure before liquid is consumed")
    static void fullSealedConsumerAppliesBackpressure(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        helper.getLevel().setBlock(cauldron.getBlockPos().above(2), Blocks.BARRIER.defaultBlockState(), Block.UPDATE_ALL);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 1_000,
            "a full sealed consumer allowed crude oil to be consumed");
        helper.succeed();
    }

    @SubscribeEvent
    static void registerTestCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(
            YukkuriCapabilities.VAPOR_CONSUMER,
            (level, pos, state, blockEntity, side) -> side == null || side == Direction.DOWN
                ? FULL_SEALED_CONSUMER
                : null,
            Blocks.BARRIER
        );
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("13x12x13")
    @TestHolder(description = "Large cauldrons apply the seed-selected royal preference")
    static void largeCauldronAppliesRoyalPreference(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(6, 2, 6);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        Item preferredGemBlock = findRoyalPreferredGemBlock(helper.getLevel());

        check(
            cauldron.getInputHandler().insertItem(0, new ItemStack(Items.IRON_BLOCK, 2), false).isEmpty(),
            "failed to insert iron blocks into the large cauldron"
        );
        check(
            cauldron.getInputHandler().insertItem(1, new ItemStack(Items.DIAMOND_BLOCK), false).isEmpty(),
            "failed to insert the diamond block into the large cauldron"
        );
        check(
            cauldron.getInputHandler().insertItem(2, new ItemStack(preferredGemBlock), false).isEmpty(),
            "failed to insert the preferred gem block into the large cauldron"
        );
        helper.setBlock(
            base.offset(-1, -1, 0),
            dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER.getDefaultState()
                .setValue(dev.dubhe.anvilcraft.block.HeaterBlock.OVERLOAD, false)
        );

        BlockPos fallingSource = new BlockPos(1, 8, 1);
        helper.setBlock(fallingSource, Blocks.SAND);
        FallingBlockEntity fallingAnvil = FallingBlockEntity.fall(
            helper.getLevel(),
            helper.absolutePos(fallingSource),
            Blocks.SAND.defaultBlockState()
        );
        BlockPos giantAnvilCenter = cauldron.getBlockPos().above(3);
        helper.getLevel().setBlock(
            giantAnvilCenter,
            dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL.getDefaultState()
                .setValue(GiantAnvilBlock.HALF, Cube3x3PartHalf.MID_CENTER)
                .setValue(GiantAnvilBlock.CUBE, GiantAnvilCube.CENTER),
            Block.UPDATE_CLIENTS
        );

        AnvilEvent.OnLand event = new AnvilEvent.OnLand(
            helper.getLevel(),
            giantAnvilCenter,
            fallingAnvil,
            1.0F
        );
        check(cauldron.handleGiantAnvilImpact(event), "large cauldron did not handle the giant-anvil impact");
        check(cauldron.getInputHandler().isEmpty(), "royal steel recipe did not consume the large-cauldron inputs");
        check(
            outputCount(
                cauldron,
                dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_STEEL_BLOCK.get().asItem()
            ) == 2,
            "preferred gem block did not double the large-cauldron royal steel output"
        );
        fallingAnvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x26x11")
    @TestHolder(description = "Condenser towers expose four output faces, store 64 buckets, "
        + "and stop at four productive layers")
    static void condenserTowerLayersAndOutputs(ExtendedGameTestHelper helper) {
        BlockPos cauldronBase = new BlockPos(5, 1, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, cauldronBase);
        BlockPos cauldronMain = helper.absolutePos(cauldronBase).above();

        List<CondenserTowerBlockEntity> placed = new ArrayList<>();
        BlockPos towerBase = helper.absolutePos(cauldronBase).above(3);
        for (int i = 0; i < 5; i++) {
            placed.add(placeTower(helper, towerBase.above(i * 3)));
        }

        List<CondenserTowerBlockEntity> productive = CondenserTowerProcess.findProductiveTowers(
            helper.getLevel(),
            cauldronMain
        );
        check(productive.size() == 4, "expected four productive tower layers, got " + productive.size());
        BlockState firstTowerState = placed.getFirst().getBlockState();
        BlockState firstTowerWorldState = helper.getLevel().getBlockState(placed.getFirst().getBlockPos());
        BlockState cauldronTopState = helper.getLevel().getBlockState(towerBase.below());
        check(firstTowerWorldState.getValue(CondenserTowerBlock.SEALED),
            "the tower directly above the large cauldron was not sealed; aligned="
                + CondenserTowerBlock.isAlignedWithLargeCauldron(helper.getLevel(), towerBase)
                + ", entity=" + firstTowerState
                + ", world=" + firstTowerWorldState
                + ", below=" + cauldronTopState);
        check(!helper.getLevel().getBlockState(placed.get(1).getBlockPos()).getValue(CondenserTowerBlock.SEALED),
            "a stacked tower was incorrectly marked as a cauldron seal");

        CondenserTowerBlockEntity first = placed.getFirst();
        var vaporConsumer = helper.getLevel().getCapability(
            YukkuriCapabilities.VAPOR_CONSUMER,
            towerBase,
            Direction.DOWN
        );
        check(vaporConsumer != null, "tower bottom center did not expose Yukkuri's vapor capability");
        check(vaporConsumer.receiveVapor(
                new VaporStack(ResourceLocation.fromNamespaceAndPath("test", "custom_vapor"), 250),
                VaporAction.SIMULATE,
                new VaporizationContext((net.minecraft.server.level.ServerLevel) helper.getLevel(), cauldron)
            ) == 250,
            "tower did not simulate accepting an addon-defined vapor");
        check(first.getGasAmount() == 0, "simulating vapor input changed the tower gas buffer");
        check(first.getFluidHandler().getTankCapacity(0) == TOWER_CAPACITY,
            "tower tank capacity is not 64 buckets");
        check(first.collect(new FluidStack(Fluids.WATER, 1_000)) == 1_000,
            "tower tank did not accept its internal collection API");

        BlockPos topNorth = towerBase.offset(0, 2, -1);
        BlockState topNorthState = helper.getLevel().getBlockState(topNorth);
        IFluidHandler outward = CondenserTowerBlockEntity.capability(
            helper.getLevel(), topNorth, topNorthState, null, Direction.NORTH
        );
        IFluidHandler inward = CondenserTowerBlockEntity.capability(
            helper.getLevel(), topNorth, topNorthState, null, Direction.SOUTH
        );
        check(outward != null, "top north interface did not expose an outward fluid handler");
        check(inward == null, "top north interface exposed an inward fluid handler");
        check(outward.fill(
                new FluidStack(Fluids.WATER, 1),
                IFluidHandler.FluidAction.EXECUTE
            ) == 0,
            "tower output interface accepted fluid input");
        check(outward.drain(250, IFluidHandler.FluidAction.EXECUTE).getAmount() == 250,
            "tower output interface could not drain stored fluid");

        var multiblockHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("multiblock/condenser_tower"));
        check(multiblockHolder.isPresent()
                && multiblockHolder.get().value() instanceof MultiblockRecipe,
            "condenser tower multiblock recipe was not loaded");
        checkCondenserInputPattern(((MultiblockRecipe) multiblockHolder.orElseThrow().value()).getPattern());

        var conversionHolder = helper.getLevel().getRecipeManager()
            .byKey(AnvilcraftPlasticraft.of("multiblock_conversion/condenser_tower"));
        check(conversionHolder.isPresent()
                && conversionHolder.get().value() instanceof MultiblockConversionRecipe,
            "condenser tower conversion recipe was not loaded");
        checkCondenserInputPattern(
            ((MultiblockConversionRecipe) conversionHolder.orElseThrow().value()).getInputPattern()
        );
        check(helper.getLevel().getRecipeManager()
                .byKey(AnvilcraftPlasticraft.of("condenser/gaseous_water_to_water")).isPresent(),
            "condenser collection recipe was not loaded");

        setJet(helper.getLevel(), cauldronMain.below(2));
        check(first.collectGas(CondenserGas.GASEOUS_WATER, 250) == 250,
            "tower did not accept gaseous water");
        check(first.getFluidHandler().fill(
                new FluidStack(Fluids.WATER, 250),
                IFluidHandler.FluidAction.SIMULATE
            ) == 250,
            "tower tank could not simulate adding water before condensation");
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            (LargeCauldronBlockEntity) helper.getLevel().getBlockEntity(cauldronMain)
        );
        check(first.getGasAmount() == 0,
            "gaseous water was not consumed by condenser recipe; got gas=" + first.getGasAmount());
        check(first.getStoredFluid().getAmount() == 1_000,
            "condenser recipe did not produce 250 mB of water; got fluid="
                + first.getStoredFluid().getAmount() + ", gas=" + first.getGasAmount());
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Plasma jet recipes execute fluid-only and combined item/fluid input-output forms")
    static void plasmaRecipesSupportAllInputOutputForms(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        cauldron.getFluids().fill(
            new FluidStack(Fluids.LAVA, 50),
            IFluidHandler.FluidAction.EXECUTE
        );
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(outputCount(cauldron, Items.CLAY_BALL) == 1,
            "a fluid-only plasma recipe did not create its item output");
        check(cauldron.getTopFluid().isEmpty(),
            "a fluid-only plasma recipe did not consume its fluid input");

        cauldron.getFluids().fill(
            new FluidStack(Fluids.WATER, 50),
            IFluidHandler.FluidAction.EXECUTE
        );
        cauldron.getInputHandler().insertItem(0, Items.COAL.getDefaultInstance(), false);
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(outputCount(cauldron, Items.DIAMOND) == 1,
            "a combined plasma recipe did not create its item output");
        check(cauldron.getInputHandler().getStackInSlot(0).isEmpty(),
            "a combined plasma recipe did not consume its item input");
        check(cauldron.getTopFluid().is(Fluids.LAVA)
                && cauldron.getTopFluid().getAmount() == 50,
            "a combined plasma recipe did not replace 50 mB of input with 50 mB of output");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Plasma jets continuously process dedicated, super-heating, and vanilla recipes")
    static void plasmaRecipesIncludeSuperHeatingAndVanilla(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        cauldron.getInputHandler().insertItem(
            0,
            dev.dubhe.anvilcraft.init.item.ModItems.RUBY.asStack(),
            false
        );
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().is(ModFluids.MELT_GEM.get())
                && cauldron.getTopFluid().getAmount() == 100,
            "the ruby plasma recipe did not produce 100 mB of molten gem");

        cauldron.getInputHandler().insertItem(
            0,
            dev.dubhe.anvilcraft.init.item.ModItems.WOOD_FIBER.asStack(2),
            false
        );
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        check(outputCount(cauldron, Items.CHARCOAL) == 1,
            "an AnvilCraft super-heating recipe was not processed continuously");

        cauldron.getInputHandler().insertItem(0, Items.CACTUS.getDefaultInstance(), false);
        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        int greenDye = outputCount(cauldron, Items.GREEN_DYE);
        check(greenDye == 2,
            "a boosted vanilla smelting recipe compatible with super heating produced " + greenDye + " instead of 2");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Experience-fluid plasma processing creates three-XP anti-gravity orbs")
    static void plasmaExperienceOrbsRiseAndExpire(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));
        cauldron.getFluids().fill(
            new FluidStack(ModFluids.EXP_FLUID.get(), 50),
            IFluidHandler.FluidAction.EXECUTE
        );

        CondenserTowerProcess.tickLargeCauldron(
            (net.minecraft.server.level.ServerLevel) helper.getLevel(),
            cauldron
        );
        List<ExperienceOrb> orbs = helper.getLevel().getEntitiesOfClass(
            ExperienceOrb.class,
            new AABB(cauldron.getBlockPos()).inflate(4.0D)
        );
        check(orbs.size() == 1, "expected one plasma-produced experience orb, got " + orbs.size());
        ExperienceOrb orb = orbs.getFirst();
        check(orb.getValue() == 3, "the plasma-produced experience orb was not worth three XP");
        check(orb instanceof PlasmaExperienceOrbExtension extension
                && extension.plasticraft$isPlasmaProduced(),
            "the plasma-produced experience orb was missing its anti-gravity marker");
        orb.tick();
        check(orb.getDeltaMovement().y >= 0.055D,
            "the plasma-produced experience orb did not accelerate upward");
        orb.setPos(orb.getX(), helper.getLevel().getMaxBuildHeight(), orb.getZ());
        orb.tick();
        check(orb.isRemoved(), "the anti-gravity experience orb survived above build height");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "Flint and steel ignites an oil-filled hardened resin cauldron instead of entering it")
    static void flintAndSteelIgnitesHardenedResinCauldron(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            dev.anvilcraft.plasticraft.init.entity.ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new net.minecraft.world.phys.Vec3(3.5D, 2.0D, 3.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(
            new FluidStack(ModFluids.OIL.get(), 1_000),
            IFluidHandler.FluidAction.EXECUTE
        );
        check(level.addFreshEntity(pot), "failed to add hardened resin cauldron");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flintAndSteel = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flintAndSteel);
        InteractionResult result = pot.interact(player, InteractionHand.MAIN_HAND);

        check(result.consumesAction(), "flint and steel interaction was not handled");
        check(pot.anvilcraft$isIgnited(), "flint and steel did not ignite the oil-filled cauldron");
        check(flintAndSteel.getCount() == 1 && flintAndSteel.getDamageValue() == 1,
            "flint and steel was consumed or damaged incorrectly");
        for (int slot = 0; slot < pot.getInput().getSlots(); slot++) {
            check(pot.getInput().getStackInSlot(slot).isEmpty(),
                "flint and steel was inserted into cauldron input slot " + slot);
        }
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x16x11")
    @TestHolder(description = "A fire-started hardened resin cauldron creates and maintains a jet "
        + "below a large cauldron")
    static void hardenedResinCauldronCreatesJetBelowLargeCauldron(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos occupied = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos jetPos = occupied.above();
        BlockPos heaterPos = occupied.below();
        level.setBlock(
            heaterPos,
            dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER.getDefaultState()
                .setValue(dev.dubhe.anvilcraft.block.HeaterBlock.OVERLOAD, false),
            Block.UPDATE_ALL
        );
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = jetPos.relative(direction);
            level.setBlock(side, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(
            jetPos.above(),
            dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON.getDefaultState()
                .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER),
            Block.UPDATE_ALL
        );

        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            dev.anvilcraft.plasticraft.init.entity.ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5D, 3.0D, 5.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(new FluidStack(ModFluids.OIL.get(), 1_000), IFluidHandler.FluidAction.EXECUTE);
        pot.getInput().insertItem(0, Items.BLAZE_POWDER.getDefaultInstance(), false);
        check(level.addFreshEntity(pot), "failed to add hardened resin cauldron");
        for (int i = 0; i < 10; i++) pot.tick();
        check(pot.anvilcraft$isIgnited(), "fire starter did not ignite the hardened resin cauldron");
        check(level.getBlockState(jetPos).is(dev.dubhe.anvilcraft.init.block.ModBlocks.PLASMA_JETS),
            "hardened resin cauldron did not create a plasma jet");
        check(level.getBlockEntity(jetPos) instanceof PlasmaJetsBlockEntity, "plasma jet block entity was not created");

        PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) level.getBlockEntity(jetPos);
        check(jet.getParticleEndPos().equals(jetPos.above().getBottomCenter()),
            "large cauldron changed the plasma jet particle endpoint");
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(level.getBlockState(jetPos).is(dev.dubhe.anvilcraft.init.block.ModBlocks.PLASMA_JETS),
            "the initial jet directly below a large cauldron was removed by wall integrity checks");
        check(pot.getFluidHandler().getFluidAmount() == 750,
            "jet maintenance did not consume 250 mB of hardened-pot oil");
        helper.succeed();
    }

    private static LargeCauldronBlockEntity placeLargeCauldron(
        ExtendedGameTestHelper helper,
        BlockPos base
    ) {
        Level level = helper.getLevel();
        dev.dubhe.anvilcraft.block.LargeCauldronBlock block =
            dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON.get();
        BlockPos absoluteBase = helper.absolutePos(base);
        BlockState state = block.defaultBlockState();
        level.setBlock(absoluteBase, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, absoluteBase, state, null, ItemStack.EMPTY);
        BlockEntityLookup.requireLargeCauldron(level, absoluteBase.above());
        return (LargeCauldronBlockEntity) level.getBlockEntity(absoluteBase.above());
    }

    private static CondenserTowerBlockEntity placeTower(
        ExtendedGameTestHelper helper,
        BlockPos absoluteBase
    ) {
        Level level = helper.getLevel();
        CondenserTowerBlock block = ModBlocks.CONDENSER_TOWER.get();
        BlockState state = block.defaultBlockState();
        level.setBlock(absoluteBase, state, Block.UPDATE_ALL);
        block.setPlacedBy(level, absoluteBase, state, null, ItemStack.EMPTY);
        BlockPos main = absoluteBase.above();
        return (CondenserTowerBlockEntity) level.getBlockEntity(main);
    }

    private static void setJet(Level level, BlockPos pos) {
        level.setBlock(
            pos,
            dev.dubhe.anvilcraft.init.block.ModBlocks.PLASMA_JETS.getDefaultState(),
            Block.UPDATE_ALL
        );
    }

    private static int outputCount(LargeCauldronBlockEntity cauldron, Item item) {
        int count = 0;
        for (int slot = 0; slot < cauldron.getOutputHandler().getSlots(); slot++) {
            ItemStack stack = cauldron.getOutputHandler().getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static Item findRoyalPreferredGemBlock(ServerLevel level) {
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(ModItemTags.GEM_BLOCKS)) {
            Item item = holder.value();
            if (RoyalPreferenceOutcome.RoyalPreference.isRoyalPreferred(level, new ItemStack(item))) {
                return item;
            }
        }
        throw new GameTestAssertException("royal preference selected no gem block");
    }

    private static void checkCondenserInputPattern(BlockPattern pattern) {
        check(pattern.getLayers().equals(List.of(
            List.of("DCD", "CFC", "DCD"),
            List.of(" C ", "C C", " C "),
            List.of(" E ", "ABA", " E ")
        )), "condenser recipe layers were not ordered from bottom to top");

        checkPipePredicate(pattern.getBySymbol('A'), Direction.Axis.X, "west/east");
        checkPipePredicate(pattern.getBySymbol('E'), Direction.Axis.Z, "north/south");
        check(pattern.getBySymbol('C').getBlock()
                == dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_BRASS_PILLAR.get(),
            "condenser recipe did not use cut brass pillars");
        check(pattern.getBySymbol('D').getBlock() == ModBlocks.HIGH_VISCOSITY_RESIN_BLOCK.get(),
            "condenser recipe did not use high-viscosity resin blocks");
        checkTrapdoorPredicate(pattern.getBySymbol('B'), Half.TOP, "top");
        checkTrapdoorPredicate(pattern.getBySymbol('F'), Half.BOTTOM, "bottom");
    }

    private static void checkPipePredicate(
        BlockPredicateWithState predicate,
        Direction.Axis axis,
        String position
    ) {
        check(predicate.getBlock() == dev.dubhe.anvilcraft.init.block.ModBlocks.PIPE_STRAIGHT.get(),
            position + " condenser interface was not a straight pipe");
        check(predicate.getPropertyValue(PipeBlock.AXIS) == axis,
            position + " condenser pipe used the wrong axis");
        check(Boolean.TRUE.equals(predicate.getPropertyValue(PipeBlock.HAS_END_START))
                && Boolean.TRUE.equals(predicate.getPropertyValue(PipeBlock.HAS_END_END)),
            position + " condenser pipe did not render both ends");
        check(Boolean.TRUE.equals(predicate.getPropertyValue(PipeBlock.HAS_CHECK_VALVE)),
            position + " condenser pipe did not include a check valve");
        check(Boolean.FALSE.equals(predicate.getPropertyValue(PipeBlock.WATERLOGGED)),
            position + " condenser pipe was waterlogged");
    }

    private static void checkTrapdoorPredicate(
        BlockPredicateWithState predicate,
        Half half,
        String position
    ) {
        check(predicate.getBlock() == Blocks.COPPER_TRAPDOOR,
            position + " condenser trapdoor was not copper");
        check(predicate.getPropertyValue(TrapDoorBlock.HALF) == half,
            position + " condenser trapdoor used the wrong half");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }

    /** 避免测试辅助方法中的重复强制转换掩盖结构未成功放置。 */
    private static final class BlockEntityLookup {
        private static void requireLargeCauldron(Level level, BlockPos pos) {
            if (!(level.getBlockEntity(pos) instanceof LargeCauldronBlockEntity)) {
                throw new GameTestAssertException("large cauldron main block entity was not created");
            }
        }
    }
}
