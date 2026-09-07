package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

public final class PlasticProjectileCollision {
    private PlasticProjectileCollision() {
    }

    public static Optional<Vec3> clip(ShapedCollisionEntity target, Vec3 start, Vec3 end) {
        Vec3 nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (PlasticConvexShape shape : collisionShapes(target)) {
            Optional<Vec3> hit = shape.clip(start, end);
            if (hit.isEmpty()) continue;
            double distance = start.distanceToSqr(hit.get());
            if (distance < nearestDistance) {
                nearest = hit.get();
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static boolean contains(ShapedCollisionEntity target, Vec3 point) {
        for (PlasticConvexShape shape : collisionShapes(target)) {
            if (shape.contains(point, 0.0D)) return true;
        }
        return false;
    }

    @Nullable
    public static BlockHitResult clip(BondedPlasticShapeIndex.Entry target, Vec3 start, Vec3 end) {
        BlockHitResult nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        Vec3 movement = end.subtract(start);
        for (PlasticConvexShape shape : target.convexShapes()) {
            Optional<Vec3> hit = shape.clip(start, end);
            if (hit.isEmpty()) continue;
            Vec3 position = hit.get();
            double distance = start.distanceToSqr(position);
            if (distance >= nearestDistance) continue;
            Vec3 normal = movement.scale(-1.0D);
            double mostOpposed = Double.POSITIVE_INFINITY;
            for (PlasticConvexShape.Face face : shape.faces()) {
                if (Math.abs(face.signedDistance(position)) > 1.0E-7D) continue;
                double projected = face.normal().dot(movement);
                if (projected < mostOpposed) {
                    normal = face.normal();
                    mostOpposed = projected;
                }
            }
            nearest = new BlockHitResult(
                position, Direction.getNearest(normal.x, normal.y, normal.z), target.anchor(),
                shape.contains(start, 0.0D)
            );
            nearestDistance = distance;
        }
        return nearest;
    }

    private static List<PlasticConvexShape> collisionShapes(ShapedCollisionEntity target) {
        PlasticEntityCollisionBox box = target.plasticraft$getCollisionBox();
        return box.hasConvexComponents()
            ? box.convexComponents()
            : box.components().stream().map(PlasticConvexShape::box).toList();
    }
}
