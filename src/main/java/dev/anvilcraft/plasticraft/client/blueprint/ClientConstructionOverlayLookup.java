package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.MultiblockBuildAdapter;
import dev.anvilcraft.plasticraft.blueprint.OrdinaryBlockAdapter;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StructureVoidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 用客户端快照预索引规划覆盖,供投影碰撞按邻接重算。 */
public final class ClientConstructionOverlayLookup {
    private static final int MAX_CACHED_OVERLAYS = 8;
    private static final int MAX_CACHED_BLOCKS = 4 * 1024 * 1024;
    private static final ConstructionProjectionIndex.PlannedOverlay EMPTY = position -> null;
    private static final Map<UUID, CachedOverlay> OVERLAYS = new LinkedHashMap<>(16, 0.75F, true);
    private static int cachedBlocks;

    private ClientConstructionOverlayLookup() {
    }

    public static ConstructionProjectionIndex.PlannedOverlay plannedOverlay(Level level, UUID jobId) {
        ConstructionJob job = ClientBlueprintJobCache.job(jobId);
        if (job == null || !job.dimension().equals(level.dimension())) return EMPTY;
        StructureSnapshot snapshot = ClientBlueprintSnapshotCache.cached(job.hash());
        if (snapshot == null) {
            invalidate(jobId);
            return EMPTY;
        }
        synchronized (OVERLAYS) {
            CachedOverlay cached = OVERLAYS.get(jobId);
            if (cached != null && cached.matches(job, snapshot)) return cached;
        }

        BlueprintPlacement placement = BlueprintPlacement.of(job);
        Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>(snapshot.blocks().size());
        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            BlockState target = placement.stateOf(snapshot.stateOf(entry));
            if (writesProjection(target)) states.put(placement.worldOf(entry.pos()).asLong(), target);
        }
        CachedOverlay created = new CachedOverlay(
            job.hash(),
            job.anchor(),
            job.rotation(),
            job.mirror(),
            snapshot,
            states
        );
        synchronized (OVERLAYS) {
            CachedOverlay current = OVERLAYS.get(jobId);
            if (current != null && current.matches(job, snapshot)) return current;
            CachedOverlay previous = OVERLAYS.put(jobId, created);
            if (previous != null) cachedBlocks -= previous.size();
            cachedBlocks += created.size();
            trim();
        }
        return created;
    }

    public static void invalidate(UUID jobId) {
        synchronized (OVERLAYS) {
            CachedOverlay removed = OVERLAYS.remove(jobId);
            if (removed != null) cachedBlocks -= removed.size();
        }
    }

    public static void invalidateHash(String hash) {
        synchronized (OVERLAYS) {
            Iterator<CachedOverlay> iterator = OVERLAYS.values().iterator();
            while (iterator.hasNext()) {
                CachedOverlay cached = iterator.next();
                if (!cached.hash().equals(hash)) continue;
                cachedBlocks -= cached.size();
                iterator.remove();
            }
        }
    }

    public static void clear() {
        synchronized (OVERLAYS) {
            OVERLAYS.clear();
            cachedBlocks = 0;
        }
    }

    private static void trim() {
        Iterator<CachedOverlay> iterator = OVERLAYS.values().iterator();
        while ((OVERLAYS.size() > MAX_CACHED_OVERLAYS || cachedBlocks > MAX_CACHED_BLOCKS)
            && OVERLAYS.size() > 1
            && iterator.hasNext()) {
            CachedOverlay cached = iterator.next();
            cachedBlocks -= cached.size();
            iterator.remove();
        }
    }

    private static boolean writesProjection(BlockState target) {
        if (target.isAir() || target.getBlock() instanceof StructureVoidBlock) {
            return false;
        }
        if (target.getBlock() instanceof LiquidBlock) {
            return true;
        }
        if (MultiblockBuildAdapter.isMultiPart(target) || OrdinaryBlockAdapter.isAttachedHalf(target)) {
            return true;
        }
        return target.getBlock().asItem() != Items.AIR;
    }

    private record CachedOverlay(
        String hash,
        BlockPos anchor,
        Rotation rotation,
        Mirror mirror,
        StructureSnapshot snapshot,
        Long2ObjectOpenHashMap<BlockState> states
    ) implements ConstructionProjectionIndex.PlannedOverlay {
        private boolean matches(ConstructionJob job, StructureSnapshot expectedSnapshot) {
            return this.snapshot == expectedSnapshot
                && this.hash.equals(job.hash())
                && this.anchor.equals(job.anchor())
                && this.rotation == job.rotation()
                && this.mirror == job.mirror();
        }

        @Override
        public BlockState stateAt(long position) {
            return this.states.get(position);
        }

        private int size() {
            return this.states.size();
        }
    }
}
