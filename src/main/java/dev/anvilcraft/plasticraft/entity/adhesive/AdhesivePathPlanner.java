package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 在十六格范围内为树脂牵引计算可供实体碰撞箱通过的平滑路径。 */
public final class AdhesivePathPlanner {
    public static final double SAFE_DISTANCE = 12.0D;
    public static final double MAX_DISTANCE = 16.0D;
    public static final double BREAK_DISTANCE = 20.0D;
    private static final double DISTANCE_EPSILON = 1.0E-6D;
    private static final double COLLISION_EPSILON = 1.0E-4D;
    private static final double SEGMENT_SAMPLE_STEP = 0.2D;
    private static final double GOAL_CONNECTION_DISTANCE_SQR = 3.25D;
    private static final int GOAL_CONNECTION_MANHATTAN_ALLOWANCE = 4;
    private static final int DETOUR_COST_BUDGET = 24;
    private static final int DANGEROUS_TERRAIN_PENALTY = 1_000_000;
    private static final int MAX_EXPANDED_NODES = 65_536;
    // 目标附近最多四个曼哈顿步长可以直接连接，据此从路线预算反推紧凑数组所需的轴向边界。
    private static final int SEARCH_BOUND_MARGIN = Math.ceilDiv(
        DETOUR_COST_BUDGET + GOAL_CONNECTION_MANHATTAN_ALLOWANCE,
        2
    );
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
        List<Vec3> simplified = simplify(level, entity, originalBox, start, collapseCollinear(rawPath));
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
        AABB movedBox = movedCollisionBounds(
            entity,
            entity.getBoundingBox(),
            entity.position(),
            targetPosition
        );
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
        List<Vec3> simplified = simplify(level, entity, originalBox, start, collapseCollinear(rawPath));
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
        Vec3 gridOrigin = new Vec3(target.x, start.y, target.z);
        double startOffsetX = start.x - target.x;
        double startOffsetY = start.y - target.y;
        double startOffsetZ = start.z - target.z;
        int startX = (int) Math.round(startOffsetX);
        int startY = 0;
        int startZ = (int) Math.round(startOffsetZ);
        int goalX = 0;
        int goalY = (int) Math.round(target.y - start.y);
        int goalZ = 0;
        int directManhattan = (int) Math.ceil(
            Math.abs(startOffsetX) + Math.abs(startOffsetY) + Math.abs(startOffsetZ) - DISTANCE_EPSILON
        );
        int maxRouteCost = directManhattan + DETOUR_COST_BUDGET;
        SearchGrid grid = new SearchGrid(startX, startY, startZ, goalX, goalY, goalZ);
        int[] costs = new int[grid.nodeCount()];
        byte[] parents = new byte[grid.nodeCount()];
        boolean[] closed = new boolean[grid.nodeCount()];
        Arrays.fill(costs, Integer.MAX_VALUE);
        Arrays.fill(parents, (byte) -1);
        NodeHeap open = new NodeHeap(grid.nodeCount());

        // 水平轴锚定目标相位以穿过单格门洞，垂直轴保留起点高度，进入后再抬升到侧贴目标。
        for (int x = startX - 1; x <= startX + 1; x++) {
            for (int y = startY - 1; y <= startY + 1; y++) {
                for (int z = startZ - 1; z <= startZ + 1; z++) {
                    if (!grid.contains(x, y, z)) continue;
                    Vec3 seedPosition = gridOrigin.add(x, y, z);
                    double connectionDistanceSqr = start.distanceToSqr(seedPosition);
                    if (connectionDistanceSqr > GOAL_CONNECTION_DISTANCE_SQR
                        || !isSegmentClear(level, entity, originalBox, start, start, seedPosition)) {
                        continue;
                    }
                    int seed = grid.index(x, y, z);
                    int seedCost = (int) Math.ceil(Math.sqrt(connectionDistanceSqr) - DISTANCE_EPSILON);
                    if (seedCost >= costs[seed]) continue;
                    costs[seed] = seedCost;
                    open.addOrDecrease(seed, seedCost + seedPosition.distanceTo(target));
                }
            }
        }

        int reached = -1;
        int expanded = 0;
        while (!open.isEmpty() && expanded < MAX_EXPANDED_NODES) {
            int current = open.removeFirst();
            if (closed[current]) continue;
            closed[current] = true;
            expanded++;
            int currentX = grid.x(current);
            int currentY = grid.y(current);
            int currentZ = grid.z(current);
            Vec3 currentPosition = gridOrigin.add(currentX, currentY, currentZ);
            if (currentPosition.distanceToSqr(target) <= GOAL_CONNECTION_DISTANCE_SQR
                && isSegmentClear(level, entity, originalBox, start, currentPosition, target)) {
                reached = current;
                break;
            }

            for (Direction direction : SEARCH_DIRECTIONS) {
                int nextX = currentX + direction.getStepX();
                int nextY = currentY + direction.getStepY();
                int nextZ = currentZ + direction.getStepZ();
                if (!grid.contains(nextX, nextY, nextZ)) continue;
                int next = grid.index(nextX, nextY, nextZ);
                if (closed[next]) continue;

                Vec3 nextPosition = gridOrigin.add(nextX, nextY, nextZ);
                int stepCost = gridStepCost(level, entity, originalBox, start, currentPosition, nextPosition);
                if (stepCost == Integer.MAX_VALUE) continue;
                int nextCost = costs[current] + stepCost;
                int remainingLowerBound = Math.max(
                    0,
                    Math.abs(goalX - nextX)
                        + Math.abs(goalY - nextY)
                        + Math.abs(goalZ - nextZ)
                        - GOAL_CONNECTION_MANHATTAN_ALLOWANCE
                );
                if (nextCost + remainingLowerBound > maxRouteCost || nextCost >= costs[next]) continue;

                costs[next] = nextCost;
                parents[next] = (byte) direction.getOpposite().get3DDataValue();
                open.addOrDecrease(next, nextCost + nextPosition.distanceTo(target));
            }
        }
        if (reached < 0) return List.of();

        List<Vec3> reversed = new ArrayList<>();
        int cursor = reached;
        while (true) {
            reversed.add(gridOrigin.add(grid.x(cursor), grid.y(cursor), grid.z(cursor)));
            if (parents[cursor] < 0) break;
            Direction parentDirection = Direction.from3DDataValue(Byte.toUnsignedInt(parents[cursor]));
            cursor = grid.index(
                grid.x(cursor) + parentDirection.getStepX(),
                grid.y(cursor) + parentDirection.getStepY(),
                grid.z(cursor) + parentDirection.getStepZ()
            );
        }
        Collections.reverse(reversed);
        if (reversed.getFirst().distanceToSqr(start) > DISTANCE_EPSILON) reversed.addFirst(start);
        if (!reversed.getLast().equals(target)) reversed.add(target);
        return reversed;
    }

    private static int gridStepCost(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 from,
        Vec3 to
    ) {
        AABB fromBox = movedBox(originalBox, originalPosition, from);
        PlasticEntityCollisionBox fromCollisionBox = movedPlasticCollisionBox(entity, originalPosition, from);
        Vec3 movement = to.subtract(from);
        AABB sweptBox = (fromCollisionBox == null ? fromBox : fromCollisionBox.bounds())
            .expandTowards(movement);
        if (!level.getWorldBorder().isWithinBounds(sweptBox)) return Integer.MAX_VALUE;

        // 网格边始终是轴向一格，一次精确扫掠即可覆盖整个移动区间。
        Vec3 allowed = fromCollisionBox == null
            ? Entity.collideBoundingBox(entity, movement, fromBox, level, List.of())
            : fromCollisionBox.collide(entity, movement, level, List.of());
        if (!sameMovement(allowed, movement)) return Integer.MAX_VALUE;
        AABB destinationBounds = fromCollisionBox == null
            ? fromBox.move(movement)
            : fromCollisionBox.move(movement).bounds();
        return isDangerousPosition(level, destinationBounds)
            ? 1 + DANGEROUS_TERRAIN_PENALTY
            : 1;
    }

    private static boolean sameMovement(Vec3 first, Vec3 second) {
        return Math.abs(first.x - second.x) <= COLLISION_EPSILON
            && Math.abs(first.y - second.y) <= COLLISION_EPSILON
            && Math.abs(first.z - second.z) <= COLLISION_EPSILON;
    }

    private static List<Vec3> collapseCollinear(List<Vec3> path) {
        if (path.size() < 3) return path;
        List<Vec3> result = new ArrayList<>();
        result.add(path.getFirst());
        Vec3 previousDirection = path.get(1).subtract(path.getFirst());
        for (int index = 1; index < path.size() - 1; index++) {
            Vec3 nextDirection = path.get(index + 1).subtract(path.get(index));
            if (previousDirection.cross(nextDirection).lengthSqr() > 1.0E-8D
                || previousDirection.dot(nextDirection) <= 0.0D) {
                result.add(path.get(index));
            }
            previousDirection = nextDirection;
        }
        result.add(path.getLast());
        return result;
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
        return result.size() > 256 ? path : result;
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
        AABB moved = movedBox(originalBox, originalPosition, position);
        PlasticEntityCollisionBox movedCollisionBox = movedPlasticCollisionBox(
            entity,
            originalPosition,
            position
        );
        AABB collisionBounds = movedCollisionBox == null ? moved : movedCollisionBox.bounds();
        if (!level.getWorldBorder().isWithinBounds(collisionBounds)) return false;
        boolean collisionFree = movedCollisionBox == null
            ? level.noBlockCollision(entity, moved)
                || position.distanceToSqr(target) < 1.0E-8D
                && level.noBlockCollision(entity, moved.deflate(0.002D))
            : noBlockCollision(level, entity, movedCollisionBox, 0.0D)
                || position.distanceToSqr(target) < 1.0E-8D
                && noBlockCollision(level, entity, movedCollisionBox, 0.002D);
        return collisionFree && !isDangerousPosition(level, collisionBounds);
    }

    private static AABB movedBox(AABB originalBox, Vec3 originalPosition, Vec3 position) {
        return originalBox.move(position.subtract(originalPosition))
            .deflate(COLLISION_EPSILON);
    }

    private static AABB movedCollisionBounds(
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 position
    ) {
        PlasticEntityCollisionBox collisionBox = movedPlasticCollisionBox(entity, originalPosition, position);
        return collisionBox == null ? movedBox(originalBox, originalPosition, position) : collisionBox.bounds();
    }

    @Nullable
    private static PlasticEntityCollisionBox movedPlasticCollisionBox(
        Entity entity,
        Vec3 originalPosition,
        Vec3 position
    ) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionBox().move(position.subtract(originalPosition))
            : null;
    }

    private static boolean noBlockCollision(
        Level level,
        Entity entity,
        PlasticEntityCollisionBox collisionBox,
        double deflation
    ) {
        for (AABB component : collisionBox.components()) {
            AABB query = deflation == 0.0D ? component : component.deflate(deflation);
            if (!level.noBlockCollision(entity, query)) return false;
        }
        return true;
    }

    private static boolean isDangerousPosition(Level level, AABB box) {
        int minX = Mth.floor(box.minX + COLLISION_EPSILON);
        int minY = Mth.floor(box.minY + COLLISION_EPSILON);
        int minZ = Mth.floor(box.minZ + COLLISION_EPSILON);
        int maxX = Mth.floor(box.maxX - COLLISION_EPSILON);
        int maxY = Mth.floor(box.maxY - COLLISION_EPSILON);
        int maxZ = Mth.floor(box.maxZ - COLLISION_EPSILON);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (isDangerousState(level, cursor, level.getBlockState(cursor))) return true;
                }
            }
        }

        int floorY = Mth.floor(box.minY - 0.002D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cursor.set(x, floorY, z);
                BlockState state = level.getBlockState(cursor);
                if (state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state)) return true;
            }
        }
        return false;
    }

    private static boolean isDangerousState(Level level, BlockPos pos, BlockState state) {
        PathType blockPathType = state.getBlockPathType(level, pos, null);
        PathType fluidPathType = state.getFluidState().getBlockPathType(level, pos, null, false);
        return isDangerousPathType(blockPathType)
            || isDangerousPathType(fluidPathType)
            || state.getFluidState().is(FluidTags.LAVA)
            || state.is(BlockTags.FIRE)
            || state.is(Blocks.CACTUS)
            || state.is(Blocks.SWEET_BERRY_BUSH)
            || state.is(Blocks.WITHER_ROSE)
            || state.is(Blocks.POINTED_DRIPSTONE)
            || state.is(Blocks.POWDER_SNOW)
            || state.is(Blocks.MAGMA_BLOCK)
            || CampfireBlock.isLitCampfire(state);
    }

    private static boolean isDangerousPathType(@Nullable PathType pathType) {
        return pathType == PathType.LAVA
            || pathType == PathType.DAMAGE_FIRE
            || pathType == PathType.DANGER_FIRE
            || pathType == PathType.DAMAGE_OTHER
            || pathType == PathType.DANGER_OTHER
            || pathType == PathType.DAMAGE_CAUTIOUS
            || pathType == PathType.POWDER_SNOW
            || pathType == PathType.DANGER_POWDER_SNOW;
    }

    private static final class SearchGrid {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeX;
        private final int sizeY;
        private final int sizeZ;
        private final int nodeCount;

        private SearchGrid(int startX, int startY, int startZ, int goalX, int goalY, int goalZ) {
            this.minX = Math.min(startX, goalX) - SEARCH_BOUND_MARGIN;
            this.minY = Math.min(startY, goalY) - SEARCH_BOUND_MARGIN;
            this.minZ = Math.min(startZ, goalZ) - SEARCH_BOUND_MARGIN;
            this.sizeX = Math.max(startX, goalX) + SEARCH_BOUND_MARGIN - this.minX + 1;
            this.sizeY = Math.max(startY, goalY) + SEARCH_BOUND_MARGIN - this.minY + 1;
            this.sizeZ = Math.max(startZ, goalZ) + SEARCH_BOUND_MARGIN - this.minZ + 1;
            this.nodeCount = this.sizeX * this.sizeY * this.sizeZ;
        }

        private int nodeCount() {
            return this.nodeCount;
        }

        private boolean contains(int x, int y, int z) {
            return x >= this.minX
                && y >= this.minY
                && z >= this.minZ
                && x < this.minX + this.sizeX
                && y < this.minY + this.sizeY
                && z < this.minZ + this.sizeZ;
        }

        private int index(int x, int y, int z) {
            return ((x - this.minX) * this.sizeY + y - this.minY) * this.sizeZ + z - this.minZ;
        }

        private int x(int index) {
            return index / (this.sizeY * this.sizeZ) + this.minX;
        }

        private int y(int index) {
            return index / this.sizeZ % this.sizeY + this.minY;
        }

        private int z(int index) {
            return index % this.sizeZ + this.minZ;
        }
    }

    private static final class NodeHeap {
        private final int[] nodes;
        private final double[] scores;
        private final int[] positions;
        private int size;

        private NodeHeap(int capacity) {
            this.nodes = new int[capacity];
            this.scores = new double[capacity];
            this.positions = new int[capacity];
            Arrays.fill(this.positions, -1);
        }

        private boolean isEmpty() {
            return this.size == 0;
        }

        private void addOrDecrease(int node, double score) {
            int position = this.positions[node];
            if (position < 0) {
                position = this.size++;
                this.nodes[position] = node;
                this.scores[position] = score;
                this.positions[node] = position;
            } else if (score >= this.scores[position]) {
                return;
            } else {
                this.scores[position] = score;
            }
            this.siftUp(position);
        }

        private int removeFirst() {
            int result = this.nodes[0];
            this.positions[result] = -1;
            int last = --this.size;
            if (last > 0) {
                this.nodes[0] = this.nodes[last];
                this.scores[0] = this.scores[last];
                this.positions[this.nodes[0]] = 0;
                this.siftDown(0);
            }
            return result;
        }

        private void siftUp(int position) {
            while (position > 0) {
                int parent = (position - 1) >>> 1;
                if (this.scores[parent] <= this.scores[position]) return;
                this.swap(parent, position);
                position = parent;
            }
        }

        private void siftDown(int position) {
            while (true) {
                int left = position * 2 + 1;
                if (left >= this.size) return;
                int right = left + 1;
                int best = right < this.size && this.scores[right] < this.scores[left] ? right : left;
                if (this.scores[position] <= this.scores[best]) return;
                this.swap(position, best);
                position = best;
            }
        }

        private void swap(int first, int second) {
            int node = this.nodes[first];
            this.nodes[first] = this.nodes[second];
            this.nodes[second] = node;
            double score = this.scores[first];
            this.scores[first] = this.scores[second];
            this.scores[second] = score;
            this.positions[this.nodes[first]] = first;
            this.positions[this.nodes[second]] = second;
        }
    }
}
