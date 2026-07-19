package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/** Renders the synced Royal-anvil-shaped block state without placing a block. */
public class PlasticAnvilRenderer extends EntityRenderer<PlasticAnvilEntity> {
    private final BlockRenderDispatcher dispatcher;

    public PlasticAnvilRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
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
        PlasticAnvilRenderTransforms.apply(pose, entity);
        PlasticEntityRenderHelper.renderBlock(entity, this.dispatcher, pose, buffers, packedLight);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(PlasticAnvilEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

}
