package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.cache.item.ICacheInput;
import dev.anvilcraft.lib.v2.recipe.predicate.item.HasItemIngredient;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/** 从实际匹配的固体原料中读取染料颜色。 */
@Mixin(HasItemIngredient.class)
abstract class HasItemIngredientMixin {
    @Inject(method = "accept", at = @At("HEAD"))
    private void plasticraft$captureDyeColor(InWorldRecipeContext context, CallbackInfo ci) {
        HasItemIngredient ingredient = (HasItemIngredient) (Object) this;
        ICacheInput input = ingredient.getItem(context);
        Consumer<ItemStack> capture = stack -> {
            if (stack.getItem() instanceof DyeItem dye) {
                PlasticMeltRecipeColor.captureDye(context, dye.getDyeColor());
            }
        };
        // 预检已经扣除了模拟库存，实际染料必须从尚未认领的消耗收据读取。
        if (input.supportsConsumptionReceipts()) input.getConsumedItems().forEach(capture);
        else input.apply(capture);
    }
}
