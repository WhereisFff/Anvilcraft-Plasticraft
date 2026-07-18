package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.api.entity.CarrierMovableEntity;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContext;
import dev.anvilcraft.plasticraft.entity.collision.CarrierMoveContextHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.ArrayList;
import java.util.List;

/** Transfers a support entity's collision-clipped movement to the object it carries. */
@Mixin(Entity.class)
abstract class EntityCarrierCollisionMixin implements CarrierMoveContextHolder {
    @Unique
    @Nullable
    private CarrierMoveContext plasticraft$carrierMoveContext;

    @Invoker("collide")
    protected abstract Vec3 plasticraft$invokeCollide(Vec3 movement);

    @Inject(method = "move", at = @At("HEAD"))
    private void plasticraft$beginCarrierMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        this.plasticraft$carrierMoveContext = new CarrierMoveContext(
            this.plasticraft$carrierMoveContext,
            self.position(),
            movement
        );
    }

    @Override
    public CarrierMoveContext plasticraft$getCarrierMoveContext() {
        return this.plasticraft$carrierMoveContext;
    }

    @ModifyReturnValue(method = "collide", at = @At("RETURN"))
    private Vec3 plasticraft$validateSteppedCarrierMovement(Vec3 actualMovement, Vec3 requestedMovement) {
        Entity self = (Entity) (Object) this;
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null || context.isRetryingCollision()) return actualMovement;
        List<CarrierMovableEntity> targets = context.targets();
        if (targets == null || targets.isEmpty()) return actualMovement;

        List<CarrierMovableEntity> blockedTargets = null;
        for (CarrierMovableEntity target : targets) {
            if (target.plasticraft$canCompleteCarrierMovement(self, actualMovement)) continue;
            if (blockedTargets == null) blockedTargets = new ArrayList<>(1);
            blockedTargets.add(target);
        }
        if (blockedTargets == null) return actualMovement;

        context.beginCollisionRetry(blockedTargets);
        try {
            return this.plasticraft$invokeCollide(requestedMovement);
        } finally {
            context.endCollisionRetry();
        }
    }

    @Inject(method = "move", at = @At("RETURN"))
    private void plasticraft$finishCarrierMove(MoverType type, Vec3 movement, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        CarrierMoveContext context = this.plasticraft$carrierMoveContext;
        if (context == null) return;
        this.plasticraft$carrierMoveContext = context.parent();
        List<CarrierMovableEntity> targets = context.targets();
        if (targets == null || targets.isEmpty()) return;
        Vec3 actualMovement = self.position().subtract(context.startPosition());
        for (CarrierMovableEntity target : targets) {
            // The RETURN hook runs after the carrier has moved, so the target is temporarily
            // offset from its support face. Completion was validated inside collide before setPos.
            target.plasticraft$moveWithCarrier(self, actualMovement);
        }
    }
}
