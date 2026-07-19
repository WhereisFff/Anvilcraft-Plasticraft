package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.api.entity.ElasticCollisionEntity;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/** 从移动者的碰撞形状中排除可随动目标，最终位移随后按目标可移动距离裁剪。 */
@Mixin(EntityGetter.class)
interface EntityGetterMixin {
    @ModifyExpressionValue(
        method = "getEntityCollisions",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/function/Predicate;and(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;"
        )
    )
    private Predicate<Entity> plasticraft$allowCarrierMovement(
        Predicate<Entity> original,
        Entity mover
    ) {
        if (!(mover instanceof CarrierMoveContextHolder holder)) return original;
        CarrierMoveContext context = holder.plasticraft$getCarrierMoveContext();
        if (context == null) return original;
        return target -> {
            boolean collides = original.test(target);
            if (!collides) return false;
            if (!(target instanceof CarrierMovableEntity movable)) {
                if (target instanceof ElasticCollisionEntity elastic) {
                    context.addElasticCollisionTarget(elastic);
                }
                return true;
            }
            if (context.collidesDuringRetry(movable)) return true;
            if (!movable.plasticraft$canMoveWithCarrier(mover, context.requestedMovement())) {
                if (target instanceof ElasticCollisionEntity elastic) {
                    context.addElasticCollisionTarget(elastic);
                }
                return true;
            }
            context.addTarget(movable);
            return false;
        };
    }
}
