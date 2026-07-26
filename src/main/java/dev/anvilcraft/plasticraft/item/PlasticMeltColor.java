package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

/** 通用塑料熔体和通用塑料粒共用的单 ID 颜色数据。 */
public final class PlasticMeltColor {
    public static final String COLOR_KEY = "PlasticMeltColor";

    private PlasticMeltColor() {
    }

    public static DyeColor get(ItemStack stack) {
        return get(stack.get(DataComponents.CUSTOM_DATA));
    }

    public static DyeColor get(FluidStack stack) {
        return get(stack.get(DataComponents.CUSTOM_DATA));
    }

    public static void set(ItemStack stack, DyeColor color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> write(tag, color));
    }

    public static void set(FluidStack stack, DyeColor color) {
        CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            : new CompoundTag();
        write(tag, color);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int tint(ItemStack stack) {
        return tint(get(stack));
    }

    public static int tint(FluidStack stack) {
        return tint(get(stack));
    }

    public static int tint(DyeColor color) {
        return color.getTextureDiffuseColor();
    }

    private static DyeColor get(CustomData data) {
        if (data == null) return DyeColor.WHITE;
        return DyeColor.byName(data.copyTag().getString(COLOR_KEY), DyeColor.WHITE);
    }

    private static void write(CompoundTag tag, DyeColor color) {
        if (color == DyeColor.WHITE) {
            tag.remove(COLOR_KEY);
        } else {
            tag.putString(COLOR_KEY, color.getName());
        }
    }
}
