package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.entity.PlasmaExperienceOrbExtension;
import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.CondenserGas;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.init.block.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.ArrayList;
import java.util.List;

/** 冷凝塔、喷流气化和硬化树脂锅的服务器端回归测试。 */
public final class CondenserTowerGameTests {
    private static final int TOWER_CAPACITY = 64 * FluidType.BUCKET_VOLUME;

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
        BlockPos jetCenter = main.below(2);
        setJet(helper.getLevel(), jetCenter);
        setJet(helper.getLevel(), jetCenter.west());
        setJet(helper.getLevel(), jetCenter.east());

        CondenserTowerProcess.tickLargeCauldron((net.minecraft.server.level.ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getFluids().getFluidInTank(0).getAmount() == 1_000,
            "a non-oil top layer was incorrectly vaporized");

        cauldron.getFluids().setFluids(List.of(oil));
        CondenserTowerProcess.tickLargeCauldron((net.minecraft.server.level.ServerLevel) helper.getLevel(), cauldron);
        check(cauldron.getTopFluid().getAmount() == 850,
            "three jets did not vaporize 150 mB from the top oil layer");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x26x11")
    @TestHolder(description = "Condenser towers expose four output faces, store 64 buckets, "
        + "and stop at four productive layers")
    static void condenserTowerLayersAndOutputs(ExtendedGameTestHelper helper) {
        BlockPos cauldronBase = new BlockPos(5, 1, 5);
        placeLargeCauldron(helper, cauldronBase);
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

        check(helper.getLevel().getRecipeManager()
                .byKey(AnvilcraftPlasticraft.of("multiblock/condenser_tower")).isPresent(),
            "condenser tower multiblock recipe was not loaded");
        check(helper.getLevel().getRecipeManager()
                .byKey(AnvilcraftPlasticraft.of("multiblock_conversion/condenser_tower")).isPresent(),
            "condenser tower conversion recipe was not loaded");
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
        check(jet.getParticleEndPos().equals(jetPos.above(2).getBottomCenter()),
            "large cauldron shortened the plasma jet particle endpoint by one block");
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
