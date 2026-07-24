package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** 检测实体与裸露树脂胶点的接触，并把胶点转换为正式粘合。 */
public final class SurfaceAdhesiveService {
    private static final double PATCH_MIN = 0.25D;
    private static final double PATCH_MAX = 0.75D;
    private static final double PATCH_THICKNESS = 0.22D;

    private SurfaceAdhesiveService() {
    }

    public static void tickEntity(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)
            || !entity.isAlive()
            || entity instanceof Player
            || entity.hasData(ModAttachments.ENTITY_ADHESION)
            || entity.hasData(ModAttachments.ADHESIVE_TRANSIT)
            || EntityBondManager.hasBonds(entity)) {
            return;
        }
        AABB bounds = entity.getBoundingBox();
        BlockPos min = BlockPos.containing(bounds.minX - PATCH_THICKNESS, bounds.minY - PATCH_THICKNESS, bounds.minZ - PATCH_THICKNESS);
        BlockPos max = BlockPos.containing(bounds.maxX + PATCH_THICKNESS, bounds.maxY + PATCH_THICKNESS, bounds.maxZ + PATCH_THICKNESS);
        for (BlockPos mutablePos : BlockPos.betweenClosed(min, max)) {
            BlockPos supportPos = mutablePos.immutable();
            BlockAdhesionState state = BondedFallingBlocks.getAdhesion(level, supportPos);
            if (state == null || state.patchMask() == 0) continue;
            for (Direction face : Direction.values()) {
                if (!state.hasPatch(face) || !patchBounds(supportPos, face).intersects(bounds)) continue;
                if (AdhesiveBondingService.bondEntityFromPatch(level, entity, supportPos, face)) return;
            }
        }
    }

    private static AABB patchBounds(BlockPos pos, Direction face) {
        double minX = pos.getX() + PATCH_MIN;
        double minY = pos.getY() + PATCH_MIN;
        double minZ = pos.getZ() + PATCH_MIN;
        double maxX = pos.getX() + PATCH_MAX;
        double maxY = pos.getY() + PATCH_MAX;
        double maxZ = pos.getZ() + PATCH_MAX;
        return switch (face) {
            case EAST -> new AABB(pos.getX() + 0.98D, minY, minZ, pos.getX() + 1.0D + PATCH_THICKNESS, maxY, maxZ);
            case WEST -> new AABB(pos.getX() - PATCH_THICKNESS, minY, minZ, pos.getX() + 0.02D, maxY, maxZ);
            case UP -> new AABB(minX, pos.getY() + 0.98D, minZ, maxX, pos.getY() + 1.0D + PATCH_THICKNESS, maxZ);
            case DOWN -> new AABB(minX, pos.getY() - PATCH_THICKNESS, minZ, maxX, pos.getY() + 0.02D, maxZ);
            case SOUTH -> new AABB(minX, minY, pos.getZ() + 0.98D, maxX, maxY, pos.getZ() + 1.0D + PATCH_THICKNESS);
            case NORTH -> new AABB(minX, minY, pos.getZ() - PATCH_THICKNESS, maxX, maxY, pos.getZ() + 0.02D);
        };
    }
}
