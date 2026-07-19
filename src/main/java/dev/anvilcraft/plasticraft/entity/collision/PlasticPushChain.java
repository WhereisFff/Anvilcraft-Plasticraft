package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilPhysics;
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

/** Collision preflight and ordered movement for a row of touching plastic bodies. */
public final class PlasticPushChain {
    private PlasticPushChain() {
    }

    @Nullable
    public static Plan create(AbstractPlasticAnvilEntity root, Entity pusher, Vec3 rootMovement) {
        if (rootMovement.lengthSqr()
            <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
            return new Plan(List.of());
        }
        Map<AbstractPlasticAnvilEntity, Vec3> movements = new IdentityHashMap<>();
        List<AbstractPlasticAnvilEntity> visiting = new ArrayList<>();
        movements.put(root, rootMovement);
        if (!collect(root, pusher, rootMovement, movements, visiting)) return null;

        List<Entry> entries = movements.entrySet().stream()
            .map(entry -> new Entry(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparingDouble(entry -> -entry.entity().position().dot(rootMovement)))
            .toList();
        return new Plan(entries);
    }

    private static boolean collect(
        AbstractPlasticAnvilEntity entity,
        Entity pusher,
        Vec3 movement,
        Map<AbstractPlasticAnvilEntity, Vec3> movements,
        List<AbstractPlasticAnvilEntity> visiting
    ) {
        if (visiting.contains(entity)) return false;
        visiting.add(entity);
        try {
            List<VoxelShape> blockingShapes = new ArrayList<>();
            List<Entity> candidates = entity.level().getEntities(
                entity,
                entity.getBoundingBox().expandTowards(movement).inflate(PlasticAnvilPhysics.FACE_EPSILON),
                other -> !other.isRemoved()
                    && !other.isSpectator()
                    && other != pusher
                    && !other.isPassengerOfSameVehicle(pusher)
                    && entity.canCollideWith(other)
            );
            for (Entity other : candidates) {
                if (other instanceof AbstractPlasticAnvilEntity plastic) {
                    Vec3 nextMovement = PlasticAnvilPhysics.sidePushMovement(
                        plastic,
                        entity,
                        entity.getBoundingBox(),
                        entity.plasticraft$currentPushGravityDirection(),
                        movement
                    );
                    if (nextMovement == null || nextMovement.lengthSqr()
                        <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
                        blockingShapes.add(Shapes.create(other.getBoundingBox()));
                        continue;
                    }
                    Vec3 existing = movements.get(plastic);
                    if (existing != null && existing.distanceToSqr(nextMovement)
                        > PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON) {
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
                <= PlasticAnvilPhysics.FACE_EPSILON * PlasticAnvilPhysics.FACE_EPSILON;
        } finally {
            visiting.remove(entity);
        }
    }

    private record Entry(AbstractPlasticAnvilEntity entity, Vec3 movement) {
    }

    public static final class Plan {
        private final List<Entry> entries;

        private Plan(List<Entry> entries) {
            this.entries = entries;
        }

        public void move(AbstractPlasticAnvilEntity root, Entity pusher) {
            for (Entry entry : this.entries) {
                if (entry.entity() == root) continue;
                entry.entity().plasticraft$applyTransferredPush(pusher, entry.movement());
            }
        }
    }
}
