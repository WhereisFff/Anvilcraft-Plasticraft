package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.neoforged.neoforge.items.IItemHandler;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 无菜单箱子使用的稀疏物品能力。 */
public final class MoldedPlasticItemHandler implements IItemHandler {
    private final Supplier<Optional<MoldedPlasticData>> data;
    private final Consumer<MoldedPlasticData> update;

    public MoldedPlasticItemHandler(
        Supplier<Optional<MoldedPlasticData>> data,
        Consumer<MoldedPlasticData> update
    ) {
        this.data = data;
        this.update = update;
    }

    @Override
    public int getSlots() {
        return this.data.get().filter(value -> MoldingProductTypes.isChest(value.finalType()))
            .map(MoldedPlasticData::capacity).orElse(0);
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return this.find(slot).map(MoldedPlasticContents.StoredItem::stack).orElse(ItemStack.EMPTY);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()
            || slot < 0
            || slot >= this.getSlots()
            || stack.has(PlasticraftDataComponents.MOLDED_PLASTIC.get())) {
            return stack;
        }
        Optional<MoldedPlasticData> current = this.data.get();
        if (current.isEmpty()) return stack;
        ItemStack existing = this.getStackInSlot(slot);
        int limit = Math.min(stack.getMaxStackSize(), 64);
        int accepted = existing.isEmpty() ? Math.min(stack.getCount(), limit)
            : ItemStack.isSameItemSameComponents(existing, stack)
                ? Math.min(stack.getCount(), limit - existing.getCount()) : 0;
        if (accepted <= 0) return stack.copy();
        if (!simulate) {
            List<MoldedPlasticContents.StoredItem> entries = new ArrayList<>(current.orElseThrow().contents().items());
            entries.removeIf(entry -> entry.slot() == slot);
            ItemStack replacement = existing.isEmpty() ? stack.copyWithCount(accepted) : existing.copyWithCount(existing.getCount() + accepted);
            entries.add(new MoldedPlasticContents.StoredItem(slot, replacement));
            this.update.accept(current.orElseThrow().withContents(
                new MoldedPlasticContents(entries, current.orElseThrow().contents().fluids())
            ));
        }
        return stack.copyWithCount(stack.getCount() - accepted);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || slot < 0 || slot >= this.getSlots()) return ItemStack.EMPTY;
        ItemStack existing = this.getStackInSlot(slot);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        ItemStack extracted = existing.copyWithCount(Math.min(amount, existing.getCount()));
        if (!simulate) {
            MoldedPlasticData value = this.data.get().orElseThrow();
            List<MoldedPlasticContents.StoredItem> entries = new ArrayList<>(value.contents().items());
            entries.removeIf(entry -> entry.slot() == slot);
            int remaining = existing.getCount() - extracted.getCount();
            if (remaining > 0) entries.add(new MoldedPlasticContents.StoredItem(slot, existing.copyWithCount(remaining)));
            this.update.accept(value.withContents(new MoldedPlasticContents(entries, value.contents().fluids())));
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

    private Optional<MoldedPlasticContents.StoredItem> find(int slot) {
        return this.data.get().flatMap(value -> value.contents().items().stream().filter(entry -> entry.slot() == slot).findFirst());
    }
}
