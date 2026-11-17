package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** 无需放置方块即可渲染已同步的皇家铁砧形方块状态。 */
public class HardenedResinAnvilRenderer extends PlasticEntityRenderer<HardenedResinAnvilEntity> {


    public HardenedResinAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractGeometry(
        HardenedResinAnvilEntity entity,
        float partialTick,
        PoseStack pose,
        PlasticRenderGeometry buffers,
        int packedLight
    ) {
        PlasticHammerScreen.HammerPreview preview = PlasticHammerScreen.getPreview(entity);
        if (preview != null) {
            pose.pushPose();
            PlasticEntityRenderTransforms.applyPreview(pose, entity, preview.orientation());
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                entity,
                pose,
                buffers,
                preview.valid()
            );
            pose.popPose();
            pose.pushPose();
            PlasticEntityRenderTransforms.applyWorldAlignedPreview(pose, entity);
            PlasticEntityRenderHelper.renderHammerAxis(entity, pose, buffers);
            pose.popPose();
            return;
        }
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        PlasticEntityRenderHelper.renderBlock(entity, pose, buffers, packedLight);
        pose.popPose();
    }


}
