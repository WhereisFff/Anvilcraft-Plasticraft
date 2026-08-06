package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/** 只读取实体已同步显示状态的通用塑料实体渲染器。 */
public class UniversalPlasticEntityRenderer extends EntityRenderer<UniversalPlasticEntity> {
    private static final double LIGHT_SAMPLE_OFFSET = 1.0E-4D;
    private final BlockRenderDispatcher dispatcher;

    public UniversalPlasticEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        UniversalPlasticEntity entity,
        float yaw,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        PlasticHammerScreen.HammerPreview preview = PlasticHammerScreen.getPreview(entity);
        if (preview != null) {
            pose.pushPose();
            PlasticEntityRenderTransforms.applyPreview(pose, entity, preview.orientation());
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                entity,
                this.dispatcher,
                pose,
                buffers,
                preview.valid()
            );
            pose.popPose();
            pose.pushPose();
            PlasticEntityRenderTransforms.applyWorldAlignedPreview(pose, entity);
            PlasticEntityRenderHelper.renderHammerAxis(entity, this.dispatcher, pose, buffers);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
            return;
        }
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        PlasticEntityRenderHelper.renderModel(entity, this.dispatcher, pose, buffers, packedLight);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(UniversalPlasticEntity entity) {
        return entity.getMoldedData()
            .map(DynamicPlasticTextureManager.INSTANCE::texture)
            .orElse(InventoryMenu.BLOCK_ATLAS);
    }

    @Override
    protected int getSkyLightLevel(UniversalPlasticEntity entity, BlockPos ignored) {
        return sampleLight(entity.level(), LightLayer.SKY, entity.getBoundingBox());
    }

    @Override
    protected int getBlockLightLevel(UniversalPlasticEntity entity, BlockPos ignored) {
        return entity.isOnFire()
            ? 15
            : sampleLight(entity.level(), LightLayer.BLOCK, entity.getBoundingBox());
    }

    private static int sampleLight(Level level, LightLayer layer, AABB bounds) {
        BlockPos.MutableBlockPos sample = new BlockPos.MutableBlockPos();
        double centerX = (bounds.minX + bounds.maxX) * 0.5D;
        double centerY = (bounds.minY + bounds.maxY) * 0.5D;
        double centerZ = (bounds.minZ + bounds.maxZ) * 0.5D;
        int light = brightness(level, layer, sample, centerX, centerY, centerZ);
        light = Math.max(light, brightness(
            level, layer, sample, bounds.minX - LIGHT_SAMPLE_OFFSET, centerY, centerZ
        ));
        light = Math.max(light, brightness(
            level, layer, sample, bounds.maxX + LIGHT_SAMPLE_OFFSET, centerY, centerZ
        ));
        light = Math.max(light, brightness(
            level, layer, sample, centerX, bounds.minY - LIGHT_SAMPLE_OFFSET, centerZ
        ));
        light = Math.max(light, brightness(
            level, layer, sample, centerX, bounds.maxY + LIGHT_SAMPLE_OFFSET, centerZ
        ));
        light = Math.max(light, brightness(
            level, layer, sample, centerX, centerY, bounds.minZ - LIGHT_SAMPLE_OFFSET
        ));
        return Math.max(light, brightness(
            level, layer, sample, centerX, centerY, bounds.maxZ + LIGHT_SAMPLE_OFFSET
        ));
    }

    private static int brightness(
        Level level,
        LightLayer layer,
        BlockPos.MutableBlockPos sample,
        double x,
        double y,
        double z
    ) {
        sample.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        return level.getBrightness(layer, sample);
    }
}
