package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.dubhe.anvilcraft.client.init.ModRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.FallingBlockEntity;

/** 实体化塑料制品共用的方块模型渲染逻辑。 */
public final class PlasticEntityRenderHelper {
    private static final ModelResourceLocation HAMMER_AXIS_MODEL = ModelResourceLocation.standalone(
        AnvilCraft.of("block/axis")
    );

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

    /** 使用本体铁砧锤的蓝色半透明材质渲染方向预览。 */
    public static void renderHammerPreviewModel(
        AbstractPlasticEntity entity,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        BakedModel model = dispatcher.getBlockModel(state);
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            consumer,
            state,
            model,
            1.0F,
            1.0F,
            1.0F,
            LightTexture.FULL_BLOCK,
            OverlayTexture.NO_OVERLAY
        );
    }

    /** 使用同一蓝色半透明材质渲染普通下落方块的终点虚影。 */
    public static void renderFallingPreviewModel(
        FallingBlockEntity entity,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        BlockState state = entity.getBlockState();
        BakedModel model = dispatcher.getBlockModel(state);
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            consumer,
            state,
            model,
            1.0F,
            1.0F,
            1.0F,
            LightTexture.FULL_BLOCK,
            OverlayTexture.NO_OVERLAY
        );
    }

    /** 渲染固定在世界坐标轴上的蓝色方向轴。 */
    public static void renderHammerAxis(
        AbstractPlasticEntity entity,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY);
        BakedModel axis = Minecraft.getInstance().getModelManager().getModel(HAMMER_AXIS_MODEL);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            consumer,
            state,
            axis,
            1.0F,
            1.0F,
            1.0F,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY
        );
    }
}
