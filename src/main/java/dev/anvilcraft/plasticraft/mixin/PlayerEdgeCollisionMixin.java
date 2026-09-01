package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 玩家边缘防滑和姿态判定必须与实体移动使用同一套塑料凸体。 */
@Mixin(Player.class)
abstract class PlayerEdgeCollisionMixin {
    @Redirect(
        method = "canFallAtLeast",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean plasticraft$useExactPlasticSupport(
        Level level,
        Entity entity,
        AABB collisionBox
    ) {
        return PlasticConvexCollisionResolver.noCollisionWithExactPlastic(entity, collisionBox, level);
    }

    @Redirect(
        method = "canPlayerFitWithinBlocksAndEntitiesWhen",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean plasticraft$useExactPlasticCollisionForPose(
        Level level,
        Entity entity,
        AABB collisionBox
    ) {
        return PlasticConvexCollisionResolver.noCollisionWithExactPlastic(entity, collisionBox, level);
    }
}
