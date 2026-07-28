package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.event.anvil.AnvilEventListener;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将一个落砧格内的多个实体锅拆成彼此隔离的配方批次。 */
@Mixin(AnvilEventListener.class)
abstract class AnvilEventListenerMixin {
    @Inject(method = "handleNeoAnvilRecipe", at = @At("HEAD"))
    private static void plasticraft$processEachTargetedCauldron(AnvilEvent.OnLand event, CallbackInfo ci) {
        if (event.getLevel() instanceof ServerLevel level) {
            CauldronImpactRecipeProcessor.beginEventRecipeProcessing(level, event);
        }
    }

    @Inject(method = "handleNeoAnvilRecipe", at = @At("RETURN"))
    private static void plasticraft$finishTargetedCauldrons(AnvilEvent.OnLand event, CallbackInfo ci) {
        if (event.getLevel() instanceof ServerLevel) {
            CauldronImpactRecipeProcessor.finishEventRecipeProcessing();
        }
    }
}
