package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stable item data shared by all movable plastic block variants. */
public final class PlasticItemData {
    private static final String COLOR_KEY = "PlasticColor";
    private static final String MAGNETIZED_KEY = "Magnetized";

    private PlasticItemData() {
    }

    public static DyeColor getColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(COLOR_KEY)) return DyeColor.WHITE;
        return DyeColor.byName(data.copyTag().getString(COLOR_KEY), DyeColor.WHITE);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(COLOR_KEY, color.getName()));
    }

    public static boolean isMagnetized(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.contains(MAGNETIZED_KEY) && data.copyTag().getBoolean(MAGNETIZED_KEY);
    }

    public static void setMagnetized(ItemStack stack, boolean magnetized) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (magnetized) {
                tag.putBoolean(MAGNETIZED_KEY, true);
            } else {
                tag.remove(MAGNETIZED_KEY);
            }
        });
    }
}
