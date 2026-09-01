package dev.anvilcraft.plasticraft.allay.observation;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 世界级观察加载索引。保存每个持票人的覆盖中心与维度,以及各任务的观察租约。
 *
 * <p>服务器启动时先按本索引恢复区块票,再等实体或休息室方块实体加载后校验资格,
 * 因此索引必须独立于实体存在——依赖悦灵自己唤醒自己会死锁:它所在区块正是要靠这张票才加载。
 *
 * <p>每个区块的引用计数不单独存字段,而是由覆盖记录集合派生,避免计数与记录分叉。
 */
public final class ObservationLoadingIndex extends SavedData {
    private static final String DATA_NAME = AnvilcraftPlasticraft.MOD_ID + "_observation_loading";
    private static final SavedData.Factory<ObservationLoadingIndex> FACTORY = new SavedData.Factory<>(
        ObservationLoadingIndex::new,
        ObservationLoadingIndex::load,
        null
    );

    private final Map<ObservationOwner, ChunkPos> coverage = new LinkedHashMap<>();
    private final Map<UUID, List<ObservationLease>> leases = new LinkedHashMap<>();

    public static ObservationLoadingIndex get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ObservationLoadingIndex get(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("Observation loading index is server side only");
        }
        return get(server);
    }

    private static ObservationLoadingIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        ObservationLoadingIndex index = new ObservationLoadingIndex();
        ListTag coverageTag = tag.getList("Coverage", Tag.TAG_COMPOUND);
        for (int entry = 0; entry < coverageTag.size(); entry++) {
            CompoundTag row = coverageTag.getCompound(entry);
            ResourceKey<Level> dimension = readDimension(row);
            ChunkPos center = new ChunkPos(row.getLong("Center"));
            if (row.hasUUID("Entity")) {
                index.coverage.put(ObservationOwner.observer(dimension, row.getUUID("Entity")), center);
            } else if (row.contains("Lounge", Tag.TAG_LONG)) {
                index.coverage.put(ObservationOwner.lounge(dimension, BlockPos.of(row.getLong("Lounge"))), center);
            }
        }
        // 覆盖记录必须落盘:重启首刻要靠它补票,指望悦灵自己唤醒自己会死锁——它所在区块正是要靠这张票才加载。
        // 观察租约不落盘:它只是"这个窗口交给谁去罩住"的运行时调度结论,任务规划每 20 刻重算一次。
        // 留着会让重启后的任务守着一份持票人可能已经不存在的旧租约,反而谁都不去建立窗口。
        return index;
    }

    private static ResourceKey<Level> readDimension(CompoundTag row) {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(row.getString("Dimension")));
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag coverageTag = new ListTag();
        for (Map.Entry<ObservationOwner, ChunkPos> entry : this.coverage.entrySet()) {
            CompoundTag row = new CompoundTag();
            row.putString("Dimension", entry.getKey().dimension().location().toString());
            if (entry.getKey() instanceof ObservationOwner.Observer observer) {
                row.putUUID("Entity", observer.entityId());
            } else if (entry.getKey() instanceof ObservationOwner.Lounge lounge) {
                row.putLong("Lounge", lounge.pos().asLong());
            }
            row.putLong("Center", entry.getValue().toLong());
            coverageTag.add(row);
        }
        tag.put("Coverage", coverageTag);
        return tag;
    }

    @Nullable
    public ChunkPos center(ObservationOwner owner) {
        return this.coverage.get(owner);
    }

    public boolean has(ObservationOwner owner) {
        return this.coverage.containsKey(owner);
    }

    /** 写入或移动覆盖中心。返回 true 表示确实发生了变化。 */
    public boolean putCoverage(ObservationOwner owner, ChunkPos center) {
        ChunkPos previous = this.coverage.put(owner, center);
        if (center.equals(previous)) return false;
        this.setDirty();
        return true;
    }

    public boolean removeCoverage(ObservationOwner owner) {
        if (this.coverage.remove(owner) == null) return false;
        this.setDirty();
        return true;
    }

    /** 全部持票人快照,遍历期间允许撤票。 */
    public List<ObservationOwner> owners() {
        return List.copyOf(this.coverage.keySet());
    }

    public List<ObservationOwner> owners(ResourceKey<Level> dimension) {
        List<ObservationOwner> result = new ArrayList<>();
        for (ObservationOwner owner : this.coverage.keySet()) {
            if (owner.dimension().equals(dimension)) result.add(owner);
        }
        return result;
    }

    /** 该区块被多少份有效观察覆盖包含。 */
    public int refCount(ResourceKey<Level> dimension, ChunkPos chunk) {
        int count = 0;
        for (Map.Entry<ObservationOwner, ChunkPos> entry : this.coverage.entrySet()) {
            if (!entry.getKey().dimension().equals(dimension)) continue;
            if (ObservationCoverage.covers(entry.getValue(), chunk)) count++;
        }
        return count;
    }

    public boolean covered(ResourceKey<Level> dimension, ChunkPos chunk) {
        return this.refCount(dimension, chunk) > 0;
    }

    public List<ObservationLease> leases(UUID jobId) {
        List<ObservationLease> jobLeases = this.leases.get(jobId);
        return jobLeases == null ? List.of() : List.copyOf(jobLeases);
    }

    public boolean hasLease(UUID jobId, ChunkPos center) {
        List<ObservationLease> jobLeases = this.leases.get(jobId);
        if (jobLeases == null) return false;
        for (ObservationLease lease : jobLeases) {
            if (lease.center().equals(center)) return true;
        }
        return false;
    }

    public void addLease(UUID jobId, ObservationLease lease) {
        List<ObservationLease> jobLeases = this.leases.computeIfAbsent(jobId, key -> new ArrayList<>());
        jobLeases.removeIf(existing -> existing.center().equals(lease.center()));
        jobLeases.add(lease);
        this.setDirty();
    }

    public void removeLease(UUID jobId, ChunkPos center) {
        List<ObservationLease> jobLeases = this.leases.get(jobId);
        if (jobLeases == null) return;
        if (!jobLeases.removeIf(existing -> existing.center().equals(center))) return;
        if (jobLeases.isEmpty()) this.leases.remove(jobId);
        this.setDirty();
    }

    public void removeLeases(UUID jobId) {
        if (this.leases.remove(jobId) != null) this.setDirty();
    }

    /** 观察者失效时清掉它名下的租约,让任务下一刻重新规划。 */
    public void releaseObserverLeases(UUID observer) {
        boolean changed = false;
        Iterator<Map.Entry<UUID, List<ObservationLease>>> iterator = this.leases.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, List<ObservationLease>> entry = iterator.next();
            if (!entry.getValue().removeIf(lease -> observer.equals(lease.observer()))) continue;
            changed = true;
            if (entry.getValue().isEmpty()) iterator.remove();
        }
        if (changed) this.setDirty();
    }

    /** 某只观察悦灵当前的租约,用于避免两份任务抢同一只。 */
    @Nullable
    public ObservationLease leaseOf(UUID observer) {
        for (List<ObservationLease> jobLeases : this.leases.values()) {
            for (ObservationLease lease : jobLeases) {
                if (observer.equals(lease.observer())) return lease;
            }
        }
        return null;
    }

    /** 某只观察悦灵所服务的任务。 */
    @Nullable
    public UUID jobOf(UUID observer) {
        for (Map.Entry<UUID, List<ObservationLease>> entry : this.leases.entrySet()) {
            for (ObservationLease lease : entry.getValue()) {
                if (observer.equals(lease.observer())) return entry.getKey();
            }
        }
        return null;
    }
}
