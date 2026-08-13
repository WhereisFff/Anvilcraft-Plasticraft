package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 任务托管台账:取出的资源在交付或返还前只存在一份。 */
public final class ConstructionLedgerEntry {
    public enum State {
        CARRIED,
        DELIVERED,
        RETURNED
    }

    private final int id;
    private final int operationId;
    private final ItemStack stack;
    @Nullable
    private final UUID droneId;
    private State state;

    public ConstructionLedgerEntry(int id, int operationId, ItemStack stack, @Nullable UUID droneId, State state) {
        this.id = id;
        this.operationId = operationId;
        this.stack = stack.copy();
        this.droneId = droneId;
        this.state = state;
    }

    public int id() {
        return this.id;
    }

    public int operationId() {
        return this.operationId;
    }

    public ItemStack stack() {
        return this.stack;
    }

    @Nullable
    public UUID droneId() {
        return this.droneId;
    }

    public State state() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", this.id);
        tag.putInt("OperationId", this.operationId);
        tag.put("Stack", this.stack.save(registries));
        if (this.droneId != null) {
            tag.putUUID("DroneId", this.droneId);
        }
        tag.putString("State", this.state.name());
        return tag;
    }

    public static ConstructionLedgerEntry load(CompoundTag tag, HolderLookup.Provider registries) {
        ItemStack stack = ItemStack.parse(registries, tag.getCompound("Stack")).orElse(ItemStack.EMPTY);
        UUID droneId = tag.hasUUID("DroneId") ? tag.getUUID("DroneId") : null;
        return new ConstructionLedgerEntry(
            tag.getInt("Id"),
            tag.getInt("OperationId"),
            stack,
            droneId,
            State.valueOf(tag.getString("State"))
        );
    }
}
