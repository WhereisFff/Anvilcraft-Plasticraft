package dev.anvilcraft.plasticraft.mixin;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Collection;
import java.util.Set;

/** 允许客户端刷新创造标签缓存，不改变原版注册流程。 */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("displayItems")
    void plasticraft$setDisplayItems(Collection<ItemStack> displayItems);

    @Accessor("displayItemsSearchTab")
    void plasticraft$setSearchTabDisplayItems(Set<ItemStack> displayItemsSearchTab);
}
