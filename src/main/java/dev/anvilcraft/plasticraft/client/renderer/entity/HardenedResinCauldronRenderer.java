package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticHammerScreen;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticOilCatalysisRenderer;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Quaternionf;

import java.util.List;

/** 渲染带朝向的釜模型及其同步流体表面。 */
public class HardenedResinCauldronRenderer extends EntityRenderer<HardenedResinCauldronEntity> {
    public static final ModelResourceLocation OUTLET_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/hardend_resin_cauldron_outlet")
    );
    private final BlockRenderDispatcher dispatcher;
    private final RandomSource random = RandomSource.create();

    public HardenedResinCauldronRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        HardenedResinCauldronEntity entity,
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
            this.renderOutlet(entity, pose, buffers, packedLight, true, preview.valid());
            pose.popPose();
            pose.pushPose();
            PlasticEntityRenderTransforms.applyWorldAlignedPreview(pose, entity);
            PlasticEntityRenderHelper.renderHammerAxis(entity, this.dispatcher, pose, buffers);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffers, packedLight);
            return;
        }
        FluidStack fluid = entity.getSyncedFluid();
        float fill = fluid.isEmpty() ? 0.0F : (float) fluid.getAmount() / HardenedResinCauldronEntity.CAPACITY;
        float fluidBottom = 0.251F;
        float fluidTop = fluidBottom + fill * 0.685F;
        List<ItemStack> items = entity.getSyncedItems();
        boolean gravityAlignedItems = !items.isEmpty() && entity.shouldUseGravityAlignedItemLayout();
        boolean itemsAtDownwardOpening = gravityAlignedItems
            && entity.getOrientation().attachmentFace() == Direction.DOWN;
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity, partialTick);
        PlasticEntityRenderHelper.renderBlock(entity, this.dispatcher, pose, buffers, packedLight);
        this.renderOutlet(entity, pose, buffers, packedLight, false, true);
        flush(buffers);
        if (!items.isEmpty() && !gravityAlignedItems && !entity.shouldEjectStoredItems()) {
            this.renderItems(entity, items, fill, false, false, pose, buffers, packedLight);
        }
        flush(buffers);
        if (!fluid.isEmpty()) {
            float innerMin = 0.126F;
            float innerMax = 0.874F;
            FluidRenderHelper.INSTANCE.renderFluidBox(
                fluid,
                innerMin,
                fluidBottom,
                innerMin,
                innerMax,
                fluidTop,
                innerMax,
                buffers,
                pose,
                packedLight,
                true,
                false
            );
            PlasticOilCatalysisRenderer.renderContainerOverlay(
                entity.level(),
                BlockPos.containing(entity.getBoundingBox().getCenter()),
                partialTick,
                fluid,
                innerMin,
                fluidBottom,
                innerMin,
                innerMax,
                fluidTop,
                innerMax,
                buffers,
                pose,
                packedLight,
                true
            );
            flush(buffers);
        }
        if (entity.anvilcraft$isIgnited()) {
            if (fluid.is(ModFluids.HIGH_HEAT_FUEL.get())) {
                IgnitedFluidFlameRenderer.renderBlue(
                    pose,
                    buffers,
                    fluidTop,
                    1.0F,
                    OverlayTexture.NO_OVERLAY
                );
            } else {
                IgnitedFluidFlameRenderer.renderOrdinary(
                    pose,
                    buffers,
                    fluidTop,
                    1.0F,
                    OverlayTexture.NO_OVERLAY
                );
            }
            flush(buffers);
        }
        pose.popPose();
        if (gravityAlignedItems) {
            pose.pushPose();
            this.renderItems(entity, items, fill, true, itemsAtDownwardOpening, pose, buffers, packedLight);
            flush(buffers);
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    private void renderOutlet(
        HardenedResinCauldronEntity entity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        boolean preview,
        boolean previewValid
    ) {
        Direction localDirection = entity.getOutletLocalDirection();
        if (localDirection == null) return;
        // PoseStack 使用右手系；模型北面转向东需要绕正 Y 轴旋转 -90 度。
        float rotation = switch (localDirection) {
            case NORTH -> 0.0F;
            case EAST -> 270.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(-0.5D, -0.5D, -0.5D);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(OUTLET_MODEL);
        if (preview) {
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                entity.getDisplayState(),
                model,
                this.dispatcher,
                pose,
                buffers,
                previewValid
            );
            pose.popPose();
            return;
        }
        this.dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            entity.getDisplayState(),
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
        pose.popPose();
    }

    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    private void renderItems(
        HardenedResinCauldronEntity entity,
        List<ItemStack> items,
        float fluidFill,
        boolean gravityAligned,
        boolean itemsAtDownwardOpening,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        this.random.setSeed(ItemHandlerUtil.hash(entity.getSyncedItemHandler()));
        float randomOffset = this.random.nextIntBetweenInclusive(-25, 25);
        float centerX = gravityAligned ? 0.0F : 0.5F;
        float centerY = gravityAligned
            ? itemsAtDownwardOpening ? 0.08F : 0.19F
            : Mth.clamp(0.31F + fluidFill * 0.56F, 0.31F, 0.81F);
        float centerZ = gravityAligned ? 0.0F : 0.5F;
        int itemCount = items.size();
        float partAngle = 360.0F / itemCount;
        int remaining = itemCount;
        for (ItemStack stack : items) {
            float angleDegrees = partAngle * remaining;
            float angle = angleDegrees * Mth.DEG_TO_RAD;
            float radius = itemCount == 1 ? 0.0F : 0.16F;
            pose.pushPose();
            pose.translate(
                centerX + Mth.cos(angle) * radius,
                centerY,
                centerZ + Mth.sin(angle) * radius
            );
            pose.mulPose(
                new Quaternionf()
                    .rotateY((angleDegrees + randomOffset + 35.0F) * Mth.DEG_TO_RAD)
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

    @Override
    public ResourceLocation getTextureLocation(HardenedResinCauldronEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(
        HardenedResinCauldronEntity entity,
        Frustum frustum,
        double cameraX,
        double cameraY,
        double cameraZ
    ) {
        if (super.shouldRender(entity, frustum, cameraX, cameraY, cameraZ)) return true;
        AABB flameBounds = entity.getBoundingBox().inflate(2.0D);
        return frustum.isVisible(flameBounds);
    }

}
