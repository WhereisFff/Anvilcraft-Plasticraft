package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Renders the synced Royal-anvil-shaped block state without placing a block. */
public class PlasticAnvilRenderer extends EntityRenderer<PlasticAnvilEntity> {
    private final BlockRenderDispatcher dispatcher;

    public PlasticAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        PlasticAnvilEntity entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        pose.pushPose();
        pose.translate(-0.5D, 0.0D, -0.5D);
        this.dispatcher.renderSingleBlock(
            entity.getDisplayState(),
            pose,
            buffers,
            packedLight,
            net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY
        );
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PlasticAnvilEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
