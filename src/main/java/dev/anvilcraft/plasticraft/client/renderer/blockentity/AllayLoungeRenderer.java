package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** 只读取休息室已同步的四面预约物,用原版物品渲染器画在水平侧面。 */
public final class AllayLoungeRenderer implements BlockEntityRenderer<AllayLoungeBlockEntity> {
    public AllayLoungeRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        AllayLoungeBlockEntity lounge,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            ItemStack stack = lounge.pickupDisplay(side);
            if (stack.isEmpty()) continue;
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(-side.toYRot()));
            poseStack.translate(0.0D, 0.0D, 0.51D);
            poseStack.scale(0.35F, 0.35F, 0.35F);
            Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                lounge.getLevel(),
                0
            );
            poseStack.popPose();
        }
    }
}
