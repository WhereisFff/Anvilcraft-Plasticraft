package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Ctrl 中键显式请求完整塑料实体状态，普通中键仍沿用实体的初始拾取结果。 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class PlasticEntityPickBlockMixin {
    @WrapOperation(
        method = "handlePickItemFromEntity",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;"
                     + "getPickResult()Lnet/minecraft/world/item/ItemStack;"
        )
    )
    private ItemStack plasticraft$preservePlasticEntityDataWhenControlDown(
        Entity entity,
        Operation<ItemStack> original,
        @Local(argsOnly = true) ServerboundPickItemFromEntityPacket packet
    ) {
        if (packet.includeData() && entity instanceof AbstractPlasticEntity plastic) {
            return plastic.getCompletePickResult();
        }
        return original.call(entity);
    }
}
