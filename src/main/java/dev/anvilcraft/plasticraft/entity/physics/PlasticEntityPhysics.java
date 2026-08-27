package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityBondManager;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityContactResolver;
import dev.dubhe.anvilcraft.util.GravityManager;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** 可移动塑料实体共用的重力、支撑、承载和侧推几何逻辑。 */
public final class PlasticEntityPhysics {
    public static final double SUPPORT_PROBE_DEPTH = 0.05D;
    public static final double FACE_EPSILON = 1.0E-5D;
    public static final double SIDE_PUSH_QUERY_DISTANCE = 0.20D;
    public static final double SIDE_CONTACT_DISTANCE = 1.0E-3D;
    public static final double MAX_CARRY_DISTANCE = 0.75D;

    private static final double MIN_EFFECTIVE_GRAVITY_SQR = 1.0E-10D;
    private PlasticEntityPhysics() {
    }

    /** 在 AnvilCraft 完整落方块重力向量上叠加世界向上的浮力。 */
    public static Vec3 effectiveGravity(FallingBlockEntity entity, double buoyancyAcceleration) {
        return GravityManager.getNetGravityVectorForFallingBlock(entity).add(0.0D, buoyancyAcceleration, 0.0D);
    }

    /** 返回占主导的有效重力面，合力抵消时回退为向下。 */
    public static Direction effectiveDirection(FallingBlockEntity entity, double buoyancyAcceleration) {
        Vec3 gravity = effectiveGravity(entity, buoyancyAcceleration);
        return gravity.lengthSqr() <= MIN_EFFECTIVE_GRAVITY_SQR ? Direction.DOWN : Direction.getNearest(gravity);
    }

    /** 合力抵消时不返回支撑方向，避免任意锁定为向下。 */
    @Nullable
    public static Direction directionOrNull(Vec3 gravity) {
        return gravity.lengthSqr() <= MIN_EFFECTIVE_GRAVITY_SQR ? null : Direction.getNearest(gravity);
    }

    /** 紧贴给定碰撞箱重力朝向面的外侧构造薄接触探针。 */
    public static AABB supportProbe(AABB box, Direction gravityDirection) {
        double inset = FACE_EPSILON;
        double depth = SUPPORT_PROBE_DEPTH;
        return switch (gravityDirection) {
            case DOWN -> new AABB(
                box.minX + inset, box.minY - depth, box.minZ + inset,
                box.maxX - inset, box.minY + inset, box.maxZ - inset
            );
            case UP -> new AABB(
                box.minX + inset, box.maxY - inset, box.minZ + inset,
                box.maxX - inset, box.maxY + depth, box.maxZ - inset
            );
            case WEST -> new AABB(
                box.minX - depth, box.minY + inset, box.minZ + inset,
                box.minX + inset, box.maxY - inset, box.maxZ - inset
            );
            case EAST -> new AABB(
                box.maxX - inset, box.minY + inset, box.minZ + inset,
                box.maxX + depth, box.maxY - inset, box.maxZ - inset
            );
            case NORTH -> new AABB(
                box.minX + inset, box.minY + inset, box.minZ - depth,
                box.maxX - inset, box.maxY - inset, box.minZ + inset
            );
            case SOUTH -> new AABB(
                box.minX + inset, box.minY + inset, box.maxZ - inset,
                box.maxX - inset, box.maxY - inset, box.maxZ + depth
            );
        };
    }

    /**
     * 选择有效重力面上距离最近的支撑。距离相同时依次按较大的抗重力法线分量和实体 ID 决定，
     * 使多个实体共享探针区域时结果仍保持稳定。
     */
    @Nullable
    public static Entity findSupport(FallingBlockEntity entity, Direction gravityDirection) {
        return findSupport(entity, entity.getBoundingBox(), gravityDirection);
    }

    /** 在局部加速位移改变碰撞箱之前，使用记录的碰撞箱查找支撑。 */
    @Nullable
    public static Entity findSupport(FallingBlockEntity entity, AABB entityBox, Direction gravityDirection) {
        List<AABB> entityComponents = collisionComponents(entity, entityBox);
        if (entityComponents.isEmpty()) return null;
        List<PlasticConvexShape> entityShapes = PlasticConvexCollisionResolver.collisionShapes(entity, entityBox);
        AABB queryBox = entityShapes.isEmpty()
            ? supportProbe(entityBox, gravityDirection)
            : enclosingShapeBounds(entityShapes).inflate(SUPPORT_PROBE_DEPTH + FACE_EPSILON);
        // 宽阶段谓词只做廉价过滤：凸体接触求解留给下面的循环，避免每个候选被求解两次。
        List<Entity> candidates = entity.level().getEntities(
            entity,
            queryBox,
            other -> canSupport(entity, other) && ShapedCollisionEntity.collisionBounds(other).intersects(queryBox)
        );
        Entity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestOverlap = Double.NEGATIVE_INFINITY;
        int bestId = Integer.MAX_VALUE;
        for (Entity candidate : candidates) {
            PlasticEntityContactResolver.SupportContact contact = PlasticEntityContactResolver.supportContact(
                entity,
                entityBox,
                candidate,
                candidate.getBoundingBox(),
                gravityDirection,
                SUPPORT_PROBE_DEPTH
            );
            if (contact == null) continue;
            double distance = contact.distance();
            double overlap = contact.alignment();
            int id = candidate.getId();
            if (distance < bestDistance - FACE_EPSILON
                || Math.abs(distance - bestDistance) <= FACE_EPSILON && overlap > bestOverlap + FACE_EPSILON
                || Math.abs(distance - bestDistance) <= FACE_EPSILON
                    && Math.abs(overlap - bestOverlap) <= FACE_EPSILON
                    && id < bestId) {
                best = candidate;
                bestDistance = distance;
                bestOverlap = overlap;
                bestId = id;
            }
        }
        return best;
    }

    /** 返回候选实体是否占据重力面外的薄支撑区域。 */
    public static boolean isSupportCandidate(
        FallingBlockEntity entity,
        Entity candidate,
        Direction gravityDirection
    ) {
        return isSupportCandidate(entity, entity.getBoundingBox(), candidate, gravityDirection);
    }

    /** 返回候选实体是否支撑记录的实体碰撞箱。 */
    public static boolean isSupportCandidate(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity candidate,
        Direction gravityDirection
    ) {
        return isSupportCandidate(entity, entityBox, candidate, candidate.getBoundingBox(), gravityDirection);
    }

    /** 使用明确记录的候选碰撞箱判断其是否支撑实体。 */
    public static boolean isSupportCandidate(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity candidate,
        AABB candidateBox,
        Direction gravityDirection
    ) {
        if (!canSupport(entity, candidate)) return false;
        return PlasticEntityContactResolver.supportContact(
            entity,
            entityBox,
            candidate,
            candidateBox,
            gravityDirection,
            SUPPORT_PROBE_DEPTH
        ) != null;
    }

    /** 支撑判定的廉价前置条件，不涉及任何凸体求解，可安全用作宽阶段谓词。 */
    private static boolean canSupport(FallingBlockEntity entity, Entity candidate) {
        return !candidate.isRemoved()
            && !candidate.isSpectator()
            && !entity.isPassengerOfSameVehicle(candidate)
            && !EntityBondManager.areInSameComponent(entity, candidate)
            && entity.canCollideWith(candidate);
    }

    /** 仅当两个支撑面实际接触时返回 true，而非仅位于捕获探针内。 */
    public static boolean hasImmediateEntityContact(
        FallingBlockEntity entity,
        Entity support,
        Direction gravityDirection
    ) {
        return hasImmediateEntityContact(entity, entity.getBoundingBox(), support, gravityDirection);
    }

    /** 检查与记录的实体碰撞箱是否直接接触。 */
    public static boolean hasImmediateEntityContact(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity support,
        Direction gravityDirection
    ) {
        return hasImmediateEntityContact(
            entity,
            entityBox,
            support,
            support.getBoundingBox(),
            gravityDirection
        );
    }

    /** 使用明确记录的支撑碰撞箱检查两个支撑面是否直接接触。 */
    public static boolean hasImmediateEntityContact(
        FallingBlockEntity entity,
        AABB entityBox,
        Entity support,
        AABB supportBox,
        Direction gravityDirection
    ) {
        if (support.isRemoved()
            || support.isSpectator()
            || entity.isPassengerOfSameVehicle(support)
            || EntityBondManager.areInSameComponent(entity, support)
            || !entity.canCollideWith(support)) {
            return false;
        }
        return PlasticEntityContactResolver.supportContact(
            entity,
            entityBox,
            support,
            supportBox,
            gravityDirection,
            FACE_EPSILON * 4.0D
        ) != null;
    }

    /** 返回支撑碰撞箱上指回被承载实体一面的中心。 */
    public static Vec3 supportAnchor(AABB supportBox, Direction gravityDirection) {
        Vec3 center = supportBox.getCenter();
        return switch (gravityDirection) {
            case DOWN -> new Vec3(center.x, supportBox.maxY, center.z);
            case UP -> new Vec3(center.x, supportBox.minY, center.z);
            case WEST -> new Vec3(supportBox.maxX, center.y, center.z);
            case EAST -> new Vec3(supportBox.minX, center.y, center.z);
            case NORTH -> new Vec3(center.x, center.y, supportBox.maxZ);
            case SOUTH -> new Vec3(center.x, center.y, supportBox.minZ);
        };
    }

    /**
     * 根据支撑实体的当前和旧位置计算一刻内的承载位移。切向位移始终保留，
     * 法向位移仅在支撑实体朝被承载实体移动时保留。
     */
    public static Vec3 carriedMovement(Entity support, Direction gravityDirection) {
        Vec3 displacement = new Vec3(
            support.getX() - support.xo,
            support.getY() - support.yo,
            support.getZ() - support.zo
        );
        AABB currentBox = support.getBoundingBox();
        return carriedMovement(currentBox.move(displacement.scale(-1.0D)), currentBox, gravityDirection);
    }

    /** 稳定的支撑观测记录，用于避免依赖其他实体的刻执行顺序。 */
    public record SupportObservation(UUID entityId, AABB boundingBox) {
        public SupportObservation {
            if (entityId == null || boundingBox == null) {
                throw new IllegalArgumentException("Support observation requires an id and bounding box");
            }
        }

        public static SupportObservation capture(Entity support) {
            return new SupportObservation(support.getUUID(), support.getBoundingBox());
        }
    }

    /** 仅当连续两次观测指向同一支撑实体时才进行承载。 */
    public static Vec3 carriedMovement(
        @Nullable SupportObservation previous,
        SupportObservation current,
        Direction gravityDirection
    ) {
        if (previous == null || !previous.entityId().equals(current.entityId())) {
            return Vec3.ZERO;
        }
        return carriedMovement(previous.boundingBox(), current.boundingBox(), gravityDirection);
    }

    /** 根据明确给出的前一支撑碰撞箱和当前支撑碰撞箱计算承载位移。 */
    public static Vec3 carriedMovement(AABB previousSupportBox, AABB currentSupportBox, Direction gravityDirection) {
        Vec3 displacement = supportAnchor(currentSupportBox, gravityDirection)
            .subtract(supportAnchor(previousSupportBox, gravityDirection));
        if (!isWithinCarryDistance(displacement)) {
            return Vec3.ZERO;
        }
        return carriedMovement(displacement, gravityDirection);
    }

    /** 保留切向和朝被承载实体的法向位移，支撑者离开时不把目标一同拖走。 */
    public static Vec3 carriedMovement(Vec3 displacement, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalMovement = displacement.dot(normal);
        Vec3 tangentialMovement = displacement.subtract(normal.scale(normalMovement));
        return tangentialMovement.add(normal.scale(Math.min(normalMovement, 0.0D)));
    }

    /** 制品当前是否把该实体记为自己的真实支撑，供姿态和碰撞查询排除头顶承载物。 */
    public static boolean isSupportedBy(Entity carried, Entity support) {
        return carried instanceof AbstractPlasticEntity plastic
            && plastic.plasticraft$isSupportedBy(support);
    }

    /**
     * 返回目标是否仍由该实体支撑并可参与本次承载。
     * 支撑者离开目标的法向分量随后由 {@link #carriedMovement(Vec3, Direction)} 删除。
     */
    public static boolean canMoveWithCarrier(
        FallingBlockEntity carried,
        Entity carrier,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        return canMoveWithCarrier(
            carried,
            carrier,
            carrier.getBoundingBox(),
            gravityDirection,
            requestedMovement
        );
    }

    /** 使用承载者移动前的碰撞箱检查本次承载位移。 */
    public static boolean canMoveWithCarrier(
        FallingBlockEntity carried,
        Entity carrier,
        AABB carrierBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        if (!isSupportCandidate(carried, carried.getBoundingBox(), carrier, carrierBox, gravityDirection)) {
            return false;
        }
        if (!isWithinCarryDistance(requestedMovement)) {
            return false;
        }
        return true;
    }

    /**
     * 返回推动实体的位移是否触及侧面，并能将其沿表面切向的位移传递给塑料实体。
     */
    public static boolean canPushFromSide(
        FallingBlockEntity target,
        Entity pusher,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        return sidePushMovement(
            target,
            pusher,
            pusher.getBoundingBox(),
            gravityDirection,
            requestedMovement
        ) != null;
    }

    /** 使用明确记录的推动者移动前碰撞箱检查侧推。 */
    public static boolean canPushFromSide(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        return sidePushMovement(target, pusher, pusherBox, gravityDirection, requestedMovement) != null;
    }

    /** 侧推需要推动者受支撑，避免跳跃或下落时把水平位移传给塑料实体。 */
    public static boolean hasSidePushSupport(Entity pusher) {
        return hasSidePushSupport(pusher, pusher.getBoundingBox());
    }

    /**
     * 使用推动位移开始前的碰撞箱判定支撑，避免最后一步离开边缘时提前丢失本次合法侧推。
     */
    private static boolean hasSidePushSupport(Entity pusher, AABB pusherBox) {
        if (pusher instanceof AbstractPlasticEntity plasticEntity) {
            Direction gravityDirection = plasticEntity.plasticraft$currentPushGravityDirection();
            return hasBlockSupport(plasticEntity, pusherBox, gravityDirection)
                || findSupport(plasticEntity, pusherBox, gravityDirection) != null;
        }
        return hasBlockSupport(pusher, pusherBox, Direction.DOWN)
            || hasEntitySupport(pusher, pusherBox, Direction.DOWN);
    }

    /** 返回实体参与塑料支撑和推动判定时使用的有效重力方向。 */
    public static Direction gravityDirection(Entity entity) {
        return entity instanceof AbstractPlasticEntity plastic
            ? plastic.plasticraft$currentPushGravityDirection()
            : Direction.DOWN;
    }

    /**
     * 将侧推分解到接触面的法向。平行于该面的位移仍由推动者保留，
     * 因而斜向行走会从实体旁滑过，而不会带着实体横向移动。
     *
     * @return 传递给目标的位移；未触及侧面时返回 {@code null}
     */
    @Nullable
    public static Vec3 sidePushMovement(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        PlasticEntityContactResolver.PushContact contact = sidePushContact(
            target,
            pusher,
            pusherBox,
            gravityDirection,
            requestedMovement
        );
        return contact == null ? null : contact.targetMovement();
    }

    /** 返回由真实接触法线推导出的侧推位移和反向裁剪关系。 */
    @Nullable
    public static PlasticEntityContactResolver.PushContact sidePushContact(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox,
        Direction gravityDirection,
        Vec3 requestedMovement
    ) {
        if (!isContinuousSidePushCarrier(target, pusher, pusherBox)
            || !isWithinCarryDistance(requestedMovement)) {
            return null;
        }
        return PlasticEntityContactResolver.sidePush(
            target,
            pusher,
            pusherBox,
            gravityDirection,
            requestedMovement
        );
    }

    /** 返回该实体接触是否由连续侧推负责，而不依赖某一种实体类型。 */
    public static boolean isContinuousSidePushCarrier(
        FallingBlockEntity target,
        Entity pusher,
        AABB pusherBox
    ) {
        if ((!(pusher instanceof CarrierMovableEntity)
            && !EntitySelector.pushableBy(target).test(pusher))
            || pusher.isRemoved()
            || pusher.isSpectator()
            || pusher.noPhysics
            || target.isPassengerOfSameVehicle(pusher)
            || EntityBondManager.areInSameComponent(target, pusher)
            || !hasSidePushSupport(pusher, pusherBox)) {
            return false;
        }
        Direction pusherGravityDirection = gravityDirection(pusher);
        return !hasSurfaceSupport(pusher, pusherBox, target, pusherGravityDirection);
    }

    /**
     * 返回实体的重力面是否仍落在指定支撑实体的任一真实碰撞子盒上。
     * 支撑探针允许边缘处的微小嵌入或分离，避免脚底只剩少量接触时退化成侧推。
     */
    public static boolean hasSurfaceSupport(
        Entity supported,
        AABB supportedBox,
        Entity support,
        Direction gravityDirection
    ) {
        if (supported.isRemoved()
            || support.isRemoved()
            || supported.isSpectator()
            || support.isSpectator()
            || supported.isPassengerOfSameVehicle(support)
            || EntityBondManager.areInSameComponent(supported, support)
            || !supported.canCollideWith(support)) {
            return false;
        }
        return PlasticEntityContactResolver.supportContact(
            supported,
            supportedBox,
            support,
            support.getBoundingBox(),
            gravityDirection,
            SUPPORT_PROBE_DEPTH
        ) != null;
    }

    /** 移除当前重力支撑面的法向分量。 */
    public static Vec3 tangentialMovement(Vec3 movement, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        return movement.subtract(normal.scale(movement.dot(normal)));
    }

    /**
     * 将目标受碰撞裁剪后的位移反推为承载者可完成的位移。
     * 不推动目标的分量保持不变，使斜向移动仍能沿接触面滑行。
     */
    public static Vec3 clampCarrierMovement(
        Vec3 carrierMovement,
        Vec3 requestedTargetMovement,
        Vec3 allowedTargetMovement
    ) {
        return new Vec3(
            clampCarrierComponent(carrierMovement.x, requestedTargetMovement.x, allowedTargetMovement.x),
            clampCarrierComponent(carrierMovement.y, requestedTargetMovement.y, allowedTargetMovement.y),
            clampCarrierComponent(carrierMovement.z, requestedTargetMovement.z, allowedTargetMovement.z)
        );
    }

    private static double clampCarrierComponent(double carrier, double requestedTarget, double allowedTarget) {
        if (Math.abs(requestedTarget) <= FACE_EPSILON) return carrier;
        double limited = carrier - requestedTarget + allowedTarget;
        if (carrier > 0.0D) return Math.max(0.0D, Math.min(carrier, limited));
        if (carrier < 0.0D) return Math.min(0.0D, Math.max(carrier, limited));
        return 0.0D;
    }

    /** 分别限制每条轴，避免将普通斜向步行误判为传送。 */
    public static boolean isWithinCarryDistance(Vec3 movement) {
        return isFinite(movement)
            && Math.abs(movement.x) <= MAX_CARRY_DISTANCE + FACE_EPSILON
            && Math.abs(movement.y) <= MAX_CARRY_DISTANCE + FACE_EPSILON
            && Math.abs(movement.z) <= MAX_CARRY_DISTANCE + FACE_EPSILON;
    }

    /** 将有效重力方向转换为可复用的接触位。 */
    public static int directionMask(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    /** 返回有效重力轴上的位移分量。 */
    public static double gravityAxisComponent(Vec3 movement, Direction gravityDirection) {
        return movement.get(gravityDirection.getAxis());
    }

    /** 当 Entity.move 在重力朝向侧裁剪了请求位移时返回 true。 */
    public static boolean collidedAlongGravity(Vec3 requested, Vec3 actual, Direction gravityDirection) {
        double requestedComponent = gravityAxisComponent(requested, gravityDirection);
        double actualComponent = gravityAxisComponent(actual, gravityDirection);
        int expectedSign = gravityDirection.getAxisDirection().getStep();
        return requestedComponent * expectedSign > FACE_EPSILON
            && Math.abs(requestedComponent - actualComponent) > FACE_EPSILON;
    }

    /** 返回方块碰撞形状是否支撑有效重力面。 */
    public static boolean hasBlockSupport(FallingBlockEntity entity, Direction gravityDirection) {
        return hasBlockSupport((Entity) entity, entity.getBoundingBox(), gravityDirection);
    }

    /** 检查记录的碰撞箱是否受支撑，使多步移动能保留移动前的接触状态。 */
    public static boolean hasBlockSupport(
        FallingBlockEntity entity,
        AABB box,
        Direction gravityDirection
    ) {
        return hasBlockSupport((Entity) entity, box, gravityDirection);
    }

    /** 供普通推动实体复用与塑料实体相同的真实方块支撑判定。 */
    public static boolean hasBlockSupport(Entity entity, Direction gravityDirection) {
        return hasBlockSupport(entity, entity.getBoundingBox(), gravityDirection);
    }

    private static boolean hasBlockSupport(Entity entity, AABB box, Direction gravityDirection) {
        return PlasticConvexCollisionResolver.hasBlockSupport(
            entity,
            box,
            gravityDirection,
            SUPPORT_PROBE_DEPTH
        );
    }

    private static boolean hasEntitySupport(Entity entity, AABB box, Direction gravityDirection) {
        List<AABB> components = collisionComponents(entity, box);
        if (components.isEmpty()) return false;
        AABB probe = enclosingBounds(components).inflate(SUPPORT_PROBE_DEPTH + FACE_EPSILON);
        for (Entity candidate : entity.level().getEntities(
            entity,
            probe,
            other -> !other.isRemoved() && !other.isSpectator()
        )) {
            if (hasSurfaceSupport(entity, box, candidate, gravityDirection)) return true;
        }
        return false;
    }

    /** 仅当本刻移动前重力面已接触方块时返回 true。 */
    public static boolean hasImmediateBlockContact(FallingBlockEntity entity, Direction gravityDirection) {
        return PlasticConvexCollisionResolver.hasBlockSupport(
            entity,
            entity.getBoundingBox(),
            gravityDirection,
            FACE_EPSILON * 4.0D
        );
    }

    /** 仅丢弃指向当前支撑面的速度分量。 */
    public static Vec3 removeIntoSupportVelocity(Vec3 velocity, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double component = velocity.dot(normal);
        return component > 0.0D ? velocity.subtract(normal.scale(component)) : velocity;
    }

    /** 仅清除仍指向本次移动中受裁剪面的速度分量。 */
    public static Vec3 removeClippedVelocity(Vec3 velocity, Vec3 requested, Vec3 actual) {
        double x = clippedIntoFace(velocity.x, requested.x, actual.x) ? 0.0D : velocity.x;
        double y = clippedIntoFace(velocity.y, requested.y, actual.y) ? 0.0D : velocity.y;
        double z = clippedIntoFace(velocity.z, requested.z, actual.z) ? 0.0D : velocity.z;
        return new Vec3(x, y, z);
    }

    /** 返回本次移动因碰撞而受裁剪的世界方向面。 */
    public static int clippedDirectionMask(Vec3 requested, Vec3 actual, double minimumSpeed) {
        int contacts = 0;
        for (Direction direction : Direction.values()) {
            double requestedComponent = requested.get(direction.getAxis());
            double actualComponent = actual.get(direction.getAxis());
            int sign = direction.getAxisDirection().getStep();
            if (requestedComponent * sign <= minimumSpeed) continue;
            if (Math.abs(requestedComponent - actualComponent) <= FACE_EPSILON) continue;
            contacts |= directionMask(direction);
        }
        return contacts;
    }

    private static boolean clippedIntoFace(double velocity, double requested, double actual) {
        return Math.abs(requested - actual) > FACE_EPSILON
            && Math.abs(requested) > FACE_EPSILON
            && Math.signum(velocity) == Math.signum(requested);
    }

    /** 六个重力面下供 AnvilCraft 落地事件使用的位置。 */
    public static BlockPos landingPosition(
        FallingBlockEntity entity,
        Direction gravityDirection
    ) {
        List<AABB> components = collisionComponents(entity, entity.getBoundingBox());
        if (components.isEmpty()) return entity.blockPosition();
        AABB box = enclosingBounds(components);
        Vec3 center = entity instanceof AbstractPlasticEntity plasticEntity
            ? plasticEntity.plasticraft$getRotationCenter()
            : box.getCenter();
        Vec3 faceCenter = switch (gravityDirection.getAxis()) {
            case X -> new Vec3(faceCoordinate(box, gravityDirection), center.y, center.z);
            case Y -> new Vec3(center.x, faceCoordinate(box, gravityDirection), center.z);
            case Z -> new Vec3(center.x, center.y, faceCoordinate(box, gravityDirection));
        };
        Vec3 inward = Vec3.atLowerCornerOf(gravityDirection.getNormal()).scale(-FACE_EPSILON);
        return BlockPos.containing(faceCenter.add(inward));
    }

    /** 构造仅查询侧推的宽阶段区域，不包含两个水平面。 */
    public static AABB sidePushProbe(AABB box) {
        return box.inflate(SIDE_PUSH_QUERY_DISTANCE);
    }

    /**
     * 仅当最小分离接触法线为水平方向时返回 true。
     * 站立实体通常会因浮点误差与顶面产生极小重叠，
     * 因此仅检查水平重叠会错误推动它。
     */
    public static boolean isSideContact(Entity entity, Entity other) {
        return isSideContact(entity, other, Direction.DOWN);
    }

    /** 当最小分离面与有效重力方向相切时返回 true。 */
    public static boolean isSideContact(Entity entity, Entity other, Direction gravityDirection) {
        Direction otherGravity = gravityDirection(other);
        if (hasSurfaceSupport(other, other.getBoundingBox(), entity, otherGravity)) return false;
        if (hasSurfaceSupport(entity, entity.getBoundingBox(), other, gravityDirection)) return false;
        return PlasticEntityContactResolver.hasLateralContact(
            entity,
            other,
            gravityDirection,
            SIDE_CONTACT_DISTANCE + FACE_EPSILON
        );
    }

    /** 仅对接触侧面的实体执行原版推动操作。 */
    public static void pushSideEntities(
        FallingBlockEntity entity,
        @Nullable Direction gravityDirection,
        @Nullable UUID supportId
    ) {
        List<Entity> nearby = entity.level().getEntities(
            entity,
            sidePushProbe(entity.getBoundingBox()),
            EntitySelector.pushableBy(entity)
        );
        for (Entity other : nearby) {
            if (supportId != null && supportId.equals(other.getUUID())) continue;
            if (!hasSidePushSupport(other)) continue;
            if (gravityDirection != null && isSupportCandidate(entity, other, gravityDirection)) {
                continue;
            }
            if (!entity.isPassengerOfSameVehicle(other)
                && isSideContact(entity, other, gravityDirection == null ? Direction.DOWN : gravityDirection)) {
                entity.push(other);
            }
        }
    }

    private static double faceCoordinate(AABB box, Direction direction) {
        return switch (direction) {
            case DOWN -> box.minY;
            case UP -> box.maxY;
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
        };
    }

    private static List<AABB> collisionComponents(Entity entity, AABB referenceBox) {
        return ShapedCollisionEntity.collisionComponents(entity, referenceBox);
    }

    private static AABB enclosingBounds(List<AABB> components) {
        AABB bounds = components.getFirst();
        for (int index = 1; index < components.size(); index++) {
            bounds = bounds.minmax(components.get(index));
        }
        return bounds;
    }

    private static AABB enclosingShapeBounds(List<PlasticConvexShape> shapes) {
        AABB bounds = shapes.getFirst().bounds();
        for (int index = 1; index < shapes.size(); index++) {
            bounds = bounds.minmax(shapes.get(index).bounds());
        }
        return bounds;
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
