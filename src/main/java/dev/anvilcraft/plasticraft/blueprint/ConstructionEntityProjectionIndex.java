package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.network.ConstructionEntityProjectionPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 已交付实体施工投影。不改 ConstructionProjectionSectionPacket 字段序,用独立网络包同步。
 */
public final class ConstructionEntityProjectionIndex {
    private static final Map<Level, Map<UUID, List<Entry>>> LEVELS = new WeakHashMap<>();

    public record Entry(int opId, Vec3 pos, CompoundTag nbt) {
    }

    private ConstructionEntityProjectionIndex() {
    }

    public static void deliver(Level level, UUID jobId, int opId, Vec3 pos, CompoundTag nbt) {
        synchronized (LEVELS) {
            Map<UUID, List<Entry>> jobs = LEVELS.computeIfAbsent(level, ignored -> new HashMap<>());
            List<Entry> updated = new ArrayList<>(jobs.getOrDefault(jobId, List.of()));
            updated.add(new Entry(opId, pos, nbt.copy()));
            jobs.put(jobId, List.copyOf(updated));
        }
        if (level instanceof ServerLevel serverLevel) {
            syncJob(serverLevel, jobId);
        }
    }

    public static void clearJob(Level level, UUID jobId) {
        synchronized (LEVELS) {
            Map<UUID, List<Entry>> jobs = LEVELS.get(level);
            if (jobs == null) {
                return;
            }
            jobs.remove(jobId);
            if (jobs.isEmpty()) {
                LEVELS.remove(level);
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(
                serverLevel,
                ConstructionEntityProjectionPacket.clear(jobId)
            );
        }
    }

    /** 删除单个实体操作的投影并立即同步该任务的新快照。 */
    public static boolean removeOperation(Level level, UUID jobId, int opId) {
        boolean changed = false;
        boolean empty = false;
        synchronized (LEVELS) {
            Map<UUID, List<Entry>> jobs = LEVELS.get(level);
            if (jobs == null) return false;
            List<Entry> entries = jobs.get(jobId);
            if (entries == null) return false;
            List<Entry> filtered = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (entry.opId() == opId) {
                    changed = true;
                } else {
                    filtered.add(entry);
                }
            }
            if (!changed) return false;
            if (filtered.isEmpty()) {
                jobs.remove(jobId);
                empty = true;
            } else {
                jobs.put(jobId, List.copyOf(filtered));
            }
            if (jobs.isEmpty()) LEVELS.remove(level);
        }
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(
                serverLevel,
                empty
                    ? ConstructionEntityProjectionPacket.clear(jobId)
                    : ConstructionEntityProjectionPacket.replace(jobId, delivered(level, jobId))
            );
        }
        return true;
    }

    public static List<Entry> delivered(Level level, UUID jobId) {
        synchronized (LEVELS) {
            Map<UUID, List<Entry>> jobs = LEVELS.get(level);
            if (jobs == null) {
                return List.of();
            }
            List<Entry> entries = jobs.get(jobId);
            return entries == null ? List.of() : entries;
        }
    }

    public static void applyClient(Level level, UUID jobId, boolean clear, List<Entry> entries) {
        synchronized (LEVELS) {
            if (clear) {
                Map<UUID, List<Entry>> jobs = LEVELS.get(level);
                if (jobs != null) {
                    jobs.remove(jobId);
                    if (jobs.isEmpty()) {
                        LEVELS.remove(level);
                    }
                }
                return;
            }
            LEVELS.computeIfAbsent(level, ignored -> new HashMap<>()).put(jobId, List.copyOf(entries));
        }
    }

    public static void syncNearby(ServerLevel level, ServerPlayer player) {
        Map<UUID, List<Entry>> snapshot;
        synchronized (LEVELS) {
            Map<UUID, List<Entry>> jobs = LEVELS.get(level);
            snapshot = jobs == null ? Map.of() : Map.copyOf(jobs);
        }
        for (Map.Entry<UUID, List<Entry>> job : snapshot.entrySet()) {
            PacketDistributor.sendToPlayer(
                player,
                ConstructionEntityProjectionPacket.replace(job.getKey(), job.getValue())
            );
        }
    }

    public static void syncChunk(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
        syncNearby(level, player);
    }

    private static void syncJob(ServerLevel level, UUID jobId) {
        List<Entry> entries = delivered(level, jobId);
        PacketDistributor.sendToPlayersInDimension(
            level,
            ConstructionEntityProjectionPacket.replace(jobId, entries)
        );
    }
}
