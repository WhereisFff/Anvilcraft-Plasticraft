package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** 方块实体在提取阶段冻结本帧的组件位置、材质和光照，避免提交阶段访问世界。 */
abstract class PlasticBlockEntityRenderer<T extends BlockEntity> implements BlockEntityRenderer<T, PlasticRenderGeometry.BlockState> {
    @Override
    public final PlasticRenderGeometry.BlockState createRenderState() {
        return new PlasticRenderGeometry.BlockState();
    }

    @Override
    public final void extractRenderState(T blockEntity, PlasticRenderGeometry.BlockState state, float partialTick,
                                         Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTick, camera, breaking);
        extractGeometry(blockEntity, partialTick, new PoseStack(), state.geometry, state.lightCoords, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public final void submit(PlasticRenderGeometry.BlockState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        state.geometry.submit(pose, collector, camera);
    }

    protected abstract void extractGeometry(T blockEntity, float partialTick, PoseStack pose, PlasticRenderGeometry geometry, int light, int overlay);
}
