package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

public final class HardenedResinCauldronSpillGameTests {
    private HardenedResinCauldronSpillGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "A sideways hardened resin cauldron discards milk without placing a world fluid")
    static void nonPlaceableFluidSpillsWithoutWorldBlock(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
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
        check(
            helper.getBlockState(new BlockPos(4, 2, 3)).isAir(),
            "non-placeable milk created a world block"
        );
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
