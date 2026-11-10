package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.CondenserTowerBlock;
import dev.dubhe.anvilcraft.client.event.LargeBlockPlacePreviewEventListener;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 冷凝塔由塑料工艺的预览配置控制，避免本体再绘制一次。 */
@Mixin(value = LargeBlockPlacePreviewEventListener.class, remap = false)
abstract class PlasticPlacementPreviewMixin {
    @Inject(method = "isPreviewable", at = @At("HEAD"), cancellable = true)
    private static void plasticraft$usePlacementPreview(Block block, CallbackInfoReturnable<Boolean> callback) {
        if (block instanceof CondenserTowerBlock) {
            callback.setReturnValue(false);
        }
    }
}
