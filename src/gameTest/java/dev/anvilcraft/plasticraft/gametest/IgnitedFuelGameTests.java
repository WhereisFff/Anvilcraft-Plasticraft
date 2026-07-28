package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.api.blockentity.EnhancedPlasmaJetExtension;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.api.heat.HeaterManager;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity.TubeWallLayer;
import dev.dubhe.anvilcraft.block.entity.PlasmaJetsBlockEntity;
import dev.dubhe.anvilcraft.block.entity.heatable.HeatableBlockEntity;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;
import java.util.Set;

import static dev.dubhe.anvilcraft.init.block.ModBlocks.FIRE_CAULDRON;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FISH_TANK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.GLOWING_NETHERITE_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.INCANDESCENT_NETHERITE_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.PLASMA_JETS;

/** 高热燃料容器、点火交互和强化喷流的服务器端回归测试。 */
public final class IgnitedFuelGameTests {
    private static final float HIGH_HEAT_TARGET_HEALTH = 12.0F;

    private IgnitedFuelGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "An ignited layered high-heat-fuel cauldron deals eight damage")
    static void layeredHighHeatFuelCauldronDealsDoubleDamage(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockState state = ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get().fullFilled()
            .setValue(HighHeatFuelCauldronBlock.IGNITED, true);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(3.5D, 2.1D, 3.5D));
        target.setNoGravity(true);
        target.invulnerableTime = 0;

        ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get().entityInside(state, level, pos, target);

        checkHealth(target.getHealth(), HIGH_HEAT_TARGET_HEALTH, "layered high-heat-fuel cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "An ignited fish tank of high-heat fuel deals eight damage")
    static void fishTankHighHeatFuelDealsDoubleDamage(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlock(pos, FISH_TANK.getDefaultState(), Block.UPDATE_ALL);
        check(level.getBlockEntity(pos) instanceof FishTankBlockEntity, "fish tank block entity was not created");
        FishTankBlockEntity tank = (FishTankBlockEntity) level.getBlockEntity(pos);
        tank.getFluidHandler().fill(
            new FluidStack(ModFluids.HIGH_HEAT_FUEL.get(), tank.getFluidHandler().getCapacity()),
            IFluidHandler.FluidAction.EXECUTE
        );
        tank.setIgnited(true);
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(3.5D, 2.1D, 3.5D));
        target.setNoGravity(true);
        target.invulnerableTime = 0;

        tank.entityInsideFluidContent(level, pos, target);

        checkHealth(target.getHealth(), HIGH_HEAT_TARGET_HEALTH, "high-heat-fuel fish tank");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "An ignited Plasticraft cauldron of high-heat fuel deals eight damage")
    static void plasticCauldronHighHeatFuelDealsDoubleDamage(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            helper.absoluteVec(new Vec3(3.5D, 2.0D, 3.5D)),
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        cauldron.getFluidHandler().fill(
            new FluidStack(
                ModFluids.HIGH_HEAT_FUEL.get(),
                HardenedResinCauldronEntity.CAPACITY
            ),
            IFluidHandler.FluidAction.EXECUTE
        );
        check(level.addFreshEntity(cauldron), "failed to add Plasticraft cauldron");
        cauldron.anvilcraft$setIgnited(true);
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(3.5D, 2.3D, 3.5D));
        target.setNoGravity(true);
        target.invulnerableTime = 0;

        cauldron.tick();

        checkHealth(target.getHealth(), HIGH_HEAT_TARGET_HEALTH, "high-heat-fuel Plasticraft cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "An ignited large cauldron of high-heat fuel deals eight damage")
    static void largeCauldronHighHeatFuelDealsDoubleDamage(ExtendedGameTestHelper helper) {
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(5, 2, 5));
        cauldron.getFluids().setFluids(List.of(new FluidStack(
            ModFluids.HIGH_HEAT_FUEL.get(),
            LargeCauldronFluidHandler.TANK_CAPACITY
        )));
        cauldron.setIgnited(true);
        var target = helper.spawnWithNoFreeWill(EntityType.CREEPER, new Vec3(5.5D, 2.55D, 5.5D));
        target.setNoGravity(true);
        target.invulnerableTime = 0;

        LargeCauldronBlockEntity.serverTick(
            helper.getLevel(),
            cauldron.getBlockPos(),
            cauldron.getBlockState(),
            cauldron
        );

        checkHealth(target.getHealth(), HIGH_HEAT_TARGET_HEALTH, "large high-heat-fuel cauldron");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "Flint and steel and fire charges ignite a large cauldron from the hand")
    static void handIgnitersLightLargeCauldron(ExtendedGameTestHelper helper) {
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(5, 2, 5));
        cauldron.getFluids().setFluids(List.of(new FluidStack(
            ModFluids.HIGH_HEAT_FUEL.get(),
            1_000
        )));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos clickedPos = cauldron.getBlockPos();
        BlockState clickedState = helper.getLevel().getBlockState(clickedPos);
        BlockHitResult hit = new BlockHitResult(clickedPos.getCenter(), Direction.UP, clickedPos, false);

        ItemStack flintAndSteel = new ItemStack(Items.FLINT_AND_STEEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, flintAndSteel);
        ItemInteractionResult flintResult = clickedState.useItemOn(
            flintAndSteel,
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        check(flintResult.consumesAction(), "large cauldron did not handle flint and steel");
        check(cauldron.isIgnited(), "flint and steel did not ignite the large cauldron");
        check(flintAndSteel.getCount() == 1 && flintAndSteel.getDamageValue() == 1,
            "large-cauldron ignition damaged flint and steel incorrectly");

        cauldron.setIgnited(false);
        ItemStack fireCharge = new ItemStack(Items.FIRE_CHARGE);
        player.setItemInHand(InteractionHand.MAIN_HAND, fireCharge);
        ItemInteractionResult chargeResult = clickedState.useItemOn(
            fireCharge,
            helper.getLevel(),
            player,
            InteractionHand.MAIN_HAND,
            hit
        );
        check(chargeResult.consumesAction(), "large cauldron did not handle a fire charge");
        check(cauldron.isIgnited(), "fire charge did not ignite the large cauldron");
        check(fireCharge.isEmpty(), "large-cauldron ignition consumed the wrong number of fire charges");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 30)
    @EmptyTemplate("11x10x11")
    @TestHolder(description = "An enhanced plasma jet deals thirty-two damage")
    static void enhancedPlasmaJetDealsDoubleDamage(ExtendedGameTestHelper helper) {
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
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(player.getAttribute(Attributes.MAX_HEALTH) != null, "mock player has no maximum-health attribute");
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
        player.setHealth(100.0F);
        player.setNoGravity(true);
        player.moveTo(helper.absoluteVec(new Vec3(1.5D, 3.0D, 1.5D)));
        check(level.addFreshEntity(player), "failed to add plasma-jet damage target");

        int delay = 10 - (int) Math.floorMod(level.getGameTime(), 10L);
        helper.runAfterDelay(delay, () -> {
            check(Math.floorMod(level.getGameTime(), 10L) == 0L, "damage check did not run on a plasma-jet damage tick");
            level.setBlock(
                jetPos,
                PLASMA_JETS.getDefaultState(),
                Block.UPDATE_ALL
            );
            check(level.getBlockEntity(jetPos) instanceof PlasmaJetsBlockEntity,
                "plasma jet block entity was not created");
            PlasmaJetsBlockEntity jet = (PlasmaJetsBlockEntity) level.getBlockEntity(jetPos);
            EnhancedPlasmaJetExtension extension = (EnhancedPlasmaJetExtension) jet;
            extension.plasticraft$setEnhanced(true);
            extension.plasticraft$setUsesLayeredFuel(true);
            player.moveTo(jetPos.getBottomCenter());
            player.clearFire();
            player.invulnerableTime = 0;
            player.setHealth(100.0F);
            PlasmaJetsBlockEntity.tick(level, jetPos, level.getBlockState(jetPos), jet);
            checkHealth(player.getHealth(), 68.0F, "enhanced plasma jet");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("11x8x11")
    @TestHolder(description = "Enhanced plasma jets heat blocks to incandescent while ordinary jets stop at glowing")
    static void enhancedPlasmaJetReachesIncandescentTemperature(ExtendedGameTestHelper helper) {
        Level level = helper.getLevel();
        HeatingJet ordinary = placeHeatingJet(
            helper,
            new BlockPos(3, 2, 5),
            FIRE_CAULDRON.get().fullFilled()
        );
        HeatingJet enhanced = placeHeatingJet(
            helper,
            new BlockPos(7, 2, 5),
            ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get().fullFilled()
                .setValue(HighHeatFuelCauldronBlock.IGNITED, true)
        );

        PlasmaJetsBlockEntity.tick(
            level,
            ordinary.entity().getBlockPos(),
            ordinary.entity().getBlockState(),
            ordinary.entity()
        );
        PlasmaJetsBlockEntity.tick(
            level,
            enhanced.entity().getBlockPos(),
            enhanced.entity().getBlockState(),
            enhanced.entity()
        );
        HeaterManager.getInstance(level).tick();

        check(
            level.getBlockState(ordinary.heatablePos())
                .is(GLOWING_NETHERITE_BLOCK.get()),
            "ordinary plasma jet heated a heatable block beyond glowing"
        );
        check(
            level.getBlockState(enhanced.heatablePos())
                .is(INCANDESCENT_NETHERITE_BLOCK.get()),
            "enhanced plasma jet did not heat a heatable block to incandescent"
        );
        check(
            level.getBlockEntity(enhanced.heatablePos()) instanceof HeatableBlockEntity heatable
                && heatable.getDuration() == 2,
            "enhanced plasma jet stacked its ordinary and enhanced heating rates"
        );
        helper.succeed();
    }

    private static HeatingJet placeHeatingJet(
        ExtendedGameTestHelper helper,
        BlockPos relativeCauldronPos,
        BlockState cauldronState
    ) {
        Level level = helper.getLevel();
        BlockPos cauldronPos = helper.absolutePos(relativeCauldronPos);
        BlockPos tubeCenter = cauldronPos.above();
        BlockPos jetPos = tubeCenter.above();
        level.setBlock(
            cauldronPos.below(),
            HEATER.getDefaultState()
                .setValue(HeaterBlock.OVERLOAD, false),
            Block.UPDATE_ALL
        );
        level.setBlock(cauldronPos, cauldronState, Block.UPDATE_ALL);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(tubeCenter.relative(direction), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos heatablePos = tubeCenter.north();
        level.setBlock(heatablePos, Blocks.NETHERITE_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        BlockState jetState = PLASMA_JETS.getDefaultState();
        level.setBlock(jetPos, jetState, Block.UPDATE_ALL);
        PlasmaJetsBlockEntity jet = new PlasmaJetsBlockEntity(
            jetPos,
            jetState,
            100,
            Set.of(TubeWallLayer.of(tubeCenter))
        );
        level.setBlockEntity(jet);
        return new HeatingJet(jet, heatablePos);
    }

    private record HeatingJet(PlasmaJetsBlockEntity entity, BlockPos heatablePos) {
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
        check(level.getBlockEntity(base.above()) instanceof LargeCauldronBlockEntity,
            "large cauldron main block entity was not created");
        return (LargeCauldronBlockEntity) level.getBlockEntity(base.above());
    }

    private static void checkHealth(float actual, float expected, String source) {
        check(Math.abs(actual - expected) < 0.0001F,
            source + " left target at " + actual + " health instead of " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
