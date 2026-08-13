package dev.anvilcraft.plasticraft.item;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 为创造物品栏的公共 4x4 选择叠加层提供完整物品栈变体。
 * 十六色塑料与无人机工具选择共用同一叠加层交互,不各自实现拦截逻辑。
 */
public interface CreativeVariantPickerItem {
    /** 返回叠加层展示的变体列表,按 4x4 网格从左到右、从上到下排列,最多 16 项。 */
    List<ItemStack> createCreativePickerVariants(ItemStack source);
}
