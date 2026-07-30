package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** 无需放置方块即可渲染已同步的皇家铁砧形方块状态。 */
public class HardenedResinAnvilRenderer extends EntityRenderer<HardenedResinAnvilEntity> {
    private final BlockRenderDispatcher dispatcher;

    public HardenedResinAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        HardenedResinAnvilEntity entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        PlasticHammerScreen.HammerPreview preview = PlasticHammerScreen.getPreview(entity);
        if (preview != null) {
            pose.pushPose();
            PlasticEntityRenderTransforms.applyPreview(pose, entity, preview.orientation());
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                entity,
                this.dispatcher,
                pose,
                buffers,
                preview.valid()
            );
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
    public ResourceLocation getTextureLocation(HardenedResinAnvilEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

}
