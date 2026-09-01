package dev.anvilcraft.plasticraft.api.entity;

import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionBox;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexShape;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** 为塑料实体提供独立于原版单 AABB 的组合碰撞体。 */
public interface ShapedCollisionEntity {
    PlasticEntityCollisionBox plasticraft$getCollisionBox();

    /** 允许带开口或其他阶段性结构按移动者和本次位移提供同源凸碰撞。 */
    default PlasticEntityCollisionBox plasticraft$getCollisionBox(Entity mover, Vec3 requestedMovement) {
        return this.plasticraft$getCollisionBox();
    }

    default VoxelShape plasticraft$getCollisionShape() {
        return this.plasticraft$getCollisionBox().shape();
    }

    /** 允许凹形实体按移动者和本次位移提供阶段性碰撞形状。 */
    default VoxelShape plasticraft$getCollisionShape(Entity mover, Vec3 requestedMovement) {
        return this.plasticraft$getCollisionShape();
    }

    /** 返回用于选取与表面交互的轮廓；纯零厚度 cube 制品可只提供此形状。 */
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
        return collideBoundingBox(
            entity,
            movement,
            fallbackBox,
            level,
            entityCollisions,
            ignored -> true
        );
    }

    static Vec3 collideBoundingBox(
        Entity entity,
        Vec3 movement,
        AABB fallbackBox,
        Level level,
        List<VoxelShape> entityCollisions,
        Predicate<Entity> exactObstacleFilter
    ) {
        Objects.requireNonNull(exactObstacleFilter, "exactObstacleFilter");
        if (entity instanceof ShapedCollisionEntity shaped) {
            Vec3 offset = fallbackBox.getCenter().subtract(entity.getBoundingBox().getCenter());
            return shaped.plasticraft$getCollisionBox().move(offset).collide(
                entity,
                movement,
                level,
                entityCollisions,
                exactObstacleFilter
            );
        }
        AABB sweptBounds = fallbackBox.expandTowards(movement);
        if (!PlasticConvexCollisionResolver.hasExactEntityObstacle(
            entity,
            movement,
            sweptBounds,
            level,
            exactObstacleFilter
        )) {
            return Entity.collideBoundingBox(entity, movement, fallbackBox, level, entityCollisions);
        }
        return PlasticConvexCollisionResolver.collide(
            entity,
            movement,
            fallbackBox,
            List.of(PlasticConvexShape.box(fallbackBox)),
            level,
            entityCollisions,
            exactObstacleFilter
        );
    }
}
