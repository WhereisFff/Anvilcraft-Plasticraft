package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 世界级施工进度库,与任务索引分开保存操作、台账和已交付集合。 */
public final class ConstructionJobStore extends SavedData {
    private static final String DATA_NAME = AnvilcraftPlasticraft.MOD_ID + "_construction_job_progress";
    private static final SavedData.Factory<ConstructionJobStore> FACTORY = new SavedData.Factory<>(
        ConstructionJobStore::new,
        ConstructionJobStore::load,
        null
    );

    private final Map<UUID, ConstructionJobProgress> jobs = new LinkedHashMap<>();

    public static ConstructionJobStore get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static ConstructionJobStore get(Level level) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            throw new IllegalStateException("Construction job store is server side only");
        }
        return get(server);
    }

    private static ConstructionJobStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructionJobStore store = new ConstructionJobStore();
        ListTag jobsTag = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int entry = 0; entry < jobsTag.size(); entry++) {
            ConstructionJobProgress progress = ConstructionJobProgress.load(jobsTag.getCompound(entry), registries);
            store.jobs.put(progress.jobId(), progress);
        }
        return store;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag jobsTag = new ListTag();
        for (ConstructionJobProgress progress : this.jobs.values()) {
            jobsTag.add(progress.save(registries));
        }
        tag.put("Jobs", jobsTag);
        return tag;
    }

    public ConstructionJobProgress getOrCreate(UUID jobId) {
        return this.jobs.computeIfAbsent(jobId, ConstructionJobProgress::new);
    }

    @Nullable
    public ConstructionJobProgress get(UUID jobId) {
        return this.jobs.get(jobId);
    }

    public void remove(UUID jobId) {
        if (this.jobs.remove(jobId) != null) this.setDirty();
    }

    public void markDirty() {
        this.setDirty();
    }
}
