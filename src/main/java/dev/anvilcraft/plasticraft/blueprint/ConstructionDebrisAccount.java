package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.nbt.CompoundTag;

/** 一份拆除操作产生的掉落对账:已生成、已收集与外部结算,不复制补发。 */
public final class ConstructionDebrisAccount {
    private final int operationId;
    private int spawned;
    private int collected;
    private int external;

    public ConstructionDebrisAccount(int operationId) {
        this.operationId = operationId;
    }

    public int operationId() {
        return this.operationId;
    }

    public int spawned() {
        return this.spawned;
    }

    public int collected() {
        return this.collected;
    }

    public int external() {
        return this.external;
    }

    public void addSpawned(int count) {
        if (count > 0) this.spawned += count;
    }

    public void addCollected(int count) {
        if (count > 0) this.collected += count;
    }

    public void addExternal(int count) {
        if (count > 0) this.external += count;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("OperationId", this.operationId);
        tag.putInt("Spawned", this.spawned);
        tag.putInt("Collected", this.collected);
        tag.putInt("External", this.external);
        return tag;
    }

    public static ConstructionDebrisAccount load(CompoundTag tag) {
        ConstructionDebrisAccount account = new ConstructionDebrisAccount(tag.getInt("OperationId"));
        account.spawned = tag.getInt("Spawned");
        account.collected = tag.getInt("Collected");
        account.external = tag.getInt("External");
        return account;
    }
}
