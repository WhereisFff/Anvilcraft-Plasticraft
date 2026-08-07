package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

/** 供实体同步、Jade 和铁砧锤共用的有界内容摘要。 */
public record MoldedPlasticContentSummary(
    ResourceLocation type,
    int capacity,
    int occupiedSlots,
    int totalFluid,
    List<ItemEntry> items,
    int omittedItemTypes,
    List<FluidEntry> fluids,
    int omittedFluidTypes
) {
    public static final int MAX_VISIBLE_ENTRIES = 8;
    public static final MoldedPlasticContentSummary EMPTY = new MoldedPlasticContentSummary(
        MoldingProductTypes.NORMAL_ID,
        0,
        0,
        0,
        List.of(),
        0,
        List.of(),
        0
    );

    public MoldedPlasticContentSummary {
        items = List.copyOf(items);
        fluids = List.copyOf(fluids);
        if (capacity < 0 || occupiedSlots < 0 || totalFluid < 0
            || omittedItemTypes < 0 || omittedFluidTypes < 0
            || items.size() > MAX_VISIBLE_ENTRIES || fluids.size() > MAX_VISIBLE_ENTRIES) {
            throw new IllegalArgumentException("Invalid molded plastic content summary");
        }
    }

    public static MoldedPlasticContentSummary create(MoldedPlasticData data) {
        List<ItemEntry> itemEntries = new ArrayList<>();
        List<MoldedPlasticContents.StoredItem> storedItems = data.contents().items();
        for (MoldedPlasticContents.StoredItem stored : storedItems) {
            ItemStack stack = stored.stack();
            int match = -1;
            for (int index = 0; index < itemEntries.size(); index++) {
                if (ItemStack.isSameItemSameComponents(itemEntries.get(index).stack, stack)) {
                    match = index;
                    break;
                }
            }
            if (match >= 0) {
                ItemEntry entry = itemEntries.get(match);
                itemEntries.set(match, new ItemEntry(entry.stack, entry.count + stack.getCount()));
            } else {
                itemEntries.add(new ItemEntry(stack.copyWithCount(1), stack.getCount()));
            }
        }

        List<FluidEntry> fluidEntries = new ArrayList<>();
        int totalFluid = 0;
        for (FluidStack fluid : data.contents().fluids()) {
            totalFluid = Math.addExact(totalFluid, fluid.getAmount());
            fluidEntries.add(new FluidEntry(fluid.getHoverName().getString(), fluid.getAmount()));
        }
        return new MoldedPlasticContentSummary(
            data.finalType(),
            data.capacity(),
            storedItems.size(),
            totalFluid,
            itemEntries.stream().limit(MAX_VISIBLE_ENTRIES).toList(),
            Math.max(0, itemEntries.size() - MAX_VISIBLE_ENTRIES),
            fluidEntries.stream().limit(MAX_VISIBLE_ENTRIES).toList(),
            Math.max(0, fluidEntries.size() - MAX_VISIBLE_ENTRIES)
        );
    }

    public CompoundTag toTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", this.type.toString());
        tag.putInt("Capacity", this.capacity);
        tag.putInt("OccupiedSlots", this.occupiedSlots);
        tag.putInt("TotalFluid", this.totalFluid);
        tag.putInt("OmittedItemTypes", this.omittedItemTypes);
        tag.putInt("OmittedFluidTypes", this.omittedFluidTypes);
        ListTag itemTags = new ListTag();
        for (ItemEntry item : this.items) {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", item.stack.save(registries));
            entry.putInt("Count", item.count);
            itemTags.add(entry);
        }
        tag.put("Items", itemTags);
        ListTag fluidTags = new ListTag();
        for (FluidEntry fluid : this.fluids) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Name", fluid.name);
            entry.putInt("Amount", fluid.amount);
            fluidTags.add(entry);
        }
        tag.put("Fluids", fluidTags);
        return tag;
    }

    public static MoldedPlasticContentSummary fromTag(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation type = ResourceLocation.tryParse(tag.getString("Type"));
        if (type == null || !MoldingProductTypes.isRegistered(type)) return EMPTY;
        ListTag itemTags = tag.getList("Items", Tag.TAG_COMPOUND);
        List<ItemEntry> items = new ArrayList<>(Math.min(itemTags.size(), MAX_VISIBLE_ENTRIES));
        for (int index = 0; index < itemTags.size() && index < MAX_VISIBLE_ENTRIES; index++) {
            CompoundTag entry = itemTags.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Stack"));
            if (!stack.isEmpty() && entry.getInt("Count") > 0) {
                items.add(new ItemEntry(stack.copyWithCount(1), entry.getInt("Count")));
            }
        }
        ListTag fluidTags = tag.getList("Fluids", Tag.TAG_COMPOUND);
        List<FluidEntry> fluids = new ArrayList<>(Math.min(fluidTags.size(), MAX_VISIBLE_ENTRIES));
        for (int index = 0; index < fluidTags.size() && index < MAX_VISIBLE_ENTRIES; index++) {
            CompoundTag entry = fluidTags.getCompound(index);
            if (!entry.getString("Name").isBlank() && entry.getInt("Amount") > 0) {
                fluids.add(new FluidEntry(entry.getString("Name"), entry.getInt("Amount")));
            }
        }
        return new MoldedPlasticContentSummary(
            type,
            Math.max(0, tag.getInt("Capacity")),
            Math.max(0, tag.getInt("OccupiedSlots")),
            Math.max(0, tag.getInt("TotalFluid")),
            items,
            Math.max(0, tag.getInt("OmittedItemTypes")),
            fluids,
            Math.max(0, tag.getInt("OmittedFluidTypes"))
        );
    }

    public record ItemEntry(ItemStack stack, int count) {
        public ItemEntry {
            if (stack.isEmpty() || count <= 0) throw new IllegalArgumentException("Invalid summarized item");
            stack = stack.copyWithCount(1);
        }

        @Override
        public ItemStack stack() {
            return this.stack.copy();
        }
    }

    public record FluidEntry(String name, int amount) {
        public FluidEntry {
            if (name.isBlank() || amount <= 0) throw new IllegalArgumentException("Invalid summarized fluid");
        }
    }
}
