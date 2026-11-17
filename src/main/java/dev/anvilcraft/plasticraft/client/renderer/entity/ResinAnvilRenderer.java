package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** 持久化弹性树脂砧实体的渲染器。 */
public class ResinAnvilRenderer extends PlasticEntityRenderer<ResinAnvilEntity> {
    private static final float MODEL_SCALE = 0.99F;


    public ResinAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void extractGeometry(
        ResinAnvilEntity entity,
        float partialTick,
        PoseStack pose,
        PlasticRenderGeometry buffers,
        int packedLight
    ) {
        PlasticHammerScreen.HammerPreview preview = PlasticHammerScreen.getPreview(entity);
        if (preview != null) {
            pose.pushPose();
            PlasticEntityRenderTransforms.applyPreview(pose, entity, preview.orientation());
            scaleModel(pose, entity);
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
        scaleModel(pose, entity);
        PlasticEntityRenderHelper.renderBlock(entity, pose, buffers, packedLight);
        pose.popPose();
    }

    private static void scaleModel(PoseStack pose, ResinAnvilEntity entity) {
        Vec3 pivot = entity.plasticraft$getGeometry().rotationPivot();
        pose.translate(pivot.x, pivot.y, pivot.z);
        pose.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
        pose.translate(-pivot.x, -pivot.y, -pivot.z);
    }

}
