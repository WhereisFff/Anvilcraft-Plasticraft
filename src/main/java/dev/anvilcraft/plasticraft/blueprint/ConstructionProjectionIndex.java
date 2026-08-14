package dev.anvilcraft.plasticraft.blueprint;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.network.ConstructionProjectionSectionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 已交付施工假方块的区块段索引。世界格保持空气,碰撞与渲染读取这里的目标状态和世界 VoxelShape,
 * 不放置占位方块实体。
 */
public final class ConstructionProjectionIndex {
    private static final Map<Level, LevelIndex> LEVELS = new WeakHashMap<>();
    private static OverlayLookup overlayLookup = (level, jobId) -> Map.of();

    private ConstructionProjectionIndex() {
    }

    @FunctionalInterface
    public interface OverlayLookup {
        Map<Long, BlockState> plannedOverlay(Level level, UUID jobId);
    }

    /** 客户端用已缓存快照补规划目标,公共类不引用 client 包。 */
    public static void setOverlayLookup(OverlayLookup lookup) {
        overlayLookup = lookup == null ? (level, jobId) -> Map.of() : lookup;
    }

    public static VoxelShape projectionShape(BlockState state, BlockGetter view, BlockPos pos) {
        return connect(state, view, pos).getCollisionShape(view, pos, CollisionContext.empty());
    }

    /** 用覆盖邻居补栅栏/墙/门的连接属性;红石导线保持蓝图原样,不按邻居重算。 */
    static BlockState connect(BlockState state, BlockGetter view, BlockPos pos) {
        if (!(view instanceof ConstructionOverlayView overlay)) {
            return state;
        }
        if (ConstructionCommitService.preservesExactState(state)) {
            return state;
        }
        BlockState connected = state;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            connected = connected.updateShape(
                direction,
                overlay.getBlockState(neighbor),
                overlay.level(),
                pos,
                neighbor
            );
        }
        return connected;
    }

    public record Collision(BlockPos pos, VoxelShape worldShape, BlockState state, UUID jobId) {
    }

    public static boolean tryDeliver(
        Level level,
        UUID jobId,
        BlockPos pos,
        BlockState state,
        Map<Long, BlockState> overlay
    ) {
        return tryDeliver(level, jobId, pos, state, overlay, null);
    }

    public static boolean tryDeliver(
        Level level,
        UUID jobId,
        BlockPos pos,
        BlockState state,
        Map<Long, BlockState> overlay,
        @Nullable Entity ignore
    ) {
        VoxelShape local = projectionShape(state, new ConstructionOverlayView(level, overlay), pos);
        VoxelShape worldShape = local.isEmpty() ? Shapes.empty() : local.move(pos.getX(), pos.getY(), pos.getZ());
        if (!worldShape.isEmpty() && isOccupied(level, worldShape, ignore)) {
            return false;
        }
        put(level, jobId, pos.immutable(), state, worldShape);
        if (level instanceof ServerLevel serverLevel) {
            syncSection(serverLevel, jobId, SectionPos.asLong(pos));
        }
        return true;
    }

    public static boolean isOccupied(Level level, VoxelShape worldShape) {
        return isOccupied(level, worldShape, null);
    }

    public static boolean isOccupied(Level level, VoxelShape worldShape, @Nullable Entity ignore) {
        if (worldShape.isEmpty()) return false;
        AABB bounds = worldShape.bounds();
        List<Entity> entities = level.getEntities(ignore, bounds);
        for (Entity entity : entities) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            if (entity instanceof WorkingAllayEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                continue;
            }
            if (Shapes.joinIsNotEmpty(worldShape, Shapes.create(entity.getBoundingBox()), BooleanOp.AND)) {
                return true;
            }
        }
        return false;
    }

    public static void refreshNeighbors(Level level, BlockPos pos, Map<Long, BlockState> overlay) {
        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            Collision existing = at(level, neighbor);
            if (existing == null) continue;
            VoxelShape local = projectionShape(existing.state(), view, neighbor);
            VoxelShape worldShape = local.isEmpty()
                ? Shapes.empty()
                : local.move(neighbor.getX(), neighbor.getY(), neighbor.getZ());
            put(level, existing.jobId(), neighbor, existing.state(), worldShape);
            if (level instanceof ServerLevel serverLevel) {
                syncSection(serverLevel, existing.jobId(), SectionPos.asLong(neighbor));
            }
        }
    }

    public static void put(Level level, UUID jobId, BlockPos pos, BlockState state, VoxelShape worldShape) {
        synchronized (LEVELS) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelIndex()).put(new Collision(
                pos.immutable(),
                worldShape,
                state,
                jobId
            ));
        }
    }

    public static void remove(Level level, BlockPos pos) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            index.remove(pos);
            if (index.isEmpty()) LEVELS.remove(level);
        }
    }

    public static void clearJob(Level level, UUID jobId) {
        List<Long> sections;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            sections = index.removeJob(jobId);
            if (index.isEmpty()) LEVELS.remove(level);
        }
        if (level instanceof ServerLevel serverLevel) {
            for (long section : sections) {
                PacketDistributor.sendToPlayersTrackingChunk(
                    serverLevel,
                    new ChunkPos(SectionPos.x(section), SectionPos.z(section)),
                    ConstructionProjectionSectionPacket.clear(jobId, section)
                );
            }
        }
    }

    public static void clearLevel(Level level) {
        synchronized (LEVELS) {
            LEVELS.remove(level);
        }
    }

    public static boolean has(Level level, BlockPos pos) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index != null && index.get(pos) != null;
        }
    }

    @Nullable
    public static Collision at(BlockGetter getter, BlockPos pos) {
        if (!(getter instanceof Level level)) return null;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? null : index.get(pos);
        }
    }

    public static List<Collision> collisions(BlockGetter getter, AABB bounds) {
        if (!(getter instanceof Level level)) return List.of();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? List.of() : index.query(bounds);
        }
    }

    public static Map<BlockPos, BlockState> deliveredIn(Level level, UUID jobId) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? Map.of() : index.delivered(jobId);
        }
    }

    public static void applyClientSection(
        Level level,
        UUID jobId,
        long section,
        List<BlockPos> positions,
        List<BlockState> states
    ) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.computeIfAbsent(level, ignored -> new LevelIndex());
            index.replaceSection(jobId, section, positions, states, level);
        }
    }

    public static void clearClientSection(Level level, UUID jobId, long section) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            index.clearSection(jobId, section);
            if (index.isEmpty()) LEVELS.remove(level);
        }
    }

    static void syncSection(ServerLevel level, UUID jobId, long section) {
        List<BlockPos> positions = new ArrayList<>();
        List<BlockState> states = new ArrayList<>();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index != null) {
                index.collectSection(jobId, section, positions, states);
            }
        }
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            new ChunkPos(SectionPos.x(section), SectionPos.z(section)),
            new ConstructionProjectionSectionPacket(jobId, section, false, positions, states)
        );
    }

    public static void syncNearby(ServerLevel level, ServerPlayer player) {
        AABB view = player.getBoundingBox().inflate(128.0D);
        Map<UUID, Map<Long, List<Collision>>> grouped = new HashMap<>();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            for (Collision collision : index.query(view)) {
                grouped.computeIfAbsent(collision.jobId(), ignored -> new HashMap<>())
                    .computeIfAbsent(SectionPos.asLong(collision.pos()), ignored -> new ArrayList<>())
                    .add(collision);
            }
        }
        sendGrouped(player, grouped);
    }

    public static void syncChunk(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
        Map<UUID, Map<Long, List<Collision>>> grouped = new HashMap<>();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            AABB chunkBounds = new AABB(
                chunk.getMinBlockX(),
                level.getMinBuildHeight(),
                chunk.getMinBlockZ(),
                chunk.getMaxBlockX() + 1,
                level.getMaxBuildHeight(),
                chunk.getMaxBlockZ() + 1
            );
            for (Collision collision : index.query(chunkBounds)) {
                grouped.computeIfAbsent(collision.jobId(), ignored -> new HashMap<>())
                    .computeIfAbsent(SectionPos.asLong(collision.pos()), ignored -> new ArrayList<>())
                    .add(collision);
            }
        }
        sendGrouped(player, grouped);
    }

    private static void sendGrouped(
        ServerPlayer player,
        Map<UUID, Map<Long, List<Collision>>> grouped
    ) {
        for (Map.Entry<UUID, Map<Long, List<Collision>>> jobEntry : grouped.entrySet()) {
            for (Map.Entry<Long, List<Collision>> sectionEntry : jobEntry.getValue().entrySet()) {
                List<BlockPos> positions = new ArrayList<>();
                List<BlockState> states = new ArrayList<>();
                for (Collision collision : sectionEntry.getValue()) {
                    positions.add(collision.pos());
                    states.add(collision.state());
                }
                PacketDistributor.sendToPlayer(
                    player,
                    new ConstructionProjectionSectionPacket(
                        jobEntry.getKey(),
                        sectionEntry.getKey(),
                        false,
                        positions,
                        states
                    )
                );
            }
        }
    }

    private static final class LevelIndex {
        private final Map<Long, Map<Long, Collision>> bySection = new HashMap<>();

        private void put(Collision collision) {
            long section = SectionPos.asLong(collision.pos());
            this.bySection.computeIfAbsent(section, ignored -> new HashMap<>())
                .put(collision.pos().asLong(), collision);
        }

        private void remove(BlockPos pos) {
            long section = SectionPos.asLong(pos);
            Map<Long, Collision> entries = this.bySection.get(section);
            if (entries == null) return;
            entries.remove(pos.asLong());
            if (entries.isEmpty()) this.bySection.remove(section);
        }

        private @Nullable Collision get(BlockPos pos) {
            Map<Long, Collision> entries = this.bySection.get(SectionPos.asLong(pos));
            return entries == null ? null : entries.get(pos.asLong());
        }

        private boolean isEmpty() {
            return this.bySection.isEmpty();
        }

        private List<Collision> query(AABB bounds) {
            int minY = SectionPos.blockToSectionCoord(Mth.floor(bounds.minY));
            int maxY = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY));
            int minX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
            int maxX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
            int minZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
            int maxZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));
            List<Collision> result = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Map<Long, Collision> entries = this.bySection.get(SectionPos.asLong(x, y, z));
                        if (entries == null) continue;
                        for (Collision collision : entries.values()) {
                            if (collision.worldShape().isEmpty()) continue;
                            if (collision.worldShape().bounds().intersects(bounds)) {
                                result.add(collision);
                            }
                        }
                    }
                }
            }
            return result;
        }

        private List<Long> removeJob(UUID jobId) {
            List<Long> sections = new ArrayList<>();
            this.bySection.entrySet().removeIf(section -> {
                boolean removed = section.getValue().values().removeIf(collision -> collision.jobId().equals(jobId));
                if (removed) sections.add(section.getKey());
                return section.getValue().isEmpty();
            });
            return sections;
        }

        private Map<BlockPos, BlockState> delivered(UUID jobId) {
            Map<BlockPos, BlockState> result = new HashMap<>();
            for (Map<Long, Collision> section : this.bySection.values()) {
                for (Collision collision : section.values()) {
                    if (collision.jobId().equals(jobId)) {
                        result.put(collision.pos(), collision.state());
                    }
                }
            }
            return result;
        }

        private void collectSection(UUID jobId, long section, List<BlockPos> positions, List<BlockState> states) {
            Map<Long, Collision> entries = this.bySection.get(section);
            if (entries == null) return;
            for (Collision collision : entries.values()) {
                if (!collision.jobId().equals(jobId)) continue;
                positions.add(collision.pos());
                states.add(collision.state());
            }
        }

        private void replaceSection(
            UUID jobId,
            long section,
            List<BlockPos> positions,
            List<BlockState> states,
            Level level
        ) {
            Map<Long, Collision> entries = this.bySection.computeIfAbsent(section, ignored -> new HashMap<>());
            entries.values().removeIf(collision -> collision.jobId().equals(jobId));
            Map<Long, BlockState> overlay = new HashMap<>(overlayLookup.plannedOverlay(level, jobId));
            for (Map.Entry<BlockPos, BlockState> delivered : this.delivered(jobId).entrySet()) {
                overlay.put(delivered.getKey().asLong(), delivered.getValue());
            }
            for (int index = 0; index < positions.size(); index++) {
                overlay.put(positions.get(index).asLong(), states.get(index));
            }
            ConstructionOverlayView view = new ConstructionOverlayView(level, overlay);
            for (int index = 0; index < positions.size(); index++) {
                BlockPos pos = positions.get(index);
                BlockState state = states.get(index);
                VoxelShape local = projectionShape(state, view, pos);
                VoxelShape worldShape = local.isEmpty()
                    ? Shapes.empty()
                    : local.move(pos.getX(), pos.getY(), pos.getZ());
                entries.put(pos.asLong(), new Collision(pos.immutable(), worldShape, state, jobId));
            }
            if (entries.isEmpty()) this.bySection.remove(section);
        }

        private void clearSection(UUID jobId, long section) {
            Map<Long, Collision> entries = this.bySection.get(section);
            if (entries == null) return;
            entries.values().removeIf(collision -> collision.jobId().equals(jobId));
            if (entries.isEmpty()) this.bySection.remove(section);
        }
    }
}
