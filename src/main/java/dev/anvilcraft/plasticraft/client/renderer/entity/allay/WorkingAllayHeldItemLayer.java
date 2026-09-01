package dev.anvilcraft.plasticraft.client.renderer.entity.allay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 主手走原版 {@code ItemInHandLayer} 右手路径。
 * 切石机再绕左右轴转 180°,把朝向自己/头的锯片翻成朝前下方;翻转后第三人称 Y 抬升会掉到手下,再沿手持 +Y 抬回握点。
 * 蟹钳再绕朝前轴转 +90°,橙身在上、深色钳口朝下张开;有托管物时换张开模型,并按同一套第三人称 display 把物品放进钳口。
 */
public class WorkingAllayHeldItemLayer extends RenderLayer<WorkingAllayEntity, WorkingAllayModel> {
    private static final ModelResourceLocation CRAB_CLAW_HOLDING_BLOCK = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath("anvilcraft", "item/crab_claw_holding_block")
    );
    private static final ModelResourceLocation CRAB_CLAW_HOLDING_ITEM = ModelResourceLocation.standalone(
        ResourceLocation.fromNamespaceAndPath("anvilcraft", "item/crab_claw_holding_item")
    );
    /** 180°X 之后本地 +Y 已反向,负值才是抬向双手。 */
    private static final float STONECUTTER_LIFT = -0.35F;
    /** +90°Z 把本地 +X 映成手持 +Y,正值抬向双手。 */
    private static final float CRAB_CLAW_LIFT = 0.20F;
    /** +90°Z 之后本地 +Y 映成手持 -X,用来把偏到外侧的钳身收回握点。 */
    private static final float CRAB_CLAW_CENTER = 0.13F;
    /** 与原版手持 -0.625 Z 同轴,正值把钳身收回胸前,看起来像抱着。 */
    private static final float CRAB_CLAW_HUG = 0.12F;

    private final ItemInHandRenderer itemInHandRenderer;
    private final ItemRenderer itemRenderer;

    public WorkingAllayHeldItemLayer(
        RenderLayerParent<WorkingAllayEntity, WorkingAllayModel> parent,
        ItemInHandRenderer itemInHandRenderer,
        ItemRenderer itemRenderer
    ) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
        this.itemRenderer = itemRenderer;
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
        ItemStack held = entity.getMainHandItem();
        ItemStack carry = entity.hostedCarry();
        if (held.isEmpty()) {
            if (carry.isEmpty()) return;
            this.renderVanillaHeld(poseStack, buffers, packedLight, entity, carry, false);
            return;
        }
        if (held.is(ModItems.CRAB_CLAW.get())) {
            this.renderCrabClaw(poseStack, buffers, packedLight, entity, held, carry);
            return;
        }
        this.renderVanillaHeld(poseStack, buffers, packedLight, entity, held, held.is(Items.STONECUTTER));
    }

    private void applyVanillaHand(PoseStack poseStack) {
        this.getParentModel().translateToHand(HumanoidArm.RIGHT, poseStack);
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.translate(1.0F / 16.0F, 0.125F, -0.625F);
    }

    private void renderVanillaHeld(
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        WorkingAllayEntity entity,
        ItemStack stack,
        boolean flipSawForwardDown
    ) {
        poseStack.pushPose();
        this.applyVanillaHand(poseStack);
        if (flipSawForwardDown) {
            poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
            poseStack.translate(0.0F, STONECUTTER_LIFT, 0.0F);
        }
        this.itemInHandRenderer.renderItem(
            entity,
            stack,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            false,
            poseStack,
            buffers,
            packedLight
        );
        poseStack.popPose();
    }

    private void renderCrabClaw(
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        WorkingAllayEntity entity,
        ItemStack claw,
        ItemStack carry
    ) {
        poseStack.pushPose();
        this.applyVanillaHand(poseStack);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.translate(CRAB_CLAW_LIFT, CRAB_CLAW_CENTER, CRAB_CLAW_HUG);
        if (carry.isEmpty()) {
            this.itemInHandRenderer.renderItem(
                entity,
                claw,
                ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                false,
                poseStack,
                buffers,
                packedLight
            );
            poseStack.popPose();
            return;
        }
        boolean holdingBlock = isBlockCarry(entity, carry);
        BakedModel openClaw = Minecraft.getInstance().getModelManager().getModel(
            holdingBlock ? CRAB_CLAW_HOLDING_BLOCK : CRAB_CLAW_HOLDING_ITEM
        );
        this.itemRenderer.render(
            claw,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            false,
            poseStack,
            buffers,
            packedLight,
            OverlayTexture.NO_OVERLAY,
            openClaw
        );
        // 钳口物品必须走同一套第三人称 display,否则会和张开模型不在一个空间里,看起来歪、穿模。
        poseStack.translate(0.0F, -2.0F / 16.0F, -2.0F / 16.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(-90.0F));
        poseStack.scale(0.8F, 0.8F, 0.8F);
        if (holdingBlock) {
            poseStack.translate(0.0F, 0.04F, -0.22F);
            poseStack.scale(0.4F, 0.4F, 0.4F);
        } else {
            poseStack.translate(0.0F, 0.02F, -0.16F);
            poseStack.scale(0.5F, 0.5F, 0.5F);
        }
        this.itemInHandRenderer.renderItem(
            entity,
            carry,
            ItemDisplayContext.NONE,
            false,
            poseStack,
            buffers,
            packedLight
        );
        poseStack.popPose();
    }

    private static boolean isBlockCarry(WorkingAllayEntity entity, ItemStack carry) {
        BakedModel model = Minecraft.getInstance().getItemRenderer()
            .getModel(carry, entity.level(), entity, 0);
        return model.isGui3d() && carry.getItem() instanceof BlockItem;
    }
}
