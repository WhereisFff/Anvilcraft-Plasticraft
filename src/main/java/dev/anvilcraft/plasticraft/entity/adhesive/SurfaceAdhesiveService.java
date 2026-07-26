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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

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
                if (!state.hasPatch(face) || !hasUncoveredContact(level, entity, supportPos, face)) continue;
                if (AdhesiveBondingService.bondEntityFromPatch(level, entity, supportPos, face)) return;
            }
        }
    }

    static boolean hasUncoveredContact(ServerLevel level, Entity target, BlockPos supportPos, Direction face) {
        AABB patch = patchBounds(supportPos, face);
        if (!patch.intersects(target.getBoundingBox())) return false;
        SurfaceRect contact = projectedContact(supportPos, face, target.getBoundingBox());
        if (contact == null) return false;

        List<SurfaceRect> covered = new ArrayList<>();
        for (Entity entity : level.getEntities(
            (Entity) null,
            patch,
            entity -> entity != target && isAttachedTo(entity, supportPos, face)
        )) {
            SurfaceRect overlap = projectedContact(supportPos, face, entity.getBoundingBox());
            if (overlap == null) continue;
            SurfaceRect intersection = contact.intersection(overlap);
            if (intersection.hasArea()) covered.add(intersection);
        }
        return hasUncoveredArea(contact, covered);
    }

    private static boolean isAttachedTo(Entity entity, BlockPos supportPos, Direction face) {
        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        return adhesion != null
            && adhesion.supportPos().equals(supportPos)
            && adhesion.attachmentFace() == face;
    }

    private static @Nullable SurfaceRect projectedContact(BlockPos pos, Direction face, AABB box) {
        double firstMin;
        double firstMax;
        double secondMin;
        double secondMax;
        switch (face.getAxis()) {
            case X -> {
                firstMin = Math.max(box.minY, pos.getY() + PATCH_MIN);
                firstMax = Math.min(box.maxY, pos.getY() + PATCH_MAX);
                secondMin = Math.max(box.minZ, pos.getZ() + PATCH_MIN);
                secondMax = Math.min(box.maxZ, pos.getZ() + PATCH_MAX);
            }
            case Y -> {
                firstMin = Math.max(box.minX, pos.getX() + PATCH_MIN);
                firstMax = Math.min(box.maxX, pos.getX() + PATCH_MAX);
                secondMin = Math.max(box.minZ, pos.getZ() + PATCH_MIN);
                secondMax = Math.min(box.maxZ, pos.getZ() + PATCH_MAX);
            }
            case Z -> {
                firstMin = Math.max(box.minX, pos.getX() + PATCH_MIN);
                firstMax = Math.min(box.maxX, pos.getX() + PATCH_MAX);
                secondMin = Math.max(box.minY, pos.getY() + PATCH_MIN);
                secondMax = Math.min(box.maxY, pos.getY() + PATCH_MAX);
            }
            default -> throw new MatchException(null, null);
        }
        return firstMax - firstMin > 1.0E-6D && secondMax - secondMin > 1.0E-6D
            ? new SurfaceRect(firstMin, firstMax, secondMin, secondMax)
            : null;
    }

    private static boolean hasUncoveredArea(SurfaceRect contact, List<SurfaceRect> covered) {
        if (covered.isEmpty()) return true;
        List<Double> firstCuts = new ArrayList<>();
        List<Double> secondCuts = new ArrayList<>();
        firstCuts.add(contact.firstMin());
        firstCuts.add(contact.firstMax());
        secondCuts.add(contact.secondMin());
        secondCuts.add(contact.secondMax());
        for (SurfaceRect rectangle : covered) {
            firstCuts.add(rectangle.firstMin());
            firstCuts.add(rectangle.firstMax());
            secondCuts.add(rectangle.secondMin());
            secondCuts.add(rectangle.secondMax());
        }
        firstCuts.sort(Double::compare);
        secondCuts.sort(Double::compare);
        for (int first = 1; first < firstCuts.size(); first++) {
            double firstMin = firstCuts.get(first - 1);
            double firstMax = firstCuts.get(first);
            if (firstMax - firstMin <= 1.0E-6D) continue;
            double firstCenter = (firstMin + firstMax) * 0.5D;
            for (int second = 1; second < secondCuts.size(); second++) {
                double secondMin = secondCuts.get(second - 1);
                double secondMax = secondCuts.get(second);
                if (secondMax - secondMin <= 1.0E-6D) continue;
                double secondCenter = (secondMin + secondMax) * 0.5D;
                if (covered.stream().noneMatch(rectangle -> rectangle.contains(firstCenter, secondCenter))) {
                    return true;
                }
            }
        }
        return false;
    }

    private record SurfaceRect(double firstMin, double firstMax, double secondMin, double secondMax) {
        private SurfaceRect intersection(SurfaceRect other) {
            return new SurfaceRect(
                Math.max(this.firstMin, other.firstMin),
                Math.min(this.firstMax, other.firstMax),
                Math.max(this.secondMin, other.secondMin),
                Math.min(this.secondMax, other.secondMax)
            );
        }

        private boolean contains(double first, double second) {
            return first >= this.firstMin && first <= this.firstMax
                && second >= this.secondMin && second <= this.secondMax;
        }

        private boolean hasArea() {
            return this.firstMax - this.firstMin > 1.0E-6D
                && this.secondMax - this.secondMin > 1.0E-6D;
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
