package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticCauldrons;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 树脂铁砧与弹性塑料共用的碰撞回弹，保持实体态材料参数一致。 */
public final class ElasticPlasticPhysics {
    private static final double BLOCK_RESTITUTION = 0.80D;
    private static final double ENTITY_RESTITUTION = 0.72D;
    private static final double TANGENTIAL_RETENTION = 0.68D;
    private static final double MIN_BOUNCE_SPEED = 0.06D;
    private static final double MIN_LIVING_BOUNCE_SPEED = 0.1D;
    private static final double MAX_ENTITY_IMPULSE = 0.45D;
    private static final int CONTACT_COOLDOWN_TICKS = 3;
    private static final double CONTACT_EPSILON = PlasticEntityPhysics.FACE_EPSILON * 8.0D;

    private final AbstractPlasticEntity entity;
    private final boolean smoothSurface;
    private final int[] contactCooldownUntil = new int[Direction.values().length];

    public ElasticPlasticPhysics(AbstractPlasticEntity entity) {
        this(entity, false);
    }

    public ElasticPlasticPhysics(AbstractPlasticEntity entity, boolean smoothSurface) {
        this.entity = entity;
        this.smoothSurface = smoothSurface;
    }

    public Vec3 resolveCollisionVelocity(
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 velocityAfterMove,
        Direction gravityDirection,
        boolean onSlidingRail
    ) {
        int clippedContacts = PlasticEntityPhysics.clippedDirectionMask(
            requestedMovement,
            actualMovement,
            PlasticEntityPhysics.FACE_EPSILON
        );
        if (clippedContacts == 0) return velocityAfterMove;
        AABB startBounds = this.entity.getBoundingBox().move(actualMovement.scale(-1.0D));
        AABB sweptBox = startBounds.expandTowards(requestedMovement).inflate(CONTACT_EPSILON);
        List<Entity> collisionEntities = this.entity.level().getEntities(
            this.entity,
            sweptBox,
            other -> other.isAlive()
                && !other.isSpectator()
                // 塑料砧会形成相互推动的链，但树脂釜同样是有效的弹性冲击面和配方目标。
                && (!(other instanceof AbstractPlasticEntity)
                    || PlasticCauldrons.isCauldron(other))
                && !this.entity.isPassengerOfSameVehicle(other)
                // 此处有意不复用船的碰撞检查：树脂会从可推动的生物实体，
                // 以及明确提供碰撞箱的实体上反弹。
                && (other.canBeCollidedWith() || other.isPushable())
        );

        int entityContacts = 0;
        for (Direction direction : Direction.values()) {
            int directionMask = PlasticEntityPhysics.directionMask(direction);
            if ((clippedContacts & directionMask) == 0) continue;
            for (Entity other : collisionEntities) {
                if (contactsFace(this.entity, startBounds, other, requestedMovement, actualMovement, direction)) {
                    entityContacts |= directionMask;
                    break;
                }
            }
        }

        Vec3 result = velocityAfterMove;
        for (Direction direction : Direction.values()) {
            int directionMask = PlasticEntityPhysics.directionMask(direction);
            if ((clippedContacts & directionMask) == 0) continue;
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            double incidentSpeed = requestedMovement.dot(normal);
            if (incidentSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;

            boolean hasEntityContact = (entityContacts & directionMask) != 0;
            boolean coolingDown = this.entity.tickCount < this.contactCooldownUntil[direction.get3DDataValue()];
            double bounceSpeed = incidentSpeed < MIN_BOUNCE_SPEED || coolingDown
                ? 0.0D
                : incidentSpeed * (hasEntityContact ? ENTITY_RESTITUTION : BLOCK_RESTITUTION);

            double currentNormal = result.dot(normal);
            double tangentialRetention = this.smoothSurface || direction == gravityDirection && onSlidingRail
                ? 1.0D
                : TANGENTIAL_RETENTION;
            Vec3 tangential = result.subtract(normal.scale(currentNormal)).scale(tangentialRetention);
            result = tangential.add(normal.scale(-bounceSpeed));
            if (bounceSpeed > 0.0D) {
                this.contactCooldownUntil[direction.get3DDataValue()] = this.entity.tickCount + CONTACT_COOLDOWN_TICKS;
                if (hasEntityContact) {
                    this.applyBoundedEntityImpulse(
                        collisionEntities,
                        startBounds,
                        requestedMovement,
                        actualMovement,
                        direction,
                        incidentSpeed
                    );
                }
            }
        }

        if (!result.equals(velocityAfterMove)) {
            this.entity.hasImpulse = true;
            this.entity.hurtMarked = true;
        }
        return result;
    }

    public void onEntityCollision(
        Entity collider,
        AABB colliderStartBox,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Vec3 incomingVelocity
    ) {
        if (collider.isSuppressingBounce()
            || requestedMovement.lengthSqr()
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            return;
        }

        int clippedContacts = PlasticEntityPhysics.clippedDirectionMask(
            requestedMovement,
            actualMovement,
            PlasticEntityPhysics.FACE_EPSILON
        );
        if (clippedContacts == 0) return;

        Vec3 colliderVelocity = collider.getDeltaMovement();
        boolean bouncedCollider = false;
        Direction gravityDirection = this.entity.plasticraft$currentPushGravityDirection();
        double restitution = !this.smoothSurface && collider instanceof LivingEntity ? 1.0D : BLOCK_RESTITUTION;
        for (Direction direction : Direction.values()) {
            if ((clippedContacts & PlasticEntityPhysics.directionMask(direction)) == 0
                || !contactsFace(
                    collider,
                    colliderStartBox,
                    this.entity,
                    requestedMovement,
                    actualMovement,
                    direction
                )) {
                continue;
            }
            Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
            double incidentSpeed = requestedMovement.dot(normal);
            if (this.smoothSurface) {
                // 请求位移可能包含补偿或多次移动，不能把它当成新的动能来源。
                incidentSpeed = Math.min(incidentSpeed, Math.max(0.0D, incomingVelocity.dot(normal)));
                if (incidentSpeed <= minimumSurfaceBounceSpeed(collider)) continue;
            }
            if (incidentSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;
            if (direction.getAxis() != gravityDirection.getAxis()) continue;
            if ((collider instanceof FallingBlockEntity || collider instanceof AbstractPlasticEntity)
                && incidentSpeed < MIN_BOUNCE_SPEED) {
                continue;
            }
            double currentNormal = colliderVelocity.dot(normal);
            colliderVelocity = colliderVelocity.subtract(normal.scale(currentNormal))
                .add(normal.scale(-incidentSpeed * restitution));
            bouncedCollider = true;
        }
        if (bouncedCollider) {
            collider.setDeltaMovement(colliderVelocity);
            if (this.smoothSurface) collider.setOnGround(false);
            collider.resetFallDistance();
            collider.hasImpulse = true;
            collider.hurtMarked = true;
        }
    }

    public static void bounceOnSmoothSurface(Entity collider) {
        Vec3 velocity = collider.getDeltaMovement();
        if (velocity.y >= 0.0D) return;
        double speed = -velocity.y;
        // 行走时每刻的贴地重力探测不应触发微小弹跳，否则会反复切换地面与空中运动。
        double rebound = speed <= minimumSurfaceBounceSpeed(collider) ? 0.0D : speed * BLOCK_RESTITUTION;
        collider.setDeltaMovement(velocity.x, rebound, velocity.z);
        if (rebound > 0.0D) {
            collider.setOnGround(false);
            collider.resetFallDistance();
        }
    }

    private static double minimumSurfaceBounceSpeed(Entity collider) {
        return collider instanceof LivingEntity ? MIN_LIVING_BOUNCE_SPEED : MIN_BOUNCE_SPEED;
    }

    private void applyBoundedEntityImpulse(
        List<Entity> candidates,
        AABB startBounds,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Direction direction,
        double incidentSpeed
    ) {
        Vec3 normal = Vec3.atLowerCornerOf(direction.getNormal());
        for (Entity other : candidates) {
            if (!contactsFace(this.entity, startBounds, other, requestedMovement, actualMovement, direction)) continue;
            double relativeSpeed = incidentSpeed - other.getDeltaMovement().dot(normal);
            if (relativeSpeed <= PlasticEntityPhysics.FACE_EPSILON) continue;
            double impulse = Math.min(MAX_ENTITY_IMPULSE, relativeSpeed * 0.45D);
            other.setDeltaMovement(other.getDeltaMovement().add(normal.scale(impulse)));
            other.hasImpulse = true;
            other.hurtMarked = true;
        }
    }

    private static boolean contactsFace(
        Entity moving,
        AABB movingStartBounds,
        Entity obstacle,
        Vec3 requestedMovement,
        Vec3 actualMovement,
        Direction direction
    ) {
        List<PlasticConvexShape> movingShapes = PlasticConvexCollisionResolver.collisionShapes(
            moving,
            movingStartBounds,
            obstacle,
            requestedMovement
        ).stream().map(shape -> shape.move(actualMovement)).toList();
        List<PlasticConvexShape> obstacleShapes = PlasticConvexCollisionResolver.collisionShapes(
            obstacle,
            obstacle.getBoundingBox(),
            moving,
            requestedMovement
        );
        double probeMovement = direction.getAxisDirection().getStep() * CONTACT_EPSILON * 2.0D;
        return PlasticConvexCollisionResolver.blocksAxisMovement(
            movingShapes,
            obstacleShapes,
            direction.getAxis(),
            probeMovement
        );
    }

}
