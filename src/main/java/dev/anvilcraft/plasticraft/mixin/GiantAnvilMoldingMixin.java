package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.molding.machine.PlasticMoldingAnvilProcessor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.event.giantanvil.GiantAnvilLandingEventListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让成型舱专用结构先于 AnvilCraft 静态多方块配方处理。 */
@Mixin(GiantAnvilLandingEventListener.class)
abstract class GiantAnvilMoldingMixin {
    @Inject(method = "handleMultiblock", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$handleDynamicMolding(AnvilEvent.GiantOnLand event, CallbackInfo callback) {
        if (PlasticMoldingAnvilProcessor.handleLanding(event)) callback.cancel();
    }
}
