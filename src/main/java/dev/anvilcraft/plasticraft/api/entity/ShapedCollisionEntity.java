package dev.anvilcraft.plasticraft.api.entity;

import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** 为塑料实体提供独立于原版单 AABB 的组合碰撞体。 */
public interface ShapedCollisionEntity {
    PlasticEntityCollisionBox plasticraft$getCollisionBox();

    default VoxelShape plasticraft$getCollisionShape() {
        return this.plasticraft$getCollisionBox().shape();
    }

    /** 允许凹形实体按移动者和本次位移提供阶段性碰撞形状。 */
    default VoxelShape plasticraft$getCollisionShape(Entity mover, Vec3 requestedMovement) {
        return this.plasticraft$getCollisionShape();
    }

    /** 返回用于选取与表面交互的轮廓；纯平面实体可只提供此形状。 */
    default VoxelShape plasticraft$getInteractionShape() {
        return this.plasticraft$getCollisionShape();
    }

    static VoxelShape collisionShape(Entity entity) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionShape()
            : Shapes.create(entity.getBoundingBox());
    }

    static AABB collisionBounds(Entity entity) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionBox().bounds()
            : entity.getBoundingBox();
    }

    static VoxelShape interactionShape(Entity entity) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getInteractionShape()
            : Shapes.create(entity.getBoundingBox());
    }

    static List<AABB> interactionComponents(Entity entity) {
        return interactionShape(entity).toAabbs();
    }

    /** 返回实体在指定参考包围盒位置上的真实碰撞子盒。 */
    static List<AABB> collisionComponents(Entity entity, AABB referenceBox) {
        if (!(entity instanceof ShapedCollisionEntity shaped)) return List.of(referenceBox);
        Vec3 movement = referenceBox.getCenter().subtract(entity.getBoundingBox().getCenter());
        return shaped.plasticraft$getCollisionBox().move(movement).components();
    }

    static Vec3 collideBoundingBox(
        Entity entity,
        Vec3 movement,
        AABB fallbackBox,
        Level level,
        List<VoxelShape> entityCollisions
    ) {
        return entity instanceof ShapedCollisionEntity shaped
            ? shaped.plasticraft$getCollisionBox().collide(entity, movement, level, entityCollisions)
            : Entity.collideBoundingBox(entity, movement, fallbackBox, level, entityCollisions);
    }
}
