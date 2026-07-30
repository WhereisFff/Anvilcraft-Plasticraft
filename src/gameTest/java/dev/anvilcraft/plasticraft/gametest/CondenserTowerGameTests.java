package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.lib.v2.yukkuri.api.vapor.IVaporConsumer;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporAction;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporStack;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationContext;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.YukkuriCapabilities;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.blockentity.EnhancedPlasmaJetExtension;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.ModBlockEntities;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EscapingVaporEffects;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.block.fluid.PipeBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.block.state.GiantAnvilCube;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import dev.dubhe.anvilcraft.init.item.ModItems;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

import static dev.anvilcraft.plasticraft.init.block.ModFluids.CRUDE_OIL_ACID;
import static dev.anvilcraft.plasticraft.init.block.ModFluids.HIGH_HEAT_FUEL;
import static dev.anvilcraft.plasticraft.init.block.ModFluids.PLASTIC_OIL;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_BRASS_PILLAR;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.GIANT_ANVIL;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.PIPE_STRAIGHT;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.PLASMA_JETS;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.REDHOT_NETHERITE_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.ROYAL_STEEL_BLOCK;

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
    @TestHolder(description = "Normal jets buffer two five-millibucket oil portions into one condenser batch")
    static void largeCauldronVaporizationUsesTopLayer(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        FluidStack oil = new FluidStack(ModFluids.OIL.get(), 1_000);
        cauldron.getFluids().setFluids(List.of(oil, new FluidStack(Fluids.WATER, 1_000)));

        BlockPos main = helper.absolutePos(base).above();
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));
        BlockPos jetCenter = main.below(2);
        setJet(helper.getLevel(), jetCenter);

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getFluids().getFluidInTank(0).getAmount() == 1_000,
            "a non-oil top layer was incorrectly vaporized");
        helper.getLevel().removeBlock(jetCenter, false);
        tower.dropContents();

        cauldron.getFluids().setFluids(List.of(oil));
        helper.runAfterDelay(1, () -> {
            setJet(helper.getLevel(), jetCenter);
            CondenserTowerProcess.tickLargeCauldron(
                (ServerLevel) helper.getLevel(),
                cauldron
            );
            check(cauldron.getTopFluid().getAmount() == 995,
                "one jet did not vaporize 5 mB from the top oil layer");
            check(tower.getStoredFluid().isEmpty() && tower.getGasAmount() == 5,
                "the first 5 mB portion did not remain buffered below the 10 mB condensation batch");

            // 普通喷流第二刻再送入 5 mB，第一层此时才应凑满并结算一个 10 mB 批次。
            helper.runAfterDelay(1, () -> {
                CondenserTowerProcess.tickLargeCauldron(
                    (ServerLevel) helper.getLevel(),
                    cauldron
                );
                check(cauldron.getTopFluid().getAmount() == 990,
                    "two normal-jet ticks did not vaporize 10 mB from the top oil layer");
                FluidStack condensed = tower.getFluidHandler().getFluidInTank(0);
                check(condensed.is(HIGH_HEAT_FUEL.get())
                        && condensed.getAmount() == 10,
                    "the condenser tower did not condense the 10 mB high-heat-fuel batch; fluid="
                        + BuiltInRegistries.FLUID.getKey(condensed.getFluid())
                        + ", amount=" + condensed.getAmount()
                        + ", gas=" + tower.getGasId()
                        + ", gasAmount=" + tower.getGasAmount());
                check(tower.getGasAmount() == 0,
                    "the condenser tower retained gas after completing a 10 mB condensation batch");
                helper.succeed();
            });
        });
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
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 995,
            "an open cauldron did not vaporize 5 mB of crude oil into the atmosphere");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "An open full condenser vents overflow without stopping vaporization")
    static void fullOpenCondenserDoesNotApplyBackpressure(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));
        check(tower.collectGas(CondenserGas.GASEOUS_OIL, TOWER_CAPACITY) == TOWER_CAPACITY,
            "failed to fill the condenser's vapor buffer");
        check(tower.collect(new FluidStack(Fluids.WATER, TOWER_CAPACITY)) == TOWER_CAPACITY,
            "failed to block condensation with a full incompatible output tank");
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        CondenserTowerProcess.tickLargeCauldron(
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 995,
            "a full open condenser incorrectly stopped crude-oil vaporization");
        check(tower.getGasAmount() == TOWER_CAPACITY,
            "a full open condenser accepted vapor beyond its capacity");

        CondenserTowerProcess.tickLargeCauldron(
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 990,
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
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        check(cauldron.getTopFluid().getAmount() == 1_000,
            "a full sealed consumer allowed crude oil to be consumed");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Nine full blocks above a large cauldron apply physical vapor backpressure")
    static void fullyBlockedOutletAppliesBackpressure(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        BlockPos outlet = cauldron.getBlockPos().above(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(outlet.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 1_000,
            "a physically sealed outlet consumed crude oil");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x16x11")
    @TestHolder(description = "A capped condenser applies backpressure only when its gas and output storages are full")
    static void cappedTowerRequiresBothStoragesForBackpressure(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));
        check(tower.collect(new FluidStack(Fluids.WATER, TOWER_CAPACITY)) == TOWER_CAPACITY,
            "failed to fill the capped tower's output storage");
        helper.getLevel().setBlock(tower.getBlockPos().above(2), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos jetPos = cauldron.getBlockPos().below(2);
        setJet(helper.getLevel(), jetPos);

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 995,
            "a full output storage applied backpressure before the gas storage was full");
        check(tower.getGasAmount() == 5,
            "the capped tower did not buffer vapor after its output storage became full");
        check(helper.getLevel().getBlockState(jetPos).is(PLASMA_JETS),
            "a full output storage extinguished the jet before the gas storage was full");

        check(tower.collectGas(CondenserGas.GASEOUS_OIL, TOWER_CAPACITY) == TOWER_CAPACITY - 5,
            "failed to fill the capped tower's remaining gas storage");
        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 995,
            "a capped tower with both storages full allowed further vaporization");
        check(helper.getLevel().getBlockState(jetPos).isAir(),
            "backpressure did not extinguish the active plasma jet");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x20x11")
    @TestHolder(description = "An enhanced jet distributes fifty millibuckets of gaseous oil across three towers")
    static void enhancedJetDistributesOilAcrossThreeTowers(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        BlockPos firstBase = helper.absolutePos(base).above(3);
        CondenserTowerBlockEntity first = placeTower(helper, firstBase);
        CondenserTowerBlockEntity second = placeTower(helper, firstBase.above(3));
        CondenserTowerBlockEntity third = placeTower(helper, firstBase.above(6));
        BlockPos jetPos = cauldron.getBlockPos().below(2);
        setJet(helper.getLevel(), jetPos);
        check(helper.getLevel().getBlockEntity(jetPos) instanceof EnhancedPlasmaJetExtension extension,
            "plasma jet block entity did not expose enhanced state");
        PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) helper.getLevel().getBlockEntity(jetPos);
        ((EnhancedPlasmaJetExtension) jet).plasticraft$setEnhanced(true);
        check(jet.getUpdatePacket() != null,
            "enhanced plasma jet did not create a client update packet");
        check(jet.getUpdateTag(helper.getLevel().registryAccess()).getBoolean("plasticraft_enhanced"),
            "enhanced plasma jet update tag did not synchronize its blue-particle state");

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 950,
            "enhanced jet did not vaporize 50 mB of crude oil");
        check(first.getStoredFluid().is(HIGH_HEAT_FUEL.get())
                && first.getStoredFluid().getAmount() == 10,
            "first tower did not condense 10 mB of high-heat fuel");
        check(second.getStoredFluid().is(PLASTIC_OIL.get())
                && second.getStoredFluid().getAmount() == 30,
            "second tower did not condense 30 mB of plastic oil");
        check(third.getStoredFluid().is(CRUDE_OIL_ACID.get())
                && third.getStoredFluid().getAmount() == 10,
            "third tower did not condense 10 mB of crude-oil essence");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x20x11")
    @TestHolder(description = "A full middle layer in an open condenser lets vapor continue to higher layers")
    static void openFullMiddleTowerPassesVaporUpward(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        cauldron.getFluids().setFluids(List.of(new FluidStack(ModFluids.OIL.get(), 1_000)));
        BlockPos firstBase = helper.absolutePos(base).above(3);
        CondenserTowerBlockEntity first = placeTower(helper, firstBase);
        CondenserTowerBlockEntity second = placeTower(helper, firstBase.above(3));
        CondenserTowerBlockEntity third = placeTower(helper, firstBase.above(6));
        check(second.collectGas(CondenserGas.GASEOUS_OIL, TOWER_CAPACITY) == TOWER_CAPACITY,
            "failed to fill the middle tower's gas storage");
        check(second.collect(new FluidStack(
                PLASTIC_OIL.get(),
                TOWER_CAPACITY
            )) == TOWER_CAPACITY,
            "failed to fill the middle tower's output storage");

        BlockPos jetPos = cauldron.getBlockPos().below(2);
        setJet(helper.getLevel(), jetPos);
        check(helper.getLevel().getBlockEntity(jetPos) instanceof EnhancedPlasmaJetExtension,
            "plasma jet block entity did not expose enhanced state");
        ((EnhancedPlasmaJetExtension) helper.getLevel().getBlockEntity(jetPos)).plasticraft$setEnhanced(true);

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 950,
            "a full middle tower stopped open-stack vaporization");
        check(first.getStoredFluid().is(HIGH_HEAT_FUEL.get())
                && first.getStoredFluid().getAmount() == 10,
            "the first tower did not condense its 10 mB share");
        check(second.getGasAmount() == TOWER_CAPACITY
                && second.getStoredFluid().getAmount() == TOWER_CAPACITY,
            "the full middle tower changed while bypassing vapor");
        check(third.getStoredFluid().is(CRUDE_OIL_ACID.get())
                && third.getStoredFluid().getAmount() == 10,
            "vapor did not bypass the full middle tower and reach the third layer");
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
            HEATER.getDefaultState()
                .setValue(HeaterBlock.OVERLOAD, false)
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
            GIANT_ANVIL.getDefaultState()
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
                ROYAL_STEEL_BLOCK.get().asItem()
            ) == 2,
            "preferred gem block did not double the large-cauldron royal steel output"
        );
        fallingAnvil.discard();
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x26x11")
    @TestHolder(description = """
        Condenser towers expose four output faces, store 64 buckets, \
        and stop at four productive layers""")
    static void condenserTowerLayersAndOutputs(ExtendedGameTestHelper helper) {
        BlockPos cauldronBase = new BlockPos(5, 1, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, cauldronBase);
        BlockPos cauldronMain = helper.absolutePos(cauldronBase).above();

        List<CondenserTowerBlockEntity> placed = new ArrayList<>();
        BlockPos towerBase = helper.absolutePos(cauldronBase).above(3);
        for (int i = 0; i < 6; i++) {
            placed.add(placeTower(helper, towerBase.above(i * 3)));
        }

        List<CondenserTowerBlockEntity> productive = CondenserTowerProcess.findProductiveTowers(
            helper.getLevel(),
            cauldronMain
        );
        check(productive.size() == 5, "expected five productive tower layers, got " + productive.size());
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
                new VaporizationContext((ServerLevel) helper.getLevel(), cauldron)
            ) == 250,
            "tower did not simulate accepting an addon-defined vapor");
        check(first.getGasAmount() == 0, "simulating vapor input changed the tower gas buffer");
        check(first.getFluidHandler().getTankCapacity(0) == TOWER_CAPACITY,
            "tower tank capacity is not 64 buckets");
        check(first.collect(new FluidStack(Fluids.WATER, 1_000)) == 1_000,
            "tower tank did not accept its internal collection API");

        BlockPos bottomNorth = towerBase.offset(0, 0, -1);
        BlockState bottomNorthState = helper.getLevel().getBlockState(bottomNorth);
        check(CondenserTowerBlockEntity.getMain(helper.getLevel(), bottomNorth, bottomNorthState) == first,
            "a non-center tower part did not resolve the main storage block entity");
        IFluidHandler outward = CondenserTowerBlockEntity.capability(
            helper.getLevel(), bottomNorth, bottomNorthState, null, Direction.NORTH
        );
        IFluidHandler inward = CondenserTowerBlockEntity.capability(
            helper.getLevel(), bottomNorth, bottomNorthState, null, Direction.SOUTH
        );
        BlockPos oldTopNorth = towerBase.offset(0, 2, -1);
        BlockState oldTopNorthState = helper.getLevel().getBlockState(oldTopNorth);
        IFluidHandler oldTop = CondenserTowerBlockEntity.capability(
            helper.getLevel(), oldTopNorth, oldTopNorthState, null, Direction.NORTH
        );
        check(outward != null, "bottom north interface did not expose an outward fluid handler");
        check(inward == null, "bottom north interface exposed an inward fluid handler");
        check(oldTop == null, "old top north interface still exposed a fluid handler");
        check(outward.fill(
                new FluidStack(Fluids.WATER, 1),
                IFluidHandler.FluidAction.EXECUTE
            ) == 0,
            "tower output interface accepted fluid input");
        check(outward.drain(250, IFluidHandler.FluidAction.EXECUTE).getAmount() == 250,
            "tower output interface could not drain stored fluid");
        var updateTag = first.getUpdateTag(helper.getLevel().registryAccess());
        CondenserTowerBlockEntity restored = new CondenserTowerBlockEntity(
            ModBlockEntities.CONDENSER_TOWER.get(),
            first.getBlockPos(),
            first.getBlockState()
        );
        restored.loadAdditional(updateTag, helper.getLevel().registryAccess());
        check(restored.getStoredFluid().is(Fluids.WATER)
                && restored.getStoredFluid().getAmount() == 750,
            "tower client update tag did not preserve the displayed storage amount");

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
        check(helper.getLevel().getRecipeManager()
                .byKey(AnvilcraftPlasticraft.of("condenser/gaseous_experience_to_experience_fluid")).isPresent(),
            "gaseous-experience condensation recipe was not loaded");

        setJet(helper.getLevel(), cauldronMain.below(2));
        check(first.collectGas(CondenserGas.GASEOUS_WATER, 250) == 250,
            "tower did not accept gaseous water");
        check(first.getFluidHandler().fill(
                new FluidStack(Fluids.WATER, 250),
                IFluidHandler.FluidAction.SIMULATE
            ) == 250,
            "tower tank could not simulate adding water before condensation");
        CondenserTowerProcess.tickLargeCauldron(
            (ServerLevel) helper.getLevel(),
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
            (ServerLevel) helper.getLevel(),
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
            (ServerLevel) helper.getLevel(),
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
    @TestHolder(description = "Plasma jets omit gem melting while retaining super-heating and vanilla recipes")
    static void plasmaRecipesIncludeSuperHeatingAndVanilla(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));

        for (String gem : List.of("ruby", "sapphire", "topaz", "emerald")) {
            check(helper.getLevel().getRecipeManager().byKey(
                AnvilcraftPlasticraft.of("plasma_jet_blasting/" + gem + "_to_molten_gem")
            ).isEmpty(), "removed " + gem + " plasma recipe is still loaded");
        }

        cauldron.getInputHandler().insertItem(
            0,
            ModItems.WOOD_FIBER.asStack(2),
            false
        );
        CondenserTowerProcess.tickLargeCauldron(
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        check(outputCount(cauldron, Items.CHARCOAL) == 1,
            "an AnvilCraft super-heating recipe was not processed continuously");

        cauldron.getInputHandler().insertItem(0, Items.CACTUS.getDefaultInstance(), false);
        CondenserTowerProcess.tickLargeCauldron(
            (ServerLevel) helper.getLevel(),
            cauldron
        );
        int greenDye = outputCount(cauldron, Items.GREEN_DYE);
        check(greenDye == 2,
            "a boosted vanilla smelting recipe compatible with super heating produced " + greenDye + " instead of 2");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Open experience vapor gives players twice the liquid yield without spawning orbs")
    static void openExperienceVaporFeedsPlayerWithoutOrbs(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));
        cauldron.getFluids().fill(
            new FluidStack(ModFluids.EXP_FLUID.get(), 20),
            IFluidHandler.FluidAction.EXECUTE
        );
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        Player player = addMockPlayer(helper, context.outletPos());

        for (int tick = 0; tick < 2; tick++) {
            CondenserTowerProcess.tickLargeCauldron(
                (ServerLevel) helper.getLevel(),
                cauldron
            );
            check(cauldron.getTopFluid().getAmount() == 20 - (tick + 1) * 5,
                "experience fluid was not vaporized at 5 mB per tick");
        }
        check(player.totalExperience == 1,
            "10 mB of gaseous experience did not grant exactly one player XP");
        check(helper.getLevel().getEntitiesOfClass(
            ExperienceOrb.class,
            new AABB(cauldron.getBlockPos()).inflate(4.0D)
        ).isEmpty(), "gaseous-experience vaporization spawned a world experience orb");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Three entities each receive thirty-three millibuckets from a one-hundred-mB release")
    static void experienceVaporRoundsEqualShares(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        Player first = addMockPlayer(helper, context.outletPos());
        Player second = addMockPlayer(helper, context.outletPos());
        Player third = addMockPlayer(helper, context.outletPos());

        for (int release = 0; release < 3; release++) {
            CondenserTowerProcess.absorbEscapingExperienceVapor(context, 100);
        }
        check(first.totalExperience == 9 && second.totalExperience == 9 && third.totalExperience == 9,
            "three 100 mB releases were not rounded to 33 mB per entity and release");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x16x11")
    @TestHolder(description = "A condenser prioritizes both buffers before exposing overflow near its top opening")
    static void experienceCondenserPrioritizesBothBuffers(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        CondenserTowerBlockEntity tower = placeTower(helper, helper.absolutePos(base).above(3));

        check(tower.collectGas(CondenserGas.GASEOUS_EXPERIENCE, 250) == 250,
            "tower did not accept five 50 mB gaseous-experience condensation batches");
        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(tower.getStoredFluid().is(ModFluids.EXP_FLUID.get())
                && tower.getStoredFluid().getAmount() == 250
                && tower.getGasAmount() == 0,
            "the tower did not condense 250 mB of gaseous experience at 1:1 efficiency");
        check(tower.collect(new FluidStack(ModFluids.EXP_FLUID.get(), TOWER_CAPACITY - 250))
                == TOWER_CAPACITY - 250,
            "failed to fill the experience-fluid buffer");
        check(tower.collectGas(CondenserGas.GASEOUS_EXPERIENCE, TOWER_CAPACITY - 5)
                == TOWER_CAPACITY - 5,
            "failed to prepare the gaseous-experience buffer");

        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        Player lowerPlayer = addMockPlayer(helper, context.outletPos());
        Player topPlayer = addMockPlayer(helper, tower.getBlockPos().above(5).east(3));
        setJet(helper.getLevel(), cauldron.getBlockPos().below(2));
        cauldron.getFluids().fill(
            new FluidStack(ModFluids.EXP_FLUID.get(), 10),
            IFluidHandler.FluidAction.EXECUTE
        );

        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        check(tower.getGasAmount() == TOWER_CAPACITY && topPlayer.totalExperience == 0,
            "vapor escaped before the condenser gas buffer became full");
        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);
        cauldron.getFluids().fill(
            new FluidStack(ModFluids.EXP_FLUID.get(), 5),
            IFluidHandler.FluidAction.EXECUTE
        );
        CondenserTowerProcess.tickLargeCauldron((ServerLevel) helper.getLevel(), cauldron);

        check(topPlayer.totalExperience == 1,
            "10 mB of overflow was not absorbed three blocks horizontally and above the top opening");
        check(lowerPlayer.totalExperience == 0,
            "a player at the covered cauldron opening absorbed vapor through the condenser");
        check(tower.getStoredFluid().getAmount() == TOWER_CAPACITY
                && tower.getGasAmount() == TOWER_CAPACITY,
            "overflow changed one of the condenser's full 64 B buffers");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "An employed villager reaches master after exactly sixty-four buckets of vapor")
    static void villagerMastersAfterSixtyFourBucketsOfVapor(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        Villager villager = new Villager(EntityType.VILLAGER, helper.getLevel());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER));
        villager.moveTo(context.outletPos().getCenter());
        check(helper.getLevel().addFreshEntity(villager), "failed to add the experience-absorbing villager");

        CondenserTowerProcess.absorbEscapingExperienceVapor(context, 63_999);
        check(villager.getVillagerData().getLevel() == 4,
            "the villager reached master before absorbing a full 64 B");
        CondenserTowerProcess.absorbEscapingExperienceVapor(context, 1);
        check(villager.getVillagerData().getLevel() == 5 && villager.getVillagerXp() == 250,
            "the villager did not reach master at exactly 64 B of gaseous experience");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Escaping water vapor extinguishes fire throughout its outlet range")
    static void escapingWaterVaporExtinguishesOutletRange(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        BlockPos outlet = context.outletPos();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                helper.getLevel().setBlock(outlet.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        BlockPos firePos = outlet.east(3).above(3);
        helper.getLevel().setBlock(firePos.below(), Blocks.NETHERRACK.defaultBlockState(), Block.UPDATE_ALL);
        helper.getLevel().setBlock(firePos, Blocks.FIRE.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos campfirePos = outlet.west(3);
        helper.getLevel().setBlock(campfirePos, Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_ALL);
        Player burningPlayer = addMockPlayer(helper, outlet.above(2));
        burningPlayer.setRemainingFireTicks(100);
        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_OIL, 10),
            new FluidStack(ModFluids.OIL.get(), 1)
        );
        check(EscapingVaporEffects.igniteOilVapor((ServerLevel) helper.getLevel(), outlet.getCenter()),
            "failed to prepare an ignited gaseous-oil cloud for water-vapor extinguishing");

        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_WATER, 10),
            new FluidStack(Fluids.WATER, 1)
        );

        check(helper.getLevel().getBlockState(firePos).isAir(),
            "water vapor did not extinguish fire at the horizontal and vertical range boundary");
        check(!helper.getLevel().getBlockState(campfirePos).getValue(CampfireBlock.LIT),
            "water vapor did not extinguish a lit campfire in range");
        check(!burningPlayer.isOnFire(), "water vapor did not extinguish a burning entity in range");
        check(!EscapingVaporEffects.isOilVaporIgnited((ServerLevel) helper.getLevel(), cauldron.getBlockPos()),
            "water vapor did not extinguish an ignited gaseous-oil cloud in range");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "Flint and steel ignites an active gaseous-oil outlet")
    static void flintAndSteelIgnitesGaseousOilOutlet(ExtendedGameTestHelper helper) {
        BlockPos base = new BlockPos(5, 2, 5);
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, base);
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_OIL, 10),
            new FluidStack(ModFluids.OIL.get(), 1)
        );

        Player player = addMockPlayer(helper, context.outletPos());
        ItemStack flintAndSteel = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flintAndSteel);
        BlockPos outlet = context.outletPos();
        BlockHitResult hit = new BlockHitResult(outlet.getBottomCenter(), Direction.UP, outlet.below(), false);
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
            player,
            InteractionHand.MAIN_HAND,
            outlet.below(),
            hit
        );
        NeoForge.EVENT_BUS.post(event);

        check(event.isCanceled(), "gaseous-oil ignition did not consume the right-click interaction");
        check(EscapingVaporEffects.isOilVaporIgnited((ServerLevel) helper.getLevel(), cauldron.getBlockPos()),
            "flint and steel did not ignite the active gaseous-oil outlet");
        check(flintAndSteel.getCount() == 1 && flintAndSteel.getDamageValue() == 1,
            "gaseous-oil ignition damaged or consumed flint and steel incorrectly");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "A fire charge ignites an active gaseous-oil outlet and is consumed")
    static void fireChargeIgnitesGaseousOilOutlet(ExtendedGameTestHelper helper) {
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(5, 2, 5));
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_OIL, 10),
            new FluidStack(ModFluids.OIL.get(), 1)
        );

        Player player = addMockPlayer(helper, context.outletPos());
        ItemStack fireCharge = new ItemStack(Items.FIRE_CHARGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, fireCharge);
        BlockPos outlet = context.outletPos();
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
            player,
            InteractionHand.MAIN_HAND,
            outlet.below(),
            new BlockHitResult(outlet.getBottomCenter(), Direction.UP, outlet.below(), false)
        );
        NeoForge.EVENT_BUS.post(event);

        check(event.isCanceled(), "gaseous-oil ignition did not consume the fire-charge interaction");
        check(EscapingVaporEffects.isOilVaporIgnited((ServerLevel) helper.getLevel(), cauldron.getBlockPos()),
            "a fire charge did not ignite the active gaseous-oil outlet");
        check(fireCharge.isEmpty(), "gaseous-oil ignition did not consume one fire charge");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "A thrown torch ignites gaseous oil and is consumed")
    static void thrownTorchIgnitesGaseousOilOutlet(ExtendedGameTestHelper helper) {
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(5, 2, 5));
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_OIL, 10),
            new FluidStack(ModFluids.OIL.get(), 1)
        );
        Vec3 outlet = context.outletPos().getCenter();
        ItemEntity torch = new ItemEntity(helper.getLevel(), outlet.x, outlet.y, outlet.z, new ItemStack(Items.TORCH));
        torch.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(torch), "failed to add the thrown torch");

        helper.runAfterDelay(1, () -> {
            check(EscapingVaporEffects.isOilVaporIgnited((ServerLevel) helper.getLevel(), cauldron.getBlockPos()),
                "a thrown torch did not ignite the active gaseous-oil outlet");
            check(!torch.isAlive() || torch.getItem().isEmpty(),
                "the torch remained after igniting gaseous oil");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x12x11")
    @TestHolder(description = "A thrown high-temperature block ignites gaseous oil without being consumed")
    static void thrownHotBlockIgnitesGaseousOilOutlet(ExtendedGameTestHelper helper) {
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(5, 2, 5));
        VaporizationContext context = new VaporizationContext((ServerLevel) helper.getLevel(), cauldron);
        CondenserTowerProcess.releaseEscapingVapor(
            context,
            new VaporStack(CondenserGas.GASEOUS_OIL, 10),
            new FluidStack(ModFluids.OIL.get(), 1)
        );
        Vec3 outlet = context.outletPos().getCenter();
        ItemStack hotBlock = REDHOT_NETHERITE_BLOCK.asStack();
        ItemEntity item = new ItemEntity(helper.getLevel(), outlet.x, outlet.y, outlet.z, hotBlock);
        item.setNoGravity(true);
        check(helper.getLevel().addFreshEntity(item), "failed to add the thrown high-temperature block");

        helper.runAfterDelay(1, () -> {
            check(EscapingVaporEffects.isOilVaporIgnited((ServerLevel) helper.getLevel(), cauldron.getBlockPos()),
                "a thrown high-temperature block did not ignite the active gaseous-oil outlet");
            check(item.isAlive() && item.getItem().getCount() == 1,
                "the reusable high-temperature block was consumed by gaseous-oil ignition");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x7x7")
    @TestHolder(description = "Flint and steel ignites an oil-filled hardened resin cauldron instead of entering it")
    static void flintAndSteelIgnitesHardenedResinCauldron(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
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
    @TestHolder(description = "A tank-fed enhanced jet consumes five millibuckets of high-heat fuel per tick")
    static void enhancedJetConsumesContinuousFuel(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos occupied = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos jetPos = occupied.above();
        level.setBlock(
            occupied.below(),
            HEATER.getDefaultState()
                .setValue(HeaterBlock.OVERLOAD, false),
            Block.UPDATE_ALL
        );
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(jetPos.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(
            jetPos.above(),
            LARGE_CAULDRON.getDefaultState()
                .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER),
            Block.UPDATE_ALL
        );
        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(
            new FluidStack(HIGH_HEAT_FUEL.get(), 1_000),
            IFluidHandler.FluidAction.EXECUTE
        );
        pot.getInput().insertItem(0, Items.BLAZE_POWDER.getDefaultInstance(), false);
        check(level.addFreshEntity(pot), "failed to add high-heat-fuel cauldron");
        for (int i = 0; i < 10; i++) pot.tick();
        check(level.getBlockEntity(jetPos) instanceof PlasmaJetsBlockEntity,
            "high-heat fuel did not create a plasma jet");
        PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) level.getBlockEntity(jetPos);
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(jet instanceof EnhancedPlasmaJetExtension extension && extension.plasticraft$isEnhanced(),
            "high-heat fuel did not mark the plasma jet as enhanced");
        check(pot.getFluidHandler().getFluidAmount() == 990,
            "enhanced jet did not consume exactly 10 mB of tank fuel in one tick");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x16x11")
    @TestHolder(description = "A layered high-heat cauldron consumes four 250 mB layers over two hundred ticks")
    static void layeredHighHeatFuelRunsForTenSeconds(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos cauldronPos = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos jetPos = cauldronPos.above();
        level.setBlock(
            cauldronPos.below(),
            HEATER.getDefaultState()
                .setValue(HeaterBlock.OVERLOAD, false),
            Block.UPDATE_ALL
        );
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(jetPos.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(
            jetPos.above(),
            LARGE_CAULDRON.getDefaultState()
                .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER),
            Block.UPDATE_ALL
        );
        level.setBlock(
            cauldronPos,
            ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get().fullFilled()
                .setValue(HighHeatFuelCauldronBlock.IGNITED, true),
            Block.UPDATE_ALL
        );
        check(PlasmaJetsBlock.trySpawn(jetPos, level),
            "full layered high-heat fuel did not create a plasma jet");
        PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) level.getBlockEntity(jetPos);
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(jet instanceof EnhancedPlasmaJetExtension extension
                && extension.plasticraft$isEnhanced()
                && extension.plasticraft$usesLayeredFuel(),
            "layered high-heat fuel did not create the correct enhanced state");
        check(level.getBlockState(cauldronPos).getValue(Layered4LevelCauldronBlock.LEVEL) == 3,
            "layered high-heat fuel did not consume exactly one 250 mB layer on activation");
        for (int i = 0; i < 149; i++) {
            PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        }
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(HighHeatFuelCauldronBlock.isSpent(level, cauldronPos),
            "layered high-heat fuel did not consume its final 250 mB layer");
        for (int i = 0; i < 49; i++) {
            PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        }
        check(level.getBlockState(jetPos).is(PLASMA_JETS),
            "layered enhanced jet stopped before 200 ticks");
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(level.getBlockState(jetPos).isAir(), "layered enhanced jet survived beyond 200 ticks");
        check(level.getBlockState(cauldronPos).is(Blocks.CAULDRON),
            "spent layered high-heat cauldron did not return to an empty cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x16x11")
    @TestHolder(description = """
        A hardened resin cauldron maintains a jet while consuming one millibucket \
        of oil every twelve ticks""")
    static void hardenedResinCauldronCreatesJetBelowLargeCauldron(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos occupied = helper.absolutePos(new BlockPos(5, 3, 5));
        BlockPos jetPos = occupied.above();
        BlockPos heaterPos = occupied.below();
        level.setBlock(
            heaterPos,
            HEATER.getDefaultState()
                .setValue(HeaterBlock.OVERLOAD, false),
            Block.UPDATE_ALL
        );
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = jetPos.relative(direction);
            level.setBlock(side, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(
            jetPos.above(),
            LARGE_CAULDRON.getDefaultState()
                .setValue(LargeCauldronBlock.HALF, Cube3x3PartHalf.BOTTOM_CENTER),
            Block.UPDATE_ALL
        );

        HardenedResinCauldronEntity pot = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(5.5D, 3.0D, 5.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        pot.setNoGravity(true);
        pot.getFluidHandler().fill(new FluidStack(ModFluids.OIL.get(), 1_000), IFluidHandler.FluidAction.EXECUTE);
        pot.getInput().insertItem(0, Items.BLAZE_POWDER.getDefaultInstance(), false);
        check(level.addFreshEntity(pot), "failed to add hardened resin cauldron");
        for (int i = 0; i < 10; i++) pot.tick();
        check(pot.anvilcraft$isIgnited(), "fire starter did not ignite the hardened resin cauldron");
        check(level.getBlockState(jetPos).is(PLASMA_JETS),
            "hardened resin cauldron did not create a plasma jet");
        check(level.getBlockEntity(jetPos) instanceof PlasmaJetsBlockEntity, "plasma jet block entity was not created");

        PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) level.getBlockEntity(jetPos);
        check(jet.getParticleEndPos().equals(jetPos.above().getBottomCenter()),
            "large cauldron changed the plasma jet particle endpoint");
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(level.getBlockState(jetPos).is(PLASMA_JETS),
            "the initial jet directly below a large cauldron was removed by wall integrity checks");
        check(pot.getFluidHandler().getFluidAmount() == 999,
            "jet activation did not consume exactly 1 mB of hardened-pot oil");
        for (int i = 0; i < 11; i++) {
            PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        }
        check(pot.getFluidHandler().getFluidAmount() == 999,
            "hardened-pot oil was consumed before twelve game ticks elapsed");
        PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
        check(pot.getFluidHandler().getFluidAmount() == 998,
            "hardened-pot jet did not consume 1 mB after twelve game ticks");
        helper.succeed();
    }

    private static LargeCauldronBlockEntity placeLargeCauldron(
        ExtendedGameTestHelper helper,
        BlockPos base
    ) {
        Level level = helper.getLevel();
        LargeCauldronBlock block =
            LARGE_CAULDRON.get();
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
            PLASMA_JETS.getDefaultState(),
            Block.UPDATE_ALL
        );
    }

    private static Player addMockPlayer(ExtendedGameTestHelper helper, BlockPos feetPos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(feetPos.getCenter().subtract(0.0D, 0.5D, 0.0D));
        check(helper.getLevel().addFreshEntity(player), "failed to add a vapor-absorbing mock player");
        return player;
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
                == CUT_BRASS_PILLAR.get(),
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
        check(predicate.getBlock() == PIPE_STRAIGHT.get(),
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
