package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 把静止或刚被碰撞裁剪的塑料实体吸附到邻近的世界整格平面，去掉碰撞皮肤留下的亚微米错位。 */
public final class PlasticEntityGridSnapping {
    private static final double SNAP_EPSILON = PlasticEntityPhysics.FACE_EPSILON;
    private static final double AGREEMENT_EPSILON = 1.0E-9D;

    private PlasticEntityGridSnapping() {
    }

    public static void applyAtRest(AbstractPlasticEntity entity) {
        applyAfterMove(entity, Vec3.ZERO, Vec3.ZERO);
    }

    public static void applyAfterMove(
        AbstractPlasticEntity entity,
        Vec3 requestedMovement,
        Vec3 actualMovement
    ) {
        if (entity.isRemoved()) return;
        AABB bounds = entity.plasticraft$getCollisionBox().bounds();
        Vec3 position = entity.position();
        Vec3 snapped = new Vec3(
            snappedComponent(position.x, bounds.minX, bounds.maxX, requestedMovement.x, actualMovement.x),
            snappedComponent(position.y, bounds.minY, bounds.maxY, requestedMovement.y, actualMovement.y),
            snappedComponent(position.z, bounds.minZ, bounds.maxZ, requestedMovement.z, actualMovement.z)
        );
        if (snapped.equals(position)) return;
        entity.setPos(snapped);
    }

    private static double snappedComponent(
        double position,
        double minimum,
        double maximum,
        double requested,
        double actual
    ) {
        if (!shouldSnap(requested, actual)) return position;
        double delta = translationDelta(minimum, maximum);
        if (delta == 0.0D) return position;
        return position + delta;
    }

    private static boolean shouldSnap(double requested, double actual) {
        boolean clipped = Math.abs(requested) > SNAP_EPSILON
            && Math.abs(requested - actual) > SNAP_EPSILON;
        boolean resting = Math.abs(actual) <= SNAP_EPSILON;
        return clipped || resting;
    }

    private static double translationDelta(double minimum, double maximum) {
        Double minimumDelta = nearIntegerDelta(minimum);
        Double maximumDelta = nearIntegerDelta(maximum);
        if (minimumDelta != null && maximumDelta != null) {
            if (Math.abs(minimumDelta - maximumDelta) > AGREEMENT_EPSILON) return 0.0D;
            return 0.5D * (minimumDelta + maximumDelta);
        }
        if (minimumDelta != null) return minimumDelta;
        if (maximumDelta != null) return maximumDelta;
        return 0.0D;
    }

    private static Double nearIntegerDelta(double coordinate) {
        if (!Double.isFinite(coordinate)) return null;
        double nearest = Math.rint(coordinate);
        double delta = nearest - coordinate;
        return Math.abs(delta) <= SNAP_EPSILON ? delta : null;
    }
}
