package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.product.storage.MoldedPlasticStorageHandle;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 无菜单箱子使用的稀疏物品能力。 */
public final class MoldedPlasticItemHandler implements IItemHandler {
    private final MoldedPlasticStorageHandle handle;

    public MoldedPlasticItemHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this.handle = new MoldedPlasticStorageHandle(data, update);
    }

    @Override
    public int getSlots() {
        return this.handle.data()
            .filter(value -> MoldingProductTypes.isChest(value.finalType()))
            .map(MoldedPlasticData::capacity)
            .orElse(0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.getSlots() ? this.handle.itemInSlot(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()
            || slot < 0
            || slot >= this.getSlots()
            || stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
            return stack;
        }
        if (this.handle.data().isEmpty()) return stack;
        ItemStack existing = this.handle.itemInSlot(slot);
        int limit = Math.min(stack.getMaxStackSize(), 64);
        int accepted = existing.isEmpty()
            ? Math.min(stack.getCount(), limit)
            : ItemStack.isSameItemSameComponents(existing, stack)
                ? Math.min(stack.getCount(), limit - existing.getCount())
                : 0;
        if (accepted <= 0) return stack.copy();
        if (!simulate) {
            ItemStack replacement = existing.isEmpty()
                ? stack.copyWithCount(accepted)
                : existing.copyWithCount(existing.getCount() + accepted);
            this.handle.setItem(slot, replacement);
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= this.getSlots()) return ItemStack.EMPTY;
        ItemStack existing = this.handle.itemInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = existing.copyWithCount(Math.min(amount, existing.getCount()));
        if (!simulate) {
            int remaining = existing.getCount() - extracted.getCount();
            this.handle.setItem(slot, remaining > 0 ? existing.copyWithCount(remaining) : ItemStack.EMPTY);
        }
        return extracted;
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < this.getSlots() ? 64 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return slot >= 0
            && slot < this.getSlots()
            && !stack.isEmpty()
            && !stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get());
    }
}
