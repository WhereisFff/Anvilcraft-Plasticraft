package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;

/** 塑料熔体和塑料粒共用的单 ID 颜色数据。 */
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
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            // 塑料粒必须显式保存白色，供村民交易的组件谓词区分“白色”和“任意颜色”。
            if (stack.getItem() instanceof UniversalPlasticGranuleItem) {
                tag.putString(COLOR_KEY, color.getName());
            } else {
                write(tag, color);
            }
        });
    }

    public static void set(FluidStack stack, DyeColor color) {
        CompoundTag tag = stack.has(DataComponents.CUSTOM_DATA)
            ? stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
            : new CompoundTag();
        write(tag, color);
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
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

    /**
     * 创建只包含指定塑料颜色的自定义数据。
     *
     * <p>村民的 {@code ItemCost} 需要实际存在的组件才能执行精确匹配，因此这里不会省略白色。</p>
     */
    public static CustomData explicitColorData(DyeColor color) {
        CompoundTag tag = new CompoundTag();
        tag.putString(COLOR_KEY, color.getName());
        return CustomData.of(tag);
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
