package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderTransforms;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.CatalyticPressLidEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 渲染方块化后的塑料实体或原始下落方块模型。 */
public class BondedEntityBlockEntityRenderer implements BlockEntityRenderer<BondedEntityBlockEntity> {
    public BondedEntityBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        BondedEntityBlockEntity blockEntity,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (!blockEntity.isInitialized()) return;
        if (!blockEntity.isPlastic()) {
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                blockEntity.getDisplayState(),
                pose,
                buffers,
                packedLight,
                OverlayTexture.NO_OVERLAY
            );
            renderAdhesivePatch(blockEntity, pose, buffers, packedLight);
            return;
        }

        Entity renderEntity = blockEntity.getOrCreateRenderEntity();
        if (renderEntity instanceof AbstractPlasticEntity plasticEntity) {
            PlasticEntityOrientation orientation = blockEntity.getPlasticOrientation();
            Vec3 entityPosition = plasticEntity.plasticraft$placementPosition(
                blockEntity.getBlockPos(),
                orientation
            );
            Vec3 relative = entityPosition.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
            EntityRenderer<? super AbstractPlasticEntity> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(plasticEntity);
            if (renderer != null) {
                Vec3 renderOffset = renderer.getRenderOffset(plasticEntity, partialTick);
                pose.pushPose();
                pose.translate(relative.x + renderOffset.x, relative.y + renderOffset.y, relative.z + renderOffset.z);
                BondedEntityBlockEntity.HammerRotationAnimation animation =
                    blockEntity.getHammerRotationAnimation(partialTick);
                if (animation == null) {
                    renderer.render(plasticEntity, plasticEntity.getYRot(), partialTick, pose, buffers, packedLight);
                } else {
                    try (PlasticEntityRenderTransforms.RenderOverrideScope ignored =
                             PlasticEntityRenderTransforms.overrideRotation(
                                 plasticEntity,
                                 animation.from(),
                                 animation.to(),
                                 animation.progress()
                             )) {
                        renderer.render(plasticEntity, plasticEntity.getYRot(), partialTick, pose, buffers, packedLight);
                    }
                }
                pose.popPose();
            }
        }
        renderAdhesivePatch(blockEntity, pose, buffers, packedLight, partialTick);
    }

    @Override
    public AABB getRenderBoundingBox(BondedEntityBlockEntity blockEntity) {
        AABB bounds = new AABB(blockEntity.getBlockPos());
        if (blockEntity.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity) {
            return bounds.minmax(plasticEntity.getBoundingBox());
        }
        if (blockEntity.getDisplayState().is(PlasticraftBlocks.CATALYTIC_PRESS_LID.get())) {
            return bounds.inflate(CatalyticPressLidEntity.RENDER_BOUNDS_EXPANSION);
        }
        return bounds;
    }

    private static void renderAdhesivePatch(
        BondedEntityBlockEntity blockEntity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        renderAdhesivePatch(blockEntity, pose, buffers, packedLight, 1.0F);
    }

    private static void renderAdhesivePatch(
        BondedEntityBlockEntity blockEntity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        float partialTick
    ) {
        if (!(blockEntity.getLevel() instanceof ClientLevel level)) return;
        AdhesivePatchRenderer.renderAttachedBlockAdhesive(
            level,
            pose,
            buffers,
            blockEntity,
            packedLight,
            partialTick
        );
    }
}
