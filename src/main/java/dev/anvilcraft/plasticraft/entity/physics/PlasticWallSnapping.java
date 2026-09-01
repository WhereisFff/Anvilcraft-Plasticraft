package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import net.minecraft.world.phys.Vec3;

/** 让受侧推的塑料实体补完到近处方块表面的最后一小段位移。 */
public final class PlasticWallSnapping {
    public static final double MAX_SNAP_DISTANCE = 1.0D / 16.0D;

    private PlasticWallSnapping() {
    }

    public static Vec3 extendToNearbyBlock(AbstractPlasticEntity entity, Vec3 requestedMovement) {
        double requestedDistance = requestedMovement.length();
        if (!Double.isFinite(requestedDistance)
            || requestedDistance <= PlasticEntityPhysics.FACE_EPSILON) {
            return requestedMovement;
        }

        Vec3 direction = requestedMovement.scale(1.0D / requestedDistance);
        Vec3 probeMovement = requestedMovement.add(direction.scale(MAX_SNAP_DISTANCE));
        if (!PlasticEntityPhysics.isWithinCarryDistance(probeMovement)) return requestedMovement;
        PlasticConvexCollisionResolver.SweepContact contact = PlasticConvexCollisionResolver.sweepBlocks(
            entity,
            entity.getBoundingBox(),
            probeMovement
        );
        if (contact == null) return requestedMovement;
        double contactDistance = probeMovement.length() * contact.time();
        if (contactDistance <= requestedDistance + PlasticEntityPhysics.FACE_EPSILON) {
            return requestedMovement;
        }
        return direction.scale(contactDistance);
    }
}
