package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.model.data.ModelData;

/** 使用营火动画贴图绘制可燃流体表面的火焰。 */
public final class IgnitedFluidFlameRenderer {
    private static final ModelResourceLocation ORDINARY_FLAME_MODEL = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath("anvilcraft", "block/fire_cauldron_fire4")
    );
    public static final ModelResourceLocation BLUE_FLAME_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/blue_fire")
    );
    private static final float ORDINARY_MODEL_SURFACE_Y = 15.0F / 16.0F - 0.001F;
    private static final float BLUE_MODEL_SURFACE_Y = -0.001F;
    private static final float BLUE_FLAME_SCALE = 1.1F;

    private IgnitedFluidFlameRenderer() {
    }

    public static void renderOrdinary(
        PoseStack poseStack,
        MultiBufferSource buffers,
        float surfaceY,
        float scale,
        int packedOverlay
    ) {
        render(
            poseStack,
            buffers,
            surfaceY,
            scale,
            packedOverlay,
            ORDINARY_FLAME_MODEL,
            ORDINARY_MODEL_SURFACE_Y
        );
    }

    public static void renderBlue(
        PoseStack poseStack,
        MultiBufferSource buffers,
        float surfaceY,
        float scale,
        int packedOverlay
    ) {
        render(
            poseStack,
            buffers,
            surfaceY,
            scale * BLUE_FLAME_SCALE,
            packedOverlay,
            BLUE_FLAME_MODEL,
            BLUE_MODEL_SURFACE_Y
        );
    }

    private static void render(
        PoseStack poseStack,
        MultiBufferSource buffers,
        float surfaceY,
        float scale,
        int packedOverlay,
        ModelResourceLocation modelLocation,
        float modelSurfaceY
    ) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModelShaper().getModelManager().getModel(modelLocation);
        float centerOffset = (1.0F - scale) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(centerOffset, surfaceY - modelSurfaceY * scale, centerOffset);
        poseStack.scale(scale, scale, scale);
        dispatcher.getModelRenderer().renderModel(
            poseStack.last(),
            buffers.getBuffer(RenderType.CUTOUT),
            null,
            model,
            1.0F,
            1.0F,
            1.0F,
            LightTexture.FULL_BRIGHT,
            packedOverlay,
            ModelData.EMPTY,
            RenderType.cutout()
        );
        poseStack.popPose();
    }
}
