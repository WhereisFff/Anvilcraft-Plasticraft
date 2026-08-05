package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 一列相接塑料实体的碰撞预检和有序移动。 */
public final class PlasticPushChain {
    private static final int CLIP_SEARCH_STEPS = 12;
    private static final double MIN_CLIPPED_MOVEMENT = 1.0E-4D;

    private PlasticPushChain() {
    }

    /** 返回整条推动链都能完成的最大 {@code rootMovement} 前缀。 */
    public static ClippedPlan clip(AbstractPlasticEntity root, Entity pusher, Vec3 rootMovement) {
        Plan fullPlan = create(root, pusher, rootMovement);
        if (fullPlan != null) {
            return new ClippedPlan(rootMovement, fullPlan);
        }

        double length = rootMovement.length();
        double probeScale = Math.min(1.0D, MIN_CLIPPED_MOVEMENT / length);
        Vec3 probeMovement = rootMovement.scale(probeScale);
        Plan probePlan = create(root, pusher, probeMovement);
        if (probePlan == null) {
            return new ClippedPlan(Vec3.ZERO, new Plan(List.of()));
        }

        double lower = probeScale;
        double upper = 1.0D;
        Plan lowerPlan = probePlan;
        for (int i = 0; i < CLIP_SEARCH_STEPS; i++) {
            double middle = (lower + upper) * 0.5D;
            Plan middlePlan = create(root, pusher, rootMovement.scale(middle));
            if (middlePlan == null) {
                upper = middle;
            } else {
                lower = middle;
                lowerPlan = middlePlan;
            }
        }
        return new ClippedPlan(rootMovement.scale(lower), lowerPlan);
    }

    @Nullable
    public static Plan create(AbstractPlasticEntity root, Entity pusher, Vec3 rootMovement) {
        if (rootMovement.lengthSqr()
            <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
            return new Plan(List.of());
        }
        MovementUnit rootUnit = MovementUnit.resolve(root);
        Map<UUID, Entry> movements = new HashMap<>();
        Set<UUID> visiting = new HashSet<>();
        List<Entry> movementOrder = new ArrayList<>();
        movements.put(rootUnit.key(), new Entry(rootUnit, rootMovement));
        if (!collect(rootUnit, pusher, rootMovement, movements, visiting, movementOrder)) return null;
        return new Plan(List.copyOf(movementOrder));
    }

    private static boolean collect(
        MovementUnit unit,
        Entity pusher,
        Vec3 movement,
        Map<UUID, Entry> movements,
        Set<UUID> visiting,
        List<Entry> movementOrder
    ) {
        if (!visiting.add(unit.key())) return false;
        try {
            for (Entity member : unit.members()) {
                List<Entity> blockingCandidates = new ArrayList<>();
                List<Entity> candidates = member.level().getEntities(
                    member,
                    ShapedCollisionEntity.collisionBounds(member)
                        .expandTowards(movement)
                        .inflate(PlasticEntityPhysics.FACE_EPSILON),
                    other -> !other.isRemoved()
                        && !other.isSpectator()
                        && other != pusher
                        && !other.isPassengerOfSameVehicle(pusher)
                        && !unit.contains(other)
                        && member.canCollideWith(other)
                );
                candidates.sort(Comparator.comparingInt(Entity::getId));
                for (Entity other : candidates) {
                    Entry planned = movementContaining(movements, other);
                    if (planned != null) continue;
                    if (!(other instanceof AbstractPlasticEntity plasticTarget)) {
                        blockingCandidates.add(other);
                        continue;
                    }

                    MovementUnit targetUnit = MovementUnit.resolve(plasticTarget);
                    if (targetUnit.key().equals(unit.key())) continue;
                    AbstractPlasticEntity plasticPusher = unit.representative();
                    if (unit.members().size() == 1
                        && member == plasticPusher
                        && isTangentialSupportMovement(plasticTarget, plasticPusher, movement)) {
                        continue;
                    }
                    Vec3 nextMovement = PlasticEntityPhysics.sidePushMovement(
                        plasticTarget,
                        plasticPusher,
                        member.getBoundingBox(),
                        plasticPusher.plasticraft$currentPushGravityDirection(),
                        movement
                    );
                    if (nextMovement == null || nextMovement.lengthSqr()
                        <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                        blockingCandidates.add(other);
                        continue;
                    }
                    Entry existing = movements.get(targetUnit.key());
                    if (existing != null && existing.movement().distanceToSqr(nextMovement)
                        > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                        return false;
                    }
                    if (existing == null) {
                        movements.put(targetUnit.key(), new Entry(targetUnit, nextMovement));
                        if (!collect(
                            targetUnit,
                            plasticPusher,
                            nextMovement,
                            movements,
                            visiting,
                            movementOrder
                        )) {
                            return false;
                        }
                    }
                }

                List<Entity> remainingBlockers = blockingCandidates.stream()
                    .filter(other -> movementContaining(movements, other) == null)
                    .toList();
                List<VoxelShape> blockingShapes = remainingBlockers.stream()
                    .filter(other -> !PlasticConvexCollisionResolver.hasConvexCollision(other))
                    .map(ShapedCollisionEntity::collisionShape)
                    .toList();
                Vec3 allowed = ShapedCollisionEntity.collideBoundingBox(
                    member,
                    movement,
                    member.getBoundingBox(),
                    member.level(),
                    blockingShapes,
                    remainingBlockers::contains
                );
                if (allowed.distanceToSqr(movement)
                    > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                    return false;
                }
            }
            movementOrder.add(movements.get(unit.key()));
            return true;
        } finally {
            visiting.remove(unit.key());
        }
    }

    private static boolean isTangentialSupportMovement(
        AbstractPlasticEntity carried,
        AbstractPlasticEntity carrier,
        Vec3 movement
    ) {
        Direction gravityDirection = carried.plasticraft$currentPushGravityDirection();
        Vec3 gravityNormal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        boolean tangential = Math.abs(movement.dot(gravityNormal)) <= PlasticEntityPhysics.FACE_EPSILON;
        boolean contact = PlasticEntityPhysics.hasImmediateEntityContact(carried, carrier, gravityDirection);
        return tangential && contact;
    }

    @Nullable
    private static Entry movementContaining(Map<UUID, Entry> movements, Entity entity) {
        for (Entry entry : movements.values()) {
            if (entry.unit().contains(entity)) return entry;
        }
        return null;
    }

    private record MovementUnit(
        UUID key,
        List<Entity> members,
        AbstractPlasticEntity representative
    ) {
        private static MovementUnit resolve(AbstractPlasticEntity entity) {
            Entity leader = EntityBondManager.resolveLeader(entity.level(), entity);
            if (leader == null || !leader.isAlive()) leader = entity;
            List<Entity> members = EntityBondManager.hasBonds(entity)
                ? List.copyOf(EntityBondManager.component(entity.level(), leader))
                : List.of(entity);
            return new MovementUnit(leader.getUUID(), members, entity);
        }

        private boolean contains(Entity entity) {
            for (Entity member : this.members) {
                if (member == entity) return true;
            }
            return false;
        }

    }

    private record Entry(MovementUnit unit, Vec3 movement) {
    }

    public record ClippedPlan(Vec3 movement, Plan plan) {
    }

    public static final class Plan {
        private final List<Entry> entries;

        private Plan(List<Entry> entries) {
            this.entries = entries;
        }

        public void move(AbstractPlasticEntity root, Entity pusher) {
            MovementUnit rootUnit = MovementUnit.resolve(root);
            for (Entry entry : this.entries) {
                if (entry.unit().key().equals(rootUnit.key())) continue;
                entry.unit().representative().plasticraft$applyTransferredPush(pusher, entry.movement());
            }
        }
    }
}
