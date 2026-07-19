package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 一列相接塑料实体的碰撞预检和有序移动。 */
public final class PlasticPushChain {
    private static final int CLIP_SEARCH_STEPS = 12;
    private static final double MIN_CLIPPED_MOVEMENT = 1.0E-4D;

    private PlasticPushChain() {
    }

    /** Returns the greatest prefix of {@code rootMovement} that the complete push chain can perform. */
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
        Map<AbstractPlasticEntity, Vec3> movements = new IdentityHashMap<>();
        List<AbstractPlasticEntity> visiting = new ArrayList<>();
        movements.put(root, rootMovement);
        if (!collect(root, pusher, rootMovement, movements, visiting)) return null;

        List<Entry> entries = movements.entrySet().stream()
            .map(entry -> new Entry(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingDouble(entry -> -entry.entity().position().dot(rootMovement)))
            .toList();
        return new Plan(entries);
    }

    private static boolean collect(
        AbstractPlasticEntity entity,
        Entity pusher,
        Vec3 movement,
        Map<AbstractPlasticEntity, Vec3> movements,
        List<AbstractPlasticEntity> visiting
    ) {
        if (visiting.contains(entity)) return false;
        visiting.add(entity);
        try {
            List<VoxelShape> blockingShapes = new ArrayList<>();
            List<Entity> candidates = entity.level().getEntities(
                entity,
                entity.getBoundingBox().expandTowards(movement).inflate(PlasticEntityPhysics.FACE_EPSILON),
                other -> !other.isRemoved()
                    && !other.isSpectator()
                    && other != pusher
                    && !other.isPassengerOfSameVehicle(pusher)
                    && entity.canCollideWith(other)
            );
            for (Entity other : candidates) {
                if (other instanceof AbstractPlasticEntity plastic) {
                    Vec3 nextMovement = PlasticEntityPhysics.sidePushMovement(
                        plastic,
                        entity,
                        entity.getBoundingBox(),
                        entity.plasticraft$currentPushGravityDirection(),
                        movement
                    );
                    if (nextMovement == null || nextMovement.lengthSqr()
                        <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                        blockingShapes.add(Shapes.create(other.getBoundingBox()));
                        continue;
                    }
                    Vec3 existing = movements.get(plastic);
                    if (existing != null && existing.distanceToSqr(nextMovement)
                        > PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON) {
                        return false;
                    }
                    if (existing == null) {
                        movements.put(plastic, nextMovement);
                        if (!collect(plastic, entity, nextMovement, movements, visiting)) return false;
                    }
                    continue;
                }
                blockingShapes.add(Shapes.create(other.getBoundingBox()));
            }

            Vec3 allowed = Entity.collideBoundingBox(
                entity,
                movement,
                entity.getBoundingBox(),
                entity.level(),
                blockingShapes
            );
            return allowed.distanceToSqr(movement)
                <= PlasticEntityPhysics.FACE_EPSILON * PlasticEntityPhysics.FACE_EPSILON;
        } finally {
            visiting.remove(entity);
        }
    }

    private record Entry(AbstractPlasticEntity entity, Vec3 movement) {
    }

    public record ClippedPlan(Vec3 movement, Plan plan) {
    }

    public static final class Plan {
        private final List<Entry> entries;

        private Plan(List<Entry> entries) {
            this.entries = entries;
        }

        public void move(AbstractPlasticEntity root, Entity pusher) {
            for (Entry entry : this.entries) {
                if (entry.entity() == root) continue;
                entry.entity().plasticraft$applyTransferredPush(pusher, entry.movement());
            }
        }
    }
}
