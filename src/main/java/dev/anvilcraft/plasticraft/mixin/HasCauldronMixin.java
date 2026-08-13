package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import dev.dubhe.anvilcraft.recipe.anvil.predicate.block.HasCauldron;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在炼药锅谓词修改流体前保存熔体颜色。 */
@Mixin(HasCauldron.class)
abstract class HasCauldronMixin {
    @Inject(method = "accept", at = @At("HEAD"))
    private void plasticraft$captureMeltColor(InWorldRecipeContext context, CallbackInfo ci) {
        PlasticMeltRecipeColor.captureCauldron(context, ((HasCauldron) (Object) this).offset());
    }
}
