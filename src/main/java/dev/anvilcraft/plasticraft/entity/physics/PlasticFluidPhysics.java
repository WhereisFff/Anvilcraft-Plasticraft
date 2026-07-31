package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.init.item.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 塑料方块实体和物品实体共用的确定性流体表面采样。 */
public final class PlasticFluidPhysics {
    private static final double EPSILON = 1.0E-6D;

    private PlasticFluidPhysics() {
    }

    public static FluidContact sample(Entity entity) {
        List<AABB> components = entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionBox().components()
            : List.of(entity.getBoundingBox());
        if (components.isEmpty()) return FluidContact.EMPTY;

        double surface = Double.NEGATIVE_INFINITY;
        double totalVolume = 0.0D;
        double submergedVolume = 0.0D;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (AABB box : components) {
            totalVolume += box.getXsize() * box.getYsize() * box.getZsize();
            int minX = Mth.floor(box.minX + EPSILON);
            int maxX = Mth.floor(box.maxX - EPSILON);
            int minY = Mth.floor(box.minY + EPSILON);
            int maxY = Mth.floor(box.maxY - EPSILON);
            int minZ = Mth.floor(box.minZ + EPSILON);
            int maxZ = Mth.floor(box.maxZ - EPSILON);
            for (int x = minX; x <= maxX; x++) {
                double overlapX = overlap(box.minX, box.maxX, x, x + 1.0D);
                if (overlapX <= EPSILON) continue;
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        double overlapZ = overlap(box.minZ, box.maxZ, z, z + 1.0D);
                        if (overlapZ <= EPSILON) continue;
                        cursor.set(x, y, z);
                        FluidState fluid = entity.level().getFluidState(cursor);
                        if (fluid.isEmpty()) continue;
                        double candidate = y + fluid.getHeight(entity.level(), cursor);
                        double overlapY = overlap(box.minY, box.maxY, y, candidate);
                        if (overlapY <= EPSILON) continue;
                        submergedVolume += overlapX * overlapY * overlapZ;
                        surface = Math.max(surface, candidate);
                    }
                }
            }
        }
        if (!Double.isFinite(surface) || totalVolume <= EPSILON) return FluidContact.EMPTY;
        double fraction = Mth.clamp(submergedVolume / totalVolume, 0.0D, 1.0D);
        return new FluidContact(fraction, surface);
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    public static void floatPlasticItem(ItemEntity item) {
        if (!item.getItem().is(ModItemTags.BUOYANT_PLASTIC_ITEMS)) return;
        FluidContact contact = sample(item);
        if (!contact.isPresent()) return;
        AABB box = item.getBoundingBox();
        double targetMinY = contact.surfaceY() - box.getYsize() * 0.5D;
        double error = targetMinY - box.minY;
        Vec3 velocity = item.getDeltaMovement();
        double y = Mth.clamp(velocity.y * 0.55D + error * 0.12D, -0.18D, 0.18D);
        if (Math.abs(error) < 0.01D && Math.abs(y) < 0.01D) y = 0.0D;
        item.setDeltaMovement(velocity.x * 0.9D, y, velocity.z * 0.9D);
        item.hasImpulse = true;
        item.hurtMarked = true;
    }

    public record FluidContact(double submergedFraction, double surfaceY) {
        private static final FluidContact EMPTY = new FluidContact(0.0D, Double.NaN);

        public boolean isPresent() {
            return this.submergedFraction > EPSILON && Double.isFinite(this.surfaceY);
        }
    }
}
