package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.collision.PlasticConvexCollisionResolver;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntity.class)
abstract class ItemEntityCollisionMixin {
    // 脱困判定必须与移动使用同一凸体，否则斜面的空角会误开启 noPhysics。
    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"
        )
    )
    private boolean plasticraft$useExactPlasticCollision(Level level, Entity entity, AABB bounds) {
        return PlasticConvexCollisionResolver.noCollisionWithExactPlastic(entity, bounds, level);
    }
}
