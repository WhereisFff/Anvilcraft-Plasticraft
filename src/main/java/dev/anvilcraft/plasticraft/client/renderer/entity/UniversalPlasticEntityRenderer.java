package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager;
import dev.anvilcraft.plasticraft.client.renderer.FluidRenderOpacity;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer.FluidLayerRenderPass;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer.PreparedTankFluids;
import dev.anvilcraft.plasticraft.client.renderer.PlasticOilCatalysisRenderer;
import dev.anvilcraft.plasticraft.client.renderer.MoldedTrayComponentRenderer;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.PlasticCauldronLayout;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** 只读取实体已同步显示状态的通用塑料实体渲染器。 */
public class UniversalPlasticEntityRenderer extends EntityRenderer<UniversalPlasticEntity> {
    private static final double LIGHT_SAMPLE_OFFSET = 1.0E-4D;
    private static final Vec3 WORLD_UP = new Vec3(0.0D, 1.0D, 0.0D);
    private static final Map<UniversalPlasticEntity, GravitySample> GRAVITY_SAMPLES = new WeakHashMap<>();
    private static final Map<UniversalPlasticEntity, FluidMeshSample> FLUID_MESH_SAMPLES = new WeakHashMap<>();
    private static final Map<UniversalPlasticEntity, LightSample> LIGHT_SAMPLES = new WeakHashMap<>();
    private final BlockRenderDispatcher dispatcher;
    private final RandomSource random = RandomSource.create();

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
            entity.getMoldedData().ifPresent(data -> {
                Direction outlet = entity.getOutletLocalDirection();
                if (outlet != null && MoldingProductTypes.isCauldron(data.finalType())) {
                    MoldedPlasticMeshRenderer.renderCauldronOutletPreview(
                        data,
                        outlet,
                        pose,
                        buffers,
                        preview.valid()
                    );
                }
            });
            pose.popPose();
            pose.pushPose();
            PlasticEntityRenderTransforms.applyWorldAlignedPreview(pose, entity);
            PlasticEntityRenderHelper.renderHammerAxis(entity, this.dispatcher, pose, buffers);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
            return;
        }
        boolean deferredPass = ClearPlasticRenderTypes.isDeferredPassActive();
        boolean transparent = PlasticEntityRenderHelper.isTransparent(entity);
        if (transparent && isLiveWorldEntity(entity) && !deferredPass) {
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
            return;
        }
        OptionalRenderData renderData = OptionalRenderData.of(entity.getMoldedData().orElse(null));
        boolean transparentCauldronPass = renderData.cauldron() && deferredPass && transparent;
        if (transparentCauldronPass) {
            // 世界重力对齐的物品不乘锅壳旋转，但透明锅仍必须最后覆盖在它们外面。
            this.renderCauldronGravityItems(entity, renderData.data(), pose, buffers, packedLight);
        }
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        if (renderData.cauldron()) {
            this.renderCauldron(entity, renderData.data(), deferredPass, pose, buffers, packedLight, partialTick);
        } else {
            boolean deferredTransparentContents = deferredPass && transparent;
            // 储罐沿用按实际内腔求解的多流体网格；炼药锅不能走这条储罐路径。
            if (renderData.data() != null && MoldingProductTypes.isTank(renderData.data().finalType())) {
                Vec3 localUp = localUp(entity, partialTick, pose);
                PreparedTankFluids tankFluids = preparedTankFluids(entity, renderData.data(), localUp);
                MoldedPlasticMeshRenderer.renderTankFluids(
                    tankFluids,
                    pose,
                    buffers,
                    packedLight,
                    deferredTransparentContents
                        ? FluidLayerRenderPass.TRANSLUCENT_ONLY
                        : FluidLayerRenderPass.ALL
                );
                // 空罐没有液面需要与透明壳体分隔，逐实体刷批只会白拆一次批次。
                if (deferredTransparentContents && !tankFluids.isEmpty()) flush(buffers);
            }
            // 流体先写深度，透明壳体随后只会覆盖位于液面前方的像素。
            PlasticEntityRenderHelper.renderModel(entity, this.dispatcher, pose, buffers, packedLight);
        }
        MoldedPlasticData trayData = renderData.data();
        if (trayData != null && MoldingProductTypes.isTray(trayData.finalType())) {
            MoldedTrayComponentRenderer.render(
                trayData,
                this.dispatcher,
                entity.plasticraft$getTrayBlockEntities(),
                partialTick,
                pose,
                buffers,
                packedLight,
                OverlayTexture.NO_OVERLAY
            );
        }
        pose.popPose();
        if (renderData.cauldron() && !transparentCauldronPass) {
            // 侧放或倒置的锅中物品要服从世界重力，不能继续乘上锅壳的离散旋转。
            this.renderCauldronGravityItems(entity, renderData.data(), pose, buffers, packedLight);
        }
        if (!deferredPass) super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    /** 在延迟透明壳开始前写入不透明流体深度，使后侧壳体和内壁通过深度测试自然被遮挡。 */
    public void renderOpaqueFluidContents(
        UniversalPlasticEntity entity,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        OptionalRenderData renderData = OptionalRenderData.of(entity.getMoldedData().orElse(null));
        if (renderData.data() == null) return;
        pose.pushPose();
        try {
            PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
            if (renderData.cauldron()) {
                List<FluidStack> fluids = entity.getSyncedFluids();
                this.renderCauldronFluids(
                    entity,
                    fluids,
                    CauldronFluidMetrics.of(entity, renderData.data(), fluids),
                    FluidLayerRenderPass.OPAQUE_ONLY,
                    false,
                    pose,
                    buffers,
                    packedLight,
                    partialTick
                );
            } else if (MoldingProductTypes.isTank(renderData.data().finalType())) {
                Vec3 localUp = localUp(entity, partialTick, pose);
                MoldedPlasticMeshRenderer.renderTankFluids(
                    preparedTankFluids(entity, renderData.data(), localUp),
                    pose,
                    buffers,
                    packedLight,
                    FluidLayerRenderPass.OPAQUE_ONLY
                );
            }
        } finally {
            pose.popPose();
        }
    }

    /** 炼药锅的绘制顺序必须独立于储罐；透明锅壳最后刷批，避免遮掉背后的锅体与内容。 */
    private void renderCauldron(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        boolean deferredPass,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        float partialTick
    ) {
        List<ItemStack> items = entity.getSyncedItems();
        List<FluidStack> fluids = entity.getSyncedFluids();
        FluidStack bottomFluid = fluids.isEmpty() ? FluidStack.EMPTY : fluids.getFirst();
        CauldronFluidMetrics metrics = CauldronFluidMetrics.of(entity, data, fluids);
        boolean gravityAlignedItems = !items.isEmpty() && entity.shouldUseGravityAlignedItemLayout();
        boolean renderStoredItems = !items.isEmpty()
            && !gravityAlignedItems
            && !entity.shouldEjectStoredItems();
        // 刷批只用来分隔锅壳、物品和液面的绘制次序；空锅没有内容可排序，逐实体刷批只会把批次
        // 数量钉死在锅的数量上。
        boolean hasContents = renderStoredItems || !fluids.isEmpty();

        boolean transparent = deferredPass && PlasticEntityRenderHelper.isTransparent(entity);
        FluidLayerRenderPass fluidRenderPass = transparent
            ? FluidLayerRenderPass.TRANSLUCENT_ONLY
            : FluidLayerRenderPass.ALL;
        if (transparent) {
            if (renderStoredItems) {
                this.renderItems(entity, items, metrics, false, false, pose, buffers, packedLight);
            }
            if (hasContents) flush(buffers);
            if (!fluids.isEmpty()) {
                this.renderCauldronFluids(
                    entity, fluids, metrics, fluidRenderPass, true, pose, buffers, packedLight, partialTick
                );
                flush(buffers);
            }
            PlasticEntityRenderHelper.renderModel(entity, this.dispatcher, pose, buffers, packedLight);
            this.renderOutlet(entity, data, pose, buffers, packedLight);
            if (hasContents) flush(buffers);
        } else {
            PlasticEntityRenderHelper.renderModel(entity, this.dispatcher, pose, buffers, packedLight);
            this.renderOutlet(entity, data, pose, buffers, packedLight);
            if (hasContents) flush(buffers);
            if (renderStoredItems) {
                this.renderItems(entity, items, metrics, false, false, pose, buffers, packedLight);
                flush(buffers);
            }
            if (!fluids.isEmpty()) {
                this.renderCauldronFluids(
                    entity, fluids, metrics, fluidRenderPass, true, pose, buffers, packedLight, partialTick
                );
                flush(buffers);
            }
        }

        if (!bottomFluid.isEmpty() && entity.anvilcraft$isIgnited()) {
            float top = metrics.fluidTop();
            if (bottomFluid.is(PlasticraftFluids.HIGH_HEAT_FUEL.get())) {
                IgnitedFluidFlameRenderer.renderBlue(
                    pose, buffers, top, metrics.flameScale(), OverlayTexture.NO_OVERLAY
                );
            } else {
                IgnitedFluidFlameRenderer.renderOrdinary(
                    pose, buffers, top, metrics.flameScale(), OverlayTexture.NO_OVERLAY
                );
            }
            flush(buffers);
        }

    }

    private void renderOutlet(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        Direction outlet = entity.getOutletLocalDirection();
        if (outlet == null) return;
        MoldedPlasticMeshRenderer.renderCauldronOutlet(
            data,
            outlet,
            pose,
            buffers,
            packedLight,
            PlasticEntityRenderHelper.isTransparent(entity)
        );
    }

    private void renderCauldronFluids(
        UniversalPlasticEntity entity,
        List<FluidStack> fluids,
        CauldronFluidMetrics metrics,
        FluidLayerRenderPass renderPass,
        boolean renderOverlays,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        float partialTick
    ) {
        boolean deferredTransparent = ClearPlasticRenderTypes.isDeferredPassActive()
            && PlasticEntityRenderHelper.isTransparent(entity);
        float layerBottom = metrics.fluidBottom();
        for (FluidStack fluid : fluids) {
            if (fluid.isEmpty()) continue;
            float layerTop = Math.min(
                metrics.fullFluidTop(),
                layerBottom + metrics.layerHeight(fluid.getAmount())
            );
            if (layerTop <= layerBottom) continue;
            if (renderPass.includes(FluidRenderOpacity.isOpaque(fluid))) {
                if (deferredTransparent) {
                    VertexConsumer consumer = buffers.getBuffer(ClearPlasticRenderTypes.fluid());
                    FluidRenderHelper.INSTANCE.renderFluidBox(
                        fluid,
                        metrics.minX(),
                        layerBottom,
                        metrics.minZ(),
                        metrics.maxX(),
                        layerTop,
                        metrics.maxZ(),
                        consumer,
                        pose,
                        packedLight,
                        true,
                        false
                    );
                } else if (renderPass == FluidLayerRenderPass.OPAQUE_ONLY) {
                    // 预绘必须写入主目标深度；FluidRenderHelper 的 buffers 重载会自行选择 translucent。
                    VertexConsumer consumer = buffers.getBuffer(RenderType.cutout());
                    FluidRenderHelper.INSTANCE.renderFluidBox(
                        fluid,
                        metrics.minX(),
                        layerBottom,
                        metrics.minZ(),
                        metrics.maxX(),
                        layerTop,
                        metrics.maxZ(),
                        consumer,
                        pose,
                        packedLight,
                        true,
                        false
                    );
                } else {
                    FluidRenderHelper.INSTANCE.renderFluidBox(
                        fluid,
                        metrics.minX(),
                        layerBottom,
                        metrics.minZ(),
                        metrics.maxX(),
                        layerTop,
                        metrics.maxZ(),
                        buffers,
                        pose,
                        packedLight,
                        true,
                        false
                    );
                }
            }
            if (renderOverlays) {
                PlasticOilCatalysisRenderer.renderContainerOverlay(
                    entity.level(),
                    BlockPos.containing(entity.getBoundingBox().getCenter()),
                    partialTick,
                    fluid,
                    metrics.minX(),
                    layerBottom,
                    metrics.minZ(),
                    metrics.maxX(),
                    layerTop,
                    metrics.maxZ(),
                    buffers,
                    pose,
                    packedLight,
                    true,
                    deferredTransparent
                );
            }
            layerBottom = layerTop;
        }
    }

    private void renderItems(
        UniversalPlasticEntity entity,
        List<ItemStack> items,
        CauldronFluidMetrics metrics,
        boolean gravityAligned,
        boolean itemsAtDownwardOpening,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        this.random.setSeed(itemHash(items));
        float minX = metrics.cavityMinX();
        float minY = metrics.cavityMinY();
        float minZ = metrics.cavityMinZ();
        float maxX = metrics.cavityMaxX();
        float maxY = metrics.cavityMaxY();
        float maxZ = metrics.cavityMaxZ();
        // cavityBounds 以方块为单位；surfaceBounds 也是同一约定。
        float localCenterX = (minX + maxX) * 0.5F;
        float localCenterZ = (minZ + maxZ) * 0.5F;
        float localCenterY = itemsAtDownwardOpening
            ? minY - 0.17F
            : itemCenterY(metrics);
        if (gravityAligned) {
            Vec3 origin = entity.plasticraft$getGeometry().entityOrigin();
            localCenterX -= (float) origin.x;
            localCenterY -= (float) origin.y;
            localCenterZ -= (float) origin.z;
        }
        float centerX = localCenterX;
        float centerY = localCenterY;
        float centerZ = localCenterZ;
        int itemCount = items.size();
        float partAngle = 360.0F / itemCount;
        int remaining = itemCount;
        for (ItemStack stack : items) {
            float angleDegrees = partAngle * remaining;
            float angle = angleDegrees * Mth.DEG_TO_RAD;
            float radius = itemCount == 1 ? 0.0F : Math.min(0.16F, Math.min(maxX - minX, maxZ - minZ) * 0.2F);
            pose.pushPose();
            pose.translate(
                centerX + Mth.cos(angle) * radius,
                centerY,
                centerZ + Mth.sin(angle) * radius
            );
            pose.mulPose(
                new Quaternionf()
                    .rotateY((angleDegrees + this.random.nextIntBetweenInclusive(-25, 25) + 35.0F) * Mth.DEG_TO_RAD)
                    .rotateX(65.0F * Mth.DEG_TO_RAD)
            );
            int renderedCopies = Math.min(5, 1 + stack.getCount() / 8);
            for (int copy = 0; copy < renderedCopies; copy++) {
                pose.pushPose();
                if (copy > 0) {
                    float spread = 1.0F / 20.0F;
                    pose.translate(
                        (this.random.nextFloat() - 0.5F) * spread,
                        (this.random.nextFloat() - 0.5F) * spread,
                        (this.random.nextFloat() - 0.5F) * spread
                    );
                }
                Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.GROUND,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffers,
                    entity.level(),
                    0
                );
                pose.popPose();
            }
            pose.popPose();
            remaining--;
        }
    }

    private void renderCauldronGravityItems(
        UniversalPlasticEntity entity,
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        List<ItemStack> items = entity.getSyncedItems();
        if (items.isEmpty() || !entity.shouldUseGravityAlignedItemLayout()) return;
        CauldronFluidMetrics metrics = CauldronFluidMetrics.of(entity, data, entity.getSyncedFluids());
        boolean itemsAtDownwardOpening = entity.getOrientation().attachmentFace() == Direction.DOWN;
        pose.pushPose();
        this.renderItems(entity, items, metrics, true, itemsAtDownwardOpening, pose, buffers, packedLight);
        flush(buffers);
        pose.popPose();
    }

    /** 与硬化树脂锅一致：物品随液面上升，但在接近锅沿前留出物品自身的可见空间。 */
    private static float itemCenterY(CauldronFluidMetrics metrics) {
        float dry = metrics.cavityMinY() + 0.06F;
        float rise = Math.max(0.0F, metrics.fullFluidTop() - metrics.fluidBottom() - 0.125F);
        float wet = metrics.cavityMaxY() - 0.19F;
        return Mth.clamp(dry + metrics.fill() * rise, dry, Math.max(dry, wet));
    }

    private static int itemHash(List<ItemStack> items) {
        int hash = 0;
        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            hash = hash * 31 + Item.getId(stack.getItem()) + stack.getDamageValue();
        }
        return hash;
    }

    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();
    }

    private record OptionalRenderData(MoldedPlasticData data, boolean cauldron) {
        private static OptionalRenderData of(MoldedPlasticData data) {
            return new OptionalRenderData(data, data != null && MoldingProductTypes.isCauldron(data.finalType()));
        }
    }

    private record CauldronFluidMetrics(
        float minX,
        float minZ,
        float maxX,
        float maxZ,
        float cavityMinX,
        float cavityMinY,
        float cavityMinZ,
        float cavityMaxX,
        float cavityMaxY,
        float cavityMaxZ,
        float fluidBottom,
        float fullFluidTop,
        float fluidTop,
        float fill,
        int totalCapacity
    ) {
        private static final float SIDE_INSET = 0.001F;
        private static final float BOTTOM_INSET = 0.001F;
        private static final float TOP_INSET = 0.064F;

        private static CauldronFluidMetrics of(
            UniversalPlasticEntity entity,
            MoldedPlasticData data,
            List<FluidStack> fluids
        ) {
            AABB cavity = data.cavityBounds().orElse(data.surfaceBounds());
            PlasticCauldronLayout layout = entity.plasticraft$cauldronLayout();
            int layerCapacity = layout.fluidLayerCapacity(data.capacity());
            int totalCapacity = Math.max(1, Math.multiplyExact(layerCapacity, layout.fluidLayers()));
            long totalAmount = 0L;
            for (FluidStack fluid : fluids) totalAmount += fluid.getAmount();
            float fill = Mth.clamp(totalAmount / (float) totalCapacity, 0.0F, 1.0F);
            float cavityMinX = (float) cavity.minX;
            float cavityMinY = (float) cavity.minY;
            float cavityMinZ = (float) cavity.minZ;
            float cavityMaxX = (float) cavity.maxX;
            float cavityMaxY = (float) cavity.maxY;
            float cavityMaxZ = (float) cavity.maxZ;
            float xInset = safeInset(cavityMaxX - cavityMinX, SIDE_INSET);
            float zInset = safeInset(cavityMaxZ - cavityMinZ, SIDE_INSET);
            float bottom = cavityMinY + safeInset(cavityMaxY - cavityMinY, BOTTOM_INSET);
            float fullTop = cavityMaxY - safeInset(cavityMaxY - cavityMinY, TOP_INSET);
            if (fullTop < bottom) fullTop = bottom;
            return new CauldronFluidMetrics(
                cavityMinX + xInset,
                cavityMinZ + zInset,
                cavityMaxX - xInset,
                cavityMaxZ - zInset,
                cavityMinX,
                cavityMinY,
                cavityMinZ,
                cavityMaxX,
                cavityMaxY,
                cavityMaxZ,
                bottom,
                fullTop,
                Mth.lerp(fill, bottom, fullTop),
                fill,
                totalCapacity
            );
        }

        private static float safeInset(float extent, float requested) {
            return Math.min(requested, Math.max(0.0F, extent * 0.5F - 1.0E-4F));
        }

        private float flameScale() {
            return Math.max(0.4F, Math.min(1.0F, Math.min(maxX - minX, maxZ - minZ)));
        }

        private float layerHeight(int amount) {
            return (this.fullFluidTop - this.fluidBottom) * amount / this.totalCapacity;
        }
    }

    @Override
    public ResourceLocation getTextureLocation(UniversalPlasticEntity entity) {
        return entity.getMoldedData()
            .map(DynamicPlasticTextureManager.INSTANCE::texture)
            .orElse(InventoryMenu.BLOCK_ATLAS);
    }

    @Override
    protected int getSkyLightLevel(UniversalPlasticEntity entity, BlockPos ignored) {
        return lightSample(entity).skyLight;
    }

    @Override
    protected int getBlockLightLevel(UniversalPlasticEntity entity, BlockPos ignored) {
        return entity.isOnFire() ? 15 : lightSample(entity).blockLight;
    }

    public static int packedLight(UniversalPlasticEntity entity) {
        LightSample sample = lightSample(entity);
        return LightTexture.pack(entity.isOnFire() ? 15 : sample.blockLight, sample.skyLight);
    }

    /**
     * 按刻缓存包围盒六面的亮度采样。
     *
     * <p>同一实体每帧会被主实体阶段、不透明预绘和延迟透明阶段分别问询亮度，逐次重采样等于把
     * 十四次光照查询乘上阶段数量；包围盒实例在位置、朝向或几何变化时必然被替换，因此与游戏刻
     * 一起作为失效条件。方块光变化最多推迟一刻生效。</p>
     */
    private static LightSample lightSample(UniversalPlasticEntity entity) {
        Level level = entity.level();
        AABB bounds = entity.getBoundingBox();
        long gameTime = level.getGameTime();
        LightSample sample = LIGHT_SAMPLES.get(entity);
        if (sample != null && sample.gameTime == gameTime && sample.bounds == bounds) return sample;
        sample = new LightSample(
            gameTime,
            bounds,
            sampleLight(level, LightLayer.BLOCK, bounds),
            sampleLight(level, LightLayer.SKY, bounds)
        );
        LIGHT_SAMPLES.put(entity, sample);
        return sample;
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

    private static boolean isLiveWorldEntity(UniversalPlasticEntity entity) {
        return entity.level().isClientSide && entity.level().getEntity(entity.getId()) == entity;
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
        GravitySample sample = GRAVITY_SAMPLES.get(entity);
        long gameTime = entity.level().getGameTime();
        // 有效重力要采样流体接触和重力场，逐帧调用会白付一次邻域扫描；每刻只解一次即可。
        if (sample == null) {
            Vec3 up = stableUp(entity.plasticraft$getEffectiveGravityVector(), WORLD_UP);
            sample = new GravitySample(up, up, gameTime);
            GRAVITY_SAMPLES.put(entity, sample);
        } else if (sample.gameTime != gameTime) {
            Vec3 up = stableUp(entity.plasticraft$getEffectiveGravityVector(), sample.current);
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

    private record LightSample(long gameTime, AABB bounds, int blockLight, int skyLight) {
    }

    private record FluidMeshSample(MoldedPlasticData data, Vec3 localUp, PreparedTankFluids prepared) {
    }
}
