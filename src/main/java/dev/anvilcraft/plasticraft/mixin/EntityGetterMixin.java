package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

/** Removes a carried target from the mover's collision shapes after a full-motion preflight. */
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
            if (!collides || !(target instanceof CarrierMovableEntity movable)) return collides;
            if (context.collidesDuringRetry(movable)) return true;
            if (!movable.plasticraft$canMoveWithCarrier(mover, context.requestedMovement())
                || !movable.plasticraft$canCompleteCarrierMovement(mover, context.requestedMovement())) {
                return true;
            }
            context.addTarget(movable);
            return false;
        };
    }
}
