package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.entity.redstone.MoldedPlasticRedstoneConductor;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.CreativeColorVariantItem;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.item.UniversalPlasticItemStacks;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

import java.util.List;

/** 创造物品栏十六色条目的公共物品数据回归测试。 */
public final class CreativeColorVariantGameTests {
    private static final List<DyeColor> EXPECTED_COLOR_ORDER = List.of(
        DyeColor.WHITE,
        DyeColor.LIGHT_GRAY,
        DyeColor.GRAY,
        DyeColor.BLACK,
        DyeColor.BROWN,
        DyeColor.RED,
        DyeColor.ORANGE,
        DyeColor.YELLOW,
        DyeColor.LIME,
        DyeColor.GREEN,
        DyeColor.CYAN,
        DyeColor.LIGHT_BLUE,
        DyeColor.BLUE,
        DyeColor.PURPLE,
        DyeColor.MAGENTA,
        DyeColor.PINK
    );

    private CreativeColorVariantGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Creative colour variants preserve a full block and synchronize molded material colour")
    static void fullBlockAndPaletteData(ExtendedGameTestHelper helper) {
        ItemStack fullBlock = UniversalPlasticItemStacks.fullBlock();
        MoldedPlasticData fullBlockData = MoldedPlasticData.get(fullBlock)
            .orElseThrow(() -> new GameTestAssertException("creative full block has no molded data"));
        AABB bounds = fullBlockData.surfaceBounds();
        check(MoldedPlasticRedstoneConductor.isFullBlockSized(bounds), "creative block is not exactly one block");
        check(PlasticMeltColor.get(fullBlock) == DyeColor.WHITE, "creative block is not white by default");
        check(
            CreativeColorVariantItem.CREATIVE_COLOR_ORDER.equals(EXPECTED_COLOR_ORDER),
            "creative colour order does not match the vanilla palette"
        );

        for (DyeColor color : CreativeColorVariantItem.CREATIVE_COLOR_ORDER) {
            ItemStack blockVariant = variant(fullBlock, color);
            MoldedPlasticData blockData = MoldedPlasticData.get(blockVariant)
                .orElseThrow(() -> new GameTestAssertException("coloured full block lost molded data"));
            check(PlasticMeltColor.get(blockVariant) == color, "full block item colour was not updated");
            check(PlasticMeltColor.get(blockData.material()) == color, "full block material colour was not updated");
            check(
                MoldedPlasticRedstoneConductor.isFullBlockSized(blockData.surfaceBounds()),
                "colour variant changed the full block shape"
            );
            check(
                PlasticMeltColor.get(variant(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack(), color)) == color,
                "bucket colour variant was not updated"
            );
            check(
                PlasticMeltColor.get(variant(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.asStack(), color)) == color,
                "granule colour variant was not updated"
            );
        }
        check(PlasticMeltColor.get(fullBlock) == DyeColor.WHITE, "creating variants mutated the source block");
        helper.succeed();
    }

    private static ItemStack variant(ItemStack source, DyeColor color) {
        if (!(source.getItem() instanceof CreativeColorVariantItem provider)) {
            throw new GameTestAssertException("creative colour item has no variant provider");
        }
        return provider.createCreativeColorVariant(source, color);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
