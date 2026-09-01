package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * 可提供调色板的材料共用的小型纯数据能力。
 *
 * <p>读取旧物品堆时，固定颜色材料仍使用相同的兼容存储键，
 * 但调用方可在标准化期间移除该键，而不必让每种材料都携带方块状态颜色属性。</p>
 */
public final class DyeableMaterial {
    public static final String COLOR_KEY = "PlasticColor";
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    private DyeableMaterial() {
    }

    public static DyeColor getColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(COLOR_KEY)) return DyeColor.WHITE;
        return DyeColor.byName(data.copyTag().getString(COLOR_KEY), DyeColor.WHITE);
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag ->
            tag.putString(COLOR_KEY, color.getName())
        );
    }

    /** 从不支持染色的材料中移除旧颜色数据。 */
    public static void clearColor(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(COLOR_KEY)) return;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.remove(COLOR_KEY));
    }

    /** 明确标记物品堆是否属于支持调色板的材料。 */
    public static boolean supportsDyeing(String materialKey) {
        return "hdpe".equals(materialKey)
            || "universal_plastic".equals(materialKey)
            || "engineering_plastic".equals(materialKey)
            || "heat_resistant_plastic".equals(materialKey)
            || "clear_plastic".equals(materialKey);
    }

    /** 调色板材料按需启用的共用方块状态属性和着色计算。 */
    public static BlockState withColor(BlockState state, DyeColor color) {
        return state.hasProperty(COLOR) ? state.setValue(COLOR, color) : state;
    }

    public static int tint(BlockState state) {
        return tint(state.hasProperty(COLOR) ? state.getValue(COLOR) : DyeColor.WHITE);
    }

    public static int tint(DyeColor color) {
        int rgb = color.getTextureDiffuseColor();
        int red = 192 + ((rgb >> 16) & 0xFF) / 4;
        int green = 192 + ((rgb >> 8) & 0xFF) / 4;
        int blue = 192 + (rgb & 0xFF) / 4;
        return (red << 16) | (green << 8) | blue;
    }

    /**
     * 自定义模型数据标记有意与自定义 NBT 分离：
     * 它允许一个物品 ID 在不注册第二个物品的情况下选择磁性模型。
     * 零表示普通变体，一表示磁性变体。
     */
    public static void setModelVariant(ItemStack stack, int variant) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(variant));
    }
}
