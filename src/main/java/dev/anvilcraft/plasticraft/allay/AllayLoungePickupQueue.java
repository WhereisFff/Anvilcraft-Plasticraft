package dev.anvilcraft.plasticraft.allay;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AllayLoungePickupQueue {
    private static final List<Direction> SIDES = List.of(
        Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    );

    private final EnumMap<Direction, Entry> active = new EnumMap<>(Direction.class);
    private final LinkedHashMap<UUID, ItemStack> pending = new LinkedHashMap<>();
    private int nextSide;

    public boolean offer(UUID owner, ItemStack stack, long gameTime) {
        if (stack.isEmpty()) return remove(owner, gameTime);
        for (Map.Entry<Direction, Entry> slot : this.active.entrySet()) {
            Entry current = slot.getValue();
            if (!owner.equals(current.owner())) continue;
            boolean sameItem = ItemStack.isSameItemSameComponents(current.stack(), stack);
            slot.setValue(new Entry(owner, combine(current.stack(), stack),
                sameItem ? current.startedAt() : gameTime));
            return true;
        }
        ItemStack previous = this.pending.get(owner);
        this.pending.put(owner, previous == null ? stack.copy() : combine(previous, stack));
        return promote(gameTime);
    }

    public boolean remove(UUID owner, long gameTime) {
        this.pending.remove(owner);
        boolean changed = this.active.entrySet().removeIf(slot -> owner.equals(slot.getValue().owner()));
        return promote(gameTime) || changed;
    }

    public boolean clear() {
        boolean changed = !this.active.isEmpty();
        this.active.clear();
        this.pending.clear();
        return changed;
    }

    public boolean promote(long gameTime) {
        boolean changed = false;
        while (!this.pending.isEmpty()) {
            int available = nextAvailable();
            if (available < 0) break;
            Map.Entry<UUID, ItemStack> request = this.pending.pollFirstEntry();
            if (request == null) break;
            this.active.put(SIDES.get(available), new Entry(request.getKey(), request.getValue(), gameTime));
            this.nextSide = (available + 1) % SIDES.size();
            changed = true;
        }
        return changed;
    }

    private int nextAvailable() {
        for (int offset = 0; offset < SIDES.size(); offset++) {
            int index = (this.nextSide + offset) % SIDES.size();
            if (!this.active.containsKey(SIDES.get(index))) return index;
        }
        return -1;
    }

    private static ItemStack combine(ItemStack previous, ItemStack added) {
        ItemStack result = added.copy();
        // 同一只悦灵的一批材料共用一个出口，追加数量不重新播放滑出。
        if (ItemStack.isSameItemSameComponents(previous, added)) result.grow(previous.getCount());
        return result;
    }

    public ItemStack display(Direction side) {
        Entry entry = this.active.get(side);
        return entry == null ? ItemStack.EMPTY : entry.stack();
    }

    public long startedAt(Direction side, long fallback) {
        Entry entry = this.active.get(side);
        return entry == null ? fallback : entry.startedAt();
    }

    @Nullable
    public Direction sideFor(UUID owner) {
        for (Map.Entry<Direction, Entry> entry : this.active.entrySet()) {
            if (owner.equals(entry.getValue().owner())) return entry.getKey();
        }
        return null;
    }

    public Map<Direction, ItemStack> displays() {
        EnumMap<Direction, ItemStack> result = new EnumMap<>(Direction.class);
        this.active.forEach((side, entry) -> result.put(side, entry.stack()));
        return Map.copyOf(result);
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries, boolean includePending) {
        CompoundTag displays = new CompoundTag();
        CompoundTag starts = new CompoundTag();
        CompoundTag owners = new CompoundTag();
        this.active.forEach((side, entry) -> {
            String name = side.getSerializedName();
            displays.put(name, entry.stack().save(registries));
            starts.putLong(name, entry.startedAt());
            owners.putUUID(name, entry.owner());
        });
        if (!displays.isEmpty()) {
            tag.put("PickupDisplays", displays);
            tag.put("PickupDisplayStarts", starts);
            tag.put("PickupDisplayOwners", owners);
        }
        if (!includePending) return;
        tag.putInt("PickupNextSide", this.nextSide);
        ListTag waiting = new ListTag();
        this.pending.forEach((owner, stack) -> {
            CompoundTag request = new CompoundTag();
            request.putUUID("Owner", owner);
            request.put("Stack", stack.save(registries));
            waiting.add(request);
        });
        if (!waiting.isEmpty()) tag.put("PickupDisplayQueue", waiting);
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        this.active.clear();
        this.pending.clear();
        this.nextSide = Math.floorMod(tag.getInt("PickupNextSide"), SIDES.size());
        CompoundTag displays = tag.getCompound("PickupDisplays");
        CompoundTag starts = tag.getCompound("PickupDisplayStarts");
        CompoundTag owners = tag.getCompound("PickupDisplayOwners");
        for (Direction side : SIDES) {
            String name = side.getSerializedName();
            if (!owners.hasUUID(name)) continue;
            ItemStack.parse(registries, displays.getCompound(name)).ifPresent(stack -> {
                if (!stack.isEmpty()) {
                    this.active.put(side, new Entry(owners.getUUID(name), stack, starts.getLong(name)));
                }
            });
        }
        ListTag waiting = tag.getList("PickupDisplayQueue", Tag.TAG_COMPOUND);
        for (int index = 0; index < waiting.size(); index++) {
            CompoundTag request = waiting.getCompound(index);
            if (!request.hasUUID("Owner")) continue;
            UUID owner = request.getUUID("Owner");
            if (sideFor(owner) != null) continue;
            ItemStack.parse(registries, request.getCompound("Stack")).ifPresent(stack -> {
                if (!stack.isEmpty()) this.pending.put(owner, stack);
            });
        }
    }

    private record Entry(UUID owner, ItemStack stack, long startedAt) {
    }
}
