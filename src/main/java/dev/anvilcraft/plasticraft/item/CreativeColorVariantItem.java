package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/** 为创造物品栏的公共十六色选择器提供完整物品栈变体。 */
public interface CreativeColorVariantItem extends CreativeVariantPickerItem {
    /** 创造物品栏 4x4 叠加层与通用塑料色板行共用的十六色顺序。 */
    List<DyeColor> CREATIVE_COLOR_ORDER = List.of(
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

    /** 色板从上到下与创造物品栏十六色顺序一致。 */
    static int paletteRow(DyeColor color) {
        int row = CREATIVE_COLOR_ORDER.indexOf(color);
        if (row < 0) {
            throw new IllegalArgumentException("Unsupported plastic palette colour: " + color);
        }
        return row;
    }

    default ItemStack createCreativeColorVariant(ItemStack source, DyeColor color) {
        ItemStack variant = source.copyWithCount(1);
        MoldedPlasticData.get(variant).ifPresent(data -> {
            FluidStack material = data.material();
            PlasticMeltColor.set(material, color);
            MoldedPlasticData.set(variant, data.withMaterial(material));
        });
        PlasticMeltColor.set(variant, color);
        return variant;
    }

    @Override
    default List<ItemStack> createCreativePickerVariants(ItemStack source) {
        return CREATIVE_COLOR_ORDER.stream()
            .map(color -> this.createCreativeColorVariant(source, color))
            .toList();
    }
}
