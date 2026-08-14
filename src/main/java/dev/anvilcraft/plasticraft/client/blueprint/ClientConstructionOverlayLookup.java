package dev.anvilcraft.plasticraft.client.blueprint;

import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.MultiblockBuildAdapter;
import dev.anvilcraft.plasticraft.blueprint.OrdinaryBlockAdapter;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StructureVoidBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 用客户端已缓存快照重建规划覆盖,供投影碰撞按邻接重算。 */
public final class ClientConstructionOverlayLookup {
    private ClientConstructionOverlayLookup() {
    }

    public static Map<Long, BlockState> plannedOverlay(Level level, UUID jobId) {
        ConstructionJob job = ClientBlueprintJobCache.job(jobId);
        if (job == null) {
            return Map.of();
        }
        StructureSnapshot snapshot = ClientBlueprintSnapshotCache.cached(job.hash());
        if (snapshot == null) {
            return Map.of();
        }
        BlueprintPlacement placement = BlueprintPlacement.of(job);
        Map<Long, BlockState> overlay = new HashMap<>();
        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            BlockState target = placement.stateOf(snapshot.stateOf(entry));
            if (!writesProjection(target)) {
                continue;
            }
            overlay.put(placement.worldOf(entry.pos()).asLong(), target);
        }
        return overlay;
    }

    private static boolean writesProjection(BlockState target) {
        if (target.isAir() || target.getBlock() instanceof StructureVoidBlock) {
            return false;
        }
        if (target.getBlock() instanceof LiquidBlock) {
            return false;
        }
        if (MultiblockBuildAdapter.isMultiPart(target) || OrdinaryBlockAdapter.isAttachedHalf(target)) {
            return true;
        }
        return target.getBlock().asItem() != Items.AIR;
    }
}
