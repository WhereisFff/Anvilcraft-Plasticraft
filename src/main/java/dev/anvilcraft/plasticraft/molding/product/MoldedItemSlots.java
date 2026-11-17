package dev.anvilcraft.plasticraft.molding.product;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/** 成型槽位同时向自动化暴露资源事务，向交互与显示提供物品堆读取。 */
public interface MoldedItemSlots extends ResourceHandler<ItemResource>, IItemHandler {
    @Override
    default int size() {
        return this.getSlots();
    }

    @Override
    default ItemResource getResource(int slot) {
        return ItemResource.of(this.getStackInSlot(slot));
    }

    @Override
    default long getAmountAsLong(int slot) {
        return this.getStackInSlot(slot).getCount();
    }

    @Override
    default boolean isValid(int slot, ItemResource resource) {
        return this.isItemValid(slot, resource.toStack());
    }

    @Override
    default ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return IItemHandler.of(this).insertItem(slot, stack, simulate);
    }

    @Override
    default ItemStack extractItem(int slot, int amount, boolean simulate) {
        return IItemHandler.of(this).extractItem(slot, amount, simulate);
    }
}
