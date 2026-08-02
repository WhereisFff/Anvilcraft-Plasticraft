package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import static dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_PILLAR;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_SLAB;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.CUT_FROST_METAL_STAIRS;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_ANVIL;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_DECO_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_DECO_OUTLINE;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_GLASS;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_GRINDSTONE;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_METAL_BLOCK;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.FROST_SMITHING_TABLE;
import static dev.dubhe.anvilcraft.init.block.ModBlocks.SLIDING_RAIL;

/** 塑料油环境催化的服务端回归测试。 */
public final class PlasticOilCatalysisGameTests {
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
            close(PlasticOilCatalysis.catalystMultiplier(0, 1), 0.125D),
            "one frost-metal catalyst was not half as fast as royal steel"
        );
        check(
            close(PlasticOilCatalysis.catalystMultiplier(0, 8), 0.25D),
            "eight frost-metal catalysts were not half as fast as royal steel"
        );
        check(
            close(PlasticOilCatalysis.catalystMultiplier(0, Integer.MAX_VALUE), 0.275D),
            "the frost-metal catalyst curve did not stop at half the royal-steel cap"
        );
        check(
            close(
                PlasticOilCatalysis.catalystMultiplier(1, 1),
                PlasticOilCatalysis.catalystMultiplier(1)
                    + (PlasticOilCatalysis.catalystMultiplier(2) - PlasticOilCatalysis.catalystMultiplier(1)) / 2.0D
            ),
            "a frost-metal catalyst did not contribute half a royal-steel gain"
        );
        check(
            close(PlasticOilCatalysis.catalystMultiplier(Integer.MAX_VALUE), 0.55D),
            "the open catalyst curve did not stop at 0.55"
        );
        checkFrostMetalCatalysts(
            FROST_ANVIL,
            FROST_GRINDSTONE,
            FROST_SMITHING_TABLE,
            FROST_METAL_BLOCK,
            CUT_FROST_METAL_BLOCK,
            CUT_FROST_METAL_PILLAR,
            CUT_FROST_METAL_SLAB,
            CUT_FROST_METAL_STAIRS,
            FROST_DECO_BLOCK,
            FROST_DECO_OUTLINE,
            FROST_GLASS,
            ModItems.FROST_METAL_INGOT,
            ModItems.FROST_METAL_NUGGET,
            ModItems.FROST_METAL_PICKAXE,
            ModItems.FROST_METAL_AXE,
            ModItems.FROST_METAL_SHOVEL,
            ModItems.FROST_METAL_HOE,
            ModItems.FROST_METAL_SWORD,
            ModItems.FROST_ANVIL_HAMMER,
            ModItems.FROST_DRAGON_ROD,
            ModItems.FROST_METAL_HEAVY_HALBERD,
            ModItems.FROST_METAL_RESONATOR,
            ModItems.FROST_METAL_UPGRADE_SMITHING_TEMPLATE
        );
        check(!Blocks.ICE.asItem().getDefaultInstance().is(ModItemTags.FROST_METAL_ITEMS), "ice became a catalyst");
        check(
            !SLIDING_RAIL.asItem().getDefaultInstance().is(ModItemTags.FROST_METAL_ITEMS),
            "a sliding rail became a catalyst"
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

    private static void checkFrostMetalCatalysts(ItemLike... catalysts) {
        for (ItemLike catalyst : catalysts) {
            check(
                catalyst.asItem().getDefaultInstance().is(ModItemTags.FROST_METAL_ITEMS),
                catalyst.asItem() + " was absent from the frost-metal catalyst tag"
            );
        }
    }

    private static boolean close(double first, double second) {
        return Math.abs(first - second) <= EPSILON;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
