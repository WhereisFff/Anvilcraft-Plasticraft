package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;

/** 使用营火动画贴图绘制可燃流体表面的火焰。 */
public final class IgnitedFluidFlameRenderer {
    public static final PlasticBlockModel ORDINARY_FLAME_MODEL = new PlasticBlockModel(
        Identifier.fromNamespaceAndPath("anvilcraft", "block/fire_cauldron_fire4")
    );
    public static final PlasticBlockModel BLUE_FLAME_MODEL = new PlasticBlockModel(
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
        PlasticBlockModel modelLocation,
        float modelSurfaceY
    ) {
        BlockStateModel model = modelLocation.baked();
        float centerOffset = (1.0F - scale) * 0.5F;
        poseStack.pushPose();
        poseStack.translate(centerOffset, surfaceY - modelSurfaceY * scale, centerOffset);
        poseStack.scale(scale, scale, scale);
        PlasticBlockModel.render(
            poseStack.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            model,
            1.0F,
            1.0F,
            1.0F,
            LightCoordsUtil.FULL_BRIGHT,
            packedOverlay
        );
        poseStack.popPose();
    }
}
