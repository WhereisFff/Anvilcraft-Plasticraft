package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/** 在十六格范围内为树脂牵引计算可供实体碰撞箱通过的平滑路径。 */
public final class AdhesivePathPlanner {
    public static final double SAFE_DISTANCE = 12.0D;
    public static final double MAX_DISTANCE = 16.0D;
    public static final double BREAK_DISTANCE = 20.0D;
    private static final double DISTANCE_EPSILON = 1.0E-6D;
    private static final double COLLISION_EPSILON = 1.0E-4D;
    private static final double SEGMENT_SAMPLE_STEP = 0.2D;
    private static final int SEARCH_MARGIN = 4;
    private static final int MAX_EXPANDED_NODES = 8000;
    private static final Direction[] SEARCH_DIRECTIONS = {
        Direction.UP,
        Direction.NORTH,
        Direction.EAST,
        Direction.SOUTH,
        Direction.WEST,
        Direction.DOWN
    };

    private AdhesivePathPlanner() {
    }

    public enum Status {
        VALID,
        OUT_OF_RANGE,
        TARGET_BLOCKED,
        SOURCE_FACE_OCCUPIED,
        TARGET_FACE_OCCUPIED,
        SAME_GROUP,
        NO_PATH
    }

    public record Plan(
        Status status,
        List<Vec3> points,
        Vec3 targetPosition,
        BlockPos occupiedPos,
        @Nullable PlasticEntityOrientation targetOrientation,
        Direction sourceFace,
        double directDistance
    ) {
        public Plan {
            points = List.copyOf(points);
        }

        public boolean valid() {
            return this.status == Status.VALID;
        }
    }

    public static Plan plan(
        Level level,
        Entity entity,
        Player player,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        Direction selectedFace = entity instanceof AbstractPlasticEntity
            ? Direction.DOWN
            : attachmentFace.getOpposite();
        return plan(level, entity, player, supportPos, attachmentFace, selectedFace);
    }

    public static Plan plan(
        Level level,
        Entity entity,
        Player player,
        BlockPos supportPos,
        Direction attachmentFace,
        Direction selectedFace
    ) {
        BlockPos occupiedPos = supportPos.relative(attachmentFace);
        Direction sourceFace = entity instanceof AbstractPlasticEntity ? selectedFace : attachmentFace.getOpposite();
        PlasticEntityOrientation targetOrientation = entity instanceof AbstractPlasticEntity plastic
            ? AdhesiveFaces.targetOrientation(plastic, sourceFace, attachmentFace.getOpposite(), player)
            : null;
        Vec3 targetPosition = targetPosition(entity, supportPos, attachmentFace, targetOrientation);
        double directDistance = entity.position().distanceTo(targetPosition);
        List<Vec3> directPreview = List.of(entity.position(), targetPosition);

        if (EntityBondManager.isFaceOccupied(entity, sourceFace)) {
            return new Plan(
                Status.SOURCE_FACE_OCCUPIED,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        if (isOutOfRange(directDistance)) {
            return new Plan(
                Status.OUT_OF_RANGE,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        if (!level.hasChunkAt(supportPos)
            || !level.hasChunkAt(occupiedPos)
            || !level.getWorldBorder().isWithinBounds(occupiedPos)
            || (entity instanceof AbstractPlasticEntity
                || AdhesiveFallingBlockBehavior.canBlockifyComponent(level, entity))
                && !level.getBlockState(occupiedPos).canBeReplaced()) {
            return new Plan(
                Status.TARGET_BLOCKED,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }

        AABB originalBox = entity.getBoundingBox();
        Vec3 start = entity.position();
        if (isSegmentClear(level, entity, originalBox, start, start, targetPosition)) {
            return new Plan(
                Status.VALID,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }

        List<Vec3> rawPath = findGridPath(level, entity, originalBox, start, targetPosition);
        if (rawPath.isEmpty()) {
            return new Plan(
                Status.NO_PATH,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        List<Vec3> simplified = simplify(level, entity, originalBox, start, rawPath);
        List<Vec3> rounded = roundCorners(level, entity, originalBox, start, simplified);
        return new Plan(
            Status.VALID,
            rounded,
            targetPosition,
            occupiedPos,
            targetOrientation,
            sourceFace,
            directDistance
        );
    }

    public static Plan planToEntity(
        Level level,
        Entity entity,
        Player player,
        Entity supportEntity,
        Direction supportWorldFace,
        Direction selectedFace
    ) {
        Direction sourceFace = entity instanceof AbstractPlasticEntity ? selectedFace : supportWorldFace.getOpposite();
        Direction supportStoredFace = AdhesiveFaces.storedFace(supportEntity, supportWorldFace);
        PlasticEntityOrientation targetOrientation = entity instanceof AbstractPlasticEntity plastic
            ? AdhesiveFaces.targetOrientation(plastic, sourceFace, supportWorldFace.getOpposite(), player)
            : null;
        Vec3 targetPosition = targetPosition(entity, supportEntity, supportWorldFace);
        double directDistance = entity.position().distanceTo(targetPosition);
        List<Vec3> directPreview = List.of(entity.position(), targetPosition);
        BlockPos occupiedPos = BlockPos.containing(targetPosition);

        EntityBondState sourceBonds = EntityBondManager.get(entity);
        EntityBondState targetBonds = EntityBondManager.get(supportEntity);
        if (sourceBonds != null
            && targetBonds != null
            && sourceBonds.leaderUuid().equals(targetBonds.leaderUuid())) {
            return new Plan(
                Status.SAME_GROUP,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        if (EntityBondManager.isFaceOccupied(entity, sourceFace)) {
            return new Plan(
                Status.SOURCE_FACE_OCCUPIED,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        if (EntityBondManager.isFaceOccupied(supportEntity, supportStoredFace)) {
            return new Plan(
                Status.TARGET_FACE_OCCUPIED,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        if (isOutOfRange(directDistance)) {
            return new Plan(
                Status.OUT_OF_RANGE,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        AABB movedBox = entity.getBoundingBox().move(targetPosition.subtract(entity.position()));
        if (!supportEntity.isAlive()
            || supportEntity == entity
            || !level.getWorldBorder().isWithinBounds(movedBox)) {
            return new Plan(
                Status.TARGET_BLOCKED,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }

        AABB originalBox = entity.getBoundingBox();
        Vec3 start = entity.position();
        if (isSegmentClear(level, entity, originalBox, start, start, targetPosition)) {
            return new Plan(
                Status.VALID,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        List<Vec3> rawPath = findGridPath(level, entity, originalBox, start, targetPosition);
        if (rawPath.isEmpty()) {
            return new Plan(
                Status.NO_PATH,
                directPreview,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }
        List<Vec3> simplified = simplify(level, entity, originalBox, start, rawPath);
        List<Vec3> rounded = roundCorners(level, entity, originalBox, start, simplified);
        return new Plan(
            Status.VALID,
            rounded,
            targetPosition,
            occupiedPos,
            targetOrientation,
            sourceFace,
            directDistance
        );
    }

    public static boolean isOutOfRange(double distance) {
        return distance > MAX_DISTANCE + DISTANCE_EPSILON;
    }

    public static boolean exceedsBreakDistance(double distance) {
        return distance > BREAK_DISTANCE + DISTANCE_EPSILON;
    }

    private static Vec3 targetPosition(
        Entity entity,
        BlockPos supportPos,
        Direction attachmentFace,
        @Nullable PlasticEntityOrientation orientation
    ) {
        BlockPos occupiedPos = supportPos.relative(attachmentFace);
        if (entity instanceof AbstractPlasticEntity && orientation != null) {
            return orientation.entityPosition(occupiedPos, entity.getBbWidth(), entity.getBbHeight());
        }
        if (AdhesiveFallingBlockBehavior.canBlockifyComponent(entity.level(), entity)) {
            return Vec3.atBottomCenterOf(occupiedPos);
        }

        Vec3 surfaceCenter = Vec3.atCenterOf(supportPos).add(
            attachmentFace.getStepX() * 0.5D,
            attachmentFace.getStepY() * 0.5D,
            attachmentFace.getStepZ() * 0.5D
        );
        AABB box = entity.getBoundingBox();
        double halfExtent = switch (attachmentFace.getAxis()) {
            case X -> box.getXsize() * 0.5D;
            case Y -> box.getYsize() * 0.5D;
            case Z -> box.getZsize() * 0.5D;
        };
        Vec3 desiredCenter = surfaceCenter.add(
            attachmentFace.getStepX() * (halfExtent + 0.001D),
            attachmentFace.getStepY() * (halfExtent + 0.001D),
            attachmentFace.getStepZ() * (halfExtent + 0.001D)
        );
        return entity.position().add(desiredCenter.subtract(box.getCenter()));
    }

    private static Vec3 targetPosition(Entity entity, Entity supportEntity, Direction supportFace) {
        AABB supportBox = supportEntity.getBoundingBox();
        Vec3 surfaceCenter = supportBox.getCenter();
        surfaceCenter = switch (supportFace.getAxis()) {
            case X -> new Vec3(supportFace == Direction.EAST ? supportBox.maxX : supportBox.minX,
                surfaceCenter.y, surfaceCenter.z);
            case Y -> new Vec3(surfaceCenter.x,
                supportFace == Direction.UP ? supportBox.maxY : supportBox.minY, surfaceCenter.z);
            case Z -> new Vec3(surfaceCenter.x, surfaceCenter.y,
                supportFace == Direction.SOUTH ? supportBox.maxZ : supportBox.minZ);
        };
        AABB sourceBox = entity.getBoundingBox();
        double halfExtent = switch (supportFace.getAxis()) {
            case X -> sourceBox.getXsize() * 0.5D;
            case Y -> sourceBox.getYsize() * 0.5D;
            case Z -> sourceBox.getZsize() * 0.5D;
        };
        Vec3 desiredCenter = surfaceCenter.add(Vec3.atLowerCornerOf(supportFace.getNormal()).scale(
            halfExtent + 0.001D
        ));
        return entity.position().add(desiredCenter.subtract(sourceBox.getCenter()));
    }

    private static List<Vec3> findGridPath(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        Vec3 target
    ) {
        GridNode startNode = new GridNode(0, 0, 0);
        int goalX = (int) Math.round(target.x - start.x);
        int goalY = (int) Math.round(target.y - start.y);
        int goalZ = (int) Math.round(target.z - start.z);
        int minX = Math.min(0, goalX) - SEARCH_MARGIN;
        int minY = Math.min(0, goalY) - SEARCH_MARGIN;
        int minZ = Math.min(0, goalZ) - SEARCH_MARGIN;
        int maxX = Math.max(0, goalX) + SEARCH_MARGIN;
        int maxY = Math.max(0, goalY) + SEARCH_MARGIN;
        int maxZ = Math.max(0, goalZ) + SEARCH_MARGIN;

        PriorityQueue<SearchEntry> open = new PriorityQueue<>();
        Map<GridNode, Double> costs = new HashMap<>();
        Map<GridNode, GridNode> parents = new HashMap<>();
        Set<GridNode> closed = new HashSet<>();
        costs.put(startNode, 0.0D);
        open.add(new SearchEntry(startNode, start.distanceTo(target)));

        GridNode reached = null;
        int expanded = 0;
        while (!open.isEmpty() && expanded++ < MAX_EXPANDED_NODES) {
            GridNode current = open.poll().node();
            if (!closed.add(current)) continue;
            Vec3 currentPosition = current.position(start);
            if (currentPosition.distanceToSqr(target) <= 3.25D
                && isSegmentClear(level, entity, originalBox, start, currentPosition, target)) {
                reached = current;
                break;
            }

            for (Direction direction : SEARCH_DIRECTIONS) {
                GridNode next = current.relative(direction);
                if (next.x < minX || next.x > maxX
                    || next.y < minY || next.y > maxY
                    || next.z < minZ || next.z > maxZ
                    || closed.contains(next)) {
                    continue;
                }
                Vec3 nextPosition = next.position(start);
                if (nextPosition.distanceToSqr(start) > (MAX_DISTANCE + SEARCH_MARGIN) * (MAX_DISTANCE + SEARCH_MARGIN)
                    || !isSegmentClear(level, entity, originalBox, start, currentPosition, nextPosition)) {
                    continue;
                }
                double nextCost = costs.get(current) + 1.0D;
                if (nextCost >= costs.getOrDefault(next, Double.POSITIVE_INFINITY)) continue;
                costs.put(next, nextCost);
                parents.put(next, current);
                open.add(new SearchEntry(next, nextCost + nextPosition.distanceTo(target)));
            }
        }
        if (reached == null) return List.of();

        List<Vec3> reversed = new ArrayList<>();
        GridNode cursor = reached;
        while (cursor != null) {
            reversed.add(cursor.position(start));
            cursor = parents.get(cursor);
        }
        Collections.reverse(reversed);
        if (!reversed.getFirst().equals(start)) reversed.addFirst(start);
        if (!reversed.getLast().equals(target)) reversed.add(target);
        return reversed;
    }

    private static List<Vec3> simplify(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        List<Vec3> raw
    ) {
        List<Vec3> result = new ArrayList<>();
        int index = 0;
        result.add(raw.getFirst());
        while (index < raw.size() - 1) {
            int next = raw.size() - 1;
            while (next > index + 1
                && !isSegmentClear(level, entity, originalBox, start, raw.get(index), raw.get(next))) {
                next--;
            }
            result.add(raw.get(next));
            index = next;
        }
        return result;
    }

    private static List<Vec3> roundCorners(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        List<Vec3> path
    ) {
        if (path.size() < 3) return path;
        List<Vec3> result = new ArrayList<>();
        result.add(path.getFirst());
        for (int index = 1; index < path.size() - 1; index++) {
            Vec3 previous = path.get(index - 1);
            Vec3 corner = path.get(index);
            Vec3 next = path.get(index + 1);
            Vec3 incoming = corner.subtract(previous);
            Vec3 outgoing = next.subtract(corner);
            if (incoming.lengthSqr() < 1.0E-8D || outgoing.lengthSqr() < 1.0E-8D) continue;

            double radius = Math.min(0.45D, Math.min(incoming.length(), outgoing.length()) * 0.3D);
            List<Vec3> curve = List.of();
            while (radius >= 0.025D) {
                Vec3 entry = corner.subtract(incoming.normalize().scale(radius));
                Vec3 exit = corner.add(outgoing.normalize().scale(radius));
                List<Vec3> candidate = quadraticCurve(entry, corner, exit);
                if (isSegmentClear(level, entity, originalBox, start, result.getLast(), entry)
                    && isCurveClear(level, entity, originalBox, start, candidate)) {
                    curve = candidate;
                    break;
                }
                radius *= 0.5D;
            }
            if (curve.isEmpty()) {
                result.add(corner);
            } else {
                result.addAll(curve);
            }
        }
        Vec3 end = path.getLast();
        if (!result.getLast().equals(end)) result.add(end);
        return result.size() > 256 ? result.subList(0, 256) : result;
    }

    private static List<Vec3> quadraticCurve(Vec3 start, Vec3 control, Vec3 end) {
        List<Vec3> points = new ArrayList<>(7);
        for (int step = 0; step <= 6; step++) {
            double t = step / 6.0D;
            double inverse = 1.0D - t;
            points.add(start.scale(inverse * inverse)
                .add(control.scale(2.0D * inverse * t))
                .add(end.scale(t * t)));
        }
        return points;
    }

    private static boolean isCurveClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        List<Vec3> curve
    ) {
        for (int i = 1; i < curve.size(); i++) {
            if (!isSegmentClear(level, entity, originalBox, start, curve.get(i - 1), curve.get(i))) return false;
        }
        return true;
    }

    private static boolean isSegmentClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 from,
        Vec3 to
    ) {
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance / SEGMENT_SAMPLE_STEP));
        for (int step = 1; step <= steps; step++) {
            Vec3 position = from.lerp(to, step / (double) steps);
            if (!isPositionClear(level, entity, originalBox, originalPosition, position, to)) return false;
        }
        return true;
    }

    private static boolean isPositionClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 position,
        Vec3 target
    ) {
        AABB moved = originalBox.move(position.subtract(originalPosition))
            .deflate(COLLISION_EPSILON)
            .move(0.0D, 0.001D, 0.0D);
        if (!level.getWorldBorder().isWithinBounds(moved)) return false;
        if (level.noBlockCollision(entity, moved)) return true;
        return position.distanceToSqr(target) < 1.0E-8D
            && level.noBlockCollision(entity, moved.deflate(0.002D));
    }

    private record GridNode(int x, int y, int z) {
        private GridNode relative(Direction direction) {
            return new GridNode(
                this.x + direction.getStepX(),
                this.y + direction.getStepY(),
                this.z + direction.getStepZ()
            );
        }

        private Vec3 position(Vec3 origin) {
            return origin.add(this.x, this.y, this.z);
        }
    }

    private record SearchEntry(GridNode node, double score) implements Comparable<SearchEntry> {
        @Override
        public int compareTo(SearchEntry other) {
            return Double.compare(this.score, other.score);
        }
    }
}
