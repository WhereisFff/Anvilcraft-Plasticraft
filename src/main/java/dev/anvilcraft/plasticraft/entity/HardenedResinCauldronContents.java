package dev.anvilcraft.plasticraft.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** 硬化树脂锅完整拾取时随物品保存的可变状态。 */
public record HardenedResinCauldronContents(
    List<StoredItem> items,
    FluidStack fluid,
    Optional<Direction> outletSide,
    boolean ignited
) {
    public static final int SLOT_COUNT = 16;
    private static final Codec<List<StoredItem>> ITEMS_CODEC = StoredItem.CODEC.listOf()
        .validate(items -> items.size() <= SLOT_COUNT
            ? DataResult.success(items)
            : DataResult.error(() -> "Too many hardened resin cauldron item entries"));
    public static final Codec<HardenedResinCauldronContents> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ITEMS_CODEC.optionalFieldOf("items", List.of()).forGetter(HardenedResinCauldronContents::items),
            FluidStack.CODEC.optionalFieldOf("fluid", FluidStack.EMPTY)
                .forGetter(HardenedResinCauldronContents::fluid),
            Codec.INT.optionalFieldOf("outlet_side", -1).forGetter(contents ->
                contents.outletSide().map(Direction::get3DDataValue).orElse(-1)
            ),
            Codec.BOOL.optionalFieldOf("ignited", false).forGetter(HardenedResinCauldronContents::ignited)
        ).apply(instance, HardenedResinCauldronContents::decodePersistent)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, HardenedResinCauldronContents> STREAM_CODEC =
        StreamCodec.of(HardenedResinCauldronContents::encode, HardenedResinCauldronContents::decode);

    public HardenedResinCauldronContents {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(fluid, "fluid");
        Objects.requireNonNull(outletSide, "outletSide");
        items = items.stream()
            .filter(item -> !item.stack().isEmpty())
            .sorted(Comparator.comparingInt(StoredItem::slot))
            .toList();
        fluid = fluid.copy();
        outletSide = outletSide.filter(direction -> direction.getAxis().isHorizontal());
        int previousSlot = -1;
        for (StoredItem item : items) {
            if (item.slot() <= previousSlot) {
                throw new IllegalArgumentException("Duplicate hardened resin cauldron item slot");
            }
            if (item.stack().getCount() > item.stack().getMaxStackSize()) {
                throw new IllegalArgumentException("Hardened resin cauldron item exceeds its stack limit");
            }
            previousSlot = item.slot();
        }
    }

    @Override
    public List<StoredItem> items() {
        return this.items.stream().map(StoredItem::copy).toList();
    }

    @Override
    public FluidStack fluid() {
        return this.fluid.copy();
    }

    public boolean isEmpty() {
        return this.items.isEmpty() && this.fluid.isEmpty() && this.outletSide.isEmpty() && !this.ignited;
    }

    public static Optional<HardenedResinCauldronContents> get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        return Optional.ofNullable(stack.get(PlasticraftDataComponents.HARDENED_RESIN_CAULDRON_CONTENTS.get()));
    }

    public static void set(ItemStack stack, HardenedResinCauldronContents contents) {
        if (contents.isEmpty()) {
            clear(stack);
        } else {
            stack.set(PlasticraftDataComponents.HARDENED_RESIN_CAULDRON_CONTENTS.get(), contents);
        }
    }

    public static void clear(ItemStack stack) {
        if (!stack.isEmpty()) stack.remove(PlasticraftDataComponents.HARDENED_RESIN_CAULDRON_CONTENTS.get());
    }

    public static ItemStack withoutContents(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = stack.copy();
        clear(result);
        return result;
    }

    private static HardenedResinCauldronContents decodePersistent(
        List<StoredItem> items,
        FluidStack fluid,
        int outletSide,
        boolean ignited
    ) {
        return new HardenedResinCauldronContents(items, fluid, directionFor(outletSide), ignited);
    }

    private static void encode(RegistryFriendlyByteBuf buffer, HardenedResinCauldronContents contents) {
        buffer.writeVarInt(contents.items.size());
        for (StoredItem item : contents.items) {
            buffer.writeVarInt(item.slot());
            ItemStack.STREAM_CODEC.encode(buffer, item.stack());
        }
        FluidStack.STREAM_CODEC.encode(buffer, contents.fluid);
        buffer.writeByte(contents.outletSide.map(Direction::get3DDataValue).orElse(-1));
        buffer.writeBoolean(contents.ignited);
    }

    private static HardenedResinCauldronContents decode(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > SLOT_COUNT) {
            throw new IllegalArgumentException("Invalid hardened resin cauldron item entry count");
        }
        List<StoredItem> items = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            items.add(new StoredItem(buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer)));
        }
        return new HardenedResinCauldronContents(
            items,
            FluidStack.STREAM_CODEC.decode(buffer),
            directionFor(buffer.readByte()),
            buffer.readBoolean()
        );
    }

    private static Optional<Direction> directionFor(int id) {
        if (id < 0 || id >= Direction.values().length) return Optional.empty();
        Direction direction = Direction.from3DDataValue(id);
        return direction.getAxis().isHorizontal() ? Optional.of(direction) : Optional.empty();
    }

    public record StoredItem(int slot, ItemStack stack) {
        public static final Codec<StoredItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("slot").forGetter(StoredItem::slot),
            ItemStack.CODEC.fieldOf("stack").forGetter(StoredItem::stack)
        ).apply(instance, StoredItem::new));

        public StoredItem {
            if (slot < 0 || slot >= SLOT_COUNT) {
                throw new IllegalArgumentException("Hardened resin cauldron item slot is outside its layout");
            }
            if (stack.isEmpty()) throw new IllegalArgumentException("Hardened resin cauldron item must not be empty");
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
