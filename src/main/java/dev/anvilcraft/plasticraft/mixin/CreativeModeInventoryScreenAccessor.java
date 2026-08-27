package dev.anvilcraft.plasticraft.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Collection;

/** 暴露原版创造栏当前标签的即时刷新入口。 */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeModeInventoryScreenAccessor {
    @Invoker("refreshCurrentTabContents")
    void plasticraft$refreshCurrentTabContents(Collection<ItemStack> items);
}
