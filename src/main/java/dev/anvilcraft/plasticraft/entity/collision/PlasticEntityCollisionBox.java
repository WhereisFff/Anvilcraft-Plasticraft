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

/** 塑料实体专用的不可变组合碰撞体。 */
public final class PlasticEntityCollisionBox {
    private static final double TANGENTIAL_INSET = 1.0E-6D;

    private final VoxelShape shape;
    private final List<AABB> components;
    private final AABB bounds;

    private PlasticEntityCollisionBox(VoxelShape shape) {
        this.shape = Objects.requireNonNull(shape, "shape").optimize();
        if (this.shape.isEmpty()) {
            throw new IllegalArgumentException("Plastic entity collision shape must not be empty");
        }
        this.components = List.copyOf(this.shape.toAabbs());
        this.bounds = this.shape.bounds();
    }

    /** 将已经旋转到实体坐标系的形状锚定到实体底面中心。 */
    public static PlasticEntityCollisionBox atEntityPosition(VoxelShape relativeShape, Vec3 entityPosition) {
        Objects.requireNonNull(relativeShape, "relativeShape");
        Objects.requireNonNull(entityPosition, "entityPosition");
        return new PlasticEntityCollisionBox(relativeShape.move(
            entityPosition.x - 0.5D,
            entityPosition.y,
            entityPosition.z - 0.5D
        ));
    }

    public VoxelShape shape() {
        return this.shape;
    }

    public List<AABB> components() {
        return this.components;
    }

    /** 返回仅用于宽阶段检索的最小外包围盒。 */
    public AABB bounds() {
        return this.bounds;
    }

    public PlasticEntityCollisionBox move(Vec3 movement) {
        Objects.requireNonNull(movement, "movement");
        if (movement.equals(Vec3.ZERO)) return this;
        return new PlasticEntityCollisionBox(this.shape.move(movement.x, movement.y, movement.z));
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
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(requestedMovement, "requestedMovement");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(entityCollisions, "entityCollisions");
        if (requestedMovement.lengthSqr() == 0.0D) return requestedMovement;

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
