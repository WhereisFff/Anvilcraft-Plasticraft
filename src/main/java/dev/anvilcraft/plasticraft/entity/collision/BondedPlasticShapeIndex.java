package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/** 按锚点区块索引全部方块化塑料的真实凸体和兼容轮廓。 */
public final class BondedPlasticShapeIndex {
    private static final double SHAPE_EPSILON = 1.0E-7D;
    private static final double MAX_ANCHOR_REACH = 3.01D;
    private static final Map<Level, LevelIndex> LEVELS = new WeakHashMap<>();

    private BondedPlasticShapeIndex() {
    }

    public static void refresh(BondedEntityBlockEntity bonded) {
        Level level = bonded.getLevel();
        if (level == null) return;
        BlockPos anchor = bonded.getBlockPos().immutable();
        Entry replacement = createEntry(bonded, anchor);
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index != null) {
                index.remove(anchor);
                if (index.isEmpty()) LEVELS.remove(level);
            }
            if (replacement == null) return;
            LEVELS.computeIfAbsent(level, ignored -> new LevelIndex()).put(replacement);
        }
    }

    public static void remove(Level level, BlockPos anchor) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            index.remove(anchor);
            if (index.isEmpty()) LEVELS.remove(level);
        }
    }

    public static List<Entry> collisionEntries(BlockGetter getter, AABB bounds) {
        return entries(getter, bounds, QueryKind.COLLISION);
    }

    public static List<Entry> extendedCollisionEntries(BlockGetter getter, AABB bounds) {
        return entries(getter, bounds, QueryKind.EXTENDED_COLLISION);
    }

    public static List<Entry> extendedInteractionEntries(BlockGetter getter, AABB bounds) {
        return entries(getter, bounds, QueryKind.EXTENDED_INTERACTION);
    }

    public static boolean isCurrent(BlockGetter getter, BlockPos anchor) {
        BlockState state = getter.getBlockState(anchor);
        return state.getBlock() instanceof AbstractPlasticEntityBlock<?>
            && state.hasProperty(AbstractPlasticEntityBlock.BONDED)
            && state.getValue(AbstractPlasticEntityBlock.BONDED)
            && getter.getBlockEntity(anchor) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic();
    }

    public static @Nullable Entry entryAt(BlockGetter getter, BlockPos anchor) {
        if (!(getter instanceof Level level)) return null;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? null : index.get(anchor);
        }
    }

    private static List<Entry> entries(BlockGetter getter, AABB bounds, QueryKind kind) {
        if (!(getter instanceof Level level)) return List.of();
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            return index == null ? List.of() : index.query(bounds, kind);
        }
    }

    private static @Nullable Entry createEntry(BondedEntityBlockEntity bonded, BlockPos anchor) {
        Level level = bonded.getLevel();
        if (!bonded.isInitialized()
            || !bonded.isPlastic()
            || level == null
            || !isCurrent(level, anchor)
            || !(bonded.getOrCreateRenderEntity() instanceof AbstractPlasticEntity product)) {
            return null;
        }
        PlasticEntityGeometry geometry = product.plasticraft$getGeometry();
        Vec3 position = geometry.placementPosition(anchor, bonded.getPlasticOrientation());
        PlasticEntityCollisionBox collisionBox = geometry.collisionBoxAt(position, bonded.getPlasticOrientation());
        VoxelShape collision = collisionBox.shape();
        VoxelShape interaction = geometry.interactionShapeAt(position, bonded.getPlasticOrientation());
        List<PlasticConvexShape> convexShapes = collisionBox.convexComponents();
        AABB collisionBounds = convexBounds(convexShapes);
        if (collisionBounds == null) collisionBounds = worldBounds(collision);
        AABB interactionBounds = worldBounds(interaction);
        if (collisionBounds == null && interactionBounds == null) return null;
        return new Entry(
            anchor,
            collisionBox,
            collision,
            interaction,
            convexShapes,
            collisionBounds,
            interactionBounds,
            collisionBounds != null && !isInsideAnchorBlock(collisionBounds, anchor),
            interactionBounds != null && !isInsideAnchorBlock(interactionBounds, anchor)
        );
    }

    private static boolean isInsideAnchorBlock(AABB bounds, BlockPos anchor) {
        return bounds.minX >= anchor.getX() - SHAPE_EPSILON
            && bounds.minY >= anchor.getY() - SHAPE_EPSILON
            && bounds.minZ >= anchor.getZ() - SHAPE_EPSILON
            && bounds.maxX <= anchor.getX() + 1.0D + SHAPE_EPSILON
            && bounds.maxY <= anchor.getY() + 1.0D + SHAPE_EPSILON
            && bounds.maxZ <= anchor.getZ() + 1.0D + SHAPE_EPSILON;
    }

    private static @Nullable AABB worldBounds(VoxelShape shape) {
        return shape.isEmpty() ? null : shape.bounds();
    }

    private static @Nullable AABB convexBounds(List<PlasticConvexShape> shapes) {
        AABB bounds = null;
        for (PlasticConvexShape shape : shapes) {
            bounds = bounds == null ? shape.bounds() : bounds.minmax(shape.bounds());
        }
        return bounds;
    }

    public record Entry(
        BlockPos anchor,
        PlasticEntityCollisionBox collisionBox,
        VoxelShape collisionShape,
        VoxelShape interactionShape,
        List<PlasticConvexShape> convexShapes,
        @Nullable AABB collisionBounds,
        @Nullable AABB interactionBounds,
        boolean collisionExtendsAnchor,
        boolean interactionExtendsAnchor
    ) {
        public Entry {
            Objects.requireNonNull(anchor, "anchor");
            Objects.requireNonNull(collisionBox, "collisionBox");
            Objects.requireNonNull(collisionShape, "collisionShape");
            Objects.requireNonNull(interactionShape, "interactionShape");
            convexShapes = List.copyOf(convexShapes);
        }
    }

    private enum QueryKind {
        COLLISION,
        EXTENDED_COLLISION,
        EXTENDED_INTERACTION
    }

    private static final class LevelIndex {
        private final Map<Long, Map<Long, Entry>> entriesByChunk = new HashMap<>();

        private void put(Entry entry) {
            this.entriesByChunk.computeIfAbsent(chunkKey(entry.anchor), ignored -> new HashMap<>())
                .put(entry.anchor.asLong(), entry);
        }

        private void remove(BlockPos anchor) {
            long chunkKey = chunkKey(anchor);
            Map<Long, Entry> entries = this.entriesByChunk.get(chunkKey);
            if (entries == null) return;
            entries.remove(anchor.asLong());
            if (entries.isEmpty()) this.entriesByChunk.remove(chunkKey);
        }

        private @Nullable Entry get(BlockPos anchor) {
            Map<Long, Entry> entries = this.entriesByChunk.get(chunkKey(anchor));
            return entries == null ? null : entries.get(anchor.asLong());
        }

        private boolean isEmpty() {
            return this.entriesByChunk.isEmpty();
        }

        private List<Entry> query(AABB bounds, QueryKind kind) {
            int minChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX - MAX_ANCHOR_REACH));
            int maxChunkX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX + MAX_ANCHOR_REACH));
            int minChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ - MAX_ANCHOR_REACH));
            int maxChunkZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ + MAX_ANCHOR_REACH));
            List<Entry> result = new ArrayList<>();
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                    Map<Long, Entry> entries = this.entriesByChunk.get(ChunkPos.asLong(chunkX, chunkZ));
                    if (entries == null) continue;
                    for (Entry entry : entries.values()) {
                        AABB entryBounds = switch (kind) {
                            case COLLISION, EXTENDED_COLLISION -> entry.collisionBounds;
                            case EXTENDED_INTERACTION -> entry.interactionBounds;
                        };
                        if (kind == QueryKind.EXTENDED_COLLISION && !entry.collisionExtendsAnchor
                            || kind == QueryKind.EXTENDED_INTERACTION && !entry.interactionExtendsAnchor) {
                            continue;
                        }
                        if (entryBounds != null && entryBounds.intersects(bounds)) result.add(entry);
                    }
                }
            }
            return List.copyOf(result);
        }

        private static long chunkKey(BlockPos anchor) {
            return ChunkPos.asLong(
                SectionPos.blockToSectionCoord(anchor.getX()),
                SectionPos.blockToSectionCoord(anchor.getZ())
            );
        }
    }
}
