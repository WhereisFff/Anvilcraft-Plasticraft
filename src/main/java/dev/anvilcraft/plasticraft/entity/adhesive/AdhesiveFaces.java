package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** 统一处理碰撞箱命中面与塑料模型局部面的换算。 */
public final class AdhesiveFaces {
    private static final double PICK_INFLATION = 0.01D;

    private AdhesiveFaces() {
    }

    public static Direction hitFace(Entity entity, Vec3 hitLocation) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(hitLocation, "hitLocation");
        Direction nearest = Direction.DOWN;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (AABB box : ShapedCollisionEntity.interactionComponents(entity)) {
            Direction direction = hitFace(box, hitLocation);
            double distance = faceDistance(box, hitLocation, direction);
            if (distance < nearestDistance) {
                nearest = direction;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** 将命中点投影到指定方向上最近的真实交互子盒表面。 */
    public static Vec3 faceLocation(Entity entity, Vec3 hitLocation, Direction face) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(hitLocation, "hitLocation");
        Objects.requireNonNull(face, "face");
        Vec3 nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (AABB box : ShapedCollisionEntity.interactionComponents(entity)) {
            Vec3 candidate = projectToFace(box, hitLocation, face);
            double distance = hitLocation.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return nearest == null ? projectToFace(entity.getBoundingBox(), hitLocation, face) : nearest;
    }

    private static Direction hitFace(AABB box, Vec3 hitLocation) {
        Direction nearest = Direction.DOWN;
        double nearestDistance = Math.abs(hitLocation.y - box.minY);
        for (Direction direction : Direction.values()) {
            double distance = faceDistance(box, hitLocation, direction);
            if (distance < nearestDistance) {
                nearest = direction;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double faceDistance(AABB box, Vec3 hitLocation, Direction direction) {
        return Math.abs(switch (direction) {
            case DOWN -> hitLocation.y - box.minY;
            case UP -> box.maxY - hitLocation.y;
            case NORTH -> hitLocation.z - box.minZ;
            case SOUTH -> box.maxZ - hitLocation.z;
            case WEST -> hitLocation.x - box.minX;
            case EAST -> box.maxX - hitLocation.x;
        });
    }

    private static Vec3 projectToFace(AABB box, Vec3 hitLocation, Direction face) {
        double x = clamp(hitLocation.x, box.minX, box.maxX);
        double y = clamp(hitLocation.y, box.minY, box.maxY);
        double z = clamp(hitLocation.z, box.minZ, box.maxZ);
        return switch (face) {
            case DOWN -> new Vec3(x, box.minY, z);
            case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, y, box.minZ);
            case SOUTH -> new Vec3(x, y, box.maxZ);
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
        };
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static Direction hitFaceFromLocal(Entity entity, Vec3 localHit) {
        return hitFace(entity, entity.position().add(localHit));
    }

    public static Direction hitFace(Player player, Entity entity) {
        Vec3 start = player.getEyePosition();
        Vec3 center = entity instanceof AbstractPlasticEntity plasticEntity
            ? plasticEntity.plasticraft$getRotationCenter()
            : entity.getBoundingBox().getCenter();
        double reach = Math.max(6.0D, start.distanceTo(center) + 2.0D);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
        Vec3 hit = center;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (AABB component : ShapedCollisionEntity.interactionComponents(entity)) {
            Vec3 candidate = component.inflate(PICK_INFLATION).clip(start, end).orElse(null);
            if (candidate == null) continue;
            double distance = start.distanceToSqr(candidate);
            if (distance < nearestDistance) {
                hit = candidate;
                nearestDistance = distance;
            }
        }
        return hitFace(entity, hit);
    }

    /** 塑料制品使用模型局部面，其余实体直接使用世界方向。 */
    public static Direction storedFace(Entity entity, Direction worldFace) {
        if (entity instanceof AbstractPlasticEntity plastic) {
            return plastic.getOrientation().localDirection(worldFace);
        }
        return worldFace;
    }

    public static Direction worldFace(Entity entity, Direction storedFace) {
        if (entity instanceof AbstractPlasticEntity plastic) {
            return plastic.getOrientation().worldDirection(storedFace);
        }
        return storedFace;
    }

    /** 返回实体当前姿态下指定世界面的真实外表面中心。 */
    public static Vec3 worldFaceCenter(Entity entity, Direction worldFace) {
        Direction storedFace = storedFace(entity, worldFace);
        return storedFaceCenter(entity, storedFace);
    }

    /** 返回实体当前姿态下指定存储面的真实外表面中心。 */
    public static Vec3 storedFaceCenter(Entity entity, Direction storedFace) {
        if (entity instanceof AbstractPlasticEntity plastic) {
            return plastic.plasticraft$getGeometry().surfacePointAt(
                plastic.position(),
                plastic.getOrientation(),
                storedFace
            );
        }
        return boxFaceCenter(entity.getBoundingBox(), storedFace);
    }

    /** 返回塑料实体在目标姿态下指定局部面的真实外表面中心。 */
    public static Vec3 storedFaceCenter(
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation,
        Direction storedFace
    ) {
        return entity.plasticraft$getGeometry().surfacePointAt(entity.position(), orientation, storedFace);
    }

    /** 返回指定世界面的法向真实表面位置，并在切向上使用实体旋转中心。 */
    public static Vec3 worldFaceAlignmentPoint(Entity entity, Direction worldFace) {
        return storedFaceAlignmentPoint(entity, storedFace(entity, worldFace));
    }

    public static Vec3 storedFaceAlignmentPoint(Entity entity, Direction storedFace) {
        if (entity instanceof AbstractPlasticEntity plastic) {
            return plastic.plasticraft$getGeometry().faceAlignmentPointAt(
                plastic.position(),
                plastic.getOrientation(),
                storedFace
            );
        }
        return boxFaceCenter(entity.getBoundingBox(), storedFace);
    }

    public static Vec3 storedFaceAlignmentPoint(
        AbstractPlasticEntity entity,
        PlasticEntityOrientation orientation,
        Direction storedFace
    ) {
        return entity.plasticraft$getGeometry().faceAlignmentPointAt(entity.position(), orientation, storedFace);
    }

    private static Vec3 boxFaceCenter(AABB box, Direction face) {
        Vec3 center = box.getCenter();
        return switch (face) {
            case DOWN -> new Vec3(center.x, box.minY, center.z);
            case UP -> new Vec3(center.x, box.maxY, center.z);
            case NORTH -> new Vec3(center.x, center.y, box.minZ);
            case SOUTH -> new Vec3(center.x, center.y, box.maxZ);
            case WEST -> new Vec3(box.minX, center.y, center.z);
            case EAST -> new Vec3(box.maxX, center.y, center.z);
        };
    }

    public static Direction defaultStoredFace(Entity entity) {
        return Direction.DOWN;
    }

    /** 选择与当前姿态差异最小、且能让指定局部面朝向目标的离散朝向。 */
    public static PlasticEntityOrientation targetOrientation(
        AbstractPlasticEntity entity,
        Direction selectedLocalFace,
        Direction targetWorldDirection,
        Player player
    ) {
        PlasticEntityOrientation current = entity.getOrientation();
        if (selectedLocalFace == Direction.DOWN) {
            return PlasticEntityOrientation.forPlacement(targetWorldDirection.getOpposite(), player);
        }

        PlasticEntityOrientation preferred = PlasticEntityOrientation.forPlacement(
            targetWorldDirection.getOpposite(),
            player
        );
        PlasticEntityOrientation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Direction attachmentFace : Direction.values()) {
            for (int quarterTurn = 0; quarterTurn < 4; quarterTurn++) {
                PlasticEntityOrientation candidate = new PlasticEntityOrientation(attachmentFace, quarterTurn);
                if (candidate.worldDirection(selectedLocalFace) != targetWorldDirection) continue;
                int score = alignmentScore(current, candidate) * 4 + alignmentScore(preferred, candidate);
                if (score > bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        if (best == null) throw new IllegalStateException("No plastic orientation can align the selected face");
        return best;
    }

    private static int alignmentScore(PlasticEntityOrientation first, PlasticEntityOrientation second) {
        return dot(first.orthogonalAxis(), second.orthogonalAxis())
            + dot(first.attachmentFace(), second.attachmentFace())
            + dot(first.longAxis(), second.longAxis());
    }

    private static int dot(Direction first, Direction second) {
        return first.getStepX() * second.getStepX()
            + first.getStepY() * second.getStepY()
            + first.getStepZ() * second.getStepZ();
    }
}
