package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 在所有配方缓存提交后把目标颜色写回容器。 */
@Mixin(InWorldRecipeContext.class)
abstract class InWorldRecipeContextMixin {
    @Inject(method = "accept", at = @At("RETURN"))
    private void plasticraft$applyMeltColor(CallbackInfo ci) {
        PlasticMeltRecipeColor.apply((InWorldRecipeContext) (Object) this);
    }
}
