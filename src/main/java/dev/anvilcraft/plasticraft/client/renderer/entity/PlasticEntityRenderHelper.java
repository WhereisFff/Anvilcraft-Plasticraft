package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/** Shared block-model rendering, including the visible magnetic glint. */
public final class PlasticEntityRenderHelper {
    private PlasticEntityRenderHelper() {
    }

    public static void renderBlock(
        AbstractPlasticAnvilEntity entity,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        BlockState state = PlasticAnvilRenderTransforms.canonicalize(entity.getDisplayState());
        BakedModel model = dispatcher.getBlockModel(state);
        RenderType renderType = ItemBlockRenderTypes.getRenderType(state, false);
        VertexConsumer consumer = ItemRenderer.getFoilBuffer(
            buffers,
            renderType,
            true,
            entity.isMagnetized()
        );
        int tint = entity.getDisplayTint();
        float red = (float) (tint >> 16 & 0xFF) / 255.0F;
        float green = (float) (tint >> 8 & 0xFF) / 255.0F;
        float blue = (float) (tint & 0xFF) / 255.0F;
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            consumer,
            state,
            model,
            red,
            green,
            blue,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
    }
}
