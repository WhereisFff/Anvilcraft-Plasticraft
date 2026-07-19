package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/** Shared gravity, support, carrying, and side-push geometry for plastic anvils. */
public final class PlasticAnvilPhysics {
    public static final double SUPPORT_PROBE_DEPTH = 0.05D;
    public static final double FACE_EPSILON = 1.0E-5D;
    public static final double SIDE_PUSH_QUERY_DISTANCE = 0.20D;
    public static final double SIDE_CONTACT_DISTANCE = 1.0E-3D;
    public static final double MAX_CARRY_DISTANCE = 0.75D;

    private static final double MIN_EFFECTIVE_GRAVITY_SQR = 1.0E-10D;

    private PlasticAnvilPhysics() {
    }

    /** Adds world-up buoyancy to AnvilCraft's complete falling-block gravity vector. */
    public static Vec3 effectiveGravity(FallingBlockEntity entity, double buoyancyAcceleration) {
        return GravityManager.getNetGravityVectorForFallingBlock(entity).add(0.0D, buoyancyAcceleration, 0.0D);
    }

    /** Returns the dominant effective-gravity face, falling back to down when the forces cancel out. */
    public static Direction effectiveDirection(FallingBlockEntity entity, double buoyancyAcceleration) {
        Vec3 gravity = effectiveGravity(entity, buoyancyAcceleration);
        return gravity.lengthSqr() <= MIN_EFFECTIVE_GRAVITY_SQR ? Direction.DOWN : Direction.getNearest(gravity);
    }

    /** Returns no support direction when forces cancel instead of arbitrarily latching downward. */
    @Nullable
    public static Direction directionOrNull(Vec3 gravity) {
        return gravity.lengthSqr() <= MIN_EFFECTIVE_GRAVITY_SQR ? null : Direction.getNearest(gravity);
    }

    /** Builds a thin contact probe immediately beyond the supplied gravity-facing box face. */
    public static AABB supportProbe(AABB box, Direction gravityDirection) {
        double inset = FACE_EPSILON;
        double depth = SUPPORT_PROBE_DEPTH;
        return switch (gravityDirection) {
            case DOWN -> new AABB(
                box.minX + inset, box.minY - depth, box.minZ + inset,
                box.maxX - inset, box.minY + inset, box.maxZ - inset
            );
            case UP -> new AABB(
                box.minX + inset, box.maxY - inset, box.minZ + inset,
                box.maxX - inset, box.maxY + depth, box.maxZ - inset
            );
            case WEST -> new AABB(
                box.minX - depth, box.minY + inset, box.minZ + inset,
                box.minX + inset, box.maxY - inset, box.maxZ - inset
            );
            case EAST -> new AABB(
                box.maxX - inset, box.minY + inset, box.minZ + inset,
                box.maxX + depth, box.maxY - inset, box.maxZ - inset
            );
            case NORTH -> new AABB(
                box.minX + inset, box.minY + inset, box.minZ - depth,
                box.maxX - inset, box.maxY - inset, box.minZ + inset
            );
            case SOUTH -> new AABB(
                box.minX + inset, box.minY + inset, box.maxZ - inset,
                box.maxX - inset, box.maxY - inset, box.maxZ + depth
            );
        };
    }

    /**
     * Selects the closest support on the effective-gravity face. Larger tangential overlap and then entity id break
     * ties, keeping the result stable when several entities share the probe.
     */
    @Nullable
    public static Entity findSupport(FallingBlockEntity entity, Direction gravityDirection) {
        return findSupport(entity, entity.getBoundingBox(), gravityDirection);
    }

    /** Finds a support using a recorded box, before a scoped acceleration move changes it. */
    @Nullable
    public static Entity findSupport(FallingBlockEntity entity, AABB entityBox, Direction gravityDirection) {
        List<Entity> candidates = entity.level().getEntities(
            entity,
            supportProbe(entityBox, gravityDirection),
            other -> isSupportCandidate(entity, entityBox, other, gravityDirection)
        );
        Entity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestOverlap = Double.NEGATIVE_INFINITY;
        int bestId = Integer.MAX_VALUE;
        for (Entity candidate : candidates) {
            double distance = Math.abs(supportGap(entityBox, candidate.getBoundingBox(), gravityDirection));
            double overlap = tangentialOverlap(entityBox, candidate.getBoundingBox(), gravityDirection);
            int id = candidate.getId();
            if (distance < bestDistance - FACE_EPSILON
                || Math.abs(distance - bestDistance) <= FACE_EPSILON && overlap > bestOverlap + FACE_EPSILON
                || Math.abs(distance - bestDistance) <= FACE_EPSILON
                    && Math.abs(overlap - bestOverlap) <= FACE_EPSILON
                    && id < bestId) {
                best = candidate;
                bestDistance = distance;
                bestOverlap = overlap;
                bestId = id;
            }
        }
        return best;
    }

    /** Returns whether the candidate occupies the thin gravity-face support region. */
    public static boolean isSupportCandidate(
        FallingBlockEntity entity,
        Entity candidate,
        Direction gravityDirection
    ) {
        return isSupportCandidate(entity, entity.getBoundingBox(), candidate, gravityDirection);
    }

    /** Returns whether a candidate supports a recorded entity box. */
    public static boolean isSupportCandidate(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity candidate,
        Direction gravityDirection
    ) {
        if (candidate.isRemoved()
            || candidate.isSpectator()
            || entity.isPassengerOfSameVehicle(candidate)
            || !entity.canCollideWith(candidate)) {
            return false;
        }
        double gap = supportGap(entityBox, candidate.getBoundingBox(), gravityDirection);
        return gap >= -SUPPORT_PROBE_DEPTH - FACE_EPSILON
            && gap <= SUPPORT_PROBE_DEPTH + FACE_EPSILON
            && tangentialOverlap(entityBox, candidate.getBoundingBox(), gravityDirection) > FACE_EPSILON;
    }

    /** True only when the two support faces are actually touching, not merely inside the acquisition probe. */
    public static boolean hasImmediateEntityContact(
        FallingBlockEntity entity,
        Entity support,
        Direction gravityDirection
    ) {
        return hasImmediateEntityContact(entity, entity.getBoundingBox(), support, gravityDirection);
    }

    /** Tests immediate contact against a recorded entity box. */
    public static boolean hasImmediateEntityContact(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity support,
        Direction gravityDirection
    ) {
        return isSupportCandidate(entity, entityBox, support, gravityDirection)
            && Math.abs(supportGap(entityBox, support.getBoundingBox(), gravityDirection))
                <= FACE_EPSILON * 4.0D;
    }

    /** Returns the center of the support box face that points back toward the carried entity. */
    public static Vec3 supportAnchor(AABB supportBox, Direction gravityDirection) {
        Vec3 center = supportBox.getCenter();
        return switch (gravityDirection) {
            case DOWN -> new Vec3(center.x, supportBox.maxY, center.z);
            case UP -> new Vec3(center.x, supportBox.minY, center.z);
            case WEST -> new Vec3(supportBox.maxX, center.y, center.z);
            case EAST -> new Vec3(supportBox.minX, center.y, center.z);
            case NORTH -> new Vec3(center.x, center.y, supportBox.maxZ);
            case SOUTH -> new Vec3(center.x, center.y, supportBox.minZ);
        };
    }

    /**
     * Computes one-tick carrier movement from the support entity's current and old positions. Tangential movement is
     * retained, while normal movement is retained only when the support moves toward the carried entity.
     */
    public static Vec3 carriedMovement(Entity support, Direction gravityDirection) {
        Vec3 displacement = new Vec3(
            support.getX() - support.xo,
            support.getY() - support.yo,
            support.getZ() - support.zo
        );
        AABB currentBox = support.getBoundingBox();
        return carriedMovement(currentBox.move(displacement.scale(-1.0D)), currentBox, gravityDirection);
    }

    /** Stable support observation used instead of depending on another entity's tick order. */
    public record SupportObservation(UUID entityId, AABB boundingBox) {
        public SupportObservation {
            if (entityId == null || boundingBox == null) {
                throw new IllegalArgumentException("Support observation requires an id and bounding box");
            }
        }

        public static SupportObservation capture(Entity support) {
            return new SupportObservation(support.getUUID(), support.getBoundingBox());
        }
    }

    /** Carries only when two consecutive observations refer to the same supporting entity. */
    public static Vec3 carriedMovement(
        @Nullable SupportObservation previous,
        SupportObservation current,
        Direction gravityDirection
    ) {
        if (previous == null || !previous.entityId().equals(current.entityId())) {
            return Vec3.ZERO;
        }
        return carriedMovement(previous.boundingBox(), current.boundingBox(), gravityDirection);
    }

    /** Computes carrier movement from explicit previous and current support boxes. */
    public static Vec3 carriedMovement(AABB previousSupportBox, AABB currentSupportBox, Direction gravityDirection) {
        Vec3 displacement = supportAnchor(currentSupportBox, gravityDirection)
            .subtract(supportAnchor(previousSupportBox, gravityDirection));
        if (!isWithinCarryDistance(displacement)) {
            return Vec3.ZERO;
        }
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalMovement = displacement.dot(normal);
        Vec3 tangentialMovement = displacement.subtract(normal.scale(normalMovement));
        return tangentialMovement.add(normal.scale(Math.min(normalMovement, 0.0D)));
    }

    /**
     * Returns whether the carrier remains on the support face and is moving into
     * or along it. The separating direction stays collidable so a falling entity
     * naturally leaves a descending anvil behind.
     */
    public static boolean canMoveWithCarrier(
        FallingBlockEntity carried,
        Entity carrier,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        if (!isSupportCandidate(carried, carrier, gravityDirection)) {
            return false;
        }
        if (!isWithinCarryDistance(requestedMovement)) {
            return false;
        }
        Vec3 gravityNormal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        return requestedMovement.dot(gravityNormal) <= FACE_EPSILON;
    }

    /**
     * Returns whether a player movement reaches a lateral face and can transfer
     * its surface-tangential displacement to the plastic body.
     */
    public static boolean canPushFromSide(
        FallingBlockEntity target,
        Entity pusher,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        return sidePushMovement(
            target,
            pusher,
            pusher.getBoundingBox(),
            gravityDirection,
            requestedMovement
        ) != null;
    }

    /** Tests a side push against an explicitly recorded pre-move pusher box. */
    public static boolean canPushFromSide(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        return sidePushMovement(target, pusher, pusherBox, gravityDirection, requestedMovement) != null;
    }

    /**
     * Resolves a lateral push to the contacted face normals. Movement parallel
     * to that face remains with the pusher, so diagonal walking slides past the
     * body instead of carrying it sideways.
     *
     * @return the displacement transferred to the target, or {@code null} when no lateral face is reached
     */
    @Nullable
    public static Vec3 sidePushMovement(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        if (!(pusher instanceof Player) && !(pusher instanceof CarrierMovableEntity)
            || pusher.isRemoved()
            || pusher.isSpectator()
            || pusher.noPhysics
            || target.isPassengerOfSameVehicle(pusher)
            || !isWithinCarryDistance(requestedMovement)) {
            return null;
        }

        AABB targetBox = target.getBoundingBox();
        AABB sweptPusherBox = pusherBox.expandTowards(requestedMovement).inflate(FACE_EPSILON);
        double bestProgress = Double.POSITIVE_INFINITY;
        Vec3 transferred = Vec3.ZERO;
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == gravityDirection.getAxis()) continue;
            int sign = direction.getAxisDirection().getStep();
            double movement = requestedMovement.get(direction.getAxis()) * sign;
            if (movement <= FACE_EPSILON) continue;

            double pusherFace = faceCoordinate(pusherBox, direction);
            double targetFace = faceCoordinate(targetBox, direction.getOpposite());
            double gap = (targetFace - pusherFace) * sign;
            if (gap < -SIDE_PUSH_QUERY_DISTANCE - FACE_EPSILON || gap > movement + FACE_EPSILON) continue;
            if (tangentialOverlap(sweptPusherBox, targetBox, direction) <= FACE_EPSILON) continue;

            double contactDistance = Math.max(0.0D, gap);
            double progress = contactDistance / movement;
            double transfer = Math.max(0.0D, movement - contactDistance) * sign;
            if (progress < bestProgress - FACE_EPSILON) {
                bestProgress = progress;
                transferred = axisVector(direction.getAxis(), transfer);
            } else if (Math.abs(progress - bestProgress) <= FACE_EPSILON) {
                transferred = transferred.add(axisVector(direction.getAxis(), transfer));
            }
        }
        return bestProgress == Double.POSITIVE_INFINITY ? null : transferred;
    }

    /** Removes the component normal to the current gravity support face. */
    public static Vec3 tangentialMovement(Vec3 movement, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        return movement.subtract(normal.scale(movement.dot(normal)));
    }

    /** Limits each axis independently so an ordinary diagonal step is not mistaken for a teleport. */
    public static boolean isWithinCarryDistance(Vec3 movement) {
        return isFinite(movement)
            && Math.abs(movement.x) <= MAX_CARRY_DISTANCE + FACE_EPSILON
            && Math.abs(movement.y) <= MAX_CARRY_DISTANCE + FACE_EPSILON
            && Math.abs(movement.z) <= MAX_CARRY_DISTANCE + FACE_EPSILON;
    }

    /** Converts an effective-gravity direction into a reusable contact bit. */
    public static int directionMask(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    /** Returns the movement component along the effective gravity axis. */
    public static double gravityAxisComponent(Vec3 movement, Direction gravityDirection) {
        return movement.get(gravityDirection.getAxis());
    }

    /** True when Entity.move clipped the requested motion on the gravity-facing side. */
    public static boolean collidedAlongGravity(Vec3 requested, Vec3 actual, Direction gravityDirection) {
        double requestedComponent = gravityAxisComponent(requested, gravityDirection);
        double actualComponent = gravityAxisComponent(actual, gravityDirection);
        int expectedSign = gravityDirection.getAxisDirection().getStep();
        return requestedComponent * expectedSign > FACE_EPSILON
            && Math.abs(requestedComponent - actualComponent) > FACE_EPSILON;
    }

    /** Returns whether a block collision shape supports the effective-gravity face. */
    public static boolean hasBlockSupport(FallingBlockEntity entity, Direction gravityDirection) {
        return hasBlockSupport(entity, entity.getBoundingBox(), gravityDirection);
    }

    /** Tests support for a recorded box, allowing multi-step movement to retain its pre-move contact state. */
    public static boolean hasBlockSupport(
        FallingBlockEntity entity,
        AABB box,
        Direction gravityDirection
    ) {
        return entity.level()
            .getBlockCollisions(entity, supportProbe(box, gravityDirection))
            .iterator()
            .hasNext();
    }

    /** True only when the gravity face is already touching a block before this tick's movement. */
    public static boolean hasImmediateBlockContact(FallingBlockEntity entity, Direction gravityDirection) {
        AABB entityBox = entity.getBoundingBox();
        for (net.minecraft.world.phys.shapes.VoxelShape shape : entity.level()
            .getBlockCollisions(entity, supportProbe(entityBox, gravityDirection))) {
            if (Math.abs(supportGap(entityBox, shape.bounds(), gravityDirection)) <= FACE_EPSILON * 4.0D) {
                return true;
            }
        }
        return false;
    }

    /** Drops only the velocity component aimed into the current support face. */
    public static Vec3 removeIntoSupportVelocity(Vec3 velocity, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double component = velocity.dot(normal);
        return component > 0.0D ? velocity.subtract(normal.scale(component)) : velocity;
    }

    /** Clears only velocity components still aimed into faces clipped by this move. */
    public static Vec3 removeClippedVelocity(Vec3 velocity, Vec3 requested, Vec3 actual) {
        double x = clippedIntoFace(velocity.x, requested.x, actual.x) ? 0.0D : velocity.x;
        double y = clippedIntoFace(velocity.y, requested.y, actual.y) ? 0.0D : velocity.y;
        double z = clippedIntoFace(velocity.z, requested.z, actual.z) ? 0.0D : velocity.z;
        return new Vec3(x, y, z);
    }

    /** Returns the world faces on which this move was collision-clipped. */
    public static int clippedDirectionMask(Vec3 requested, Vec3 actual, double minimumSpeed) {
        int contacts = 0;
        for (Direction direction : Direction.values()) {
            double requestedComponent = requested.get(direction.getAxis());
            double actualComponent = actual.get(direction.getAxis());
            int sign = direction.getAxisDirection().getStep();
            if (requestedComponent * sign <= minimumSpeed) continue;
            if (Math.abs(requestedComponent - actualComponent) <= FACE_EPSILON) continue;
            contacts |= directionMask(direction);
        }
        return contacts;
    }

    private static boolean clippedIntoFace(double velocity, double requested, double actual) {
        return Math.abs(requested - actual) > FACE_EPSILON
            && Math.abs(requested) > FACE_EPSILON
            && Math.signum(velocity) == Math.signum(requested);
    }

    /** Position used by AnvilCraft's landing event for any of the six gravity faces. */
    public static BlockPos landingPosition(
        FallingBlockEntity entity,
        Direction gravityDirection
    ) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 faceCenter = switch (gravityDirection.getAxis()) {
            case X -> new Vec3(faceCoordinate(box, gravityDirection), center.y, center.z);
            case Y -> new Vec3(center.x, faceCoordinate(box, gravityDirection), center.z);
            case Z -> new Vec3(center.x, center.y, faceCoordinate(box, gravityDirection));
        };
        Vec3 inward = Vec3.atLowerCornerOf(gravityDirection.getNormal()).scale(-FACE_EPSILON);
        return BlockPos.containing(faceCenter.add(inward));
    }

    /** Builds the broad-phase side-only push query without including either horizontal face. */
    public static AABB sidePushProbe(AABB box) {
        return box.inflate(SIDE_PUSH_QUERY_DISTANCE);
    }

    /**
     * Returns true only when the minimum-separation contact normal is horizontal.
     * A standing entity commonly overlaps the top face by a tiny floating-point
     * amount, so merely checking horizontal overlap would incorrectly push it.
     */
    public static boolean isSideContact(Entity entity, Entity other) {
        return isSideContact(entity, other, Direction.DOWN);
    }

    /** Returns true when the minimum-separation face is tangential to effective gravity. */
    public static boolean isSideContact(Entity entity, Entity other, Direction gravityDirection) {
        AABB box = entity.getBoundingBox();
        AABB otherBox = other.getBoundingBox();
        double xSeparation = signedIntervalSeparation(box.minX, box.maxX, otherBox.minX, otherBox.maxX);
        double ySeparation = signedIntervalSeparation(box.minY, box.maxY, otherBox.minY, otherBox.maxY);
        double zSeparation = signedIntervalSeparation(box.minZ, box.maxZ, otherBox.minZ, otherBox.maxZ);

        Direction.Axis contactAxis = dominantContactAxis(xSeparation, ySeparation, zSeparation);
        double separation = switch (contactAxis) {
            case X -> xSeparation;
            case Y -> ySeparation;
            case Z -> zSeparation;
        };
        return contactAxis != gravityDirection.getAxis()
            && separation <= SIDE_CONTACT_DISTANCE + FACE_EPSILON
            && tangentialOverlap(box, otherBox, axisDirection(contactAxis)) > FACE_EPSILON;
    }

    /** Runs the vanilla push operation only for entities touching a lateral face. */
    public static void pushSideEntities(
        FallingBlockEntity entity,
        @Nullable Direction gravityDirection,
        @Nullable UUID supportId
    ) {
        List<Entity> nearby = entity.level().getEntities(
            entity,
            sidePushProbe(entity.getBoundingBox()),
            EntitySelector.pushableBy(entity)
        );
        for (Entity other : nearby) {
            if (supportId != null && supportId.equals(other.getUUID())) continue;
            if (gravityDirection != null && isSupportCandidate(entity, other, gravityDirection)) {
                continue;
            }
            if (!entity.isPassengerOfSameVehicle(other)
                && isSideContact(entity, other, gravityDirection == null ? Direction.DOWN : gravityDirection)) {
                entity.push(other);
            }
        }
    }

    private static Direction.Axis dominantContactAxis(double x, double y, double z) {
        if (x >= y - FACE_EPSILON && x >= z - FACE_EPSILON) return Direction.Axis.X;
        if (y >= z - FACE_EPSILON) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static Direction axisDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static Vec3 axisVector(Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, value, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, value);
        };
    }

    private static double supportGap(AABB entityBox, AABB supportBox, Direction gravityDirection) {
        double entityFace = faceCoordinate(entityBox, gravityDirection);
        double supportFace = faceCoordinate(supportBox, gravityDirection.getOpposite());
        return (supportFace - entityFace) * gravityDirection.getAxisDirection().getStep();
    }

    private static double faceCoordinate(AABB box, Direction direction) {
        return switch (direction) {
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
    }

    private static double tangentialOverlap(AABB first, AABB second, Direction direction) {
        double x = overlap(first.minX, first.maxX, second.minX, second.maxX);
        double y = overlap(first.minY, first.maxY, second.minY, second.maxY);
        double z = overlap(first.minZ, first.maxZ, second.minZ, second.maxZ);
        return switch (direction.getAxis()) {
            case X -> y * z;
            case Y -> x * z;
            case Z -> x * y;
        };
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    /** Positive for a gap, zero for touching, and negative for penetration depth. */
    private static double signedIntervalSeparation(
        double firstMin,
        double firstMax,
        double secondMin,
        double secondMax
    ) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return -overlap(firstMin, firstMax, secondMin, secondMax);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
