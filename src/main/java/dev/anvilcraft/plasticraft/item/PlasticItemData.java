package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** 所有可移动塑料方块变体共用的稳定物品数据。 */
public final class PlasticItemData {
    private static final String MATERIAL_KEY = "PlasticMaterial";
    private static final String MAGNETIZED_KEY = "Magnetized";
    private static final String DEMONSTRATION_MODEL_KEY = "DemonstrationModel";

    private PlasticItemData() {
    }

    public static DyeColor getColor(ItemStack stack) {
        return DyeableMaterial.getColor(stack);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        DyeableMaterial.setColor(stack, color);
    }

    public static void clearColor(ItemStack stack) {
        DyeableMaterial.clearColor(stack);
    }

    /** 返回持久化材料键，并将旧塑料标识作为迁移默认值。 */
    public static String getMaterial(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(MATERIAL_KEY)) {
            return defaultMaterial(stack);
        }
        String value = data.copyTag().getString(MATERIAL_KEY);
        return value.isBlank() ? defaultMaterial(stack) : value;
    }

    private static String defaultMaterial(ItemStack stack) {
        // 普通有序配方产出的新制品物品堆有意不含自定义数据，因此从物品 ID 推断材料。
        return switch (stack.getItem()) {
            case ResinAnvilItem resinAnvilItem -> "resin";
            case ClearPlasticBlockItem clearPlasticBlockItem -> "clear_plastic";
            case EngineeringPlasticBlockItem engineeringPlasticBlockItem -> "engineering_plastic";
            case UniversalPlasticBlockItem universalPlasticBlockItem -> "universal_plastic";
            default -> "hardened_resin";
        };
    }

    public static void setMaterial(ItemStack stack, String materialKey) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
            tag.putString(MATERIAL_KEY, materialKey)
        );
    }

    public static boolean isDemonstrationModel(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null
            && data.contains(DEMONSTRATION_MODEL_KEY)
            && data.copyTag().getBoolean(DEMONSTRATION_MODEL_KEY);
    }

    public static void markDemonstrationModel(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
            tag.putBoolean(DEMONSTRATION_MODEL_KEY, true)
        );
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
        // 将模型选择器保存在类型化组件中，使配方谓词无需解析自定义 NBT，
        // 即可区分磁性和普通物品堆。
        DyeableMaterial.setModelVariant(stack, magnetized ? 1 : 0);
    }
}
