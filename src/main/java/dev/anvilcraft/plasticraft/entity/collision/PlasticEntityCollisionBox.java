package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** 塑料实体专用的不可变组合碰撞体。 */
public final class PlasticEntityCollisionBox {
    private static final double TANGENTIAL_INSET = 1.0E-6D;

    private final VoxelShape shape;
    private final List<AABB> components;
    private final List<PlasticConvexShape> convexComponents;
    private final AABB bounds;
    private volatile List<PlasticConvexCollisionOutline.Segment> convexOutline;

    private PlasticEntityCollisionBox(
        VoxelShape shape,
        AABB bounds,
        List<PlasticConvexShape> convexComponents
    ) {
        this.shape = Objects.requireNonNull(shape, "shape").optimize();
        this.components = List.copyOf(this.shape.toAabbs());
        this.convexComponents = List.copyOf(convexComponents);
        this.bounds = Objects.requireNonNull(bounds, "bounds");
    }

    /** 将已经旋转到实体坐标系的形状锚定到实体底面中心。 */
    public static PlasticEntityCollisionBox atEntityPosition(VoxelShape relativeShape, Vec3 entityPosition) {
        return atEntityPosition(
            relativeShape,
            relativeShape,
            List.of(),
            entityPosition,
            PlasticEntityGeometry.UNIT_CUBE_ENTITY_ORIGIN
        );
    }

    /** 将物理形状和宽阶段轮廓锚定到实体位置。 */
    public static PlasticEntityCollisionBox atEntityPosition(
        VoxelShape relativeShape,
        VoxelShape relativeBoundsShape,
        Vec3 entityPosition,
        Vec3 entityOrigin
    ) {
        return atEntityPosition(
            relativeShape,
            relativeBoundsShape,
            List.of(),
            entityPosition,
            entityOrigin
        );
    }

    /** 将连续凸体和方块 API 使用的粗略轮廓锚定到同一个实体位置。 */
    public static PlasticEntityCollisionBox atEntityPosition(
        VoxelShape relativeShape,
        VoxelShape relativeBoundsShape,
        List<PlasticConvexShape> relativeConvexShapes,
        Vec3 entityPosition,
        Vec3 entityOrigin
    ) {
        Objects.requireNonNull(relativeShape, "relativeShape");
        Objects.requireNonNull(relativeBoundsShape, "relativeBoundsShape");
        Objects.requireNonNull(relativeConvexShapes, "relativeConvexShapes");
        Objects.requireNonNull(entityPosition, "entityPosition");
        Objects.requireNonNull(entityOrigin, "entityOrigin");
        if (relativeBoundsShape.isEmpty()) {
            throw new IllegalArgumentException("Plastic entity bounds shape must not be empty");
        }
        Vec3 movement = entityPosition.subtract(entityOrigin);
        return new PlasticEntityCollisionBox(
            relativeShape.move(movement.x, movement.y, movement.z),
            relativeBoundsShape.bounds().move(movement),
            relativeConvexShapes.stream().map(shape -> shape.move(movement)).toList()
        );
    }

    public VoxelShape shape() {
        return this.shape;
    }

    public List<AABB> components() {
        return this.components;
    }

    public List<PlasticConvexShape> convexComponents() {
        return this.convexComponents;
    }

    public boolean hasConvexComponents() {
        return !this.convexComponents.isEmpty();
    }

    /** 返回当前凸碰撞并集的调试轮廓；结果随不可变碰撞箱实例缓存。 */
    public List<PlasticConvexCollisionOutline.Segment> convexOutline() {
        if (this.convexComponents.isEmpty()) return List.of();
        List<PlasticConvexCollisionOutline.Segment> cached = this.convexOutline;
        if (cached != null) return cached;
        cached = PlasticConvexCollisionOutline.build(this.convexComponents);
        this.convexOutline = cached;
        return cached;
    }

    /** 返回仅用于宽阶段检索的最小外包围盒。 */
    public AABB bounds() {
        return this.bounds;
    }

    public PlasticEntityCollisionBox move(Vec3 movement) {
        Objects.requireNonNull(movement, "movement");
        if (movement.equals(Vec3.ZERO)) return this;
        return new PlasticEntityCollisionBox(
            this.shape.move(movement.x, movement.y, movement.z),
            this.bounds.move(movement),
            this.convexComponents.stream().map(shape -> shape.move(movement)).toList()
        );
    }

    /**
     * 让所有子盒共享同一位移，逐轴取最严格的裁剪结果。
     * 轴顺序与原版 {@code Entity} 保持一致。
     */
    public Vec3 collide(
        Entity entity,
        Vec3 requestedMovement,
        Level level,
        List<VoxelShape> entityCollisions
    ) {
        return this.collide(entity, requestedMovement, level, entityCollisions, ignored -> true);
    }

    public Vec3 collide(
        Entity entity,
        Vec3 requestedMovement,
        Level level,
        List<VoxelShape> entityCollisions,
        Predicate<Entity> exactObstacleFilter
    ) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityCollisions, "entityCollisions");
        Objects.requireNonNull(exactObstacleFilter, "exactObstacleFilter");
        if (requestedMovement.lengthSqr() == 0.0D) return requestedMovement;
        if (this.components.isEmpty()) return requestedMovement;
        if (!this.convexComponents.isEmpty()) {
            return PlasticConvexCollisionResolver.collide(
                entity,
                requestedMovement,
                this.bounds,
                this.convexComponents,
                level,
                entityCollisions,
                exactObstacleFilter
            );
        }

        AABB sweptBounds = this.bounds.expandTowards(requestedMovement);
        List<VoxelShape> colliders = new ArrayList<>(entityCollisions.size() + 8);
        for (VoxelShape collision : entityCollisions) {
            if (!collision.isEmpty()) colliders.add(collision);
        }
        if (level.getWorldBorder().isInsideCloseToBorder(entity, sweptBounds)) {
            colliders.add(level.getWorldBorder().getCollisionShape());
        }
        for (VoxelShape collision : level.getBlockCollisions(entity, sweptBounds)) {
            if (!collision.isEmpty()) colliders.add(collision);
        }
        if (colliders.isEmpty()) return requestedMovement;

        List<AABB> movedComponents = this.components;
        double x = requestedMovement.x;
        double y = collideAxis(Direction.Axis.Y, movedComponents, colliders, requestedMovement.y);
        double z = requestedMovement.z;
        if (y != 0.0D) movedComponents = move(movedComponents, 0.0D, y, 0.0D);

        boolean zFirst = Math.abs(x) < Math.abs(z);
        if (zFirst && z != 0.0D) {
            z = collideAxis(Direction.Axis.Z, movedComponents, colliders, z);
            if (z != 0.0D) movedComponents = move(movedComponents, 0.0D, 0.0D, z);
        }
        if (x != 0.0D) {
            x = collideAxis(Direction.Axis.X, movedComponents, colliders, x);
            if (!zFirst && x != 0.0D) movedComponents = move(movedComponents, x, 0.0D, 0.0D);
        }
        if (!zFirst && z != 0.0D) {
            z = collideAxis(Direction.Axis.Z, movedComponents, colliders, z);
        }
        return new Vec3(x, y, z);
    }

    private static double collideAxis(
        Direction.Axis axis,
        List<AABB> components,
        List<VoxelShape> colliders,
        double requestedMovement
    ) {
        double allowedMovement = requestedMovement;
        for (AABB component : components) {
            allowedMovement = Shapes.collide(axis, insetTangentially(component, axis), colliders, allowedMovement);
            if (Math.abs(allowedMovement) < 1.0E-7D) return 0.0D;
        }
        return allowedMovement;
    }

    /** 共面的边只表示能恰好穿过，不应在垂直移动时被当成切向重叠。 */
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
        for (AABB component : components) {
            moved.add(component.move(x, y, z));
        }
        return moved;
    }
}
