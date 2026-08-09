package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer.PreparedTankFluids;
import dev.anvilcraft.plasticraft.client.renderer.MoldedTrayComponentRenderer;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;

/** 只读取实体已同步显示状态的通用塑料实体渲染器。 */
public class UniversalPlasticEntityRenderer extends EntityRenderer<UniversalPlasticEntity> {
    private static final double LIGHT_SAMPLE_OFFSET = 1.0E-4D;
    private static final Map<UniversalPlasticEntity, GravitySample> GRAVITY_SAMPLES = new WeakHashMap<>();
    private static final Map<UniversalPlasticEntity, FluidMeshSample> FLUID_MESH_SAMPLES = new WeakHashMap<>();
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
        entity.getMoldedData()
            .filter(data -> MoldingProductTypes.isTank(data.finalType()))
            .ifPresent(data -> {
                Vec3 localUp = localUp(entity, partialTick, pose);
                MoldedPlasticMeshRenderer.renderTankFluids(
                    preparedTankFluids(entity, data, localUp),
                    pose,
                    buffers,
                    packedLight
                );
            });
        entity.getMoldedData().ifPresent(data -> MoldedTrayComponentRenderer.render(
            data,
            this.dispatcher,
            entity.plasticraft$getTrayBlockEntities(),
            partialTick,
            pose,
            buffers,
            packedLight,
            OverlayTexture.NO_OVERLAY
        ));
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

    private static Vec3 localUp(UniversalPlasticEntity entity, float partialTick, PoseStack pose) {
        Vec3 worldUp = gravitySample(entity, partialTick);
        Vector3f local = new Vector3f((float) worldUp.x, (float) worldUp.y, (float) worldUp.z);
        new Matrix3f(pose.last().normal()).transpose().transform(local);
        return new Vec3(local.x(), local.y(), local.z()).normalize();
    }

    private static PreparedTankFluids preparedTankFluids(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        Vec3 localUp
    ) {
        FluidMeshSample sample = FLUID_MESH_SAMPLES.get(entity);
        // 同步内容会替换数据对象；局部重力不变时可直接复用完整液体顶点。
        if (sample == null || sample.data != data || !sameDirection(sample.localUp, localUp)) {
            sample = new FluidMeshSample(
                data,
                localUp,
                MoldedPlasticMeshRenderer.prepareTankFluids(data, localUp)
            );
            FLUID_MESH_SAMPLES.put(entity, sample);
        }
        return sample.prepared;
    }

    private static boolean sameDirection(Vec3 first, Vec3 second) {
        return first.x == second.x && first.y == second.y && first.z == second.z;
    }

    private static Vec3 gravitySample(UniversalPlasticEntity entity, float partialTick) {
        Vec3 effectiveGravity = entity.plasticraft$getEffectiveGravityVector();
        GravitySample sample = GRAVITY_SAMPLES.get(entity);
        long gameTime = entity.level().getGameTime();
        if (sample == null) {
            Vec3 up = stableUp(effectiveGravity, new Vec3(0.0D, 1.0D, 0.0D));
            sample = new GravitySample(up, up, gameTime);
            GRAVITY_SAMPLES.put(entity, sample);
        } else if (sample.gameTime != gameTime) {
            Vec3 up = stableUp(effectiveGravity, sample.current);
            sample = new GravitySample(sample.current, up, gameTime);
            GRAVITY_SAMPLES.put(entity, sample);
        }
        return sample.previous.lerp(sample.current, Math.clamp(partialTick, 0.0F, 1.0F)).normalize();
    }

    private static Vec3 stableUp(Vec3 gravity, Vec3 fallback) {
        return gravity.lengthSqr() <= 1.0E-8D ? fallback : gravity.normalize().scale(-1.0D);
    }

    private record GravitySample(Vec3 previous, Vec3 current, long gameTime) {
    }

    private record FluidMeshSample(MoldedPlasticData data, Vec3 localUp, PreparedTankFluids prepared) {
    }
}
