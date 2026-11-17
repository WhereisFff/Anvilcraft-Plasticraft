package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/** 各种材料在提取阶段解算外形和内容物，黏合方块也可以按自身光照提取相同网格。 */
public abstract class PlasticEntityRenderer<T extends AbstractPlasticEntity> extends EntityRenderer<T, PlasticRenderGeometry.EntityState> {
    protected PlasticEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public final PlasticRenderGeometry.EntityState createRenderState() {
        return new PlasticRenderGeometry.EntityState();
    }

    @Override
    public final void extractRenderState(T entity, PlasticRenderGeometry.EntityState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        if (state.packedLightOverride >= 0) {
            state.lightCoords = state.packedLightOverride;
        }
        extractGeometry(entity, partialTick, new PoseStack(), state.geometry, state.lightCoords);
    }

    @Override
    public final void submit(PlasticRenderGeometry.EntityState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        state.geometry.submit(pose, collector, camera);
        super.submit(state, pose, collector, camera);
    }

    public abstract void extractGeometry(T entity, float partialTick, PoseStack pose, PlasticRenderGeometry geometry, int light);
}
