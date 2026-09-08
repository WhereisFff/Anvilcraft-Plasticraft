package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.MoldedLargeCauldronEnvironment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class LivingEntityLargeCauldronMixin {
    @Inject(method = "onClimbable", at = @At("RETURN"), cancellable = true)
    private void plasticraft$climbLargeCauldron(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && MoldedLargeCauldronEnvironment.canClimb((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
