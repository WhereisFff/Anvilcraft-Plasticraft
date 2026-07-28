package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;

import static dev.dubhe.anvilcraft.init.block.ModBlocks.FISH_TANK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.LARGE_CAULDRON;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.OVERHEATED_EMBER_METAL_BLOCK;

/** 塑料油环境催化的服务端回归测试。 */
public final class PlasticOilCatalysisGameTests {
    private static final int BUCKET = 1_000;
    private static final int LARGE_BATCH = 64 * BUCKET;
    private static final double EPSILON = 1.0E-9D;

    private PlasticOilCatalysisGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("3x3x3")
    @TestHolder(description = "Open royal-steel catalysis uses the bounded logarithmic speed curve")
    static void catalystSpeedCurve(ExtendedGameTestHelper helper) {
        check(close(PlasticOilCatalysis.catalystMultiplier(0), 0.0D), "zero catalysts had a reaction speed");
        check(close(PlasticOilCatalysis.catalystMultiplier(1), 0.25D), "one catalyst was not quarter speed");
        check(close(PlasticOilCatalysis.catalystMultiplier(8), 0.5D), "eight catalysts were not half speed");
        check(
            close(PlasticOilCatalysis.catalystMultiplier(Integer.MAX_VALUE), 0.55D),
            "the open catalyst curve did not stop at 0.55"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x5x7")
    @TestHolder(description = "A Large Cauldron averages all nine actual heat-source outputs")
    static void largeCauldronAveragesMixedHeat(ExtendedGameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos main = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockPos heatCenter = main.below(2);
        level.setBlock(heatCenter, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(heatCenter.east(), Blocks.LAVA.defaultBlockState(), Block.UPDATE_ALL);

        check(
            close(PlasticOilCatalysis.largeCauldronHeat(level, main), 6.0D / 9.0D),
            "mixed heat sources were not summed before division by nine"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 12)
    @EmptyTemplate(value = "7x6x7", floor = true)
    @TestHolder(description = "Loose royal steel catalyzes layered and world-source plastic oil without consumption")
    static void looseCatalystsConvertCauldronAndWorldSource(ExtendedGameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos cauldronPos = helper.absolutePos(new BlockPos(2, 2, 3));
        BlockPos sourcePos = helper.absolutePos(new BlockPos(4, 2, 3));
        setHighHeat(level, cauldronPos.below());
        setHighHeat(level, sourcePos.below());
        level.setBlock(cauldronPos, ModBlocks.PLASTIC_OIL_CAULDRON.get().fullFilled(), Block.UPDATE_ALL);
        level.setBlock(sourcePos, ModBlocks.PLASTIC_OIL.get().defaultBlockState(), Block.UPDATE_ALL);

        ItemEntity cauldronCatalyst = spawnCatalyst(level, cauldronPos.getCenter());
        ItemEntity sourceCatalyst = spawnCatalyst(level, sourcePos.getCenter());
        helper.runAfterDelay(8, () -> {
            check(
                level.getBlockState(cauldronPos).is(ModBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get()),
                "the layered cauldron did not convert"
            );
            check(
                level.getFluidState(sourcePos).isSourceOfType(ModFluids.UNIVERSAL_PLASTIC_MELT.get()),
                "the world plastic-oil source did not convert"
            );
            check(cauldronCatalyst.isAlive() && sourceCatalyst.isAlive(), "a loose catalyst was consumed");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 12)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "A Fish Tank converts all plastic oil while retaining its royal-steel catalyst")
    static void fishTankCatalysis(ExtendedGameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setHighHeat(level, pos.below());
        level.setBlock(
            pos,
            FISH_TANK.getDefaultState(),
            Block.UPDATE_ALL
        );
        check(level.getBlockEntity(pos) instanceof FishTankBlockEntity, "the fish tank was not created");
        FishTankBlockEntity tank = (FishTankBlockEntity) level.getBlockEntity(pos);
        check(
            tank.getFluidHandler().fill(plasticOil(BUCKET), IFluidHandler.FluidAction.EXECUTE) == BUCKET,
            "the fish tank rejected plastic oil"
        );
        ItemStack catalyst = royalSteelIngot();
        check(
            tank.getInputHandler().insertItem(0, catalyst, false).isEmpty(),
            "the fish tank rejected its catalyst"
        );

        helper.runAfterDelay(4, () -> {
            assertMelt(tank.getFluidHandler().getFluid(), BUCKET, "fish tank");
            check(count(tank.getInputHandler(), catalyst.getItem()) == 1, "the fish tank consumed its catalyst");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 12)
    @EmptyTemplate("9x8x9")
    @TestHolder(description = "Nine heat sources let a Large Cauldron catalyze a full sixty-four-bucket layer")
    static void largeCauldronCatalysis(ExtendedGameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        LargeCauldronBlockEntity cauldron = placeLargeCauldron(helper, new BlockPos(4, 3, 4));
        BlockPos heatCenter = cauldron.getBlockPos().below(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) setHighHeat(level, heatCenter.offset(x, 0, z));
        }
        cauldron.getFluids().setFluids(List.of(plasticOil(LARGE_BATCH)));
        ItemStack catalyst = royalSteelIngot();
        check(
            cauldron.getInputHandler().insertItem(0, catalyst, false).isEmpty(),
            "the Large Cauldron rejected its catalyst"
        );

        helper.runAfterDelay(4, () -> {
            assertMelt(cauldron.getTopFluid(), LARGE_BATCH, "Large Cauldron");
            check(count(cauldron.getInputHandler(), catalyst.getItem()) == 1, "the Large Cauldron consumed its catalyst");
            helper.succeed();
        });
    }

    @GameTest(timeoutTicks = 12)
    @EmptyTemplate(value = "5x6x5", floor = true)
    @TestHolder(description = "An upward Hardened Resin Cauldron supports open plastic-oil catalysis")
    static void resinCauldronCatalysis(ExtendedGameTestHelper helper) {
        ServerLevel level = (ServerLevel) helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        setHighHeat(level, pos.below());
        HardenedResinCauldronEntity cauldron = spawnResinCauldron(level, pos);
        check(
            cauldron.getFluidHandler().fill(plasticOil(BUCKET), IFluidHandler.FluidAction.EXECUTE) == BUCKET,
            "the resin cauldron rejected plastic oil"
        );
        ItemStack catalyst = royalSteelIngot();
        check(
            cauldron.getItemHandler().insertItem(8, catalyst, false).isEmpty(),
            "the resin cauldron rejected its catalyst"
        );

        helper.runAfterDelay(4, () -> {
            assertMelt(cauldron.getFluidHandler().getFluid(), BUCKET, "resin cauldron");
            check(count(cauldron.getItemHandler(), catalyst.getItem()) == 1, "the resin cauldron consumed its catalyst");
            helper.succeed();
        });
    }

    private static ItemEntity spawnCatalyst(ServerLevel level, Vec3 position) {
        ItemEntity item = new ItemEntity(level, position.x, position.y, position.z, royalSteelIngot());
        item.setNoGravity(true);
        item.setDeltaMovement(Vec3.ZERO);
        check(level.addFreshEntity(item), "failed to spawn a loose catalyst");
        return item;
    }

    private static HardenedResinCauldronEntity spawnResinCauldron(ServerLevel level, BlockPos pos) {
        Vec3 position = PlasticEntityOrientation.DEFAULT.entityPosition(
            pos,
            ModEntities.HARDEND_RESIN_CAULDRON.get().getWidth(),
            ModEntities.HARDEND_RESIN_CAULDRON.get().getHeight()
        );
        HardenedResinCauldronEntity cauldron = new HardenedResinCauldronEntity(
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            level,
            position,
            ModBlocks.HARDEND_RESIN_CAULDRON.get().defaultBlockState(),
            ModBlocks.HARDEND_RESIN_CAULDRON.asStack(),
            PlasticEntityOrientation.DEFAULT
        );
        cauldron.setNoGravity(true);
        check(level.addFreshEntity(cauldron), "failed to spawn a resin cauldron");
        return cauldron;
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
            "the Large Cauldron main block entity was not created"
        );
        return (LargeCauldronBlockEntity) level.getBlockEntity(base.above());
    }

    private static void setHighHeat(ServerLevel level, BlockPos pos) {
        level.setBlock(
            pos,
            OVERHEATED_EMBER_METAL_BLOCK.getDefaultState(),
            Block.UPDATE_ALL
        );
    }

    private static FluidStack plasticOil(int amount) {
        return new FluidStack(ModFluids.PLASTIC_OIL.get(), amount);
    }

    private static ItemStack royalSteelIngot() {
        ItemStack stack = ModItems.ROYAL_STEEL_INGOT.asStack();
        check(stack.is(ModItemTags.ROYAL_STEEL_ITEMS), "royal steel ingot was absent from the catalyst tag");
        return stack;
    }

    private static int count(IItemHandler handler, Item item) {
        int result = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }

    private static void assertMelt(FluidStack fluid, int amount, String carrier) {
        check(
            fluid.is(ModFluids.UNIVERSAL_PLASTIC_MELT.get()) && fluid.getAmount() == amount,
            carrier + " contained " + fluid + " after catalysis"
        );
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
