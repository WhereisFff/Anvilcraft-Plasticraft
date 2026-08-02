package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticOilCatalysisRenderer;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import dev.dubhe.anvilcraft.block.entity.LargeCauldronBlockEntity;
import dev.dubhe.anvilcraft.client.renderer.blockentity.LargeCauldronBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 渲染大型炼药锅内的高热燃料火焰与塑料油催化渐变。 */
@Mixin(LargeCauldronBlockEntityRenderer.class)
abstract class LargeCauldronBlockEntityRendererMixin {
    @Unique private static final float plasticraft$CONTENT_MIN_XZ = -0.75F + 0.001F;
    @Unique private static final float plasticraft$CONTENT_MAX_XZ = 1.75F - 0.001F;
    @Unique private static final float plasticraft$CONTENT_MIN_Y = -0.5F + 0.001F;
    @Unique private static final float plasticraft$CONTENT_HEIGHT = 2.25F;
    @Unique private static final float plasticraft$FLAME_SCALE = 3.0F;

    @Redirect(
        method = """
            render(Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;F\
            Lcom/mojang/blaze3d/vertex/PoseStack;\
            Lnet/minecraft/client/renderer/MultiBufferSource;II)V""",
        at = @At(
            value = "INVOKE",
            target = "Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;isIgnited()Z"
        )
    )
    private boolean plasticraft$showOrdinaryFire(LargeCauldronBlockEntity cauldron) {
        return cauldron.isIgnited() && !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid());
    }

    @Inject(
        method = """
            render(Ldev/dubhe/anvilcraft/block/entity/LargeCauldronBlockEntity;F\
            Lcom/mojang/blaze3d/vertex/PoseStack;\
            Lnet/minecraft/client/renderer/MultiBufferSource;II)V""",
        at = @At("TAIL")
    )
    private void plasticraft$renderFluidEffects(
        LargeCauldronBlockEntity cauldron,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay,
        CallbackInfo ci
    ) {
        if (!cauldron.isMainPart() || cauldron.getLevel() == null) return;

        float layerMinY = plasticraft$CONTENT_MIN_Y;
        for (int tank = 0; tank < cauldron.getFluids().getTanks(); tank++) {
            FluidStack fluid = cauldron.getFluids().getFluidInTank(tank);
            if (fluid.isEmpty()) continue;
            float layerMaxY = layerMinY
                + plasticraft$CONTENT_HEIGHT * fluid.getAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY;
            if (fluid.is(PlasticraftFluids.PLASTIC_OIL.get())) {
                PlasticOilCatalysisRenderer.renderContainerOverlay(
                    cauldron.getLevel(),
                    cauldron.getBlockPos(),
                    partialTick,
                    fluid,
                    plasticraft$CONTENT_MIN_XZ,
                    layerMinY,
                    plasticraft$CONTENT_MIN_XZ,
                    plasticraft$CONTENT_MAX_XZ,
                    layerMaxY,
                    plasticraft$CONTENT_MAX_XZ,
                    buffers,
                    poseStack,
                    packedLight,
                    true
                );
            }
            layerMinY = layerMaxY;
        }

        if (!cauldron.isIgnited() || !IgnitedFluidEffects.isHighHeatFuel(cauldron.getTopFluid())) return;
        float fill = Mth.clamp(
            (float) cauldron.getFluids().getTotalAmount() / LargeCauldronFluidHandler.TOTAL_CAPACITY,
            0.0F,
            1.0F
        );
        IgnitedFluidFlameRenderer.renderBlue(
            poseStack,
            buffers,
            plasticraft$CONTENT_MIN_Y + plasticraft$CONTENT_HEIGHT * fill,
            plasticraft$FLAME_SCALE,
            packedOverlay
        );
    }
}
