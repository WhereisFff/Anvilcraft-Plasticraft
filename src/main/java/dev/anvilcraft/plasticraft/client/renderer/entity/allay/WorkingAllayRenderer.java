package dev.anvilcraft.plasticraft.client.renderer.entity.allay;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** 施工悦灵使用原版悦灵贴图、光照与手持层,再叠加安全帽与托管携带物。 */
public class WorkingAllayRenderer extends MobRenderer<WorkingAllayEntity, WorkingAllayModel> {
    private static final ResourceLocation TEXTURE =
        ResourceLocation.withDefaultNamespace("textures/entity/allay/allay.png");

    public WorkingAllayRenderer(EntityRendererProvider.Context context) {
        super(context, new WorkingAllayModel(context.bakeLayer(ModelLayers.ALLAY)), 0.4F);
        this.addLayer(new WorkingAllayHeldItemLayer(
            this,
            context.getItemInHandRenderer(),
            context.getItemRenderer()
        ));
        this.addLayer(new AllayHardHatLayer(this));
        this.addLayer(new AllayHostedCarryLayer(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(WorkingAllayEntity entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(WorkingAllayEntity entity, BlockPos pos) {
        return 15;
    }
}
