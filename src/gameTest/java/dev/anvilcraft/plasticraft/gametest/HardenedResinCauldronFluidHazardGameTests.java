package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** 覆盖硬化树脂锅的熔岩销毁、移动穿越和不可放置流体溢出契约。 */
public final class HardenedResinCauldronFluidHazardGameTests {
    private HardenedResinCauldronFluidHazardGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "17x7x7", floor = true)
    @TestHolder(description = "Hardened resin cauldrons handle lava fills, contact, movement, bonding, and spills")
    static void hardenedResinCauldronFluidHazards(ExtendedGameTestHelper helper) {
        assertExecutedLavaFillBurnsCauldron(helper, 2);
        assertWorldLavaBurnsCauldron(helper, 5);
        assertCrossingWorldLavaBurnsCauldron(helper, 8);
        assertLavaFillBurnsBondedCauldron(helper, 12);
        assertNonPlaceableFluidSpills(helper, 15);
        helper.succeed();
    }

    private static void assertExecutedLavaFillBurnsCauldron(ExtendedGameTestHelper helper, int x) {
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(x + 0.5D, 2.0D, 3.5D));
        FluidStack lava = new FluidStack(Fluids.LAVA, HardenedResinCauldronEntity.CAPACITY);

        int simulated = cauldron.getFluidHandler().fill(lava, IFluidHandler.FluidAction.SIMULATE);
        check(simulated == HardenedResinCauldronEntity.CAPACITY, "lava fill simulation returned the wrong amount");
        check(!cauldron.isRemoved(), "lava fill simulation burned the cauldron");
        check(cauldron.getFluidHandler().isEmpty(), "lava fill simulation changed the tank");

        int filled = cauldron.getFluidHandler().fill(lava, IFluidHandler.FluidAction.EXECUTE);
        check(filled == HardenedResinCauldronEntity.CAPACITY, "executed lava fill returned the wrong amount");
        check(cauldron.isRemoved(), "executed lava fill did not burn the cauldron");
        BlockPos fluidPos = helper.absolutePos(new BlockPos(x, 2, 3));
        check(helper.getLevel().getFluidState(fluidPos).isSource()
                && helper.getLevel().getFluidState(fluidPos).getType() == Fluids.LAVA,
            "burned cauldron did not leave a lava source");
    }

    private static void assertWorldLavaBurnsCauldron(ExtendedGameTestHelper helper, int x) {
        helper.setBlock(new BlockPos(x, 2, 3), Blocks.LAVA);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(x + 0.5D, 2.0D, 3.5D));
        cauldron.tick();
        check(cauldron.isRemoved(), "world lava did not burn the cauldron");
    }

    private static void assertCrossingWorldLavaBurnsCauldron(ExtendedGameTestHelper helper, int x) {
        helper.setBlock(new BlockPos(x + 2, 2, 3), Blocks.LAVA);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(x - 1.5D, 2.0D, 3.5D));
        cauldron.setDeltaMovement(4.0D, 0.0D, 0.0D);
        cauldron.tick();
        check(cauldron.isRemoved(), "cauldron crossed world lava without burning");
    }

    private static void assertLavaFillBurnsBondedCauldron(ExtendedGameTestHelper helper, int x) {
        BlockPos support = new BlockPos(x, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        BlockState bondedState = PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.getLevel().setBlock(helper.absolutePos(occupied), bondedState, Block.UPDATE_ALL);
        check(helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity,
            "bonded cauldron block entity was not created");
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getBlockEntity(occupied);
        HardenedResinCauldronEntity stored = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(x + 0.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(bonded.initialize(
            stored,
            stored.getDisplayState(),
            Direction.UP,
            PlasticEntityOrientation.DEFAULT,
            true
        ), "bonded cauldron could not be initialized");

        int filled = bonded.getFluidHandler().fill(
            new FluidStack(Fluids.LAVA, HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );
        check(filled == HardenedResinCauldronEntity.CAPACITY, "bonded cauldron rejected lava");
        bonded.tickFunctionalEntity();

        BlockPos fluidPos = helper.absolutePos(occupied);
        check(helper.getLevel().getFluidState(fluidPos).isSource()
                && helper.getLevel().getFluidState(fluidPos).getType() == Fluids.LAVA,
            "burned bonded cauldron did not become a lava source");
    }

    private static void assertNonPlaceableFluidSpills(ExtendedGameTestHelper helper, int x) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(x + 0.5D, 2.0D, 3.5D)),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            new PlasticEntityOrientation(Direction.EAST, 0)
        );
        cauldron.setNoGravity(true);
        check(level.addFreshEntity(cauldron), "failed to add sideways hardened resin cauldron");
        int filled = cauldron.getFluidHandler().fill(
            new FluidStack(NeoForgeMod.MILK.get(), HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );
        check(filled == HardenedResinCauldronEntity.CAPACITY, "cauldron rejected milk");

        cauldron.tick();

        check(cauldron.getFluidHandler().isEmpty(), "non-placeable milk remained in the sideways cauldron");
        check(helper.getBlockState(new BlockPos(x + 1, 2, 3)).isAir(),
            "non-placeable milk created a world block");
    }

    private static HardenedResinCauldronEntity createCauldron(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(relativePosition),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            PlasticraftBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(level.addFreshEntity(cauldron), "failed to add hardened resin cauldron");
        return cauldron;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
