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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 在十六格范围内为树脂牵引计算可供实体碰撞箱通过的平滑路径。 */
public final class AdhesivePathPlanner {
    public static final double SAFE_DISTANCE = 12.0D;
    public static final double MAX_DISTANCE = 16.0D;
    public static final double BREAK_DISTANCE = 20.0D;
    private static final double DISTANCE_EPSILON = 1.0E-6D;
    private static final double COLLISION_EPSILON = 1.0E-4D;
    private static final double SEGMENT_SAMPLE_STEP = 0.05D;
    private static final double CURVE_SEGMENT_SAMPLE_STEP = 0.01D;
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
    private static final int PATH_SEARCH_THREADS = Math.clamp(
        Runtime.getRuntime().availableProcessors() - 1,
        1,
        2
    );
    private static final AtomicInteger PATH_SEARCH_THREAD_IDS = new AtomicInteger();
    private static final ThreadPoolExecutor PATH_SEARCH_EXECUTOR = new ThreadPoolExecutor(
        PATH_SEARCH_THREADS,
        PATH_SEARCH_THREADS,
        0L,
        TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(),
        runnable -> {
            Thread thread = new Thread(
                runnable,
                "plasticraft-adhesive-path-search-" + PATH_SEARCH_THREAD_IDS.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        }
    );

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

    public static PreviewTask beginPreview(
        Level level,
        Entity entity,
        Player player,
        BlockPos supportPos,
        Direction attachmentFace,
        Direction selectedFace
    ) {
        return PreviewTask.forBlock(level, entity, player, supportPos, attachmentFace, selectedFace);
    }

    public static PreviewTask beginPreviewToEntity(
        Level level,
        Entity entity,
        Player player,
        Entity supportEntity,
        Direction supportWorldFace,
        Direction selectedFace
    ) {
        return PreviewTask.forEntity(
            level,
            entity,
            player,
            supportEntity,
            supportWorldFace,
            selectedFace
        );
    }

    public static final class PreviewTask {
        private final Level level;
        private final Entity entity;
        private final AABB originalBox;
        private final Vec3 start;
        private final Plan directPreview;
        private final int maxExpandedNodes;
        private boolean complete;
        private boolean searchStarted;
        private @Nullable Future<List<Vec3>> searchFuture;
        private @Nullable Plan result;

        private PreviewTask(Plan result) {
            this.level = null;
            this.entity = null;
            this.originalBox = null;
            this.start = null;
            this.directPreview = result;
            this.maxExpandedNodes = 0;
            this.complete = true;
            this.result = result;
        }

        private PreviewTask(
            Level level,
            Entity entity,
            AABB originalBox,
            Vec3 start,
            Plan directPreview
        ) {
            this.level = level;
            this.entity = entity;
            this.originalBox = originalBox;
            this.start = start;
            this.directPreview = directPreview;
            this.maxExpandedNodes = MAX_EXPANDED_NODES;
        }

        private static PreviewTask forBlock(
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
            Vec3 start = entity.position();
            Plan directPreview = new Plan(
                Status.VALID,
                List.of(start, targetPosition),
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );

            if (EntityBondManager.isFaceOccupied(entity, sourceFace)) {
                return new PreviewTask(withStatus(directPreview, Status.SOURCE_FACE_OCCUPIED));
            }
            if (isOutOfRange(directDistance)) {
                return new PreviewTask(withStatus(directPreview, Status.OUT_OF_RANGE));
            }
            if (!level.hasChunkAt(supportPos)
                || !level.hasChunkAt(occupiedPos)
                || !level.getWorldBorder().isWithinBounds(occupiedPos)
                || (entity instanceof AbstractPlasticEntity
                    || AdhesiveFallingBlockBehavior.canBlockifyComponent(level, entity))
                    && !level.getBlockState(occupiedPos).canBeReplaced()) {
                return new PreviewTask(withStatus(directPreview, Status.TARGET_BLOCKED));
            }
            return new PreviewTask(level, entity, entity.getBoundingBox(), start, directPreview);
        }

        private static PreviewTask forEntity(
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
            Vec3 targetPosition = targetPosition(
                entity,
                sourceFace,
                targetOrientation,
                supportEntity,
                supportWorldFace
            );
            double directDistance = entity.position().distanceTo(targetPosition);
            Vec3 start = entity.position();
            Plan directPreview = new Plan(
                Status.VALID,
                List.of(start, targetPosition),
                targetPosition,
                BlockPos.containing(targetPosition),
                targetOrientation,
                sourceFace,
                directDistance
            );

            EntityBondState sourceBonds = EntityBondManager.get(entity);
            EntityBondState targetBonds = EntityBondManager.get(supportEntity);
            if (sourceBonds != null
                && targetBonds != null
                && sourceBonds.leaderUuid().equals(targetBonds.leaderUuid())) {
                return new PreviewTask(withStatus(directPreview, Status.SAME_GROUP));
            }
            if (EntityBondManager.isFaceOccupied(entity, sourceFace)) {
                return new PreviewTask(withStatus(directPreview, Status.SOURCE_FACE_OCCUPIED));
            }
            if (EntityBondManager.isFaceOccupied(supportEntity, supportStoredFace)) {
                return new PreviewTask(withStatus(directPreview, Status.TARGET_FACE_OCCUPIED));
            }
            if (isOutOfRange(directDistance)) {
                return new PreviewTask(withStatus(directPreview, Status.OUT_OF_RANGE));
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
                return new PreviewTask(withStatus(directPreview, Status.TARGET_BLOCKED));
            }
            if (entity instanceof AbstractPlasticEntity plasticEntity
                && targetOrientation != null
                && !plasticEntity.plasticraft$canOccupyBlocks(targetOrientation, targetPosition)) {
                return new PreviewTask(withStatus(directPreview, Status.TARGET_BLOCKED));
            }
            return new PreviewTask(level, entity, entity.getBoundingBox(), start, directPreview);
        }

        private static Plan withStatus(Plan preview, Status status) {
            return new Plan(
                status,
                preview.points(),
                preview.targetPosition(),
                preview.occupiedPos(),
                preview.targetOrientation(),
                preview.sourceFace(),
                preview.directDistance()
            );
        }

        public boolean isComplete() {
            return this.complete;
        }

        public @Nullable Plan result() {
            return this.result;
        }

        public void advance() {
            if (this.complete) return;
            if (!this.searchStarted) {
                this.searchStarted = true;
                if (isSegmentClear(
                    this.level,
                    this.entity,
                    this.originalBox,
                    this.start,
                    this.start,
                    this.directPreview.targetPosition(),
                    true,
                    SEGMENT_SAMPLE_STEP,
                    new PathCheckCache()
                )) {
                    finishPath(this.directPreview.points());
                    return;
                }
                PathSearchCapture capture = PathSearchCapture.create(
                    this.level,
                    this.entity,
                    this.originalBox,
                    this.start,
                    this.directPreview.targetPosition()
                );
                if (capture == null) {
                    this.result = withStatus(this.directPreview, Status.NO_PATH);
                    this.complete = true;
                    return;
                }
                this.searchFuture = PATH_SEARCH_EXECUTOR.submit(
                    () -> capture.findPath(
                        this.start,
                        this.directPreview.targetPosition(),
                        this.maxExpandedNodes
                    )
                );
            }

            if (this.searchFuture != null) {
                if (!this.searchFuture.isDone()) return;
                List<Vec3> rawPath;
                try {
                    rawPath = this.searchFuture.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    rawPath = List.of();
                } catch (CancellationException | ExecutionException exception) {
                    rawPath = List.of();
                }
                finishPath(rawPath);
            }
        }

        private void finishPath(List<Vec3> path) {
            if (path.isEmpty()) {
                this.result = withStatus(this.directPreview, Status.NO_PATH);
            } else {
                this.result = new Plan(
                    Status.VALID,
                    path,
                    this.directPreview.targetPosition(),
                    this.directPreview.occupiedPos(),
                    this.directPreview.targetOrientation(),
                    this.directPreview.sourceFace(),
                    this.directPreview.directDistance()
                );
            }
            this.complete = true;
        }

        public void cancel() {
            if (this.searchFuture != null) {
                this.searchFuture.cancel(true);
                if (this.searchFuture instanceof Runnable queuedTask) {
                    PATH_SEARCH_EXECUTOR.remove(queuedTask);
                }
            }
            this.complete = true;
        }

        public Plan displayPlan() {
            return this.result == null ? this.directPreview : this.result;
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
        PathCheckCache checks = new PathCheckCache();
        if (isSegmentClear(
            level,
            entity,
            originalBox,
            start,
            start,
            targetPosition,
            true,
            SEGMENT_SAMPLE_STEP,
            checks
        )) {
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

        List<Vec3> sharedPath = findSnapshotPath(level, entity, originalBox, start, targetPosition);
        if (!sharedPath.isEmpty()
            && isPathClear(
                new LiveSearchCollision(level, entity, originalBox, start, checks),
                sharedPath
            )) {
            return new Plan(
                Status.VALID,
                sharedPath,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }

        List<Vec3> rawPath = findGridPath(level, entity, originalBox, start, targetPosition, checks);
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
        List<Vec3> simplified = simplify(
            level,
            entity,
            originalBox,
            start,
            collapseCollinear(rawPath),
            checks
        );
        List<Vec3> rounded = roundCorners(level, entity, originalBox, start, simplified, checks);
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
        Vec3 targetPosition = targetPosition(
            entity,
            sourceFace,
            targetOrientation,
            supportEntity,
            supportWorldFace
        );
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
        // 目标姿态的轮廓可能超出当前姿态，路径搜索不能只检查移动前的几何。
        if (entity instanceof AbstractPlasticEntity plasticEntity
            && targetOrientation != null
            && !plasticEntity.plasticraft$canOccupyBlocks(targetOrientation, targetPosition)) {
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
        PathCheckCache checks = new PathCheckCache();
        if (isSegmentClear(
            level,
            entity,
            originalBox,
            start,
            start,
            targetPosition,
            true,
            SEGMENT_SAMPLE_STEP,
            checks
        )) {
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
        List<Vec3> sharedPath = findSnapshotPath(level, entity, originalBox, start, targetPosition);
        if (!sharedPath.isEmpty()
            && isPathClear(
                new LiveSearchCollision(level, entity, originalBox, start, checks),
                sharedPath
            )) {
            return new Plan(
                Status.VALID,
                sharedPath,
                targetPosition,
                occupiedPos,
                targetOrientation,
                sourceFace,
                directDistance
            );
        }

        List<Vec3> rawPath = findGridPath(level, entity, originalBox, start, targetPosition, checks);
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
        List<Vec3> simplified = simplify(
            level,
            entity,
            originalBox,
            start,
            collapseCollinear(rawPath),
            checks
        );
        List<Vec3> rounded = roundCorners(level, entity, originalBox, start, simplified, checks);
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
        if (entity instanceof AbstractPlasticEntity plasticEntity && orientation != null) {
            return plasticEntity.plasticraft$placementPosition(occupiedPos, orientation);
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

    private static Vec3 targetPosition(
        Entity entity,
        Direction sourceFace,
        @Nullable PlasticEntityOrientation targetOrientation,
        Entity supportEntity,
        Direction supportFace
    ) {
        Vec3 supportPoint = AdhesiveFaces.worldFaceAlignmentPoint(supportEntity, supportFace);
        Vec3 sourcePoint = entity instanceof AbstractPlasticEntity plastic && targetOrientation != null
            ? AdhesiveFaces.storedFaceAlignmentPoint(plastic, targetOrientation, sourceFace)
            : AdhesiveFaces.storedFaceAlignmentPoint(entity, sourceFace);
        return entity.position().add(supportPoint.subtract(sourcePoint));
    }

    private static List<Vec3> findGridPath(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        Vec3 target,
        PathCheckCache checks
    ) {
        IncrementalSearch search = new IncrementalSearch(
            new LiveSearchCollision(level, entity, originalBox, start, checks),
            start,
            target,
            MAX_EXPANDED_NODES
        );
        while (!search.isFinished()) {
            search.advance(Long.MAX_VALUE, Integer.MAX_VALUE, SEGMENT_SAMPLE_STEP);
        }
        return search.path();
    }

    private static List<Vec3> findSnapshotPath(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 start,
        Vec3 target
    ) {
        PathSearchCapture capture = PathSearchCapture.create(
            level,
            entity,
            originalBox,
            start,
            target
        );
        return capture == null ? List.of() : capture.findPath(start, target, MAX_EXPANDED_NODES);
    }

    private static int gridStepCost(
        SearchCollision collision,
        Vec3 from,
        Vec3 to,
        int destinationNode,
        boolean[] dangerousKnown,
        boolean[] dangerous
    ) {
        if (!collision.isSweptSegmentClear(from, to)) {
            return Integer.MAX_VALUE;
        }
        AABB destinationBounds = collision.collisionBounds(to);
        if (!dangerousKnown[destinationNode]) {
            dangerous[destinationNode] = collision.isDangerousPosition(destinationBounds);
            dangerousKnown[destinationNode] = true;
        }
        return dangerous[destinationNode]
            ? 1 + DANGEROUS_TERRAIN_PENALTY
            : 1;
    }

    private static List<Vec3> simplify(SearchCollision collision, List<Vec3> raw) {
        if (raw.isEmpty()) return List.of();
        List<Vec3> result = new ArrayList<>();
        int index = 0;
        result.add(raw.getFirst());
        while (index < raw.size() - 1) {
            int next = raw.size() - 1;
            while (next > index + 1
                && !collision.isSegmentClear(
                    raw.get(index),
                    raw.get(next),
                    next == raw.size() - 1,
                    SEGMENT_SAMPLE_STEP
                )) {
                next--;
            }
            result.add(raw.get(next));
            index = next;
        }
        return result;
    }

    private static List<Vec3> roundCorners(SearchCollision collision, List<Vec3> path) {
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
                boolean reachesTarget = index == path.size() - 2;
                if (collision.isSegmentClear(
                        result.getLast(),
                        entry,
                        false,
                        SEGMENT_SAMPLE_STEP
                    )
                    && isCurveClear(collision, candidate)
                    && collision.isSegmentClear(
                        candidate.getLast(),
                        next,
                        reachesTarget,
                        SEGMENT_SAMPLE_STEP
                    )) {
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

    private static boolean isCurveClear(SearchCollision collision, List<Vec3> curve) {
        for (int index = 1; index < curve.size(); index++) {
            if (!collision.isSegmentClear(
                curve.get(index - 1),
                curve.get(index),
                false,
                CURVE_SEGMENT_SAMPLE_STEP
            )) return false;
        }
        return true;
    }

    private static boolean isPathClear(SearchCollision collision, List<Vec3> path) {
        for (int index = 0; index < path.size() - 1; index++) {
            if (!collision.isSegmentClear(
                path.get(index),
                path.get(index + 1),
                index == path.size() - 2,
                SEGMENT_SAMPLE_STEP
            )) return false;
        }
        return true;
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
        List<Vec3> raw,
        PathCheckCache checks
    ) {
        List<Vec3> result = new ArrayList<>();
        int index = 0;
        result.add(raw.getFirst());
        while (index < raw.size() - 1) {
            int next = raw.size() - 1;
            while (next > index + 1
                && !isSegmentClear(
                    level,
                    entity,
                    originalBox,
                    start,
                    raw.get(index),
                    raw.get(next),
                    next == raw.size() - 1,
                    SEGMENT_SAMPLE_STEP,
                    checks
                )) {
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
        List<Vec3> path,
        PathCheckCache checks
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
                boolean reachesTarget = index == path.size() - 2;
                if (isSegmentClear(
                        level,
                        entity,
                        originalBox,
                        start,
                        result.getLast(),
                        entry,
                        false,
                        SEGMENT_SAMPLE_STEP,
                        checks
                    )
                    && isCurveClear(level, entity, originalBox, start, candidate, checks)
                    && isSegmentClear(
                        level,
                        entity,
                        originalBox,
                        start,
                        candidate.getLast(),
                        next,
                        reachesTarget,
                        SEGMENT_SAMPLE_STEP,
                        checks
                    )) {
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
        List<Vec3> curve,
        PathCheckCache checks
    ) {
        for (int i = 1; i < curve.size(); i++) {
            if (!isSegmentClear(
                level,
                entity,
                originalBox,
                start,
                curve.get(i - 1),
                curve.get(i),
                false,
                CURVE_SEGMENT_SAMPLE_STEP,
                checks
            )) return false;
        }
        return true;
    }

    private static boolean isSegmentClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 from,
        Vec3 to,
        boolean allowFinalContact,
        double sampleStep,
        PathCheckCache checks
    ) {
        SegmentKey key = new SegmentKey(
            from,
            to,
            allowFinalContact,
            Double.doubleToLongBits(sampleStep)
        );
        Boolean cached = checks.segmentResults.get(key);
        if (cached != null) return cached;
        if (!isSweptSegmentClear(level, entity, originalBox, originalPosition, from, to, checks)) {
            checks.segmentResults.put(key, false);
            return false;
        }
        double distance = from.distanceTo(to);
        int steps = Math.max(1, (int) Math.ceil(distance / sampleStep));
        for (int step = 1; step <= steps; step++) {
            Vec3 position = from.lerp(to, step / (double) steps);
            if (!isPositionClear(
                level,
                entity,
                originalBox,
                originalPosition,
                position,
                allowFinalContact && step == steps,
                checks
            )) {
                checks.segmentResults.put(key, false);
                return false;
            }
        }
        checks.segmentResults.put(key, true);
        return true;
    }

    private static boolean isSweptSegmentClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 from,
        Vec3 to,
        PathCheckCache checks
    ) {
        SweptSegmentKey key = new SweptSegmentKey(from, to);
        Boolean cached = checks.sweptResults.get(key);
        if (cached != null) return cached;
        AABB fromBox = movedBox(originalBox, originalPosition, from);
        PlasticEntityCollisionBox fromCollisionBox = movedPlasticCollisionBox(entity, originalPosition, from);
        Vec3 movement = to.subtract(from);
        AABB sweptBox = (fromCollisionBox == null ? fromBox : fromCollisionBox.bounds())
            .expandTowards(movement);
        if (!level.getWorldBorder().isWithinBounds(sweptBox)) {
            checks.sweptResults.put(key, false);
            return false;
        }
        Vec3 allowed = fromCollisionBox == null
            ? Entity.collideBoundingBox(entity, movement, fromBox, level, List.of())
            : fromCollisionBox.collide(entity, movement, level, List.of(), ignored -> false);
        boolean result = sameMovement(allowed, movement);
        checks.sweptResults.put(key, result);
        return result;
    }

    private static boolean isPositionClear(
        Level level,
        Entity entity,
        AABB originalBox,
        Vec3 originalPosition,
        Vec3 position,
        boolean allowFinalContact,
        PathCheckCache checks
    ) {
        PositionKey key = new PositionKey(position, allowFinalContact);
        Boolean cached = checks.positionResults.get(key);
        if (cached != null) return cached;
        AABB moved = movedBox(originalBox, originalPosition, position);
        PlasticEntityCollisionBox movedCollisionBox = movedPlasticCollisionBox(
            entity,
            originalPosition,
            position
        );
        AABB collisionBounds = movedCollisionBox == null ? moved : movedCollisionBox.bounds();
        if (!level.getWorldBorder().isWithinBounds(collisionBounds)) {
            checks.positionResults.put(key, false);
            return false;
        }
        boolean collisionFree = movedCollisionBox == null
            ? level.noBlockCollision(entity, moved)
                || allowFinalContact
                && level.noBlockCollision(entity, moved.deflate(0.002D))
            : noBlockCollision(level, entity, movedCollisionBox, 0.0D)
                || allowFinalContact
                && noBlockCollision(level, entity, movedCollisionBox, 0.002D);
        boolean result = collisionFree && !isDangerousPosition(level, collisionBounds, checks);
        checks.positionResults.put(key, result);
        return result;
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

    private static boolean isDangerousPosition(Level level, AABB box, PathCheckCache checks) {
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
                    if (isDangerousBlock(level, cursor, checks)) return true;
                }
            }
        }

        int floorY = Mth.floor(box.minY - 0.002D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                cursor.set(x, floorY, z);
                long key = cursor.asLong();
                Boolean cached = checks.floorDangerousBlocks.get(key);
                if (cached == null) {
                    BlockState state = level.getBlockState(cursor);
                    cached = state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state);
                    checks.floorDangerousBlocks.put(key, cached);
                }
                if (cached) return true;
            }
        }
        return false;
    }

    private static boolean isDangerousBlock(Level level, BlockPos pos, PathCheckCache checks) {
        long key = pos.asLong();
        Boolean cached = checks.dangerousBlocks.get(key);
        if (cached != null) return cached;
        boolean result = isDangerousState(level, pos, level.getBlockState(pos));
        checks.dangerousBlocks.put(key, result);
        return result;
    }

    private static boolean isDangerousState(BlockGetter level, BlockPos pos, BlockState state) {
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

    private static final class PathCheckCache {
        private final Map<SegmentKey, Boolean> segmentResults = new HashMap<>();
        private final Map<SweptSegmentKey, Boolean> sweptResults = new HashMap<>();
        private final Map<PositionKey, Boolean> positionResults = new HashMap<>();
        private final Map<Long, Boolean> dangerousBlocks = new HashMap<>();
        private final Map<Long, Boolean> floorDangerousBlocks = new HashMap<>();
    }

    private record SegmentKey(
        Vec3 from,
        Vec3 to,
        boolean allowFinalContact,
        long sampleStepBits
    ) {
    }

    private record SweptSegmentKey(Vec3 from, Vec3 to) {
    }

    private record PositionKey(Vec3 position, boolean allowFinalContact) {
    }

    private interface SearchCollision {
        boolean isSegmentClear(Vec3 from, Vec3 to, boolean allowFinalContact, double sampleStep);

        boolean isSweptSegmentClear(Vec3 from, Vec3 to);

        boolean isDangerousPosition(AABB bounds);

        AABB collisionBounds(Vec3 position);
    }

    private static final class LiveSearchCollision implements SearchCollision {
        private final Level level;
        private final Entity entity;
        private final AABB originalBox;
        private final Vec3 originalPosition;
        private final PathCheckCache checks;

        private LiveSearchCollision(
            Level level,
            Entity entity,
            AABB originalBox,
            Vec3 originalPosition,
            PathCheckCache checks
        ) {
            this.level = level;
            this.entity = entity;
            this.originalBox = originalBox;
            this.originalPosition = originalPosition;
            this.checks = checks;
        }

        @Override
        public boolean isSegmentClear(Vec3 from, Vec3 to, boolean allowFinalContact, double sampleStep) {
            return AdhesivePathPlanner.isSegmentClear(
                this.level,
                this.entity,
                this.originalBox,
                this.originalPosition,
                from,
                to,
                allowFinalContact,
                sampleStep,
                this.checks
            );
        }

        @Override
        public boolean isSweptSegmentClear(Vec3 from, Vec3 to) {
            return AdhesivePathPlanner.isSweptSegmentClear(
                this.level,
                this.entity,
                this.originalBox,
                this.originalPosition,
                from,
                to,
                this.checks
            );
        }

        @Override
        public boolean isDangerousPosition(AABB bounds) {
            return AdhesivePathPlanner.isDangerousPosition(this.level, bounds, this.checks);
        }

        @Override
        public AABB collisionBounds(Vec3 position) {
            return movedCollisionBounds(this.entity, this.originalBox, this.originalPosition, position);
        }
    }

    private static final class PathSearchCapture {
        private static final int NEIGHBOR_PADDING = 1;
        private final CapturedBlockGetter blocks;
        private final List<AABB> relativeComponents;
        private final AABB relativeBounds;
        private final double borderMinX;
        private final double borderMaxX;
        private final double borderMinZ;
        private final double borderMaxZ;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;
        private final int minZ;
        private final int maxZ;

        private PathSearchCapture(
            CapturedBlockGetter blocks,
            List<AABB> relativeComponents,
            AABB relativeBounds,
            double borderMinX,
            double borderMaxX,
            double borderMinZ,
            double borderMaxZ,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
        ) {
            this.blocks = blocks;
            this.relativeComponents = relativeComponents;
            this.relativeBounds = relativeBounds;
            this.borderMinX = borderMinX;
            this.borderMaxX = borderMaxX;
            this.borderMinZ = borderMinZ;
            this.borderMaxZ = borderMaxZ;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        private static @Nullable PathSearchCapture create(
            Level level,
            Entity entity,
            AABB originalBox,
            Vec3 start,
            Vec3 target
        ) {
            SearchGrid grid = PathSearchSnapshot.createSearchGrid(start, target);
            List<AABB> relativeComponents = new ArrayList<>();
            AABB entityBounds;
            if (entity instanceof ShapedCollisionEntity shaped) {
                PlasticEntityCollisionBox collisionBox = shaped.plasticraft$getCollisionBox();
                entityBounds = collisionBox.bounds();
                for (AABB component : collisionBox.components()) {
                    relativeComponents.add(component.move(-start.x, -start.y, -start.z));
                }
            } else {
                AABB collisionBox = originalBox.deflate(COLLISION_EPSILON);
                entityBounds = collisionBox;
                relativeComponents.add(collisionBox.move(-start.x, -start.y, -start.z));
            }
            if (relativeComponents.isEmpty()) return null;

            Vec3 gridOrigin = new Vec3(target.x, start.y, target.z);
            AABB searchBounds = new AABB(
                entityBounds.minX + gridOrigin.x + grid.minX - start.x,
                entityBounds.minY + gridOrigin.y + grid.minY - start.y,
                entityBounds.minZ + gridOrigin.z + grid.minZ - start.z,
                entityBounds.maxX + gridOrigin.x + grid.minX + grid.sizeX - 1 - start.x,
                entityBounds.maxY + gridOrigin.y + grid.minY + grid.sizeY - 1 - start.y,
                entityBounds.maxZ + gridOrigin.z + grid.minZ + grid.sizeZ - 1 - start.z
            );
            searchBounds = PathSearchSnapshot.union(searchBounds, entityBounds.move(target.subtract(start)));

            int minX = Mth.floor(searchBounds.minX);
            int maxX = Mth.floor(searchBounds.maxX);
            int minY = Math.max(level.getMinBuildHeight(), Mth.floor(searchBounds.minY) - 1);
            int maxY = Math.min(level.getMaxBuildHeight() - 1, Mth.floor(searchBounds.maxY));
            int minZ = Mth.floor(searchBounds.minZ);
            int maxZ = Mth.floor(searchBounds.maxZ);
            if (minX > maxX || minY > maxY || minZ > maxZ) return null;

            int captureMinX = minX - NEIGHBOR_PADDING;
            int captureMaxX = maxX + NEIGHBOR_PADDING;
            int captureMinY = Math.max(level.getMinBuildHeight(), minY - NEIGHBOR_PADDING);
            int captureMaxY = Math.min(level.getMaxBuildHeight() - 1, maxY + NEIGHBOR_PADDING);
            int captureMinZ = minZ - NEIGHBOR_PADDING;
            int captureMaxZ = maxZ + NEIGHBOR_PADDING;
            // 线程边界只传递不可变 BlockState 快照，碰撞形状和危险地形分析留在搜索线程执行。
            CapturedBlockGetter blocks = CapturedBlockGetter.capture(
                level,
                captureMinX,
                captureMaxX,
                captureMinY,
                captureMaxY,
                captureMinZ,
                captureMaxZ
            );
            if (blocks == null) return null;

            return new PathSearchCapture(
                blocks,
                List.copyOf(relativeComponents),
                entityBounds.move(-start.x, -start.y, -start.z),
                level.getWorldBorder().getMinX(),
                level.getWorldBorder().getMaxX(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxZ(),
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ
            );
        }

        private List<Vec3> findPath(Vec3 start, Vec3 target, int maxExpandedNodes) {
            PathSearchSnapshot snapshot = this.compile();
            return snapshot == null ? List.of() : snapshot.findPath(start, target, maxExpandedNodes);
        }

        private @Nullable PathSearchSnapshot compile() {
            Map<Long, List<AABB>> collisionBoxes = new HashMap<>();
            Set<Long> dangerousBlocks = new HashSet<>();
            Set<Long> floorDangerousBlocks = new HashSet<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            int processed = 0;
            for (int x = this.minX; x <= this.maxX; x++) {
                for (int z = this.minZ; z <= this.maxZ; z++) {
                    for (int y = this.minY; y <= this.maxY; y++) {
                        if ((processed++ & 255) == 0 && Thread.currentThread().isInterrupted()) return null;
                        cursor.set(x, y, z);
                        BlockState state = this.blocks.getBlockState(cursor);
                        if (state.isAir()) continue;
                        long blockKey = cursor.asLong();
                        List<AABB> boxes = new ArrayList<>();
                        for (AABB component : state.getCollisionShape(this.blocks, cursor).toAabbs()) {
                            boxes.add(component.move(x, y, z));
                        }
                        if (!boxes.isEmpty()) collisionBoxes.put(blockKey, List.copyOf(boxes));
                        if (isDangerousState(this.blocks, cursor, state)) dangerousBlocks.add(blockKey);
                        if (state.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(state)) {
                            floorDangerousBlocks.add(blockKey);
                        }
                    }
                }
            }
            return new PathSearchSnapshot(
                collisionBoxes,
                dangerousBlocks,
                floorDangerousBlocks,
                this.relativeComponents,
                this.relativeBounds,
                this.borderMinX,
                this.borderMaxX,
                this.borderMinZ,
                this.borderMaxZ
            );
        }

    }

    private static final class CapturedBlockGetter implements BlockGetter {
        private final int minX;
        private final int minY;
        private final int minZ;
        private final int sizeY;
        private final int sizeZ;
        private final int maxX;
        private final int maxY;
        private final int maxZ;
        private final int minBuildHeight;
        private final int height;
        private final BlockState[] states;

        private CapturedBlockGetter(
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            int minBuildHeight,
            int height,
            BlockState[] states
        ) {
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.sizeY = maxY - minY + 1;
            this.sizeZ = maxZ - minZ + 1;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
            this.minBuildHeight = minBuildHeight;
            this.height = height;
            this.states = states;
        }

        private static @Nullable CapturedBlockGetter capture(
            Level level,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ
        ) {
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            long stateCount = (long) sizeX * sizeY * sizeZ;
            if (stateCount <= 0L || stateCount > Integer.MAX_VALUE) return null;
            BlockState[] states = new BlockState[(int) stateCount];
            CapturedBlockGetter result = new CapturedBlockGetter(
                minX,
                maxX,
                minY,
                maxY,
                minZ,
                maxZ,
                level.getMinBuildHeight(),
                level.getHeight(),
                states
            );
            Map<Long, LevelChunk> chunks = new HashMap<>();
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    LevelChunk chunk = chunks.get(chunkKey);
                    if (chunk == null) {
                        cursor.set(x, minY, z);
                        if (!level.hasChunkAt(cursor)) return null;
                        chunk = level.getChunk(chunkX, chunkZ);
                        chunks.put(chunkKey, chunk);
                    }
                    int sectionIndex = Integer.MIN_VALUE;
                    LevelChunkSection section = null;
                    boolean sectionEmpty = false;
                    for (int y = minY; y <= maxY; y++) {
                        int nextSectionIndex = chunk.getSectionIndex(y);
                        if (nextSectionIndex != sectionIndex) {
                            sectionIndex = nextSectionIndex;
                            section = chunk.getSection(sectionIndex);
                            sectionEmpty = section.hasOnlyAir();
                        }
                        states[result.index(x, y, z)] = sectionEmpty
                            ? Blocks.AIR.defaultBlockState()
                            : section.getBlockState(x & 15, y & 15, z & 15);
                    }
                }
            }
            return result;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getX() < this.minX
                || pos.getX() > this.maxX
                || pos.getY() < this.minY
                || pos.getY() > this.maxY
                || pos.getZ() < this.minZ
                || pos.getZ() > this.maxZ) {
                return Blocks.AIR.defaultBlockState();
            }
            return this.states[this.index(pos.getX(), pos.getY(), pos.getZ())];
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getMinBuildHeight() {
            return this.minBuildHeight;
        }

        @Override
        public int getHeight() {
            return this.height;
        }

        private int index(int x, int y, int z) {
            return ((x - this.minX) * this.sizeZ + z - this.minZ) * this.sizeY + y - this.minY;
        }
    }

    private static final class PathSearchSnapshot {
        private final Map<Long, List<AABB>> collisionBoxes;
        private final Set<Long> dangerousBlocks;
        private final Set<Long> floorDangerousBlocks;
        private final List<AABB> relativeComponents;
        private final AABB relativeBounds;
        private final double borderMinX;
        private final double borderMaxX;
        private final double borderMinZ;
        private final double borderMaxZ;

        private PathSearchSnapshot(
            Map<Long, List<AABB>> collisionBoxes,
            Set<Long> dangerousBlocks,
            Set<Long> floorDangerousBlocks,
            List<AABB> relativeComponents,
            AABB relativeBounds,
            double borderMinX,
            double borderMaxX,
            double borderMinZ,
            double borderMaxZ
        ) {
            this.collisionBoxes = collisionBoxes;
            this.dangerousBlocks = dangerousBlocks;
            this.floorDangerousBlocks = floorDangerousBlocks;
            this.relativeComponents = relativeComponents;
            this.relativeBounds = relativeBounds;
            this.borderMinX = borderMinX;
            this.borderMaxX = borderMaxX;
            this.borderMinZ = borderMinZ;
            this.borderMaxZ = borderMaxZ;
        }

        private static SearchGrid createSearchGrid(Vec3 start, Vec3 target) {
            int startX = (int) Math.round(start.x - target.x);
            int startZ = (int) Math.round(start.z - target.z);
            int goalY = (int) Math.round(target.y - start.y);
            return new SearchGrid(startX, 0, startZ, 0, goalY, 0);
        }

        private static AABB union(AABB first, AABB second) {
            return new AABB(
                Math.min(first.minX, second.minX),
                Math.min(first.minY, second.minY),
                Math.min(first.minZ, second.minZ),
                Math.max(first.maxX, second.maxX),
                Math.max(first.maxY, second.maxY),
                Math.max(first.maxZ, second.maxZ)
            );
        }

        private List<Vec3> findPath(Vec3 start, Vec3 target, int maxExpandedNodes) {
            SnapshotSearchCollision collision = new SnapshotSearchCollision(this);
            IncrementalSearch search = new IncrementalSearch(
                collision,
                start,
                target,
                maxExpandedNodes
            );
            while (!search.isFinished()) {
                search.advance(Long.MAX_VALUE, Integer.MAX_VALUE, SEGMENT_SAMPLE_STEP);
            }
            List<Vec3> rawPath = search.path();
            if (rawPath.isEmpty() || !isPathClear(collision, rawPath)) return List.of();
            List<Vec3> simplified = simplify(collision, collapseCollinear(rawPath));
            List<Vec3> rounded = roundCorners(collision, simplified);
            return isPathClear(collision, rounded) ? rounded : List.of();
        }

        private AABB boundsAt(Vec3 position) {
            return this.relativeBounds.move(position);
        }

        private boolean withinBorder(AABB bounds) {
            return bounds.minX >= this.borderMinX
                && bounds.maxX <= this.borderMaxX
                && bounds.minZ >= this.borderMinZ
                && bounds.maxZ <= this.borderMaxZ;
        }
    }

    private static final class SnapshotSearchCollision implements SearchCollision {
        private static final double TANGENTIAL_INSET = 1.0E-6D;
        private final PathSearchSnapshot snapshot;
        private final PathCheckCache checks = new PathCheckCache();

        private SnapshotSearchCollision(PathSearchSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public boolean isSegmentClear(Vec3 from, Vec3 to, boolean allowFinalContact, double sampleStep) {
            SegmentKey key = new SegmentKey(
                from,
                to,
                allowFinalContact,
                Double.doubleToLongBits(sampleStep)
            );
            Boolean cached = this.checks.segmentResults.get(key);
            if (cached != null) return cached;
            if (!isSweptSegmentClear(from, to)) {
                this.checks.segmentResults.put(key, false);
                return false;
            }
            double distance = from.distanceTo(to);
            int steps = Math.max(1, (int) Math.ceil(distance / sampleStep));
            for (int step = 1; step <= steps; step++) {
                Vec3 position = from.lerp(to, step / (double) steps);
                if (!isPositionClear(position, allowFinalContact && step == steps)) {
                    this.checks.segmentResults.put(key, false);
                    return false;
                }
            }
            this.checks.segmentResults.put(key, true);
            return true;
        }

        @Override
        public boolean isSweptSegmentClear(Vec3 from, Vec3 to) {
            SweptSegmentKey key = new SweptSegmentKey(from, to);
            Boolean cached = this.checks.sweptResults.get(key);
            if (cached != null) return cached;
            Vec3 movement = to.subtract(from);
            AABB fromBounds = this.snapshot.boundsAt(from);
            AABB sweptBounds = fromBounds.expandTowards(movement);
            if (!this.snapshot.withinBorder(sweptBounds)) {
                this.checks.sweptResults.put(key, false);
                return false;
            }
            Vec3 allowed = collide(
                this.componentsAt(from),
                movement,
                this.colliders(sweptBounds)
            );
            boolean result = sameMovement(allowed, movement);
            this.checks.sweptResults.put(key, result);
            return result;
        }

        @Override
        public boolean isDangerousPosition(AABB bounds) {
            int minX = Mth.floor(bounds.minX + COLLISION_EPSILON);
            int minY = Mth.floor(bounds.minY + COLLISION_EPSILON);
            int minZ = Mth.floor(bounds.minZ + COLLISION_EPSILON);
            int maxX = Mth.floor(bounds.maxX - COLLISION_EPSILON);
            int maxY = Mth.floor(bounds.maxY - COLLISION_EPSILON);
            int maxZ = Mth.floor(bounds.maxZ - COLLISION_EPSILON);
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (this.snapshot.dangerousBlocks.contains(BlockPos.asLong(x, y, z))) return true;
                    }
                }
            }
            int floorY = Mth.floor(bounds.minY - 0.002D);
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (this.snapshot.floorDangerousBlocks.contains(BlockPos.asLong(x, floorY, z))) return true;
                }
            }
            return false;
        }

        @Override
        public AABB collisionBounds(Vec3 position) {
            return this.snapshot.boundsAt(position);
        }

        private boolean isPositionClear(Vec3 position, boolean allowFinalContact) {
            PositionKey key = new PositionKey(position, allowFinalContact);
            Boolean cached = this.checks.positionResults.get(key);
            if (cached != null) return cached;
            AABB bounds = this.snapshot.boundsAt(position);
            if (!this.snapshot.withinBorder(bounds)) {
                this.checks.positionResults.put(key, false);
                return false;
            }
            boolean collisionFree = !hasCollision(position, 0.0D)
                || allowFinalContact && !hasCollision(position, 0.002D);
            boolean result = collisionFree && !isDangerousPosition(bounds);
            this.checks.positionResults.put(key, result);
            return result;
        }

        private boolean hasCollision(Vec3 position, double deflation) {
            for (AABB component : this.snapshot.relativeComponents) {
                AABB query = component.move(position);
                if (deflation != 0.0D) query = query.deflate(deflation);
                for (AABB collider : this.colliders(query)) {
                    if (query.intersects(collider)) return true;
                }
            }
            return false;
        }

        private List<AABB> componentsAt(Vec3 position) {
            List<AABB> result = new ArrayList<>(this.snapshot.relativeComponents.size());
            for (AABB component : this.snapshot.relativeComponents) result.add(component.move(position));
            return result;
        }

        private List<AABB> colliders(AABB query) {
            int minX = Mth.floor(query.minX);
            int minY = Mth.floor(query.minY);
            int minZ = Mth.floor(query.minZ);
            int maxX = Mth.floor(query.maxX);
            int maxY = Mth.floor(query.maxY);
            int maxZ = Mth.floor(query.maxZ);
            List<AABB> result = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        List<AABB> boxes = this.snapshot.collisionBoxes.get(BlockPos.asLong(x, y, z));
                        if (boxes != null) result.addAll(boxes);
                    }
                }
            }
            return result;
        }

        private static Vec3 collide(List<AABB> components, Vec3 movement, List<AABB> colliders) {
            if (movement.lengthSqr() == 0.0D || colliders.isEmpty()) return movement;
            List<VoxelShape> collisionShapes = new ArrayList<>(colliders.size());
            for (AABB collider : colliders) collisionShapes.add(Shapes.create(collider));

            List<AABB> movedComponents = components;
            double x = movement.x;
            double y = collideAxis(Direction.Axis.Y, movedComponents, collisionShapes, movement.y);
            if (y != 0.0D) movedComponents = move(movedComponents, 0.0D, y, 0.0D);

            boolean zFirst = Math.abs(x) < Math.abs(movement.z);
            double z = movement.z;
            if (zFirst && z != 0.0D) {
                z = collideAxis(Direction.Axis.Z, movedComponents, collisionShapes, z);
                if (z != 0.0D) movedComponents = move(movedComponents, 0.0D, 0.0D, z);
            }
            if (x != 0.0D) {
                x = collideAxis(Direction.Axis.X, movedComponents, collisionShapes, x);
                if (!zFirst && x != 0.0D) movedComponents = move(movedComponents, x, 0.0D, 0.0D);
            }
            if (!zFirst && z != 0.0D) {
                z = collideAxis(Direction.Axis.Z, movedComponents, collisionShapes, z);
            }
            return new Vec3(x, y, z);
        }

        private static double collideAxis(
            Direction.Axis axis,
            List<AABB> components,
            List<VoxelShape> colliders,
            double movement
        ) {
            double allowed = movement;
            for (AABB component : components) {
                allowed = Shapes.collide(axis, insetTangentially(component, axis), colliders, allowed);
                if (Math.abs(allowed) < 1.0E-7D) return 0.0D;
            }
            return allowed;
        }

        private static AABB insetTangentially(AABB box, Direction.Axis movementAxis) {
            return switch (movementAxis) {
                case X -> new AABB(
                    box.minX,
                    box.minY + TANGENTIAL_INSET,
                    box.minZ + TANGENTIAL_INSET,
                    box.maxX,
                    box.maxY - TANGENTIAL_INSET,
                    box.maxZ - TANGENTIAL_INSET
                );
                case Y -> new AABB(
                    box.minX + TANGENTIAL_INSET,
                    box.minY,
                    box.minZ + TANGENTIAL_INSET,
                    box.maxX - TANGENTIAL_INSET,
                    box.maxY,
                    box.maxZ - TANGENTIAL_INSET
                );
                case Z -> new AABB(
                    box.minX + TANGENTIAL_INSET,
                    box.minY + TANGENTIAL_INSET,
                    box.minZ,
                    box.maxX - TANGENTIAL_INSET,
                    box.maxY - TANGENTIAL_INSET,
                    box.maxZ
                );
            };
        }

        private static List<AABB> move(List<AABB> components, double x, double y, double z) {
            List<AABB> moved = new ArrayList<>(components.size());
            for (AABB component : components) moved.add(component.move(x, y, z));
            return moved;
        }
    }

    private static final class IncrementalSearch {
        private final SearchCollision collision;
        private final Vec3 start;
        private final Vec3 target;
        private final Vec3 gridOrigin;
        private final int startX;
        private final int startY;
        private final int startZ;
        private final int goalX;
        private final int goalY;
        private final int goalZ;
        private final int maxRouteCost;
        private final int maxExpandedNodes;
        private final SearchGrid grid;
        private final int[] costs;
        private final byte[] parents;
        private final boolean[] closed;
        private final boolean[] dangerousKnown;
        private final boolean[] dangerous;
        private final NodeHeap open;
        private int seedX;
        private int seedY;
        private int seedZ;
        private int reached = -1;
        private int expanded;
        private boolean seeding = true;
        private boolean finished;

        private IncrementalSearch(
            SearchCollision collision,
            Vec3 start,
            Vec3 target,
            int maxExpandedNodes
        ) {
            this.collision = collision;
            this.start = start;
            this.target = target;
            this.gridOrigin = new Vec3(target.x, start.y, target.z);
            double startOffsetX = start.x - target.x;
            double startOffsetY = start.y - target.y;
            double startOffsetZ = start.z - target.z;
            this.startX = (int) Math.round(startOffsetX);
            this.startY = 0;
            this.startZ = (int) Math.round(startOffsetZ);
            this.goalX = 0;
            this.goalY = (int) Math.round(target.y - start.y);
            this.goalZ = 0;
            int directManhattan = (int) Math.ceil(
                Math.abs(startOffsetX) + Math.abs(startOffsetY) + Math.abs(startOffsetZ) - DISTANCE_EPSILON
            );
            this.maxRouteCost = directManhattan + DETOUR_COST_BUDGET;
            this.maxExpandedNodes = maxExpandedNodes;
            this.grid = new SearchGrid(this.startX, this.startY, this.startZ, this.goalX, this.goalY, this.goalZ);
            this.costs = new int[this.grid.nodeCount()];
            this.parents = new byte[this.grid.nodeCount()];
            this.closed = new boolean[this.grid.nodeCount()];
            this.dangerousKnown = new boolean[this.grid.nodeCount()];
            this.dangerous = new boolean[this.grid.nodeCount()];
            Arrays.fill(this.costs, Integer.MAX_VALUE);
            Arrays.fill(this.parents, (byte) -1);
            this.open = new NodeHeap(this.grid.nodeCount());
            this.seedX = this.startX - 1;
            this.seedY = this.startY - 1;
            this.seedZ = this.startZ - 1;
        }

        private boolean isFinished() {
            return this.finished;
        }

        private void advance(
            long deadline,
            int nodeBudget,
            double segmentSampleStep
        ) {
            int expandedThisStep = 0;
            while (!this.finished) {
                if (Thread.currentThread().isInterrupted()) {
                    this.finished = true;
                    return;
                }
                if (deadline != Long.MAX_VALUE && System.nanoTime() >= deadline) return;
                if (this.seeding) {
                    this.seedNext(segmentSampleStep);
                    continue;
                }
                if (this.open.isEmpty() || this.expanded >= this.maxExpandedNodes) {
                    this.finished = true;
                    return;
                }
                if (expandedThisStep >= nodeBudget) return;

                int current = this.open.removeFirst();
                if (this.closed[current]) continue;
                this.closed[current] = true;
                this.expanded++;
                expandedThisStep++;
                int currentX = this.grid.x(current);
                int currentY = this.grid.y(current);
                int currentZ = this.grid.z(current);
                Vec3 currentPosition = this.gridOrigin.add(currentX, currentY, currentZ);
                if (currentPosition.distanceToSqr(this.target) <= GOAL_CONNECTION_DISTANCE_SQR
                    && this.collision.isSegmentClear(
                        currentPosition,
                        this.target,
                        true,
                        segmentSampleStep
                    )) {
                    this.reached = current;
                    this.finished = true;
                    return;
                }

                for (Direction direction : SEARCH_DIRECTIONS) {
                    int nextX = currentX + direction.getStepX();
                    int nextY = currentY + direction.getStepY();
                    int nextZ = currentZ + direction.getStepZ();
                    if (!this.grid.contains(nextX, nextY, nextZ)) continue;
                    int next = this.grid.index(nextX, nextY, nextZ);
                    if (this.closed[next]) continue;

                    Vec3 nextPosition = this.gridOrigin.add(nextX, nextY, nextZ);
                    int stepCost = gridStepCost(
                        this.collision,
                        currentPosition,
                        nextPosition,
                        next,
                        this.dangerousKnown,
                        this.dangerous
                    );
                    if (stepCost == Integer.MAX_VALUE) continue;
                    int nextCost = this.costs[current] + stepCost;
                    int remainingLowerBound = remainingLowerBound(nextX, nextY, nextZ);
                    if (nextCost + remainingLowerBound > this.maxRouteCost
                        || nextCost >= this.costs[next]) {
                        continue;
                    }

                    this.costs[next] = nextCost;
                    this.parents[next] = (byte) direction.getOpposite().get3DDataValue();
                    this.open.addOrDecrease(next, nextCost + remainingLowerBound);
                }
            }
        }

        private void seedNext(double segmentSampleStep) {
            if (this.seedX > this.startX + 1) {
                this.seeding = false;
                return;
            }
            int x = this.seedX;
            int y = this.seedY;
            int z = this.seedZ;
            this.seedZ++;
            if (this.seedZ > this.startZ + 1) {
                this.seedZ = this.startZ - 1;
                this.seedY++;
            }
            if (this.seedY > this.startY + 1) {
                this.seedY = this.startY - 1;
                this.seedX++;
            }

            if (!this.grid.contains(x, y, z)) return;
            Vec3 seedPosition = this.gridOrigin.add(x, y, z);
            double connectionDistanceSqr = this.start.distanceToSqr(seedPosition);
            if (connectionDistanceSqr > GOAL_CONNECTION_DISTANCE_SQR
                || !this.collision.isSegmentClear(
                    this.start,
                    seedPosition,
                    seedPosition.distanceToSqr(this.target) < 1.0E-8D,
                    segmentSampleStep
                )) {
                return;
            }
            int seed = this.grid.index(x, y, z);
            int seedCost = (int) Math.ceil(Math.sqrt(connectionDistanceSqr) - DISTANCE_EPSILON);
            if (seedCost >= this.costs[seed]) return;
            this.costs[seed] = seedCost;
            this.open.addOrDecrease(seed, seedCost + remainingLowerBound(x, y, z));
        }

        private int remainingLowerBound(int x, int y, int z) {
            return Math.max(
                0,
                Math.abs(this.goalX - x)
                    + Math.abs(this.goalY - y)
                    + Math.abs(this.goalZ - z)
                    - GOAL_CONNECTION_MANHATTAN_ALLOWANCE
            );
        }

        private List<Vec3> path() {
            if (this.reached < 0) return List.of();
            List<Vec3> reversed = new ArrayList<>();
            int cursor = this.reached;
            while (true) {
                reversed.add(this.gridOrigin.add(this.grid.x(cursor), this.grid.y(cursor), this.grid.z(cursor)));
                if (this.parents[cursor] < 0) break;
                Direction parentDirection = Direction.from3DDataValue(Byte.toUnsignedInt(this.parents[cursor]));
                cursor = this.grid.index(
                    this.grid.x(cursor) + parentDirection.getStepX(),
                    this.grid.y(cursor) + parentDirection.getStepY(),
                    this.grid.z(cursor) + parentDirection.getStepZ()
                );
            }
            Collections.reverse(reversed);
            if (reversed.getFirst().distanceToSqr(this.start) > DISTANCE_EPSILON) {
                reversed.addFirst(this.start);
            }
            if (!reversed.getLast().equals(this.target)) reversed.add(this.target);
            return reversed;
        }
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
