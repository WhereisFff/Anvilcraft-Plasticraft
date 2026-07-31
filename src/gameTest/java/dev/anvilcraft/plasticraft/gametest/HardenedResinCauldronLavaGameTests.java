package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

public final class HardenedResinCauldronLavaGameTests {
    private HardenedResinCauldronLavaGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Executing a lava fill burns a hardened resin cauldron without simulation side effects")
    static void lavaFillBurnsCauldron(ExtendedGameTestHelper helper) {
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));
        FluidStack lava = new FluidStack(Fluids.LAVA, HardenedResinCauldronEntity.CAPACITY);

        int simulated = cauldron.getFluidHandler().fill(lava, IFluidHandler.FluidAction.SIMULATE);
        check(simulated == HardenedResinCauldronEntity.CAPACITY, "lava fill simulation returned the wrong amount");
        check(!cauldron.isRemoved(), "lava fill simulation burned the cauldron");
        check(cauldron.getFluidHandler().isEmpty(), "lava fill simulation changed the tank");

        int filled = cauldron.getFluidHandler().fill(lava, IFluidHandler.FluidAction.EXECUTE);
        check(filled == HardenedResinCauldronEntity.CAPACITY, "executed lava fill returned the wrong amount");
        check(cauldron.isRemoved(), "executed lava fill did not burn the cauldron");
        check(
            helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 2, 3))).isSource()
                && helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 2, 3))).getType() == Fluids.LAVA,
            "burned cauldron did not leave a lava source"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "World lava burns a hardened resin cauldron on contact")
    static void worldLavaBurnsCauldron(ExtendedGameTestHelper helper) {
        BlockPos lavaPos = new BlockPos(3, 2, 3);
        helper.setBlock(lavaPos, Blocks.LAVA);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(3.5D, 2.0D, 3.5D));

        cauldron.tick();

        check(cauldron.isRemoved(), "world lava did not burn the cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "9x6x7", floor = true)
    @TestHolder(description = "A fast hardened resin cauldron burns when its movement crosses world lava")
    static void crossingWorldLavaBurnsCauldron(ExtendedGameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.LAVA);
        HardenedResinCauldronEntity cauldron = createCauldron(helper, new Vec3(1.5D, 2.0D, 3.5D));
        cauldron.setDeltaMovement(4.0D, 0.0D, 0.0D);

        cauldron.tick();

        check(cauldron.isRemoved(), "cauldron crossed world lava without burning");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Lava replaces a bonded hardened resin cauldron with a source block")
    static void lavaFillBurnsBondedCauldron(ExtendedGameTestHelper helper) {
        BlockPos support = new BlockPos(3, 1, 3);
        BlockPos occupied = support.above();
        helper.setBlock(support, Blocks.STONE);
        BlockState bondedState = ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState()
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        helper.getLevel().setBlock(helper.absolutePos(occupied), bondedState, Block.UPDATE_ALL);
        check(
            helper.getBlockEntity(occupied) instanceof BondedEntityBlockEntity,
            "bonded cauldron block entity was not created"
        );
        BondedEntityBlockEntity bonded = (BondedEntityBlockEntity) helper.getBlockEntity(occupied);
        HardenedResinCauldronEntity stored = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            helper.getLevel(),
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        check(
            bonded.initialize(
                stored,
                stored.getDisplayState(),
                Direction.UP,
                PlasticEntityOrientation.DEFAULT,
                true
            ),
            "bonded cauldron could not be initialized"
        );

        int filled = bonded.getFluidHandler().fill(
            new FluidStack(Fluids.LAVA, HardenedResinCauldronEntity.CAPACITY),
            IFluidHandler.FluidAction.EXECUTE
        );
        check(filled == HardenedResinCauldronEntity.CAPACITY, "bonded cauldron rejected lava");
        bonded.tickFunctionalEntity();

        check(
            helper.getLevel().getFluidState(helper.absolutePos(occupied)).isSource()
                && helper.getLevel().getFluidState(helper.absolutePos(occupied)).getType() == Fluids.LAVA,
            "burned bonded cauldron did not become a lava source"
        );
        helper.succeed();
    }

    private static HardenedResinCauldronEntity createCauldron(
        ExtendedGameTestHelper helper,
        Vec3 relativePosition
    ) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(relativePosition),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
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
