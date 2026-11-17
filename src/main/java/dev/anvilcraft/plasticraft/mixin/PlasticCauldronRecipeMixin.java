package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.anvilcraft.plasticraft.recipe.CauldronImpactRecipeProcessor;
import dev.dubhe.anvilcraft.api.event.AnvilEvent;
import dev.dubhe.anvilcraft.event.anvil.AnvilEventListener;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(AnvilEventListener.class)
abstract class PlasticCauldronRecipeMixin {
    @WrapMethod(method = "handleNeoAnvilRecipe")
    private static void plasticraft$processCauldron(AnvilEvent.OnLand event, Operation<Void> original) {
        CauldronImpactRecipeProcessor.beginEventRecipeProcessing(event.getLevel(), event);
        try {
            original.call(event);
        } finally {
            CauldronImpactRecipeProcessor.finishEventRecipeProcessing();
        }
    }
}
