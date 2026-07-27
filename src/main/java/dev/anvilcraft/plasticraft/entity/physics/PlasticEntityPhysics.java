package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ShapedCollisionEntity;
import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
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
    private static final double CORNER_CONTACT_PROGRESS_EPSILON = 0.025D;

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
     * 选择有效重力面上距离最近的支撑。距离相同时依次按较大的切向重叠面积和实体 ID 决定，
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
        AABB queryBox = entity instanceof ShapedCollisionEntity
            ? enclosingBounds(entityComponents).inflate(SUPPORT_PROBE_DEPTH + FACE_EPSILON)
            : supportProbe(entityBox, gravityDirection);
        List<Entity> candidates = entity.level().getEntities(
            entity,
            queryBox,
            other -> isSupportCandidate(entity, entityBox, other, gravityDirection)
        );
        Entity best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestOverlap = Double.NEGATIVE_INFINITY;
        int bestId = Integer.MAX_VALUE;
        for (Entity candidate : candidates) {
            SupportContact contact = supportContact(
                entityComponents,
                collisionComponents(candidate, candidate.getBoundingBox()),
                gravityDirection
            );
            if (contact == null) continue;
            double distance = Math.abs(contact.gap());
            double overlap = contact.overlap();
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
        if (candidate.isRemoved()
            || candidate.isSpectator()
            || entity.isPassengerOfSameVehicle(candidate)
            || !entity.canCollideWith(candidate)) {
            return false;
        }
        return supportContact(
            collisionComponents(entity, entityBox),
            collisionComponents(candidate, candidateBox),
            gravityDirection
        ) != null;
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
            || !entity.canCollideWith(support)) {
            return false;
        }
        SupportContact contact = supportContact(
            collisionComponents(entity, entityBox),
            collisionComponents(support, supportBox),
            gravityDirection
        );
        return contact != null && Math.abs(contact.gap()) <= FACE_EPSILON * 4.0D;
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
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        double normalMovement = displacement.dot(normal);
        Vec3 tangentialMovement = displacement.subtract(normal.scale(normalMovement));
        return tangentialMovement.add(normal.scale(Math.min(normalMovement, 0.0D)));
    }

    /**
     * 返回承载实体是否仍位于支撑面上，并正朝向或沿着该面移动。
     * 分离方向仍参与碰撞，使下落实体能自然脱离正在下降的砧。
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
        Vec3 gravityNormal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        return requestedMovement.dot(gravityNormal) <= FACE_EPSILON;
    }

    /**
     * 返回玩家位移是否触及侧面，并能将其沿表面切向的位移传递给塑料实体。
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
        if (!(pusher instanceof Player) && !(pusher instanceof CarrierMovableEntity)
            || pusher.isRemoved()
            || pusher.isSpectator()
            || pusher.noPhysics
            || target.isPassengerOfSameVehicle(pusher)
            || !isWithinCarryDistance(requestedMovement)) {
            return null;
        }

        // 玩家脚底仍由同一塑料实体承托时，切向输入属于表面行走，不是从侧面推动该实体。
        if (pusher instanceof Player
            && hasSurfaceSupport(pusher, pusherBox, target, Direction.DOWN)) {
            return null;
        }

        List<AABB> targetComponents = collisionComponents(target, target.getBoundingBox());
        AABB targetBounds = enclosingBounds(targetComponents);
        AABB sweptPusherBox = pusherBox.expandTowards(requestedMovement).inflate(FACE_EPSILON);
        List<SidePushContact> contacts = new ArrayList<>(2);
        for (Direction direction : Direction.values()) {
            if (direction.getAxis() == gravityDirection.getAxis()) continue;
            int sign = direction.getAxisDirection().getStep();
            double movement = requestedMovement.get(direction.getAxis()) * sign;
            if (movement <= FACE_EPSILON) continue;

            double pusherFace = faceCoordinate(pusherBox, direction);
            double pusherCenter = pusherBox.getCenter().get(direction.getAxis());
            double exteriorFace = faceCoordinate(targetBounds, direction.getOpposite());
            if ((exteriorFace - pusherCenter) * sign <= FACE_EPSILON) continue;

            double directionProgress = Double.POSITIVE_INFINITY;
            double directionTransfer = 0.0D;
            for (AABB targetComponent : targetComponents) {
                double targetFace = faceCoordinate(targetComponent, direction.getOpposite());
                double gap = (targetFace - pusherFace) * sign;
                if (gap < -SIDE_PUSH_QUERY_DISTANCE - FACE_EPSILON || gap > movement + FACE_EPSILON) continue;
                if (tangentialOverlap(sweptPusherBox, targetComponent, direction) <= FACE_EPSILON) continue;

                double contactDistance = Math.max(0.0D, gap);
                double progress = contactDistance / movement;
                if (progress < directionProgress - FACE_EPSILON) {
                    directionProgress = progress;
                    directionTransfer = Math.max(0.0D, movement - contactDistance) * sign;
                }
            }
            if (directionProgress != Double.POSITIVE_INFINITY) {
                contacts.add(new SidePushContact(
                    directionProgress,
                    axisVector(direction.getAxis(), directionTransfer)
                ));
            }
        }
        if (contacts.isEmpty()) return null;

        double bestProgress = contacts.stream()
            .mapToDouble(SidePushContact::progress)
            .min()
            .orElseThrow();
        Vec3 transferred = Vec3.ZERO;
        for (SidePushContact contact : contacts) {
            // 两个面在同一刻附近接触时视作稳定的斜角推动，避免每刻在 X/Z 之间来回切换。
            if (contact.progress() <= bestProgress + CORNER_CONTACT_PROGRESS_EPSILON) {
                transferred = transferred.add(contact.movement());
            }
        }
        return transferred;
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
            || !supported.canCollideWith(support)) {
            return false;
        }
        return supportContact(
            collisionComponents(supported, supportedBox),
            collisionComponents(support, support.getBoundingBox()),
            gravityDirection
        ) != null;
    }

    /** 移除当前重力支撑面的法向分量。 */
    public static Vec3 tangentialMovement(Vec3 movement, Direction gravityDirection) {
        Vec3 normal = Vec3.atLowerCornerOf(gravityDirection.getNormal());
        return movement.subtract(normal.scale(movement.dot(normal)));
    }

    /**
     * Converts a collision-clipped target movement back into the corresponding carrier movement.
     * Components that do not push the target are retained so diagonal movement can continue sliding along its face.
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
        return hasBlockSupport(entity, entity.getBoundingBox(), gravityDirection);
    }

    /** 检查记录的碰撞箱是否受支撑，使多步移动能保留移动前的接触状态。 */
    public static boolean hasBlockSupport(
        FallingBlockEntity entity,
        AABB box,
        Direction gravityDirection
    ) {
        for (AABB component : collisionComponents(entity, box)) {
            if (entity.level().getBlockCollisions(entity, supportProbe(component, gravityDirection))
                .iterator()
                .hasNext()) {
                return true;
            }
        }
        return false;
    }

    /** 仅当本刻移动前重力面已接触方块时返回 true。 */
    public static boolean hasImmediateBlockContact(FallingBlockEntity entity, Direction gravityDirection) {
        for (AABB component : collisionComponents(entity, entity.getBoundingBox())) {
            for (net.minecraft.world.phys.shapes.VoxelShape shape : entity.level()
                .getBlockCollisions(entity, supportProbe(component, gravityDirection))) {
                if (Math.abs(supportGap(component, shape.bounds(), gravityDirection)) <= FACE_EPSILON * 4.0D
                    && tangentialOverlap(component, shape.bounds(), gravityDirection) > FACE_EPSILON) {
                    return true;
                }
            }
        }
        return false;
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
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
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
        AABB box = entity.getBoundingBox();
        AABB otherBox = other.getBoundingBox();
        double xSeparation = signedIntervalSeparation(box.minX, box.maxX, otherBox.minX, otherBox.maxX);
        double ySeparation = signedIntervalSeparation(box.minY, box.maxY, otherBox.minY, otherBox.maxY);
        double zSeparation = signedIntervalSeparation(box.minZ, box.maxZ, otherBox.minZ, otherBox.maxZ);

        Direction.Axis contactAxis = dominantContactAxis(xSeparation, ySeparation, zSeparation);
        double separation = switch (contactAxis) {
            case X -> xSeparation;
            case Y -> ySeparation;
            case Z -> zSeparation;
        };
        return contactAxis != gravityDirection.getAxis()
            && separation <= SIDE_CONTACT_DISTANCE + FACE_EPSILON
            && tangentialOverlap(box, otherBox, axisDirection(contactAxis)) > FACE_EPSILON;
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
            if (gravityDirection != null && isSupportCandidate(entity, other, gravityDirection)) {
                continue;
            }
            if (!entity.isPassengerOfSameVehicle(other)
                && isSideContact(entity, other, gravityDirection == null ? Direction.DOWN : gravityDirection)) {
                entity.push(other);
            }
        }
    }

    private static Direction.Axis dominantContactAxis(double x, double y, double z) {
        if (x >= y - FACE_EPSILON && x >= z - FACE_EPSILON) return Direction.Axis.X;
        if (y >= z - FACE_EPSILON) return Direction.Axis.Y;
        return Direction.Axis.Z;
    }

    private static Direction axisDirection(Direction.Axis axis) {
        return switch (axis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };
    }

    private static Vec3 axisVector(Direction.Axis axis, double value) {
        return switch (axis) {
            case X -> new Vec3(value, 0.0D, 0.0D);
            case Y -> new Vec3(0.0D, value, 0.0D);
            case Z -> new Vec3(0.0D, 0.0D, value);
        };
    }

    private static double supportGap(AABB entityBox, AABB supportBox, Direction gravityDirection) {
        double entityFace = faceCoordinate(entityBox, gravityDirection);
        double supportFace = faceCoordinate(supportBox, gravityDirection.getOpposite());
        return (supportFace - entityFace) * gravityDirection.getAxisDirection().getStep();
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

    private static double tangentialOverlap(AABB first, AABB second, Direction direction) {
        double x = overlap(first.minX, first.maxX, second.minX, second.maxX);
        double y = overlap(first.minY, first.maxY, second.minY, second.maxY);
        double z = overlap(first.minZ, first.maxZ, second.minZ, second.maxZ);
        return switch (direction.getAxis()) {
            case X -> y * z;
            case Y -> x * z;
            case Z -> x * y;
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

    @Nullable
    private static SupportContact supportContact(
        List<AABB> entityComponents,
        List<AABB> supportComponents,
        Direction gravityDirection
    ) {
        SupportContact best = null;
        for (AABB entityComponent : entityComponents) {
            for (AABB supportComponent : supportComponents) {
                double gap = supportGap(entityComponent, supportComponent, gravityDirection);
                if (gap < -SUPPORT_PROBE_DEPTH - FACE_EPSILON
                    || gap > SUPPORT_PROBE_DEPTH + FACE_EPSILON) {
                    continue;
                }
                double overlap = tangentialOverlap(entityComponent, supportComponent, gravityDirection);
                if (overlap <= FACE_EPSILON) continue;
                if (best == null
                    || Math.abs(gap) < Math.abs(best.gap()) - FACE_EPSILON
                    || Math.abs(Math.abs(gap) - Math.abs(best.gap())) <= FACE_EPSILON
                        && overlap > best.overlap() + FACE_EPSILON) {
                    best = new SupportContact(gap, overlap);
                }
            }
        }
        return best;
    }

    private record SupportContact(double gap, double overlap) {
    }

    private record SidePushContact(double progress, Vec3 movement) {
    }

    private static double overlap(double firstMin, double firstMax, double secondMin, double secondMax) {
        return Math.max(0.0D, Math.min(firstMax, secondMax) - Math.max(firstMin, secondMin));
    }

    /** 正值表示间隙，零表示接触，负值表示穿透深度。 */
    private static double signedIntervalSeparation(
        double firstMin,
        double firstMax,
        double secondMin,
        double secondMax
    ) {
        if (firstMax < secondMin) {
            return secondMin - firstMax;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax;
        }
        return -overlap(firstMin, firstMax, secondMin, secondMax);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
