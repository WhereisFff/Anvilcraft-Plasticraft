package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.api.entity.PlasticGravityTypeProvider;
import dev.dubhe.anvilcraft.util.GravityManager;
import dev.dubhe.anvilcraft.util.GravityType;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GravityManager.class, remap = false)
abstract class GravityManagerMixin {
    @Inject(
        method = "getGravityType(Lnet/minecraft/world/entity/Entity;)Ldev/dubhe/anvilcraft/util/GravityType;",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void plasticraft$useProvidedGravityType(
        Entity entity,
        CallbackInfoReturnable<GravityType> cir
    ) {
        if (!(entity instanceof PlasticGravityTypeProvider provider)) return;
        GravityType gravityType = provider.plasticraft$getGravityType();
        if (gravityType != null) cir.setReturnValue(gravityType);
    }
}
