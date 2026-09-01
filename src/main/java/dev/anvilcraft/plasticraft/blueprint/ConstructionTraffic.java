package dev.anvilcraft.plasticraft.blueprint;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** 接近位、撤离段和休息室预约只排斥最终工位,窄廊只提示让行;所有预约均不注入寻路碰撞。 */
public final class ConstructionTraffic {
    private static final Map<Level, LevelTraffic> LEVELS = new WeakHashMap<>();

    private ConstructionTraffic() {
    }

    public static void reserveApproach(Level level, UUID allay, BlockPos pos) {
        traffic(level).reserve(allay, Kind.APPROACH, List.of(pos.immutable()));
    }

    public static void reserveEvacuation(Level level, UUID allay, List<BlockPos> cells) {
        traffic(level).reserve(allay, Kind.EVACUATION, copy(cells));
    }

    public static void reserveCorridor(Level level, UUID allay, List<BlockPos> cells) {
        traffic(level).reserve(allay, Kind.CORRIDOR, copy(cells));
    }

    public static void reserveLounge(Level level, UUID allay, BlockPos pos) {
        traffic(level).reserve(allay, Kind.LOUNGE, List.of(pos.immutable()));
    }

    public static void release(Level level, UUID allay) {
        synchronized (LEVELS) {
            LevelTraffic traffic = LEVELS.get(level);
            if (traffic != null) traffic.release(allay);
        }
    }

    public static boolean isReserved(Level level, BlockPos pos, @Nullable UUID except) {
        synchronized (LEVELS) {
            LevelTraffic traffic = LEVELS.get(level);
            return traffic != null && traffic.reserved(pos.asLong(), except);
        }
    }

    public static boolean isNarrow(ServerLevel level, BlockPos pos) {
        if (!ConstructionWorkerSpace.fitsGeometry(level, pos)) return false;
        int open = 0;
        for (Direction direction : Direction.values()) {
            if (ConstructionWorkerSpace.fitsGeometry(level, pos.relative(direction))) {
                open++;
            }
        }
        return open <= 2;
    }

    public static List<BlockPos> narrowCells(ServerLevel level, List<BlockPos> path) {
        List<BlockPos> narrow = new ArrayList<>();
        for (BlockPos pos : path) {
            if (isNarrow(level, pos)) narrow.add(pos);
        }
        return narrow;
    }

    private static LevelTraffic traffic(Level level) {
        synchronized (LEVELS) {
            return LEVELS.computeIfAbsent(level, ignored -> new LevelTraffic());
        }
    }

    private static List<BlockPos> copy(List<BlockPos> cells) {
        List<BlockPos> copied = new ArrayList<>(cells.size());
        for (BlockPos pos : cells) {
            copied.add(pos.immutable());
        }
        return copied;
    }

    private enum Kind {
        APPROACH(true),
        EVACUATION(true),
        CORRIDOR(false),
        LOUNGE(true);

        private final boolean reservesEndpoint;

        Kind(boolean reservesEndpoint) {
            this.reservesEndpoint = reservesEndpoint;
        }
    }

    private static final class LevelTraffic {
        private final Map<UUID, Map<Kind, List<BlockPos>>> byAllay = new HashMap<>();
        private final Map<Kind, Map<Long, Set<UUID>>> ownersByKind = new EnumMap<>(Kind.class);

        private LevelTraffic() {
            for (Kind kind : Kind.values()) {
                this.ownersByKind.put(kind, new HashMap<>());
            }
        }

        private void reserve(UUID allay, Kind kind, List<BlockPos> cells) {
            Map<Kind, List<BlockPos>> current = this.byAllay.computeIfAbsent(allay, ignored -> new HashMap<>());
            List<BlockPos> previous = current.put(kind, cells);
            if (previous != null) {
                for (BlockPos pos : previous) {
                    this.drop(kind, pos.asLong(), allay);
                }
            }
            Map<Long, Set<UUID>> owners = this.ownersByKind.get(kind);
            for (BlockPos pos : cells) {
                owners.computeIfAbsent(pos.asLong(), ignored -> new HashSet<>()).add(allay);
            }
        }

        private void release(UUID allay) {
            Map<Kind, List<BlockPos>> current = this.byAllay.remove(allay);
            if (current == null) return;
            for (Map.Entry<Kind, List<BlockPos>> entry : current.entrySet()) {
                for (BlockPos pos : entry.getValue()) {
                    this.drop(entry.getKey(), pos.asLong(), allay);
                }
            }
        }

        private void drop(Kind kind, long key, UUID allay) {
            Map<Long, Set<UUID>> owners = this.ownersByKind.get(kind);
            Set<UUID> holders = owners.get(key);
            if (holders == null) return;
            holders.remove(allay);
            if (holders.isEmpty()) owners.remove(key);
        }

        private boolean reserved(long key, @Nullable UUID except) {
            for (Kind kind : Kind.values()) {
                if (!kind.reservesEndpoint) continue;
                if (heldByOther(this.ownersByKind.get(kind).get(key), except)) return true;
            }
            return false;
        }

        private static boolean heldByOther(@Nullable Set<UUID> holders, @Nullable UUID except) {
            if (holders == null || holders.isEmpty()) return false;
            return except == null || holders.size() > 1 || !holders.contains(except);
        }
    }
}
