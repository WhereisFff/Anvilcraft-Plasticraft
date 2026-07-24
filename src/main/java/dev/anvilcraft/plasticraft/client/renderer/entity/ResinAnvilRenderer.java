package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** 持久化弹性树脂砧实体的渲染器。 */
public class ResinAnvilRenderer extends EntityRenderer<ResinAnvilEntity> {
    private final BlockRenderDispatcher dispatcher;

    public ResinAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        ResinAnvilEntity entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        PlasticEntityOrientation preview = PlasticHammerScreen.getPreviewOrientation(entity);
        if (preview != null) {
            pose.pushPose();
            PlasticEntityRenderTransforms.applyPreview(pose, entity, preview);
            PlasticEntityRenderHelper.renderHammerPreviewModel(entity, this.dispatcher, pose, buffers);
            pose.popPose();
            pose.pushPose();
            PlasticEntityRenderTransforms.applyWorldAlignedPreview(pose, entity);
            PlasticEntityRenderHelper.renderHammerAxis(entity, this.dispatcher, pose, buffers);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
            return;
        }
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        PlasticEntityRenderHelper.renderBlock(entity, this.dispatcher, pose, buffers, packedLight);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(ResinAnvilEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
