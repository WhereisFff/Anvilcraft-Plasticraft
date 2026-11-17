package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticEntityRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticBlockModel;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderTransforms;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 渲染方块化后的塑料实体或原始下落方块模型。 */
public class BondedEntityBlockEntityRenderer extends PlasticBlockEntityRenderer<BondedEntityBlockEntity> {
    public BondedEntityBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void extractGeometry(
        BondedEntityBlockEntity blockEntity,
        float partialTick,
        PoseStack pose,
        PlasticRenderGeometry buffers,
        int packedLight,
        int packedOverlay
    ) {
        if (ClearPlasticEntityRenderer.enqueue(blockEntity)) {
            if (blockEntity.isHammerDeflected()) {
                renderAdhesivePatch(blockEntity, pose, buffers, packedLight, partialTick);
            }
            return;
        }
        if (!blockEntity.isInitialized()) return;
        if (!blockEntity.isPlastic()) {
            PlasticBlockModel.render(
                blockEntity.getDisplayState(),
                pose,
                buffers,
                packedLight,
                OverlayTexture.NO_OVERLAY
            );
            return;
        }

        Entity renderEntity = blockEntity.getOrCreateRenderEntity();
        if (renderEntity instanceof AbstractPlasticEntity plasticEntity) {
            PlasticEntityOrientation orientation = blockEntity.getPlasticOrientation();
            Vec3 entityPosition = plasticEntity.plasticraft$placementPosition(
                blockEntity.getBlockPos(),
                orientation,
                blockEntity.getAdhesiveLocalFace()
            );
            Vec3 relative = entityPosition.subtract(Vec3.atLowerCornerOf(blockEntity.getBlockPos()));
            var dispatch = Minecraft.getInstance().getEntityRenderDispatcher();
            var renderer = dispatch.getRenderer(plasticEntity);
            pose.pushPose();
            pose.translate(relative.x, relative.y, relative.z);
            var animation = blockEntity.getHammerRotationAnimation(partialTick);
            if (animation == null) {
                extractPlasticModel(renderer, plasticEntity, partialTick, pose, buffers, packedLight);
            } else {
                try (var ignored = PlasticEntityRenderTransforms.overrideRotation(
                    plasticEntity, animation.from(), animation.to(), animation.progress())) {
                    extractPlasticModel(renderer, plasticEntity, partialTick, pose, buffers, packedLight);
                }
            }
            pose.popPose();
        }
        if (blockEntity.isHammerDeflected()) {
            renderAdhesivePatch(blockEntity, pose, buffers, packedLight, partialTick);
        }
    }

    private static <T extends Entity, S extends EntityRenderState> void extractPlasticModel(
        EntityRenderer<T, S> renderer, T entity, float partialTick, PoseStack pose, PlasticRenderGeometry buffers, int light
    ) {
        S state = renderer.createRenderState();
        if (state instanceof PlasticRenderGeometry.EntityState plastic) {
            plastic.packedLightOverride = light;
        }
        renderer.extractRenderState(entity, state, partialTick);
        Vec3 offset = renderer.getRenderOffset(state);
        pose.pushPose();
        pose.translate(offset.x, offset.y, offset.z);
        buffers.nested(pose, (parent, collector, camera) -> renderer.submit(state, parent, collector, camera));
        pose.popPose();
    }

    @Override
    public AABB getRenderBoundingBox(BondedEntityBlockEntity blockEntity) {
        AABB bounds = new AABB(blockEntity.getBlockPos());
        if (blockEntity.getOrCreateRenderEntity() instanceof AbstractPlasticEntity plasticEntity) {
            return bounds.minmax(plasticEntity.getBoundingBox());
        }
        return bounds;
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
