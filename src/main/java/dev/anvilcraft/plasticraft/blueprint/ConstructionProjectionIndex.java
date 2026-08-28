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
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 已交付施工假方块的区块段索引。世界格保持空气,碰撞与渲染读取这里的目标状态和世界 VoxelShape,
 * 不放置占位方块实体。
 */
public final class ConstructionProjectionIndex {
    private static final Map<Level, LevelIndex> LEVELS = new WeakHashMap<>();
    private static final Map<ServerLevel, Set<DirtySection>> DIRTY_SECTIONS = new WeakHashMap<>();
    private static final AtomicLong REVISION_SEQUENCE = new AtomicLong();
    private static OverlayLookup overlayLookup = (level, jobId) -> position -> null;

    private ConstructionProjectionIndex() {
    }

    @FunctionalInterface
    public interface OverlayLookup {
        PlannedOverlay plannedOverlay(Level level, UUID jobId);
    }

    @FunctionalInterface
    public interface PlannedOverlay {
        @Nullable
        BlockState stateAt(long position);
    }

    public record DeliveredSection(long section, long revision, List<Collision> entries) {
        public DeliveredSection {
            List<Collision> sorted = new ArrayList<>(entries);
            sorted.sort(Comparator.comparingLong(collision -> collision.pos().asLong()));
            entries = List.copyOf(sorted);
        }
    }

    public record DeliveredSnapshot(long revision, List<DeliveredSection> sections) {
        private static final DeliveredSnapshot EMPTY = new DeliveredSnapshot(0L, List.of());

        public DeliveredSnapshot {
            sections = List.copyOf(sections);
        }
    }

    /** 客户端用已缓存快照补规划目标,公共类不引用 client 包。 */
    public static void setOverlayLookup(OverlayLookup lookup) {
        overlayLookup = lookup == null ? (level, jobId) -> position -> null : lookup;
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

    /**
     * @param pendingDecorations 该位置尚未交付的告示牌加工位掩码,见 {@link SignDecorationAdapter#maskOf(int)};
     *                           0 表示按目标状态原样渲染,不涉及告示牌的投影一律为 0。
     */
    public record Collision(
        BlockPos pos,
        VoxelShape worldShape,
        BlockState state,
        UUID jobId,
        int pendingDecorations
    ) {
        public Collision(BlockPos pos, VoxelShape worldShape, BlockState state, UUID jobId) {
            this(pos, worldShape, state, jobId, 0);
        }
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
        Collision existing = at(level, pos);
        if (existing != null && !existing.jobId().equals(jobId)) {
            return false;
        }
        VoxelShape local = projectionShape(state, new ConstructionOverlayView(level, overlay), pos);
        VoxelShape worldShape = local.isEmpty() ? Shapes.empty() : local.move(pos.getX(), pos.getY(), pos.getZ());
        if (!worldShape.isEmpty() && isOccupied(level, worldShape, ignore, state)) {
            return false;
        }
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.computeIfAbsent(level, ignored -> new LevelIndex());
            Collision current = index.get(pos);
            if (current != null && !current.jobId().equals(jobId)) {
                return false;
            }
            index.put(new Collision(pos.immutable(), worldShape, state, jobId));
        }
        if (level instanceof ServerLevel serverLevel) {
            markDirty(serverLevel, jobId, SectionPos.asLong(pos));
        }
        return true;
    }

    public static boolean isOccupied(Level level, VoxelShape worldShape) {
        return isOccupied(level, worldShape, null);
    }

    public static boolean isOccupied(Level level, VoxelShape worldShape, @Nullable Entity ignore) {
        return isOccupied(level, worldShape, ignore, null);
    }

    public static boolean isOccupied(
        Level level,
        VoxelShape worldShape,
        @Nullable Entity ignore,
        @Nullable BlockState placing
    ) {
        if (worldShape.isEmpty()) return false;
        AABB bounds = worldShape.bounds();
        List<Entity> entities = level.getEntities(ignore, bounds);
        for (Entity entity : entities) {
            if (!entity.isAlive() || entity.isSpectator()) continue;
            if (entity instanceof WorkingAllayEntity || entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
                continue;
            }
            if (ignoresOccupant(placing, entity)) {
                continue;
            }
            if (Shapes.joinIsNotEmpty(worldShape, Shapes.create(entity.getBoundingBox()), BooleanOp.AND)) {
                return true;
            }
        }
        return false;
    }

    /** 铁轨本来就可以铺在矿车底下;把矿车当占用会让悦灵对着同一格反复 A*。 */
    static boolean ignoresOccupant(@Nullable BlockState placing, Entity entity) {
        return placing != null
            && placing.getBlock() instanceof BaseRailBlock
            && entity instanceof AbstractMinecart;
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
            put(level, existing.jobId(), neighbor, existing.state(), worldShape, existing.pendingDecorations());
            if (level instanceof ServerLevel serverLevel) {
                markDirty(serverLevel, existing.jobId(), SectionPos.asLong(neighbor));
            }
        }
    }

    public static void put(Level level, UUID jobId, BlockPos pos, BlockState state, VoxelShape worldShape) {
        put(level, jobId, pos, state, worldShape, 0);
    }

    public static void put(
        Level level,
        UUID jobId,
        BlockPos pos,
        BlockState state,
        VoxelShape worldShape,
        int pendingDecorations
    ) {
        synchronized (LEVELS) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelIndex()).put(new Collision(
                pos.immutable(),
                worldShape,
                state,
                jobId,
                pendingDecorations
            ));
        }
    }

    /**
     * 更新已交付假告示牌的未加工掩码。重新入索引会顶起该区段 revision,客户端 BER 缓存据此失效重建,
     * 因此不必再单独通知渲染层。世界已满足的告示牌没有投影条目,直接忽略。
     */
    public static void setPendingDecorations(Level level, UUID jobId, BlockPos pos, int pendingDecorations) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            Collision current = index == null ? null : index.get(pos);
            if (current == null || !current.jobId().equals(jobId)) return;
            if (current.pendingDecorations() == pendingDecorations) return;
            index.put(new Collision(
                current.pos(),
                current.worldShape(),
                current.state(),
                jobId,
                pendingDecorations
            ));
        }
        if (level instanceof ServerLevel serverLevel) {
            markDirty(serverLevel, jobId, SectionPos.asLong(pos));
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

    /** 只移除指定任务的投影，避免取消一个任务时误删相邻任务的同格索引。 */
    public static boolean remove(Level level, UUID jobId, BlockPos pos) {
        boolean removed = false;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            Collision collision = index == null ? null : index.get(pos);
            if (collision == null || !collision.jobId().equals(jobId)) return false;
            index.remove(pos);
            removed = true;
            if (index.isEmpty()) LEVELS.remove(level);
        }
        if (removed && level instanceof ServerLevel serverLevel) {
            markDirty(serverLevel, jobId, SectionPos.asLong(pos));
        }
        return removed;
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
            synchronized (DIRTY_SECTIONS) {
                Set<DirtySection> dirty = DIRTY_SECTIONS.get(serverLevel);
                if (dirty != null) {
                    dirty.removeIf(section -> section.jobId.equals(jobId));
                    if (dirty.isEmpty()) DIRTY_SECTIONS.remove(serverLevel);
                }
            }
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
        if (level instanceof ServerLevel serverLevel) {
            synchronized (DIRTY_SECTIONS) {
                DIRTY_SECTIONS.remove(serverLevel);
            }
        }
    }

    public static boolean has(Level level, BlockPos pos) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index != null && index.get(pos) != null;
        }
    }

    public static boolean has(Level level, UUID jobId, BlockPos pos) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            Collision collision = index == null ? null : index.get(pos);
            return collision != null && collision.jobId().equals(jobId);
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

    /**
     * 返回可跨帧持有的任务快照。revision 只在该任务内容变化时递增;
     * 区段 revision 可直接作为渲染网格的失效键。
     */
    public static DeliveredSnapshot deliveredSnapshot(Level level, UUID jobId) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? DeliveredSnapshot.EMPTY : index.snapshot(jobId);
        }
    }

    public static void applyClientSection(
        Level level,
        UUID jobId,
        long section,
        List<BlockPos> positions,
        List<BlockState> states,
        Map<Long, Integer> pendingDecorations
    ) {
        if (positions.size() != states.size()) return;
        for (BlockPos pos : positions) {
            if (SectionPos.asLong(pos) != section) return;
        }

        PlannedOverlay planned = overlayLookup.plannedOverlay(level, jobId);
        Set<Long> relevantPositions = new HashSet<>(positions.size() * 4);
        for (BlockPos pos : positions) {
            relevantPositions.add(pos.asLong());
            for (Direction direction : Direction.values()) {
                relevantPositions.add(pos.relative(direction).asLong());
            }
        }

        Map<Long, BlockState> overlay = new HashMap<>(relevantPositions.size());
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index != null) {
                index.collectOverlay(jobId, section, relevantPositions, overlay);
            }
        }
        for (int index = 0; index < positions.size(); index++) {
            overlay.put(positions.get(index).asLong(), states.get(index));
        }

        ConstructionOverlayView view = new ConstructionOverlayView(level, overlay, planned);
        List<Collision> collisions = new ArrayList<>(positions.size());
        for (int index = 0; index < positions.size(); index++) {
            BlockPos pos = positions.get(index);
            BlockState state = states.get(index);
            VoxelShape local = projectionShape(state, view, pos);
            VoxelShape worldShape = local.isEmpty()
                ? Shapes.empty()
                : local.move(pos.getX(), pos.getY(), pos.getZ());
            collisions.add(new Collision(
                pos.immutable(),
                worldShape,
                state,
                jobId,
                pendingDecorations.getOrDefault(pos.asLong(), 0)
            ));
        }
        collisions.sort(Comparator.comparingLong(collision -> collision.pos().asLong()));
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.computeIfAbsent(level, ignored -> new LevelIndex());
            index.replaceSection(jobId, section, collisions);
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
        List<Collision> entries = new ArrayList<>();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index != null) {
                index.collectSection(jobId, section, entries);
            }
        }
        PacketDistributor.sendToPlayersTrackingChunk(
            level,
            new ChunkPos(SectionPos.x(section), SectionPos.z(section)),
            sectionPacket(jobId, section, entries)
        );
    }

    private static ConstructionProjectionSectionPacket sectionPacket(
        UUID jobId,
        long section,
        List<Collision> entries
    ) {
        List<BlockPos> positions = new ArrayList<>(entries.size());
        List<BlockState> states = new ArrayList<>(entries.size());
        List<ConstructionProjectionSectionPacket.SignMask> masks = new ArrayList<>();
        for (Collision collision : entries) {
            positions.add(collision.pos());
            states.add(collision.state());
            if (collision.pendingDecorations() != 0) {
                masks.add(new ConstructionProjectionSectionPacket.SignMask(
                    collision.pos(),
                    collision.pendingDecorations()
                ));
            }
        }
        return new ConstructionProjectionSectionPacket(jobId, section, false, positions, states, masks);
    }

    /**
     * 每刻最多同步的区段数。载荷已按段内调色板与位集压缩,但 64 只悦灵同刻交付仍可能脏掉几十个区段,
     * 一次全发会把整份蓝图的可见区段塞进同一刻;超出预算的区段留在队列里下一刻继续。
     */
    private static final int MAX_SECTIONS_PER_TICK = 16;

    public static void flushDirty(ServerLevel level) {
        List<DirtySection> dirty;
        synchronized (DIRTY_SECTIONS) {
            Set<DirtySection> pending = DIRTY_SECTIONS.get(level);
            if (pending == null || pending.isEmpty()) return;
            // 没有玩家看得到的区段直接丢弃:玩家开始追踪该区块时 ChunkWatchEvent.Sent 会补一份完整快照
            pending.removeIf(section -> visibleDistanceSqr(level, section.section) < 0.0D);
            if (pending.size() <= MAX_SECTIONS_PER_TICK) {
                dirty = List.copyOf(pending);
                DIRTY_SECTIONS.remove(level);
            } else {
                // 就近可见优先:远处区段晚一刻到达看不出来,近处区段晚到会让玩家撞进看不见的假方块
                List<DirtySection> ordered = new ArrayList<>(pending);
                ordered.sort(Comparator.comparingDouble(section -> visibleDistanceSqr(level, section.section)));
                dirty = List.copyOf(ordered.subList(0, MAX_SECTIONS_PER_TICK));
                dirty.forEach(pending::remove);
            }
        }
        for (DirtySection section : dirty) {
            syncSection(level, section.jobId, section.section);
        }
    }

    /** 该区段到最近玩家的水平距离平方;没有任何玩家能看到时返回负值。 */
    private static double visibleDistanceSqr(ServerLevel level, long section) {
        double centerX = SectionPos.sectionToBlockCoord(SectionPos.x(section)) + 8.0D;
        double centerZ = SectionPos.sectionToBlockCoord(SectionPos.z(section)) + 8.0D;
        double limit = level.getServer().getPlayerList().getViewDistance() * 16.0D + 32.0D;
        double best = -1.0D;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - centerX;
            double dz = player.getZ() - centerZ;
            double distance = dx * dx + dz * dz;
            if (distance > limit * limit) continue;
            if (best < 0.0D || distance < best) best = distance;
        }
        return best;
    }

    public static boolean isOwnedBy(Level level, BlockPos pos, UUID jobId) {
        Collision collision = at(level, pos);
        return collision != null && collision.jobId().equals(jobId);
    }

    private static void markDirty(ServerLevel level, UUID jobId, long section) {
        synchronized (DIRTY_SECTIONS) {
            DIRTY_SECTIONS.computeIfAbsent(level, ignored -> new HashSet<>())
                .add(new DirtySection(jobId, section));
        }
    }

    public static void syncNearby(ServerLevel level, ServerPlayer player) {
        AABB view = player.getBoundingBox().inflate(128.0D);
        List<Collision> collisions;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            collisions = index.query(view);
        }
        Map<UUID, Map<Long, List<Collision>>> grouped = groupByJobAndSection(collisions);
        sendGrouped(player, grouped);
    }

    public static void syncChunk(ServerLevel level, ServerPlayer player, ChunkPos chunk) {
        AABB chunkBounds = new AABB(
            chunk.getMinBlockX(),
            level.getMinBuildHeight(),
            chunk.getMinBlockZ(),
            chunk.getMaxBlockX() + 1,
            level.getMaxBuildHeight(),
            chunk.getMaxBlockZ() + 1
        );
        List<Collision> collisions;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            collisions = index.query(chunkBounds);
        }
        Map<UUID, Map<Long, List<Collision>>> grouped = groupByJobAndSection(collisions);
        sendGrouped(player, grouped);
    }

    private static Map<UUID, Map<Long, List<Collision>>> groupByJobAndSection(List<Collision> collisions) {
        Map<UUID, Map<Long, List<Collision>>> grouped = new HashMap<>();
        for (Collision collision : collisions) {
            grouped.computeIfAbsent(collision.jobId(), ignored -> new HashMap<>())
                .computeIfAbsent(SectionPos.asLong(collision.pos()), ignored -> new ArrayList<>())
                .add(collision);
        }
        return grouped;
    }

    private static void sendGrouped(
        ServerPlayer player,
        Map<UUID, Map<Long, List<Collision>>> grouped
    ) {
        for (Map.Entry<UUID, Map<Long, List<Collision>>> jobEntry : grouped.entrySet()) {
            for (Map.Entry<Long, List<Collision>> sectionEntry : jobEntry.getValue().entrySet()) {
                PacketDistributor.sendToPlayer(
                    player,
                    sectionPacket(jobEntry.getKey(), sectionEntry.getKey(), sectionEntry.getValue())
                );
            }
        }
    }

    private static final class LevelIndex {
        private final Map<Long, Map<Long, Collision>> bySection = new HashMap<>();
        private final Map<UUID, JobData> byJob = new HashMap<>();

        private void put(Collision collision) {
            long section = SectionPos.asLong(collision.pos());
            Map<Long, Collision> entries = this.bySection.computeIfAbsent(section, ignored -> new HashMap<>());
            Collision previous = entries.put(collision.pos().asLong(), collision);
            if (previous == null) {
                this.changed(collision.jobId(), section, 1);
            } else if (previous.jobId().equals(collision.jobId())) {
                this.changed(collision.jobId(), section, 0);
            } else {
                this.changed(previous.jobId(), section, -1);
                this.changed(collision.jobId(), section, 1);
            }
        }

        private void remove(BlockPos pos) {
            long section = SectionPos.asLong(pos);
            Map<Long, Collision> entries = this.bySection.get(section);
            if (entries == null) return;
            Collision removed = entries.remove(pos.asLong());
            if (entries.isEmpty()) this.bySection.remove(section);
            if (removed != null) this.changed(removed.jobId(), section, -1);
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
                int before = section.getValue().size();
                section.getValue().values().removeIf(collision -> collision.jobId().equals(jobId));
                int removed = before - section.getValue().size();
                if (removed > 0) {
                    sections.add(section.getKey());
                    this.changed(jobId, section.getKey(), -removed);
                }
                return section.getValue().isEmpty();
            });
            return sections;
        }

        private Map<BlockPos, BlockState> delivered(UUID jobId) {
            JobData job = this.byJob.get(jobId);
            if (job == null) return Map.of();
            if (job.delivered != null) return job.delivered;
            Map<BlockPos, BlockState> result = new HashMap<>();
            for (DeliveredSection section : this.snapshot(jobId).sections()) {
                for (Collision collision : section.entries()) {
                    result.put(collision.pos(), collision.state());
                }
            }
            job.delivered = Map.copyOf(result);
            return job.delivered;
        }

        private DeliveredSnapshot snapshot(UUID jobId) {
            JobData job = this.byJob.get(jobId);
            if (job == null) return DeliveredSnapshot.EMPTY;
            if (job.snapshot != null) return job.snapshot;
            List<DeliveredSection> sections = new ArrayList<>(job.sectionRevisions.size());
            for (Map.Entry<Long, Long> revision : job.sectionRevisions.entrySet()) {
                long section = revision.getKey();
                DeliveredSection cached = job.sectionSnapshots.get(section);
                long sectionRevision = revision.getValue();
                if (cached == null || cached.revision() != sectionRevision) {
                    List<Collision> entries = new ArrayList<>();
                    Map<Long, Collision> indexed = this.bySection.get(section);
                    if (indexed != null) {
                        for (Collision collision : indexed.values()) {
                            if (collision.jobId().equals(jobId)) entries.add(collision);
                        }
                    }
                    cached = new DeliveredSection(section, sectionRevision, entries);
                    job.sectionSnapshots.put(section, cached);
                }
                sections.add(cached);
            }
            job.snapshot = new DeliveredSnapshot(job.revision, sections);
            return job.snapshot;
        }

        private void collectSection(UUID jobId, long section, List<Collision> entries) {
            Map<Long, Collision> indexed = this.bySection.get(section);
            if (indexed == null) return;
            for (Collision collision : indexed.values()) {
                if (!collision.jobId().equals(jobId)) continue;
                entries.add(collision);
            }
        }

        private void collectOverlay(
            UUID jobId,
            long replacedSection,
            Set<Long> positions,
            Map<Long, BlockState> overlay
        ) {
            for (long position : positions) {
                BlockPos pos = BlockPos.of(position);
                if (SectionPos.asLong(pos) == replacedSection) continue;
                Collision collision = this.get(pos);
                if (collision != null && collision.jobId().equals(jobId)) {
                    overlay.put(position, collision.state());
                }
            }
        }

        private void replaceSection(UUID jobId, long section, List<Collision> replacements) {
            Map<Long, Collision> entries = this.bySection.computeIfAbsent(section, ignored -> new HashMap<>());
            Map<UUID, Integer> countChanges = new HashMap<>();
            int before = entries.size();
            entries.values().removeIf(collision -> collision.jobId().equals(jobId));
            int removed = before - entries.size();
            if (removed > 0) countChanges.put(jobId, -removed);
            for (Collision collision : replacements) {
                Collision previous = entries.put(collision.pos().asLong(), collision);
                if (previous != null) countChanges.merge(previous.jobId(), -1, Integer::sum);
                countChanges.merge(collision.jobId(), 1, Integer::sum);
            }
            if (removed > 0 || !replacements.isEmpty()) {
                countChanges.putIfAbsent(jobId, 0);
            }
            if (entries.isEmpty()) this.bySection.remove(section);
            for (Map.Entry<UUID, Integer> change : countChanges.entrySet()) {
                this.changed(change.getKey(), section, change.getValue());
            }
            JobData job = this.byJob.get(jobId);
            if (job != null && job.sectionCounts.getOrDefault(section, 0) == replacements.size()) {
                long sectionRevision = job.sectionRevisions.get(section);
                job.sectionSnapshots.put(
                    section,
                    new DeliveredSection(section, sectionRevision, replacements)
                );
            }
        }

        private void clearSection(UUID jobId, long section) {
            Map<Long, Collision> entries = this.bySection.get(section);
            if (entries == null) return;
            int before = entries.size();
            boolean removed = entries.values().removeIf(collision -> collision.jobId().equals(jobId));
            if (entries.isEmpty()) this.bySection.remove(section);
            if (removed) this.changed(jobId, section, entries.size() - before);
        }

        private void changed(UUID jobId, long section, int countChange) {
            JobData job = this.byJob.get(jobId);
            if (job == null && countChange <= 0) return;
            if (job == null) {
                job = new JobData();
                this.byJob.put(jobId, job);
            }
            int count = job.sectionCounts.getOrDefault(section, 0) + countChange;
            if (count > 0) {
                job.sectionCounts.put(section, count);
            } else {
                job.sectionCounts.remove(section);
            }
            long nextRevision = REVISION_SEQUENCE.incrementAndGet();
            if (count > 0) {
                job.sectionRevisions.put(section, nextRevision);
            } else {
                job.sectionRevisions.remove(section);
            }
            job.sectionSnapshots.remove(section);
            job.revision = nextRevision;
            job.snapshot = null;
            job.delivered = null;
            if (job.sectionRevisions.isEmpty()) this.byJob.remove(jobId);
        }

        private static final class JobData {
            private final Map<Long, Integer> sectionCounts = new HashMap<>();
            private final Map<Long, Long> sectionRevisions = new TreeMap<>();
            private final Map<Long, DeliveredSection> sectionSnapshots = new HashMap<>();
            private long revision;
            private @Nullable DeliveredSnapshot snapshot;
            private @Nullable Map<BlockPos, BlockState> delivered;
        }
    }

    private record DirtySection(UUID jobId, long section) {
    }
}
