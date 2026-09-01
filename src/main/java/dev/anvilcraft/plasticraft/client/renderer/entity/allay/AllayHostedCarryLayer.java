package dev.anvilcraft.plasticraft.client.renderer.entity.allay;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import dev.dubhe.anvilcraft.init.item.ModItems;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 任务托管携带物画在身体前方,与主手工具分开。 */
public class AllayHostedCarryLayer extends RenderLayer<WorkingAllayEntity, WorkingAllayModel> {
    private final ItemInHandRenderer itemInHandRenderer;

    public AllayHostedCarryLayer(
        RenderLayerParent<WorkingAllayEntity, WorkingAllayModel> parent,
        ItemInHandRenderer itemInHandRenderer
    ) {
        super(parent);
        this.itemInHandRenderer = itemInHandRenderer;
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
        ItemStack carry = entity.hostedCarry();
        if (carry.isEmpty()) return;
        // 空手画在手里,蟹钳夹在钳口,都不再在身前另画一份。
        if (entity.getMainHandItem().isEmpty() || entity.getMainHandItem().is(ModItems.CRAB_CLAW.get())) {
            return;
        }
        poseStack.pushPose();
        this.getParentModel().root().getChild("body").translateAndRotate(poseStack);
        poseStack.translate(0.0D, 0.12D, -0.18D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F));
        poseStack.scale(0.35F, 0.35F, 0.35F);
        this.itemInHandRenderer.renderItem(
            entity,
            carry,
            ItemDisplayContext.GROUND,
            false,
            poseStack,
            buffers,
            packedLight
        );
        poseStack.popPose();
    }
}
