package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ctrl 中键显式请求完整塑料实体状态，普通中键仍沿用实体的初始拾取结果。 */
@Mixin(Minecraft.class)
abstract class PlasticEntityPickBlockMixin {
    @WrapOperation(
        method = "pickBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;"
                     + "getPickedResult(Lnet/minecraft/world/phys/HitResult;)Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack plasticraft$preservePlasticEntityDataWhenControlDown(
        Entity entity,
        HitResult hitResult,
        Operation<ItemStack> original
    ) {
        if (Screen.hasControlDown() && entity instanceof AbstractPlasticEntity plastic) {
            return plastic.getCompletePickResult();
        }
        return original.call(entity, hitResult);
    }
}
