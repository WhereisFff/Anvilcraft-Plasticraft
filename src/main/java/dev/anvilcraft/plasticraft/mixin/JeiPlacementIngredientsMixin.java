package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.dubhe.anvilcraft.api.block.BlockPlacementRules;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** 隔离 JEI 材料汇总与放置规则共享的物品堆，防止显示数量被写回规则。 */
@Mixin(value = BlockPlacementRules.class, remap = false)
abstract class JeiPlacementIngredientsMixin {
    @ModifyReturnValue(method = "getPlacementIngredients", at = @At("RETURN"), require = 1)
    private static List<ItemStack> plasticraft$copyPlacementIngredients(List<ItemStack> ingredients) {
        // 多方块汇总会直接 setCount；只复制列表仍会污染规则中缓存的 ItemStack。
        return ingredients.stream().map(ItemStack::copy).toList();
    }
}
