package dev.anvilcraft.plasticraft.api.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * 可跟随当前支撑实体移动的碰撞目标。
 * 实现类负责将承载位移裁剪到自身能够完成的范围，随后接收该实际位移。
 */
public interface CarrierMovableEntity {
    boolean plasticraft$canMoveWithCarrier(Entity carrier, Vec3 requestedMovement);

    void plasticraft$moveWithCarrier(Entity carrier, Vec3 actualMovement);

    /** 将承载者的请求位移裁剪到此目标及其推链能够共同完成的最大位移。 */
    Vec3 plasticraft$clampCarrierMovement(Entity carrier, Vec3 requestedMovement);
}
