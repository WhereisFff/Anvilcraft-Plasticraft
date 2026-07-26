package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;

/** 分别渲染压盖本体和随加工进度抬升的中柱。 */
public final class CatalyticPressLidRenderer extends EntityRenderer<CatalyticPressLidEntity> {
    public static final ModelResourceLocation ARM_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/catalytic_press_lid_arm")
    );
    private final BlockRenderDispatcher dispatcher;

    public CatalyticPressLidRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        CatalyticPressLidEntity entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        PlasticEntityRenderHelper.renderBlock(entity, this.dispatcher, pose, buffers, packedLight);

        pose.pushPose();
        pose.translate(0.0D, entity.armLift(partialTick), 0.0D);
        if (entity.isReady() && entity.pressAnimationProgress(partialTick) == 0.0F) {
            float phase = (entity.level().getGameTime() + partialTick) * 0.65F;
            float wobble = Mth.sin(phase) * 1.4F;
            pose.translate(0.5D, 0.0D, 0.5D);
            pose.mulPose(Axis.XP.rotationDegrees(wobble));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.cos(phase * 0.87F) * 1.2F));
            pose.translate(-0.5D, 0.0D, -0.5D);
        }
        this.dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            entity.getDisplayState(),
            Minecraft.getInstance().getModelManager().getModel(ARM_MODEL),
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
        pose.popPose();
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CatalyticPressLidEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
