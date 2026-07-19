package dev.anvilcraft.plasticraft.api.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 接收经过实体碰撞裁剪后的最终位移。 */
public interface ElasticCollisionEntity {
    void plasticraft$onEntityCollision(
        Entity collider,
        AABB colliderStartBox,
        Vec3 requestedMovement,
        Vec3 actualMovement
    );
}
