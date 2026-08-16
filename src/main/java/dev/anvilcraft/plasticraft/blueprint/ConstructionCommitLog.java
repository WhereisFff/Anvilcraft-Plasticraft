package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;

import java.util.HashSet;
import java.util.Set;

/** 可恢复的分区提交日志,追加在进度 NBT 的 Debris 之后。 */
public final class ConstructionCommitLog {
    public enum Phase {
        NONE,
        STATES,
        BLOCK_ENTITIES,
        MULTIBLOCK,
        BOUNDARY,
        PASTE,
        WIRE_TOPOLOGY,
        WIRE_PORTS,
        ENTITIES,
        FINAL_PASTE,
        FINAL_WIRE_TOPOLOGY,
        FINAL_WIRE_PORTS,
        PUBLISH,
        DONE
    }

    private Phase phase = Phase.NONE;
    private int nextIndex;
    private final Set<Integer> written = new HashSet<>();

    public Phase phase() {
        return this.phase;
    }

    public void setPhase(Phase phase) {
        this.phase = phase;
        this.nextIndex = 0;
    }

    public int nextIndex() {
        return this.nextIndex;
    }

    public void setNextIndex(int nextIndex) {
        this.nextIndex = nextIndex;
    }

    public Set<Integer> written() {
        return this.written;
    }

    public void markWritten(int operationId) {
        this.written.add(operationId);
    }

    public boolean isWritten(int operationId) {
        return this.written.contains(operationId);
    }

    public boolean isIdle() {
        return this.phase == Phase.NONE || this.phase == Phase.DONE;
    }

    public void reset() {
        this.phase = Phase.NONE;
        this.nextIndex = 0;
        this.written.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Phase", this.phase.name());
        tag.putInt("NextIndex", this.nextIndex);
        tag.put("Written", new IntArrayTag(this.written.stream().mapToInt(Integer::intValue).toArray()));
        return tag;
    }

    public static ConstructionCommitLog load(CompoundTag tag) {
        ConstructionCommitLog log = new ConstructionCommitLog();
        if (tag.contains("Phase")) {
            log.phase = Phase.valueOf(tag.getString("Phase"));
        }
        log.nextIndex = tag.getInt("NextIndex");
        for (int id : tag.getIntArray("Written")) {
            log.written.add(id);
        }
        return log;
    }
}
