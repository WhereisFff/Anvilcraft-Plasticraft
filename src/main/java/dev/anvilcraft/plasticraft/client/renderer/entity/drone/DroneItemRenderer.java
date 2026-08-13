package dev.anvilcraft.plasticraft.client.renderer.entity.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.drone.DroneData;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.item.DroneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 四种无人机物品共用的三维渲染器:物品栏、手持、掉落物与物品展示框
 * 都经同一渲染管线显示物品数据内的两个真实螺旋桨和对应附件。
 */
public final class DroneItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static DroneItemRenderer instance;
    private DroneRenderDispatcher dispatcher;

    private DroneItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static DroneItemRenderer getInstance() {
        if (instance == null) instance = new DroneItemRenderer(Minecraft.getInstance());
        return instance;
    }

    private DroneRenderDispatcher dispatcher() {
        if (this.dispatcher == null) {
            this.dispatcher = new DroneRenderDispatcher(Minecraft.getInstance().getEntityModels()::bakeLayer);
        }
        return this.dispatcher;
    }

    @Override
    public void renderByItem(
        ItemStack stack,
        ItemDisplayContext displayContext,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        ResourceLocation toolId = stack.getItem() instanceof DroneItem droneItem
            ? droneItem.definition().id()
            : DroneToolDefinitions.CONSTRUCTION.id();
        DroneData data = DroneData.get(stack).orElse(null);
        ItemStack leftPropeller = data == null ? ItemStack.EMPTY : data.leftPropeller();
        ItemStack rightPropeller = data == null ? ItemStack.EMPTY : data.rightPropeller();

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.0D, 0.5D);
        this.dispatcher().render(
            toolId,
            leftPropeller,
            rightPropeller,
            poseStack,
            buffers,
            packedLight,
            packedOverlay
        );
        poseStack.popPose();
    }
}
