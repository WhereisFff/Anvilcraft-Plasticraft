package dev.anvilcraft.plasticraft.client.renderer.entity.allay;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/** 把玩家打印的安全帽按模型原尺寸戴在施工悦灵头上。 */
public class AllayHardHatLayer extends RenderLayer<WorkingAllayEntity, WorkingAllayModel> {
    /** 悦灵头立方从头骨原点向上 5 px,用来把原点移到头顶。 */
    private static final float HEAD_HEIGHT = 5.0F / 16.0F;
    /** 底面再沉入头顶 1 px,看起来是戴进去而不是搁在顶上。 */
    private static final float WEAR_SINK = 1.0F / 16.0F;

    public AllayHardHatLayer(RenderLayerParent<WorkingAllayEntity, WorkingAllayModel> parent) {
        super(parent);
    }

    @Override
    public void render(
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        WorkingAllayEntity entity,
        float limbSwing,
        float limbSwingAmount,
        float partialTick,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        ItemStack hat = entity.getHardHat();
        MoldedPlasticData data = MoldedPlasticData.get(hat).orElse(null);
        if (data == null) return;
        AABB bounds = data.surfaceBounds();
        poseStack.pushPose();
        this.getParentModel().translateToHead(poseStack);
        // 头骨原点在头底;先移到头顶再沉入 1 px。Y/Z 翻转对齐实体空间,不缩放,水平中心对齐头顶中心。
        poseStack.translate(0.0F, -HEAD_HEIGHT + WEAR_SINK, 0.0F);
        poseStack.scale(1.0F, -1.0F, -1.0F);
        poseStack.translate(
            -(bounds.minX + bounds.maxX) * 0.5D,
            -bounds.minY,
            -(bounds.minZ + bounds.maxZ) * 0.5D
        );
        boolean translucent = PlasticMaterial.fromMelt(data.material())
            .map(PlasticMaterial::isTransparent)
            .orElse(false);
        MoldedPlasticMeshRenderer.render(data, poseStack, buffers, packedLight, 0xFFFFFFFF, translucent);
        poseStack.popPose();
    }
}
