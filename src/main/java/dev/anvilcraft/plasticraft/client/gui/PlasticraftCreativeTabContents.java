package dev.anvilcraft.plasticraft.client.gui;

import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
import dev.anvilcraft.plasticraft.item.PlasticraftCreativeTabState;
import dev.anvilcraft.plasticraft.mixin.CreativeModeTabAccessor;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;

import java.util.Collection;
import java.util.Set;

/** 在不重启游戏的情况下按分区状态重新生成 Plasticraft 创造栏内容。 */
public final class PlasticraftCreativeTabContents {
    private PlasticraftCreativeTabContents() {
    }

    public static Collection<ItemStack> rebuild(
        CreativeModeTab tab,
        CreativeModeTab.ItemDisplayParameters parameters
    ) {
        Set<ItemStack> parentEntries = ItemStackLinkedSet.createTypeAndComponentsSet();
        Set<ItemStack> searchEntries = ItemStackLinkedSet.createTypeAndComponentsSet();
        CreativeModeTab.Output output = (stack, visibility) -> {
            if (stack.getCount() != 1) {
                throw new IllegalArgumentException("Stack size must be exactly 1");
            }
            if (!stack.getItem().isEnabled(parameters.enabledFeatures())) return;
            if (visibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY) {
                parentEntries.add(stack.copy());
            }
            if (visibility != CreativeModeTab.TabVisibility.PARENT_TAB_ONLY) {
                searchEntries.add(stack.copy());
            }
        };
        PlasticraftItemGroups.buildDisplayItems(
            parameters,
            output,
            PlasticraftCreativeTabState::isExpanded
        );
        CreativeModeTabAccessor accessor = (CreativeModeTabAccessor) (Object) tab;
        // 标签缓存只保存真实物品，避免再次打开时重复插入横幅占位。
        accessor.plasticraft$setDisplayItems(parentEntries);
        accessor.plasticraft$setSearchTabDisplayItems(searchEntries);
        return parentEntries;
    }
}
