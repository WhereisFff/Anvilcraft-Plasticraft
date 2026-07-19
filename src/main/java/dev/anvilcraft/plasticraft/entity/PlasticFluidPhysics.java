package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.init.PlasticItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Deterministic fluid-surface sampling shared by plastic block and item entities. */
public final class PlasticFluidPhysics {
    private static final double EPSILON = 1.0E-6D;

    private PlasticFluidPhysics() {
    }

    public static FluidContact sample(Entity entity) {
        AABB box = entity.getBoundingBox();
        double surface = Double.NEGATIVE_INFINITY;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minX = Mth.floor(box.minX + EPSILON);
        int maxX = Mth.floor(box.maxX - EPSILON);
        int minY = Mth.floor(box.minY - 1.0D);
        int maxY = Mth.floor(box.maxY + EPSILON);
        int minZ = Mth.floor(box.minZ + EPSILON);
        int maxZ = Mth.floor(box.maxZ - EPSILON);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    FluidState fluid = entity.level().getFluidState(cursor);
                    if (fluid.isEmpty()) continue;
                    double candidate = y + fluid.getHeight(entity.level(), cursor);
                    if (candidate > box.minY + EPSILON && candidate > surface) surface = candidate;
                }
            }
        }
        if (!Double.isFinite(surface)) return FluidContact.EMPTY;
        double fraction = Mth.clamp((surface - box.minY) / box.getYsize(), 0.0D, 1.0D);
        return new FluidContact(fraction, surface);
    }

    public static void floatPlasticItem(ItemEntity item) {
        if (!item.getItem().is(PlasticItemTags.BUOYANT_PLASTIC_ITEMS)) return;
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
