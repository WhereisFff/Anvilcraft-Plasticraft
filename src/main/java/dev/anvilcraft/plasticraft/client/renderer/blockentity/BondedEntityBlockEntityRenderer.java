package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
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
            Vec3 entityPosition = orientation.entityPosition(
                blockEntity.getBlockPos(),
                plasticEntity.getBbWidth(),
                plasticEntity.getBbHeight()
            );
            Vec3 relative = entityPosition.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
            EntityRenderer<? super AbstractPlasticEntity> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher()
                .getRenderer(plasticEntity);
            if (renderer != null) {
                Vec3 renderOffset = renderer.getRenderOffset(plasticEntity, partialTick);
                pose.pushPose();
                pose.translate(relative.x + renderOffset.x, relative.y + renderOffset.y, relative.z + renderOffset.z);
                renderer.render(plasticEntity, plasticEntity.getYRot(), partialTick, pose, buffers, packedLight);
                pose.popPose();
            }
        }
        renderAdhesivePatch(blockEntity, pose, buffers, packedLight);
    }

    private static void renderAdhesivePatch(
        BondedEntityBlockEntity blockEntity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        if (!(blockEntity.getLevel() instanceof ClientLevel level)) return;
        AdhesivePatchRenderer.renderAttachedPatch(
            level,
            pose,
            buffers,
            blockEntity.getBlockPos(),
            blockEntity.getSupportPos(),
            blockEntity.getAttachmentFace(),
            packedLight
        );
    }
}
