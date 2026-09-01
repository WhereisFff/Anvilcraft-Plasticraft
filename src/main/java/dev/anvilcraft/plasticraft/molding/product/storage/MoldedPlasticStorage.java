package dev.anvilcraft.plasticraft.molding.product.storage;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticContents;
import dev.dubhe.anvilcraft.api.itemhandler.unlimited.UnlimitedItemStacksResourceHandler;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** 一份塑料箱子/储罐的权威稀疏内容。物品 NBT 复用本体无限堆处理器，不改用板条箱按类型合并。 */
public final class MoldedPlasticStorage {
    private final UUID id;
    private final TreeMap<Integer, ItemStack> items = new TreeMap<>();
    private final List<FluidStack> fluids = new ArrayList<>();

    public MoldedPlasticStorage(UUID id) {
        this.id = id;
    }

    public UUID id() {
        return this.id;
    }

    public List<MoldedPlasticContents.StoredItem> items() {
        List<MoldedPlasticContents.StoredItem> result = new ArrayList<>(this.items.size());
        for (Map.Entry<Integer, ItemStack> entry : this.items.entrySet()) {
            result.add(new MoldedPlasticContents.StoredItem(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public List<FluidStack> fluids() {
        return this.fluids.stream().map(FluidStack::copy).toList();
    }

    public ItemStack itemInSlot(int slot) {
        ItemStack stack = this.items.get(slot);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    public void setItems(List<MoldedPlasticContents.StoredItem> replacement) {
        this.items.clear();
        for (MoldedPlasticContents.StoredItem item : replacement) {
            if (!item.stack().isEmpty()) this.items.put(item.slot(), item.stack().copy());
        }
    }

    public void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty()) this.items.remove(slot);
        else this.items.put(slot, stack.copy());
    }

    public void setFluids(List<FluidStack> replacement) {
        this.fluids.clear();
        for (FluidStack fluid : replacement) {
            if (!fluid.isEmpty()) this.fluids.add(fluid.copy());
        }
    }

    public boolean isEmpty() {
        return this.items.isEmpty() && this.fluids.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        int size = this.items.isEmpty() ? 0 : this.items.lastKey() + 1;
        UnlimitedItemStacksResourceHandler handler = new UnlimitedItemStacksResourceHandler(size);
        for (Map.Entry<Integer, ItemStack> entry : this.items.entrySet()) {
            handler.insertItem(entry.getKey(), entry.getValue(), false);
        }
        tag.put("items", handler.serializeNBT(registries));
        ListTag fluidTags = new ListTag();
        for (FluidStack fluid : this.fluids) {
            fluidTags.add(fluid.save(registries));
        }
        tag.put("fluids", fluidTags);
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.items.clear();
        this.fluids.clear();
        if (tag.contains("items", Tag.TAG_COMPOUND)) {
            UnlimitedItemStacksResourceHandler handler = new UnlimitedItemStacksResourceHandler(0);
            handler.deserializeNBT(registries, tag.getCompound("items"));
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty()) this.items.put(slot, stack);
            }
        }
        if (tag.contains("fluids", Tag.TAG_LIST)) {
            ListTag fluidTags = tag.getList("fluids", Tag.TAG_COMPOUND);
            for (int index = 0; index < fluidTags.size(); index++) {
                FluidStack fluid = FluidStack.parse(registries, fluidTags.getCompound(index)).orElse(FluidStack.EMPTY);
                if (!fluid.isEmpty()) this.fluids.add(fluid);
            }
        }
    }
}
