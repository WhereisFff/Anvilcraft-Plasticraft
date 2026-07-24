package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.client.renderer.HighHeatFuelFlameRenderer;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.client.renderer.blockentity.LargeCauldronBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 将大型炼药锅内高热燃料的普通火焰替换为蓝白色火焰。 */
@Mixin(LargeCauldronBlockEntityRenderer.class)
abstract class LargeCauldronBlockEntityRendererMixin {
    @Unique private static final float plasticraft$CONTENT_MIN_Y = -0.5F + 0.001F;
    @Unique private static final float plasticraft$CONTENT_HEIGHT = 2.25F;
    @Unique private static final float plasticraft$FLAME_SURFACE_HALF_WIDTH = 1.25F;

    @Redirect(
        method = "render(Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At(
            value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;isIgnited()Z"
        )
    )
    private boolean plasticraft$showOrdinaryFire(LargeCauldronBlockEntity cauldron) {
        return cauldron.isIgnited() && !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid());
    }

    @Inject(
        method = "render(Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;F"
            + "Lcom/mojang/blaze3d/vertex/PoseStack;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
        at = @At("TAIL")
    )
    private void plasticraft$renderHighHeatFuelFlame(
        LargeCauldronBlockEntity cauldron,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay,
        CallbackInfo ci
    ) {
        if (!cauldron.isMainPart()
            || cauldron.getLevel() == null
            || !cauldron.isIgnited()
            || !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid())) {
            return;
        }
        float fill = Mth.clamp(
            (float) cauldron.getFluids().getTotalAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY,
            0.0F,
            1.0F
        );
        HighHeatFuelFlameRenderer.render(
            poseStack,
            buffers,
            plasticraft$CONTENT_MIN_Y + plasticraft$CONTENT_HEIGHT * fill,
            plasticraft$FLAME_SURFACE_HALF_WIDTH,
            cauldron.getLevel().getGameTime() + partialTick,
            cauldron.getBlockPos().asLong()
        );
    }
}
