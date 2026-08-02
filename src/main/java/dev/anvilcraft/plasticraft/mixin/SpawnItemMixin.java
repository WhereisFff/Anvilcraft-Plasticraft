package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.lib.v2.recipe.outcome.SpawnItem;
import dev.anvilcraft.lib.v2.recipe.util.InWorldRecipeContext;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.recipe.PlasticMeltRecipeColor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 让固液冷却生成的单一塑料粒物品继承熔体颜色。 */
@Mixin(SpawnItem.class)
abstract class SpawnItemMixin {
    @Redirect(
        method = "accept",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;copyWithCount(I)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack plasticraft$colorPlasticGranules(
        ItemStack original,
        int count,
        InWorldRecipeContext context
    ) {
        ItemStack result = original.copyWithCount(count);
        if (result.is(PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get())) {
            PlasticMeltColor.set(result, PlasticMeltRecipeColor.resultColor(context));
        }
        return result;
    }
}
