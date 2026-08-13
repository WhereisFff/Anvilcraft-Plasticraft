package dev.anvilcraft.plasticraft.client.renderer.entity.drone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.drone.tool.DroneToolDefinitions;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 实体与物品共用的无人机渲染管线:同一主体模型、按工具 ID 选择的附件层,
 * 以及两个保存完整物品堆的螺旋桨。物品形态与实体形态不能出现不同的简化外观,
 * 因此两个渲染器都必须经过这里,不各自绘制。
 */
public final class DroneRenderDispatcher {
    public static final ResourceLocation DRONE_TEXTURE =
        AnvilcraftPlasticraft.of("textures/entity/drone.png");
    /** 螺旋桨轴心在成型建模空间的水平中心(24 像素处)。 */
    private static final double PROPELLER_PIVOT = 1.5D;
    /** 双桨间距一格,桨盘最大直径一格;缩小到 0.75 避免桨叶互相接触。 */
    private static final float PROPELLER_SCALE = 0.75F;
    /** 螺旋桨锚点顶面相对实体底部的高度(11 像素)。 */
    private static final double PROPELLER_ANCHOR_HEIGHT = 11.0D / 16.0D;
    private static final Map<ResourceLocation, AttachmentEntry> ATTACHMENTS = new LinkedHashMap<>();

    static {
        registerAttachment(
            DroneToolDefinitions.CONSTRUCTION.id(),
            DroneToolAttachmentModel.CONSTRUCTION_CLAW_LAYER,
            AnvilcraftPlasticraft.of("textures/entity/drone/tool/construction_claw.png")
        );
        registerAttachment(
            DroneToolDefinitions.DEMOLITION.id(),
            DroneToolAttachmentModel.DEMOLITION_STONECUTTER_LAYER,
            AnvilcraftPlasticraft.of("textures/entity/drone/tool/demolition_stonecutter.png")
        );
        registerAttachment(
            DroneToolDefinitions.COLLECTION.id(),
            DroneToolAttachmentModel.COLLECTION_MAGNET_LAYER,
            AnvilcraftPlasticraft.of("textures/entity/drone/tool/collection_magnet.png")
        );
        registerAttachment(
            DroneToolDefinitions.OBSERVATION.id(),
            DroneToolAttachmentModel.OBSERVATION_SPYGLASS_LAYER,
            AnvilcraftPlasticraft.of("textures/entity/drone/tool/observation_spyglass.png")
        );
    }

    private final DroneModel model;
    private final Map<ResourceLocation, BakedAttachment> attachments;

    public DroneRenderDispatcher(Function<ModelLayerLocation, ModelPart> bakery) {
        this.model = new DroneModel(bakery.apply(DroneModel.LAYER));
        this.attachments = new LinkedHashMap<>();
        synchronized (ATTACHMENTS) {
            for (Map.Entry<ResourceLocation, AttachmentEntry> entry : ATTACHMENTS.entrySet()) {
                this.attachments.put(entry.getKey(), new BakedAttachment(
                    new DroneToolAttachmentModel(bakery.apply(entry.getValue().layer())),
                    entry.getValue().texture()
                ));
            }
        }
    }

    /** 供未来工具定义在客户端补充自己的附件层与贴图。 */
    public static void registerAttachment(
        ResourceLocation toolId,
        ModelLayerLocation layer,
        ResourceLocation texture
    ) {
        synchronized (ATTACHMENTS) {
            if (ATTACHMENTS.putIfAbsent(toolId, new AttachmentEntry(layer, texture)) != null) {
                throw new IllegalStateException("Duplicate drone attachment for tool " + toolId);
            }
        }
    }

    /**
     * 渲染完整无人机。姿态栈需要位于实体底部中心、Y 轴向上并已按实体朝向旋转;
     * 模型前方是本地 -Z;螺旋桨转角由调用方按同步的飞行状态计算,双桨反向。
     */
    public void render(
        ResourceLocation toolId,
        ItemStack leftPropeller,
        ItemStack rightPropeller,
        float propellerSpinDegrees,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        this.render(
            toolId,
            leftPropeller,
            rightPropeller,
            ItemStack.EMPTY,
            propellerSpinDegrees,
            poseStack,
            buffers,
            packedLight,
            packedOverlay
        );
    }

    public void render(
        ResourceLocation toolId,
        ItemStack leftPropeller,
        ItemStack rightPropeller,
        ItemStack carried,
        float propellerSpinDegrees,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        this.renderPropeller(leftPropeller, true, propellerSpinDegrees, poseStack, buffers, packedLight);
        this.renderPropeller(rightPropeller, false, -propellerSpinDegrees, poseStack, buffers, packedLight);

        poseStack.pushPose();
        // 实体模型惯例:Y 向下建模,渲染时翻转并抬升 1.5 格。
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.translate(0.0F, -1.5F, 0.0F);
        this.model.setupIdlePose();
        VertexConsumer bodyBuffer = buffers.getBuffer(RenderType.entityCutoutNoCull(DRONE_TEXTURE));
        this.model.render(poseStack, bodyBuffer, packedLight, packedOverlay);

        BakedAttachment attachment = this.attachments.get(toolId);
        if (attachment != null) {
            poseStack.pushPose();
            this.model.translateToToolMount(poseStack);
            VertexConsumer attachmentBuffer =
                buffers.getBuffer(RenderType.entityCutoutNoCull(attachment.texture()));
            attachment.model().render(poseStack, attachmentBuffer, packedLight, packedOverlay);
            if (!carried.isEmpty()) {
                poseStack.pushPose();
                attachment.model().translateToHeldItem(poseStack);
                poseStack.scale(0.4F, 0.4F, 0.4F);
                Minecraft.getInstance().getItemRenderer().renderStatic(
                    carried,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    buffers,
                    null,
                    0
                );
                poseStack.popPose();
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private void renderPropeller(
        ItemStack propeller,
        boolean left,
        float spinDegrees,
        PoseStack poseStack,
        MultiBufferSource buffers,
        int packedLight
    ) {
        MoldedPlasticData data = MoldedPlasticData.get(propeller).orElse(null);
        if (data == null) return;
        poseStack.pushPose();
        poseStack.translate(left ? -0.5D : 0.5D, PROPELLER_ANCHOR_HEIGHT, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        poseStack.scale(PROPELLER_SCALE, PROPELLER_SCALE, PROPELLER_SCALE);
        // 螺旋桨形状校验保证桨盘围绕建模空间中心,把轴心平移到锚点、桨底贴住毂顶。
        AABB bounds = data.surfaceBounds();
        poseStack.translate(-PROPELLER_PIVOT, -bounds.minY, -PROPELLER_PIVOT);
        MoldedPlasticMeshRenderer.render(data, poseStack, buffers, packedLight, 0xFFFFFFFF, false);
        poseStack.popPose();
    }

    private record AttachmentEntry(ModelLayerLocation layer, ResourceLocation texture) {
    }

    private record BakedAttachment(DroneToolAttachmentModel model, ResourceLocation texture) {
    }
}
