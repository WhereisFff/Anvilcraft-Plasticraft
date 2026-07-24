package dev.anvilcraft.plasticraft.entity.adhesive;

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
        AABB box = entity.getBoundingBox();
        Direction nearest = Direction.DOWN;
        double nearestDistance = Math.abs(hitLocation.y - box.minY);
        for (Direction direction : Direction.values()) {
            double distance = switch (direction) {
                case DOWN -> Math.abs(hitLocation.y - box.minY);
                case UP -> Math.abs(hitLocation.y - box.maxY);
                case NORTH -> Math.abs(hitLocation.z - box.minZ);
                case SOUTH -> Math.abs(hitLocation.z - box.maxZ);
                case WEST -> Math.abs(hitLocation.x - box.minX);
                case EAST -> Math.abs(hitLocation.x - box.maxX);
            };
            if (distance < nearestDistance) {
                nearest = direction;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    public static Direction hitFaceFromLocal(Entity entity, Vec3 localHit) {
        return hitFace(entity, entity.position().add(localHit));
    }

    public static Direction hitFace(Player player, Entity entity) {
        Vec3 start = player.getEyePosition();
        double reach = Math.max(6.0D, start.distanceTo(entity.getBoundingBox().getCenter()) + 2.0D);
        Vec3 end = start.add(player.getViewVector(1.0F).scale(reach));
        Vec3 hit = entity.getBoundingBox().inflate(PICK_INFLATION).clip(start, end)
            .orElse(entity.getBoundingBox().getCenter());
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
