package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/** 实体化塑料制品共用的方块模型渲染逻辑。 */
public final class PlasticEntityRenderHelper {
    private PlasticEntityRenderHelper() {
    }

    public static void renderBlock(
        AbstractPlasticEntity entity,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        BakedModel model = dispatcher.getBlockModel(state);
        RenderType renderType = entity instanceof ResinAnvilEntity
            ? Sheets.translucentItemSheet()
            : Sheets.cutoutBlockSheet();
        VertexConsumer consumer = buffers.getBuffer(renderType);
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
