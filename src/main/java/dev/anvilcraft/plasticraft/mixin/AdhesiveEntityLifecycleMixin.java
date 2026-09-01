package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class AdhesiveEntityLifecycleMixin {
    @Inject(method = "remove", at = @At("TAIL"))
    private void plasticraft$clearAdhesiveLifecycle(Entity.RemovalReason reason, CallbackInfo callback) {
        AdhesiveBondingService.onEntityRemoved((Entity) (Object) this);
    }
}
