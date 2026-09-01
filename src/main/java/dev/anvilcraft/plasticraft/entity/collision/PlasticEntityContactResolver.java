package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/** 从同一组真实凸体中解析支撑与推动接触。 */
public final class PlasticEntityContactResolver {
    private static final double CONTACT_EPSILON = 1.0E-5D;

    private PlasticEntityContactResolver() {
    }

    @Nullable
    public static SupportContact supportContact(
        Entity supported,
        AABB supportedBox,
        Entity support,
        AABB supportBox,
        Direction gravityDirection,
        double probeDepth
    ) {
        Objects.requireNonNull(supported, "supported");
        Objects.requireNonNull(supportedBox, "supportedBox");
        Objects.requireNonNull(support, "support");
        Objects.requireNonNull(supportBox, "supportBox");
        Objects.requireNonNull(gravityDirection, "gravityDirection");
        if (!Double.isFinite(probeDepth) || probeDepth <= 0.0D) return null;

        Vec3 probeMovement = axisVector(
            gravityDirection.getAxis(),
            gravityDirection.getAxisDirection().getStep() * probeDepth
        );
        List<PlasticConvexShape> supportedShapes = PlasticConvexCollisionResolver.collisionShapes(
            supported,
            supportedBox
        );
        List<PlasticConvexShape> supportShapes = PlasticConvexCollisionResolver.collisionShapes(
            support,
            supportBox,
            supported,
            probeMovement
        );
        PlasticConvexCollisionResolver.SweepContact contact = PlasticConvexCollisionResolver.sweep(
            supportedShapes,
            supportShapes,
            probeMovement
        );
        if (contact == null) return null;
        Vec3 gravity = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double alignment = -gravity.dot(contact.normal());
        if (alignment <= CONTACT_EPSILON) return null;
        return new SupportContact(
            contact.time() * probeDepth,
            alignment,
            contact.normal()
        );
    }

    @Nullable
    public static PushContact sidePush(
        Entity target,
        Entity pusher,
        AABB pusherBox,
        Direction targetGravityDirection,
        Vec3 requestedMovement
    ) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(pusher, "pusher");
        Objects.requireNonNull(pusherBox, "pusherBox");
        Objects.requireNonNull(targetGravityDirection, "targetGravityDirection");
        Objects.requireNonNull(requestedMovement, "requestedMovement");

        Vec3 gravity = Vec3.atLowerCornerOf(targetGravityDirection.getNormal());
        Vec3 lateralMovement = requestedMovement.subtract(gravity.scale(requestedMovement.dot(gravity)));
        if (lateralMovement.lengthSqr() <= CONTACT_EPSILON * CONTACT_EPSILON) return null;
        List<PlasticConvexShape> pusherShapes = PlasticConvexCollisionResolver.collisionShapes(
            pusher,
            pusherBox
        );
        List<PlasticConvexShape> targetShapes = PlasticConvexCollisionResolver.collisionShapes(
            target,
            target.getBoundingBox(),
            pusher,
            lateralMovement
        );
        PlasticConvexCollisionResolver.SweepContact contact = PlasticConvexCollisionResolver.sweep(
            pusherShapes,
            targetShapes,
            lateralMovement
        );
        if (contact == null) return null;

        Vec3 lateralNormal = contact.normal().subtract(gravity.scale(contact.normal().dot(gravity)));
        double lateralNormalLength = lateralNormal.length();
        if (lateralNormalLength <= CONTACT_EPSILON) return null;
        Vec3 targetDirection = lateralNormal.scale(-1.0D / lateralNormalLength);
        double requestedCarrierDistance = lateralMovement.dot(targetDirection);
        if (requestedCarrierDistance <= CONTACT_EPSILON) return null;
        double contactDistance = requestedCarrierDistance * contact.time();
        double targetDistance = requestedCarrierDistance - contactDistance;
        if (targetDistance <= CONTACT_EPSILON) return null;
        return new PushContact(
            targetDirection.scale(targetDistance),
            targetDirection,
            contactDistance,
            requestedCarrierDistance,
            contact.normal()
        );
    }

    public static boolean hasLateralContact(
        Entity first,
        Entity second,
        Direction gravityDirection,
        double probeDistance
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(gravityDirection, "gravityDirection");
        if (!Double.isFinite(probeDistance) || probeDistance <= 0.0D) return false;
        List<PlasticConvexShape> firstShapes = PlasticConvexCollisionResolver.collisionShapes(
            first,
            first.getBoundingBox()
        );
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == gravityDirection.getAxis()) continue;
            Vec3 movement = Vec3.atLowerCornerOf(direction.getNormal()).scale(probeDistance);
            List<PlasticConvexShape> secondShapes = PlasticConvexCollisionResolver.collisionShapes(
                second,
                second.getBoundingBox(),
                first,
                movement
            );
            PlasticConvexCollisionResolver.SweepContact contact = PlasticConvexCollisionResolver.sweep(
                firstShapes,
                secondShapes,
                movement
            );
            if (contact == null) continue;
            Vec3 gravity = Vec3.atLowerCornerOf(gravityDirection.getNormal());
            Vec3 lateralNormal = contact.normal().subtract(gravity.scale(contact.normal().dot(gravity)));
            if (lateralNormal.lengthSqr() > CONTACT_EPSILON * CONTACT_EPSILON) return true;
        }
        return false;
    }

    private static Vec3 axisVector(Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, value, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, value);
        };
    }

    public record SupportContact(double distance, double alignment, Vec3 normal) {
        public SupportContact {
            normal = Objects.requireNonNull(normal, "normal");
        }
    }

    public record PushContact(
        Vec3 targetMovement,
        Vec3 targetDirection,
        double contactDistance,
        double requestedCarrierDistance,
        Vec3 contactNormal
    ) {
        public PushContact {
            targetMovement = Objects.requireNonNull(targetMovement, "targetMovement");
            targetDirection = Objects.requireNonNull(targetDirection, "targetDirection");
            contactNormal = Objects.requireNonNull(contactNormal, "contactNormal");
        }

        public Vec3 clampCarrierMovement(Vec3 carrierMovement, Vec3 allowedTargetMovement) {
            Objects.requireNonNull(carrierMovement, "carrierMovement");
            Objects.requireNonNull(allowedTargetMovement, "allowedTargetMovement");
            double allowedTargetDistance = Math.clamp(
                allowedTargetMovement.dot(this.targetDirection),
                0.0D,
                this.targetMovement.length()
            );
            double allowedCarrierDistance = this.contactDistance + allowedTargetDistance;
            return carrierMovement.add(this.targetDirection.scale(
                allowedCarrierDistance - this.requestedCarrierDistance
            ));
        }
    }
}
