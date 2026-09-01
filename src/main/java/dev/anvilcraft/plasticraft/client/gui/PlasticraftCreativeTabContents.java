package dev.anvilcraft.plasticraft.client.gui;

import dev.anvilcraft.lib.v2.registrum.util.CreativeTabSections;
import dev.anvilcraft.lib.v2.registrum.util.CreativeVariantPickerRegistry;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
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
        Collection<ItemStack> arranged = CreativeTabSections.arrange(
            PlasticraftItemGroups.MAIN_ID,
            CreativeVariantPickerRegistry.fold(parentEntries)
        );
        CreativeModeTabAccessor accessor = (CreativeModeTabAccessor) (Object) tab;
        accessor.plasticraft$setDisplayItems(arranged);
        accessor.plasticraft$setSearchTabDisplayItems(searchEntries);
        return arranged;
    }
}
