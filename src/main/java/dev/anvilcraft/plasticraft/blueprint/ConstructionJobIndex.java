package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 服务器级施工任务索引:保存全部已部署蓝图的任务条目,挂在主世界数据存储上。
 * 状态转换只由服务端完成;每名玩家同一时间最多一份 ACTIVE 任务,
 * 启动新任务会把该玩家先前的活动任务置回 INACTIVE。
 */
public final class ConstructionJobIndex extends SavedData {
    private static final String DATA_NAME = AnvilcraftPlasticraft.MOD_ID + "_construction_jobs";
    private static final SavedData.Factory<ConstructionJobIndex> FACTORY = new SavedData.Factory<>(
        ConstructionJobIndex::new,
        ConstructionJobIndex::load,
        null
    );

    private final Map<UUID, ConstructionJob> jobs = new LinkedHashMap<>();

    public static ConstructionJobIndex get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ConstructionJobIndex load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionJobIndex index = new ConstructionJobIndex();
        ListTag jobsTag = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int entry = 0; entry < jobsTag.size(); entry++) {
            ConstructionJob.CODEC
                .parse(NbtOps.INSTANCE, jobsTag.getCompound(entry))
                .resultOrPartial(error ->
                    AnvilcraftPlasticraft.LOGGER.error("Failed to load construction job: {}", error))
                .ifPresent(job -> index.jobs.put(job.jobId(), job));
        }
        return index;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobsTag = new ListTag();
        for (ConstructionJob job : this.jobs.values()) {
            ConstructionJob.CODEC
                .encodeStart(NbtOps.INSTANCE, job)
                .resultOrPartial(error ->
                    AnvilcraftPlasticraft.LOGGER.error("Failed to save construction job: {}", error))
                .ifPresent(jobsTag::add);
        }
        tag.put("Jobs", jobsTag);
        return tag;
    }

    @Nullable
    public ConstructionJob job(UUID jobId) {
        return this.jobs.get(jobId);
    }

    public List<ConstructionJob> jobs() {
        return List.copyOf(this.jobs.values());
    }

    public List<ConstructionJob> jobsIn(ServerLevel level) {
        List<ConstructionJob> result = new ArrayList<>();
        for (ConstructionJob job : this.jobs.values()) {
            if (job.dimension().equals(level.dimension())) result.add(job);
        }
        return result;
    }

    public Optional<ConstructionJob> activeJobOf(UUID owner) {
        for (ConstructionJob job : this.jobs.values()) {
            if (job.isActive() && job.owner().equals(owner)) return Optional.of(job);
        }
        return Optional.empty();
    }

    /** 新建或整体替换一个任务条目。 */
    public void put(ConstructionJob job) {
        this.jobs.put(job.jobId(), job);
        this.setDirty();
    }

    @Nullable
    public ConstructionJob remove(UUID jobId) {
        ConstructionJob removed = this.jobs.remove(jobId);
        if (removed != null) this.setDirty();
        return removed;
    }

    /**
     * 启动一个任务并把同一所有者的其他活动任务置回未启动;
     * 返回状态被暂停的任务列表,调用方据此同步客户端。
     */
    public List<ConstructionJob> activate(UUID jobId) {
        ConstructionJob target = this.jobs.get(jobId);
        if (target == null) return List.of();
        List<ConstructionJob> paused = new ArrayList<>();
        for (Map.Entry<UUID, ConstructionJob> entry : this.jobs.entrySet()) {
            ConstructionJob job = entry.getValue();
            if (!job.jobId().equals(jobId) && job.isActive() && job.owner().equals(target.owner())) {
                ConstructionJob pausedJob = job.withState(ConstructionJob.STATE_INACTIVE);
                entry.setValue(pausedJob);
                paused.add(pausedJob);
            }
        }
        this.jobs.put(jobId, target.withState(ConstructionJob.STATE_PLANNING));
        this.setDirty();
        return paused;
    }

    /** 便捷入口:从任意服务端维度获取索引。 */
    public static ConstructionJobIndex get(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("Construction job index is server side only");
        }
        return get(server);
    }
}
