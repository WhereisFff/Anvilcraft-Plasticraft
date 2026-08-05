package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** 用连续分离轴检测裁剪凸体与方块、普通实体及其他凸体之间的轴向位移。 */
public final class PlasticConvexCollisionResolver {
    private static final double AXIS_EPSILON = 1.0E-10D;
    private static final double CONTACT_EPSILON = 1.0E-7D;
    private static final double TIME_EPSILON = 1.0E-9D;
    private static final int SORTED_INDEX_MINIMUM_SIZE = 8;

    private PlasticConvexCollisionResolver() {
    }

    public static Vec3 collide(
        Entity entity,
        Vec3 requestedMovement,
        AABB broadBounds,
        List<PlasticConvexShape> movingShapes,
        Level level,
        List<VoxelShape> entityCollisions
    ) {
        return collide(
            entity,
            requestedMovement,
            broadBounds,
            movingShapes,
            level,
            entityCollisions,
            ignored -> true
        );
    }

    public static Vec3 collide(
        Entity entity,
        Vec3 requestedMovement,
        AABB broadBounds,
        List<PlasticConvexShape> movingShapes,
        Level level,
        List<VoxelShape> entityCollisions,
        Predicate<Entity> exactObstacleFilter
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        Objects.requireNonNull(broadBounds, "broadBounds");
        Objects.requireNonNull(movingShapes, "movingShapes");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityCollisions, "entityCollisions");
        Objects.requireNonNull(exactObstacleFilter, "exactObstacleFilter");
        if (requestedMovement.lengthSqr() == 0.0D || movingShapes.isEmpty()) return requestedMovement;

        AABB sweptBounds = broadBounds.expandTowards(requestedMovement);
        List<BondedPlasticShapeIndex.Entry> bondedObstacles = bondedObstacles(level, sweptBounds);
        List<PlasticConvexShape> obstacles = new ArrayList<>();
        if (level.getWorldBorder().isInsideCloseToBorder(entity, sweptBounds)) {
            addVoxelObstacle(obstacles, level.getWorldBorder().getCollisionShape());
        }
        for (VoxelShape blockCollision : level.getBlockCollisions(entity, sweptBounds)) {
            if (isBondedCompatibilityShape(blockCollision, bondedObstacles)) continue;
            addVoxelObstacle(obstacles, blockCollision);
        }
        addBondedObstacles(obstacles, bondedObstacles, sweptBounds);
        addEntityObstacles(
            obstacles,
            entity,
            requestedMovement,
            sweptBounds,
            level,
            exactObstacleFilter
        );
        if (obstacles.isEmpty()) return requestedMovement;
        ObstacleIndex obstacleIndex = new ObstacleIndex(obstacles);

        List<PlasticConvexShape> movedShapes = movingShapes;
        double x = requestedMovement.x;
        double y = collideAxisInternal(
            Direction.Axis.Y,
            movedShapes,
            obstacleIndex,
            requestedMovement.y,
            null
        );
        double z = requestedMovement.z;
        if (y != 0.0D) movedShapes = move(movedShapes, new Vec3(0.0D, y, 0.0D));

        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = collideAxisInternal(Direction.Axis.Z, movedShapes, obstacleIndex, z, null);
            if (z != 0.0D) movedShapes = move(movedShapes, new Vec3(0.0D, 0.0D, z));
        }
        if (x != 0.0D) {
            x = collideAxisInternal(Direction.Axis.X, movedShapes, obstacleIndex, x, null);
            if (!zFirst && x != 0.0D) movedShapes = move(movedShapes, new Vec3(x, 0.0D, 0.0D));
        }
        if (!zFirst && z != 0.0D) {
            z = collideAxisInternal(Direction.Axis.Z, movedShapes, obstacleIndex, z, null);
        }
        return new Vec3(x, y, z);
    }

    private static void addVoxelObstacle(List<PlasticConvexShape> output, VoxelShape shape) {
        if (shape.isEmpty()) return;
        for (AABB box : shape.toAabbs()) output.add(PlasticConvexShape.box(box));
    }

    private static List<BondedPlasticShapeIndex.Entry> bondedObstacles(Level level, AABB sweptBounds) {
        List<BondedPlasticShapeIndex.Entry> entries = BondedPlasticShapeIndex.collisionEntries(
            level,
            sweptBounds.inflate(CONTACT_EPSILON)
        );
        if (entries.isEmpty()) return List.of();
        List<BondedPlasticShapeIndex.Entry> current = new ArrayList<>(entries.size());
        for (BondedPlasticShapeIndex.Entry entry : entries) {
            if (!entry.convexShapes().isEmpty()
                && BondedPlasticShapeIndex.isCurrent(level, entry.anchor())) {
                current.add(entry);
            }
        }
        return current;
    }

    private static boolean isBondedCompatibilityShape(
        VoxelShape blockCollision,
        List<BondedPlasticShapeIndex.Entry> bondedObstacles
    ) {
        if (blockCollision.isEmpty()) return false;
        AABB blockBounds = blockCollision.bounds();
        for (BondedPlasticShapeIndex.Entry entry : bondedObstacles) {
            VoxelShape compatibility = entry.collisionShape();
            if (compatibility.isEmpty()
                || !sameBounds(blockBounds, compatibility.bounds())
                || Shapes.joinIsNotEmpty(blockCollision, compatibility, BooleanOp.NOT_SAME)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean sameBounds(AABB first, AABB second) {
        return Math.abs(first.minX - second.minX) <= CONTACT_EPSILON
            && Math.abs(first.minY - second.minY) <= CONTACT_EPSILON
            && Math.abs(first.minZ - second.minZ) <= CONTACT_EPSILON
            && Math.abs(first.maxX - second.maxX) <= CONTACT_EPSILON
            && Math.abs(first.maxY - second.maxY) <= CONTACT_EPSILON
            && Math.abs(first.maxZ - second.maxZ) <= CONTACT_EPSILON;
    }

    private static void addBondedObstacles(
        List<PlasticConvexShape> output,
        List<BondedPlasticShapeIndex.Entry> bondedObstacles,
        AABB sweptBounds
    ) {
        AABB queryBounds = sweptBounds.inflate(CONTACT_EPSILON);
        for (BondedPlasticShapeIndex.Entry entry : bondedObstacles) {
            for (PlasticConvexShape shape : entry.convexShapes()) {
                if (shape.bounds().intersects(queryBounds)) output.add(shape);
            }
        }
    }

    private static void addEntityObstacles(
        List<PlasticConvexShape> output,
        Entity mover,
        Vec3 requestedMovement,
        AABB sweptBounds,
        Level level,
        Predicate<Entity> exactObstacleFilter
    ) {
        for (Entity target : level.getEntities(
            mover,
            sweptBounds.inflate(CONTACT_EPSILON),
            candidate -> exactObstacleFilter.test(candidate)
                && usesEntityCollision(mover, candidate)
        )) {
            if (!keepsCollisionDuringCarrierMove(mover, target)) continue;
            if (target instanceof ShapedCollisionEntity shaped) {
                PlasticEntityCollisionBox collisionBox = shaped.plasticraft$getCollisionBox(mover, requestedMovement);
                if (collisionBox.hasConvexComponents()) {
                    output.addAll(collisionBox.convexComponents());
                } else {
                    for (AABB component : collisionBox.components()) {
                        output.add(PlasticConvexShape.box(component));
                    }
                }
            } else {
                output.add(PlasticConvexShape.box(target.getBoundingBox()));
            }
        }
    }

    public static boolean hasExactEntityObstacle(
        Entity mover,
        Vec3 requestedMovement,
        AABB sweptBounds,
        Level level
    ) {
        return hasExactEntityObstacle(mover, requestedMovement, sweptBounds, level, ignored -> true);
    }

    public static boolean hasExactEntityObstacle(
        Entity mover,
        Vec3 requestedMovement,
        AABB sweptBounds,
        Level level,
        Predicate<Entity> exactObstacleFilter
    ) {
        Objects.requireNonNull(exactObstacleFilter, "exactObstacleFilter");
        if (!bondedObstacles(level, sweptBounds).isEmpty()) return true;
        return !level.getEntities(
            mover,
            sweptBounds.inflate(CONTACT_EPSILON),
            candidate -> exactObstacleFilter.test(candidate)
                && usesExactCollision(mover, candidate, requestedMovement)
                && keepsCollisionDuringCarrierMove(mover, candidate)
        ).isEmpty();
    }

    /**
     * 玩家边缘防滑等只接受布尔结果的查询也必须排除兼容轮廓，
     * 否则动态斜面与方块化斜面会重新退化成外接盒。
     */
    public static boolean noCollisionWithExactPlastic(
        Entity entity,
        AABB collisionBox,
        Level level
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(collisionBox, "collisionBox");
        Objects.requireNonNull(level, "level");
        if (!hasExactEntityObstacle(entity, Vec3.ZERO, collisionBox, level)) {
            return level.noCollision(entity, collisionBox);
        }
        return !hasCollisionAt(entity, collisionBox, level, Vec3.ZERO);
    }

    /**
     * 等价于服务端玩家移动校验中的“目标位置是否新增碰撞”，但塑料障碍使用真实凸体。
     * 调用方应沿用原版相同的包围盒收缩量。
     */
    public static boolean isEntityCollidingWithAnythingNew(
        Entity entity,
        AABB previousBox,
        AABB targetBox,
        Level level
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(previousBox, "previousBox");
        Objects.requireNonNull(targetBox, "targetBox");
        Objects.requireNonNull(level, "level");
        Vec3 movement = targetBox.getCenter().subtract(previousBox.getCenter());
        List<BondedPlasticShapeIndex.Entry> bondedObstacles = bondedObstacles(level, targetBox);
        VoxelShape previousShape = Shapes.create(previousBox);
        VoxelShape targetShape = Shapes.create(targetBox);

        for (VoxelShape blockCollision : level.getBlockCollisions(entity, targetBox)) {
            if (blockCollision.isEmpty()
                || isBondedCompatibilityShape(blockCollision, bondedObstacles)) {
                continue;
            }
            if (isNewVoxelCollision(blockCollision, previousShape, targetShape)) return true;
        }
        for (BondedPlasticShapeIndex.Entry entry : bondedObstacles) {
            if (isNewConvexCollision(entry.convexShapes(), previousBox, targetBox)) return true;
        }
        for (Entity obstacle : level.getEntities(
            entity,
            targetBox.inflate(CONTACT_EPSILON),
            candidate -> usesEntityCollision(entity, candidate)
        )) {
            List<PlasticConvexShape> obstacleShapes = collisionShapes(
                obstacle,
                obstacle.getBoundingBox(),
                entity,
                movement
            );
            if (isNewConvexCollision(obstacleShapes, previousBox, targetBox)) return true;
        }
        if (level.getWorldBorder().isInsideCloseToBorder(entity, targetBox)) {
            return isNewVoxelCollision(
                level.getWorldBorder().getCollisionShape(),
                previousShape,
                targetShape
            );
        }
        return false;
    }

    public static boolean hasNonAxisAlignedExactObstacle(
        Entity mover,
        Vec3 requestedMovement,
        AABB sweptBounds,
        Level level
    ) {
        for (BondedPlasticShapeIndex.Entry entry : bondedObstacles(level, sweptBounds)) {
            if (entry.convexShapes().stream().anyMatch(shape -> !shape.isAxisAlignedBox())) return true;
        }
        return !level.getEntities(
            mover,
            sweptBounds.inflate(CONTACT_EPSILON),
            candidate -> usesExactCollision(mover, candidate, requestedMovement)
                && keepsCollisionDuringCarrierMove(mover, candidate)
                && ((ShapedCollisionEntity) candidate).plasticraft$getCollisionBox(mover, requestedMovement)
                    .convexComponents()
                    .stream()
                    .anyMatch(shape -> !shape.isAxisAlignedBox())
        ).isEmpty();
    }

    /** 计算单个凸体沿一个世界坐标轴接近另一凸体时可完成的连续位移。 */
    public static double collideAxis(
        PlasticConvexShape moving,
        PlasticConvexShape obstacle,
        Direction.Axis movementAxis,
        double requestedMovement
    ) {
        return collidePair(moving, obstacle, movementAxis, requestedMovement);
    }

    /** 把实体在指定参考包围盒位置上的真实碰撞转换为连续凸体。 */
    public static List<PlasticConvexShape> collisionShapes(Entity entity, AABB referenceBox) {
        return collisionShapes(entity, referenceBox, null, Vec3.ZERO);
    }

    /** 把实体对指定移动者生效的碰撞转换到给定参考包围盒位置。 */
    public static List<PlasticConvexShape> collisionShapes(
        Entity entity,
        AABB referenceBox,
        Entity mover,
        Vec3 requestedMovement
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(referenceBox, "referenceBox");
        if (!(entity instanceof ShapedCollisionEntity shaped)) {
            return List.of(PlasticConvexShape.box(referenceBox));
        }
        Vec3 movement = referenceBox.getCenter().subtract(entity.getBoundingBox().getCenter());
        PlasticEntityCollisionBox collisionBox = (mover == null
            ? shaped.plasticraft$getCollisionBox()
            : shaped.plasticraft$getCollisionBox(mover, requestedMovement)).move(movement);
        if (collisionBox.hasConvexComponents()) return collisionBox.convexComponents();
        return collisionBox.components().stream().map(PlasticConvexShape::box).toList();
    }

    public static boolean hasConvexCollision(Entity entity) {
        return entity instanceof ShapedCollisionEntity shaped
            && shaped.plasticraft$getCollisionBox().hasConvexComponents();
    }

    /** 返回一组凸体沿世界坐标轴移动时可完成的最大位移。 */
    public static double collideAxis(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles,
        Direction.Axis movementAxis,
        double requestedMovement
    ) {
        Objects.requireNonNull(movingShapes, "movingShapes");
        Objects.requireNonNull(obstacles, "obstacles");
        Objects.requireNonNull(movementAxis, "movementAxis");
        return collideAxisInternal(
            movementAxis,
            movingShapes,
            new ObstacleIndex(obstacles),
            requestedMovement,
            null
        );
    }

    /** 返回整段位移中最先发生的凸体接触；法线从障碍指向移动体。 */
    public static SweepContact sweep(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles,
        Vec3 movement
    ) {
        Objects.requireNonNull(movingShapes, "movingShapes");
        Objects.requireNonNull(obstacles, "obstacles");
        Objects.requireNonNull(movement, "movement");
        if (movement.lengthSqr() <= AXIS_EPSILON) return null;
        SweepContact best = null;
        for (PlasticConvexShape moving : movingShapes) {
            AABB sweptBounds = moving.bounds().expandTowards(movement).inflate(CONTACT_EPSILON);
            for (PlasticConvexShape obstacle : obstacles) {
                if (!sweptBounds.intersects(obstacle.bounds().inflate(CONTACT_EPSILON))) continue;
                SweepContact candidate = sweepPair(moving, obstacle, movement);
                if (candidate != null && isPreferredContact(candidate, best, movement)) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    /** 使用真实凸体检查实体在指定重力方向上是否受到方块支撑。 */
    public static boolean hasBlockSupport(
        Entity entity,
        AABB referenceBox,
        Direction gravityDirection,
        double probeDepth
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(referenceBox, "referenceBox");
        Objects.requireNonNull(gravityDirection, "gravityDirection");
        if (!Double.isFinite(probeDepth) || probeDepth <= 0.0D) return false;
        List<PlasticConvexShape> movingShapes = collisionShapes(entity, referenceBox);
        if (movingShapes.isEmpty()) return false;
        Vec3 movement = axisVector(
            gravityDirection.getAxis(),
            gravityDirection.getAxisDirection().getStep() * probeDepth
        );
        AABB sweptBounds = enclosingBounds(movingShapes).expandTowards(movement);
        List<PlasticConvexShape> obstacles = blockObstacles(entity, entity.level(), sweptBounds);
        return intersects(movingShapes, obstacles) || sweep(movingShapes, obstacles, movement) != null;
    }

    /**
     * 用真实斜面执行一次抬升、水平移动和回落，避免原版从兼容外接盒提取错误的跨步高度。
     */
    public static Vec3 resolveStepMovement(
        Entity entity,
        Vec3 requestedMovement,
        AABB collisionBox,
        Vec3 noStepMovement
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        Objects.requireNonNull(collisionBox, "collisionBox");
        Objects.requireNonNull(noStepMovement, "noStepMovement");
        boolean landedDuringMove = requestedMovement.y < 0.0D
            && Math.abs(requestedMovement.y - noStepMovement.y) > CONTACT_EPSILON;
        noStepMovement = resolveDescendingSurfaceMovement(
            entity,
            requestedMovement,
            collisionBox,
            noStepMovement,
            entity.onGround() || landedDuringMove
        );
        double maximumStep = entity.maxUpStep();
        boolean horizontalCollision = Math.abs(requestedMovement.x - noStepMovement.x) > CONTACT_EPSILON
            || Math.abs(requestedMovement.z - noStepMovement.z) > CONTACT_EPSILON;
        if (maximumStep <= CONTACT_EPSILON
            || !horizontalCollision
            || !(entity.onGround() || landedDuringMove)) {
            return noStepMovement;
        }

        double baseY = landedDuringMove ? noStepMovement.y : 0.0D;
        AABB baseBox = collisionBox.move(0.0D, baseY, 0.0D);
        Vec3 upward = collideAt(entity, new Vec3(0.0D, maximumStep, 0.0D), baseBox);
        if (upward.y <= CONTACT_EPSILON) return noStepMovement;

        AABB raisedBox = baseBox.move(upward);
        Vec3 horizontal = collideAt(
            entity,
            new Vec3(requestedMovement.x, 0.0D, requestedMovement.z),
            raisedBox
        );
        if (horizontal.horizontalDistanceSqr()
            <= noStepMovement.horizontalDistanceSqr() + CONTACT_EPSILON * CONTACT_EPSILON) {
            return noStepMovement;
        }

        AABB advancedBox = raisedBox.move(horizontal);
        double downwardRequest = requestedMovement.y - baseY - upward.y;
        if (downwardRequest >= -CONTACT_EPSILON) {
            downwardRequest = -upward.y - CONTACT_EPSILON;
        }
        Vec3 downward = collideAt(
            entity,
            new Vec3(0.0D, downwardRequest, 0.0D),
            advancedBox
        );
        return new Vec3(
            horizontal.x,
            baseY + upward.y + downward.y,
            horizontal.z
        );
    }

    /** 已着地实体下坡时需要在水平位移后回落，否则 Y 优先轴序会让它悬在上一脚点的高度。 */
    private static Vec3 resolveDescendingSurfaceMovement(
        Entity entity,
        Vec3 requestedMovement,
        AABB collisionBox,
        Vec3 yFirstMovement,
        boolean canFollowSurface
    ) {
        if (!canFollowSurface
            || requestedMovement.y >= -CONTACT_EPSILON
            || yFirstMovement.horizontalDistanceSqr() <= CONTACT_EPSILON * CONTACT_EPSILON) {
            return yFirstMovement;
        }
        Vec3 horizontal = new Vec3(
            yFirstMovement.x,
            0.0D,
            yFirstMovement.z
        );
        AABB advancedBox = collisionBox.move(horizontal);
        double downwardRequest = requestedMovement.y - CONTACT_EPSILON * 32.0D;
        Vec3 downward = collideAt(
            entity,
            new Vec3(0.0D, downwardRequest, 0.0D),
            advancedBox
        );
        if (downward.y >= yFirstMovement.y - CONTACT_EPSILON) return yFirstMovement;
        if (downward.y <= downwardRequest + CONTACT_EPSILON * 0.5D) {
            return yFirstMovement;
        }
        return new Vec3(horizontal.x, downward.y, horizontal.z);
    }

    public static AxisCollisionResult collideAxisWithStats(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles,
        Direction.Axis movementAxis,
        double requestedMovement
    ) {
        Objects.requireNonNull(movingShapes, "movingShapes");
        Objects.requireNonNull(obstacles, "obstacles");
        Objects.requireNonNull(movementAxis, "movementAxis");
        MutableCollisionStats stats = new MutableCollisionStats();
        double movement = collideAxisInternal(
            movementAxis,
            movingShapes,
            new ObstacleIndex(obstacles),
            requestedMovement,
            stats
        );
        return new AxisCollisionResult(movement, stats.snapshot());
    }

    /** 检查两个凸体是否存在具有正体积的交叠；仅接触共面不算交叠。 */
    public static boolean intersects(PlasticConvexShape first, PlasticConvexShape second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.bounds().inflate(CONTACT_EPSILON).intersects(second.bounds().inflate(CONTACT_EPSILON))) {
            return false;
        }
        for (Vec3 axis : separatingAxes(first, second)) {
            PlasticConvexShape.Projection firstProjection = first.project(axis);
            PlasticConvexShape.Projection secondProjection = second.project(axis);
            double overlap = Math.min(firstProjection.maximum(), secondProjection.maximum())
                - Math.max(firstProjection.minimum(), secondProjection.minimum());
            if (overlap <= CONTACT_EPSILON) return false;
        }
        return true;
    }

    public static boolean intersects(
        PlasticConvexShape moving,
        List<PlasticConvexShape> obstacles
    ) {
        for (PlasticConvexShape obstacle : obstacles) {
            if (intersects(moving, obstacle)) return true;
        }
        return false;
    }

    public static boolean intersects(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles
    ) {
        for (PlasticConvexShape moving : movingShapes) {
            if (intersects(moving, obstacles)) return true;
        }
        return false;
    }

    /** 用短轴向探测判断是否会在给定距离内接触真实凸体。 */
    public static boolean blocksAxisMovement(
        List<PlasticConvexShape> movingShapes,
        List<PlasticConvexShape> obstacles,
        Direction.Axis movementAxis,
        double requestedMovement
    ) {
        if (Math.abs(requestedMovement) <= AXIS_EPSILON) return false;
        double allowed = collideAxis(movingShapes, obstacles, movementAxis, requestedMovement);
        return Math.abs(allowed - requestedMovement) > CONTACT_EPSILON * 0.5D;
    }

    private static boolean usesExactCollision(Entity mover, Entity target) {
        return usesExactCollision(mover, target, Vec3.ZERO);
    }

    private static boolean usesExactCollision(Entity mover, Entity target, Vec3 requestedMovement) {
        return target instanceof ShapedCollisionEntity shaped
            && shaped.plasticraft$getCollisionBox(mover, requestedMovement).hasConvexComponents()
            && usesEntityCollision(mover, target);
    }

    private static boolean usesEntityCollision(Entity mover, Entity target) {
        return !target.isRemoved()
            && !target.isSpectator()
            && !mover.isPassengerOfSameVehicle(target)
            && !EntityBondManager.areInSameComponent(mover, target)
            && !EntityBondManager.ignoresPreclippedCollision(mover, target)
            && mover.canCollideWith(target);
    }

    private static boolean keepsCollisionDuringCarrierMove(Entity mover, Entity target) {
        if (!(mover instanceof CarrierMoveContextHolder holder)
            || !(target instanceof CarrierMovableEntity movable)) {
            return true;
        }
        CarrierMoveContext context = holder.plasticraft$getCarrierMoveContext();
        if (context == null) return true;
        if (context.collidesDuringRetry(movable)) return true;
        return !context.movesWithRecordedTarget(movable);
    }

    private static Vec3 collideAt(Entity entity, Vec3 movement, AABB referenceBox) {
        return ShapedCollisionEntity.collideBoundingBox(
            entity,
            movement,
            referenceBox,
            entity.level(),
            List.of()
        );
    }

    private static boolean hasCollisionAt(
        Entity entity,
        AABB collisionBox,
        Level level,
        Vec3 requestedMovement
    ) {
        List<BondedPlasticShapeIndex.Entry> bondedObstacles = bondedObstacles(level, collisionBox);
        VoxelShape queryShape = Shapes.create(collisionBox);
        for (VoxelShape blockCollision : level.getBlockCollisions(entity, collisionBox)) {
            if (blockCollision.isEmpty()
                || isBondedCompatibilityShape(blockCollision, bondedObstacles)) {
                continue;
            }
            if (Shapes.joinIsNotEmpty(blockCollision, queryShape, BooleanOp.AND)) return true;
        }

        PlasticConvexShape probe = PlasticConvexShape.box(collisionBox);
        for (BondedPlasticShapeIndex.Entry entry : bondedObstacles) {
            if (intersects(probe, entry.convexShapes())) return true;
        }
        for (Entity obstacle : level.getEntities(
            entity,
            collisionBox.inflate(CONTACT_EPSILON),
            candidate -> usesEntityCollision(entity, candidate)
        )) {
            if (intersects(
                probe,
                collisionShapes(obstacle, obstacle.getBoundingBox(), entity, requestedMovement)
            )) {
                return true;
            }
        }
        return level.getWorldBorder().isInsideCloseToBorder(entity, collisionBox)
            && Shapes.joinIsNotEmpty(
                level.getWorldBorder().getCollisionShape(),
                queryShape,
                BooleanOp.AND
            );
    }

    private static List<PlasticConvexShape> blockObstacles(
        Entity entity,
        Level level,
        AABB sweptBounds
    ) {
        List<BondedPlasticShapeIndex.Entry> bondedObstacles = bondedObstacles(level, sweptBounds);
        List<PlasticConvexShape> obstacles = new ArrayList<>();
        for (VoxelShape blockCollision : level.getBlockCollisions(entity, sweptBounds)) {
            if (isBondedCompatibilityShape(blockCollision, bondedObstacles)) continue;
            addVoxelObstacle(obstacles, blockCollision);
        }
        addBondedObstacles(obstacles, bondedObstacles, sweptBounds);
        return obstacles;
    }

    private static boolean isNewVoxelCollision(
        VoxelShape obstacle,
        VoxelShape previousShape,
        VoxelShape targetShape
    ) {
        return Shapes.joinIsNotEmpty(obstacle, targetShape, BooleanOp.AND)
            && !Shapes.joinIsNotEmpty(obstacle, previousShape, BooleanOp.AND);
    }

    private static boolean isNewConvexCollision(
        List<PlasticConvexShape> obstacles,
        AABB previousBox,
        AABB targetBox
    ) {
        if (obstacles.isEmpty()) return false;
        PlasticConvexShape target = PlasticConvexShape.box(targetBox);
        if (!intersects(target, obstacles)) return false;
        return !intersects(PlasticConvexShape.box(previousBox), obstacles);
    }

    private static AABB enclosingBounds(List<PlasticConvexShape> shapes) {
        AABB bounds = shapes.getFirst().bounds();
        for (int index = 1; index < shapes.size(); index++) {
            bounds = bounds.minmax(shapes.get(index).bounds());
        }
        return bounds;
    }

    private static SweepContact sweepPair(
        PlasticConvexShape moving,
        PlasticConvexShape obstacle,
        Vec3 movement
    ) {
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        Vec3 entryNormal = Vec3.ZERO;
        boolean strictlyOverlapping = true;
        double minimumPenetration = Double.POSITIVE_INFINITY;
        Vec3 separationNormal = Vec3.ZERO;

        for (Vec3 axis : separatingAxes(moving, obstacle)) {
            PlasticConvexShape.Projection first = moving.project(axis);
            PlasticConvexShape.Projection second = obstacle.project(axis);
            double overlap = Math.min(first.maximum(), second.maximum())
                - Math.max(first.minimum(), second.minimum());
            strictlyOverlapping &= overlap > CONTACT_EPSILON;
            if (overlap > CONTACT_EPSILON) {
                double negativeDistance = first.maximum() - second.minimum();
                double positiveDistance = second.maximum() - first.minimum();
                double penetration = Math.min(negativeDistance, positiveDistance);
                Vec3 candidateNormal = positiveDistance < negativeDistance ? axis : axis.scale(-1.0D);
                if (penetration < minimumPenetration - CONTACT_EPSILON
                    || Math.abs(penetration - minimumPenetration) <= CONTACT_EPSILON
                        && movement.dot(candidateNormal) < movement.dot(separationNormal)) {
                    minimumPenetration = penetration;
                    separationNormal = candidateNormal;
                }
            }

            double projectedMovement = movement.dot(axis);
            if (Math.abs(projectedMovement) <= AXIS_EPSILON) {
                if (overlap <= CONTACT_EPSILON) return null;
                continue;
            }
            double firstTime = (second.minimum() - first.maximum()) / projectedMovement;
            double secondTime = (second.maximum() - first.minimum()) / projectedMovement;
            double axisEntry = Math.min(firstTime, secondTime);
            double axisExit = Math.max(firstTime, secondTime);
            Vec3 candidateNormal = projectedMovement > 0.0D ? axis.scale(-1.0D) : axis;
            if (axisEntry > entry + TIME_EPSILON
                || Math.abs(axisEntry - entry) <= TIME_EPSILON
                    && movement.dot(candidateNormal) < movement.dot(entryNormal)) {
                entry = axisEntry;
                entryNormal = candidateNormal;
            }
            exit = Math.min(exit, axisExit);
            if (entry > exit + TIME_EPSILON) return null;
        }

        if (strictlyOverlapping) {
            return movement.dot(separationNormal) < -AXIS_EPSILON
                ? new SweepContact(0.0D, separationNormal, true)
                : null;
        }
        if (exit < -TIME_EPSILON
            || entry > 1.0D + TIME_EPSILON
            || exit <= TIME_EPSILON && entry < -TIME_EPSILON
            || entryNormal.lengthSqr() <= AXIS_EPSILON
            || movement.dot(entryNormal) >= -AXIS_EPSILON) {
            return null;
        }
        return new SweepContact(Math.clamp(entry, 0.0D, 1.0D), entryNormal, false);
    }

    private static boolean isPreferredContact(
        SweepContact candidate,
        SweepContact current,
        Vec3 movement
    ) {
        if (current == null) return true;
        if (candidate.time() < current.time() - TIME_EPSILON) return true;
        if (candidate.time() > current.time() + TIME_EPSILON) return false;
        double candidateOpposition = -movement.dot(candidate.normal());
        double currentOpposition = -movement.dot(current.normal());
        if (candidateOpposition > currentOpposition + CONTACT_EPSILON) return true;
        if (candidateOpposition < currentOpposition - CONTACT_EPSILON) return false;
        if (candidate.normal().x != current.normal().x) {
            return candidate.normal().x < current.normal().x;
        }
        if (candidate.normal().y != current.normal().y) {
            return candidate.normal().y < current.normal().y;
        }
        return candidate.normal().z < current.normal().z;
    }

    private static double collideAxisInternal(
        Direction.Axis movementAxis,
        List<PlasticConvexShape> movingShapes,
        ObstacleIndex obstacles,
        double requestedMovement,
        MutableCollisionStats stats
    ) {
        if (Math.abs(requestedMovement) <= AXIS_EPSILON) return requestedMovement;
        if (stats != null) stats.inputPairs += (long) movingShapes.size() * obstacles.size();
        double allowedMovement = requestedMovement;
        ObstacleOrder obstacleOrder = obstacles.ordered(movementAxis, requestedMovement);
        for (PlasticConvexShape moving : movingShapes) {
            for (PlasticConvexShape obstacle : obstacleOrder.shapes) {
                Vec3 movement = axisVector(movementAxis, allowedMovement);
                AABB sweptBounds = moving.bounds().expandTowards(movement).inflate(CONTACT_EPSILON);
                if (pastOrderedRange(obstacle.bounds(), sweptBounds, movementAxis, requestedMovement)) {
                    if (obstacleOrder.supportsEarlyExit) break;
                    continue;
                }
                if (!overlapsMovementInterval(obstacle.bounds(), sweptBounds, movementAxis)) continue;
                if (stats != null) stats.broadPhaseCandidatePairs++;
                if (!sweptBounds.intersects(obstacle.bounds().inflate(CONTACT_EPSILON))) {
                    continue;
                }
                if (stats != null) stats.satPairs++;
                allowedMovement = collidePair(moving, obstacle, movementAxis, allowedMovement);
                if (Math.abs(allowedMovement) <= CONTACT_EPSILON) return 0.0D;
            }
        }
        return allowedMovement;
    }

    private static double collidePair(
        PlasticConvexShape moving,
        PlasticConvexShape obstacle,
        Direction.Axis movementAxis,
        double requestedMovement
    ) {
        Vec3 movement = axisVector(movementAxis, requestedMovement);
        double entry = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        boolean strictlyOverlapping = true;
        double minimumPenetration = Double.POSITIVE_INFINITY;
        Vec3 separationNormal = Vec3.ZERO;

        for (Vec3 axis : separatingAxes(moving, obstacle)) {
            PlasticConvexShape.Projection first = moving.project(axis);
            PlasticConvexShape.Projection second = obstacle.project(axis);
            double overlap = Math.min(first.maximum(), second.maximum())
                - Math.max(first.minimum(), second.minimum());
            strictlyOverlapping &= overlap > CONTACT_EPSILON;
            if (overlap > CONTACT_EPSILON) {
                double negativeDistance = first.maximum() - second.minimum();
                double positiveDistance = second.maximum() - first.minimum();
                double penetration = Math.min(negativeDistance, positiveDistance);
                Vec3 candidateNormal = positiveDistance < negativeDistance ? axis : axis.scale(-1.0D);
                if (penetration < minimumPenetration - CONTACT_EPSILON
                    || Math.abs(penetration - minimumPenetration) <= CONTACT_EPSILON
                        && movement.dot(candidateNormal) < movement.dot(separationNormal)) {
                    minimumPenetration = penetration;
                    separationNormal = candidateNormal;
                }
            }
            double projectedMovement = movement.dot(axis);
            if (Math.abs(projectedMovement) <= AXIS_EPSILON) {
                if (overlap <= CONTACT_EPSILON) return requestedMovement;
                continue;
            }
            double firstTime = (second.minimum() - first.maximum()) / projectedMovement;
            double secondTime = (second.maximum() - first.minimum()) / projectedMovement;
            double axisEntry = Math.min(firstTime, secondTime);
            double axisExit = Math.max(firstTime, secondTime);
            entry = Math.max(entry, axisEntry);
            exit = Math.min(exit, axisExit);
            if (entry > exit + TIME_EPSILON) return requestedMovement;
        }

        if (strictlyOverlapping) {
            return movement.dot(separationNormal) < -AXIS_EPSILON ? 0.0D : requestedMovement;
        }
        if (exit < -TIME_EPSILON
            || entry > 1.0D + TIME_EPSILON
            || exit <= TIME_EPSILON && entry < -TIME_EPSILON) {
            return requestedMovement;
        }
        double collisionTime = Math.clamp(entry, 0.0D, 1.0D);
        double distance = Math.max(0.0D, Math.abs(requestedMovement) * collisionTime - CONTACT_EPSILON);
        return Math.copySign(distance, requestedMovement);
    }

    private static List<Vec3> separatingAxes(
        PlasticConvexShape first,
        PlasticConvexShape second
    ) {
        return first.separatingAxes(second);
    }

    private static Vec3 axisVector(Direction.Axis axis, double movement) {
        return switch (axis) {
            case X -> new Vec3(movement, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, movement, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, movement);
        };
    }

    private static List<PlasticConvexShape> move(List<PlasticConvexShape> shapes, Vec3 movement) {
        List<PlasticConvexShape> moved = new ArrayList<>(shapes.size());
        for (PlasticConvexShape shape : shapes) moved.add(shape.move(movement));
        return moved;
    }

    private static boolean pastOrderedRange(
        AABB obstacle,
        AABB sweptBounds,
        Direction.Axis axis,
        double movement
    ) {
        return movement > 0.0D
            ? minimum(obstacle, axis) > maximum(sweptBounds, axis) + CONTACT_EPSILON
            : maximum(obstacle, axis) < minimum(sweptBounds, axis) - CONTACT_EPSILON;
    }

    private static boolean overlapsMovementInterval(AABB obstacle, AABB sweptBounds, Direction.Axis axis) {
        return maximum(obstacle, axis) >= minimum(sweptBounds, axis) - CONTACT_EPSILON
            && minimum(obstacle, axis) <= maximum(sweptBounds, axis) + CONTACT_EPSILON;
    }

    private static double minimum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double maximum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    public record AxisCollisionResult(double movement, CollisionStats stats) {
        public AxisCollisionResult {
            Objects.requireNonNull(stats, "stats");
        }
    }

    public record SweepContact(double time, Vec3 normal, boolean penetrating) {
        public SweepContact {
            if (!Double.isFinite(time) || time < 0.0D || time > 1.0D) {
                throw new IllegalArgumentException("Sweep contact time must be within [0, 1]");
            }
            normal = Objects.requireNonNull(normal, "normal");
        }
    }

    public record CollisionStats(long inputPairs, long broadPhaseCandidatePairs, long satPairs) {
    }

    private static final class MutableCollisionStats {
        private long inputPairs;
        private long broadPhaseCandidatePairs;
        private long satPairs;

        private CollisionStats snapshot() {
            return new CollisionStats(this.inputPairs, this.broadPhaseCandidatePairs, this.satPairs);
        }
    }

    private static final class ObstacleIndex {
        private final List<PlasticConvexShape> source;
        private final Map<Direction.Axis, AxisIndex> axes = new EnumMap<>(Direction.Axis.class);

        private ObstacleIndex(List<PlasticConvexShape> obstacles) {
            Objects.requireNonNull(obstacles, "obstacles");
            this.source = List.copyOf(obstacles);
        }

        private int size() {
            return this.source.size();
        }

        private ObstacleOrder ordered(Direction.Axis axis, double movement) {
            if (this.source.size() < SORTED_INDEX_MINIMUM_SIZE) {
                return new ObstacleOrder(this.source, false);
            }
            AxisIndex index = this.axes.computeIfAbsent(axis, ignored -> new AxisIndex());
            return new ObstacleOrder(index.ordered(this.source, axis, movement), true);
        }
    }

    private static final class AxisIndex {
        private List<PlasticConvexShape> minimumAscending;
        private List<PlasticConvexShape> maximumDescending;

        private List<PlasticConvexShape> ordered(
            List<PlasticConvexShape> source,
            Direction.Axis axis,
            double movement
        ) {
            if (movement > 0.0D) {
                if (this.minimumAscending == null) {
                    this.minimumAscending = new ArrayList<>(source);
                    this.minimumAscending.sort(Comparator.comparingDouble(
                        shape -> minimum(shape.bounds(), axis)
                    ));
                }
                return this.minimumAscending;
            }
            if (this.maximumDescending == null) {
                this.maximumDescending = new ArrayList<>(source);
                this.maximumDescending.sort(Comparator.comparingDouble(
                    (PlasticConvexShape shape) -> maximum(shape.bounds(), axis)
                ).reversed());
            }
            return this.maximumDescending;
        }
    }

    private record ObstacleOrder(List<PlasticConvexShape> shapes, boolean supportsEarlyExit) {
    }
}
