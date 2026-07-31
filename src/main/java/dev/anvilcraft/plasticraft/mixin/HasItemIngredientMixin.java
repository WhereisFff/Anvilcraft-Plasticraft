package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.predicate.item.HasItemIngredient;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import net.minecraft.world.item.DyeItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 从实际匹配的固体原料中读取染料颜色。 */
@Mixin(HasItemIngredient.class)
abstract class HasItemIngredientMixin {
    @Inject(method = "accept", at = @At("HEAD"))
    private void plasticraft$captureDyeColor(InWorldRecipeContext context, CallbackInfo ci) {
        HasItemIngredient ingredient = (HasItemIngredient) (Object) this;
        ingredient.getItem(context).apply(stack -> {
            if (stack.getItem() instanceof DyeItem dye) {
                PlasticMeltRecipeColor.captureDye(context, dye.getDyeColor());
            }
        });
    }
}
