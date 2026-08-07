package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 成型制品的稀疏物品槽和稳定顺序多流体内容。 */
public record MoldedPlasticContents(
    List<StoredItem> items,
    List<FluidStack> fluids,
    Optional<MoldedTrayComponent> trayComponent
) {
    public static final int MAX_STORED_ITEMS = 1728;
    public static final int MAX_STORED_FLUIDS = 1024;
    public static final MoldedPlasticContents EMPTY = new MoldedPlasticContents(List.of(), List.of());
    private static final Codec<List<StoredItem>> ITEMS_CODEC = StoredItem.CODEC.listOf()
        .validate(items -> items.size() <= MAX_STORED_ITEMS
            ? DataResult.success(items)
            : DataResult.error(() -> "Too many molded plastic item entries"));
    private static final Codec<List<FluidStack>> FLUIDS_CODEC = FluidStack.CODEC.listOf()
        .validate(fluids -> fluids.size() <= MAX_STORED_FLUIDS
            ? DataResult.success(fluids)
            : DataResult.error(() -> "Too many molded plastic fluid entries"));
    public static final Codec<MoldedPlasticContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(MoldedPlasticContents::items),
        FLUIDS_CODEC.optionalFieldOf("fluids", List.of()).forGetter(MoldedPlasticContents::fluids),
        MoldedTrayComponent.CODEC.optionalFieldOf("tray_component")
            .forGetter(MoldedPlasticContents::trayComponent)
    ).apply(instance, MoldedPlasticContents::new));

    public MoldedPlasticContents(List<StoredItem> items, List<FluidStack> fluids) {
        this(items, fluids, Optional.empty());
    }

    public MoldedPlasticContents {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(fluids, "fluids");
        Objects.requireNonNull(trayComponent, "trayComponent");
        items = items.stream()
            .filter(item -> !item.stack().isEmpty())
            .sorted(Comparator.comparingInt(StoredItem::slot))
            .toList();
        fluids = fluids.stream().filter(fluid -> !fluid.isEmpty()).map(FluidStack::copy).toList();
        if (items.size() > MAX_STORED_ITEMS || fluids.size() > MAX_STORED_FLUIDS) {
            throw new IllegalArgumentException("Molded plastic contents exceed their entry limits");
        }
        int previousSlot = -1;
        for (StoredItem item : items) {
            if (item.slot() <= previousSlot) throw new IllegalArgumentException("Duplicate molded plastic item slot");
            if (item.stack().getCount() > item.stack().getMaxStackSize()) {
                throw new IllegalArgumentException("Molded plastic stored item exceeds its stack limit");
            }
            previousSlot = item.slot();
        }
        for (int index = 0; index < fluids.size(); index++) {
            FluidStack fluid = fluids.get(index);
            if (fluid.getAmount() <= 0) throw new IllegalArgumentException("Invalid molded plastic fluid amount");
            for (int other = index + 1; other < fluids.size(); other++) {
                if (FluidStack.isSameFluidSameComponents(fluid, fluids.get(other))) {
                    throw new IllegalArgumentException("Duplicate molded plastic fluid entry");
                }
            }
        }
    }

    @Override
    public List<StoredItem> items() {
        return this.items.stream().map(StoredItem::copy).toList();
    }

    @Override
    public List<FluidStack> fluids() {
        return this.fluids.stream().map(FluidStack::copy).toList();
    }

    public boolean isEmpty() {
        return this.items.isEmpty() && this.fluids.isEmpty() && this.trayComponent.isEmpty();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MoldedPlasticContents that)
            || this.items.size() != that.items.size()
            || this.fluids.size() != that.fluids.size()
            || !this.trayComponent.equals(that.trayComponent)) return false;
        for (int index = 0; index < this.items.size(); index++) {
            StoredItem left = this.items.get(index);
            StoredItem right = that.items.get(index);
            if (left.slot != right.slot || !ItemStack.matches(left.stack, right.stack)) return false;
        }
        for (int index = 0; index < this.fluids.size(); index++) {
            if (!FluidStack.isSameFluidSameComponents(this.fluids.get(index), that.fluids.get(index))
                || this.fluids.get(index).getAmount() != that.fluids.get(index).getAmount()) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (StoredItem item : this.items) {
            result = 31 * result + item.slot;
            result = 31 * result + Objects.hash(item.stack.getItemHolder().unwrapKey().orElse(null), item.stack.getCount(), item.stack.getComponentsPatch());
        }
        for (FluidStack fluid : this.fluids) {
            result = 31 * result + Objects.hash(fluid.getFluid(), fluid.getAmount(), fluid.getComponentsPatch());
        }
        result = 31 * result + this.trayComponent.hashCode();
        return result;
    }

    public MoldedPlasticContents renderView() {
        return this.items.isEmpty()
            ? this
            : new MoldedPlasticContents(List.of(), this.fluids, this.trayComponent);
    }

    public MoldedPlasticContents withTrayComponent(Optional<MoldedTrayComponent> replacement) {
        if (this.trayComponent.equals(replacement)) return this;
        return new MoldedPlasticContents(this.items, this.fluids, replacement);
    }

    public static void encode(RegistryFriendlyByteBuf buffer, MoldedPlasticContents contents) {
        buffer.writeVarInt(contents.items.size());
        for (StoredItem item : contents.items) {
            buffer.writeVarInt(item.slot);
            ItemStack.STREAM_CODEC.encode(buffer, item.stack);
        }
        buffer.writeVarInt(contents.fluids.size());
        for (FluidStack fluid : contents.fluids) FluidStack.STREAM_CODEC.encode(buffer, fluid);
        buffer.writeBoolean(contents.trayComponent.isPresent());
        contents.trayComponent.ifPresent(component -> MoldedTrayComponent.STREAM_CODEC.encode(buffer, component));
    }

    public static MoldedPlasticContents decode(RegistryFriendlyByteBuf buffer) {
        int itemCount = boundedCount(buffer, MAX_STORED_ITEMS, "molded plastic items");
        List<StoredItem> items = new ArrayList<>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            items.add(new StoredItem(buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer)));
        }
        int fluidCount = boundedCount(buffer, MAX_STORED_FLUIDS, "molded plastic fluids");
        List<FluidStack> fluids = new ArrayList<>(fluidCount);
        for (int index = 0; index < fluidCount; index++) {
            fluids.add(FluidStack.STREAM_CODEC.decode(buffer));
        }
        Optional<MoldedTrayComponent> trayComponent = buffer.readBoolean()
            ? Optional.of(MoldedTrayComponent.STREAM_CODEC.decode(buffer))
            : Optional.empty();
        return new MoldedPlasticContents(items, fluids, trayComponent);
    }

    private static int boundedCount(RegistryFriendlyByteBuf buffer, int maximum, String name) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid " + name + " count");
        return count;
    }

    public record StoredItem(int slot, ItemStack stack) {
        public static final Codec<StoredItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(StoredItem::slot),
            ItemStack.CODEC.fieldOf("stack").forGetter(StoredItem::stack)
        ).apply(instance, StoredItem::new));

        public StoredItem {
            if (slot < 0 || slot >= MAX_STORED_ITEMS) {
                throw new IllegalArgumentException("Molded plastic item slot is outside its limit");
            }
            if (stack.isEmpty()) throw new IllegalArgumentException("Molded plastic stored item must not be empty");
            stack = stack.copy();
        }

        @Override
        public ItemStack stack() {
            return this.stack.copy();
        }

        private StoredItem copy() {
            return new StoredItem(this.slot, this.stack);
        }
    }
}
