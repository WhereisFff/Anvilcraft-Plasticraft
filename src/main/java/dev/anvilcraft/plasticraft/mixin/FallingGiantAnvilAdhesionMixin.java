package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFallingBlockBehavior;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 巨型铁砧覆盖了原版 tick，因此单独接入树脂粘附生命周期。 */
@Mixin(FallingGiantAnvilEntity.class)
abstract class FallingGiantAnvilAdhesionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$holdAdhesiveControlledGiantAnvil(CallbackInfo callback) {
        FallingGiantAnvilEntity entity = (FallingGiantAnvilEntity) (Object) this;
        if (AdhesiveFallingBlockBehavior.beforeTick(entity)) callback.cancel();
    }
}
