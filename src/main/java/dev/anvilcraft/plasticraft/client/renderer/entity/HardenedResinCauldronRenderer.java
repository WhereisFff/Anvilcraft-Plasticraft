package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.HardenedResinCauldronEntity;
import dev.dubhe.anvilcraft.api.itemhandler.ItemHandlerUtil;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

import org.joml.Quaternionf;

/** 渲染带朝向的釜模型及其同步流体表面。 */
public class HardenedResinCauldronRenderer extends EntityRenderer<HardenedResinCauldronEntity> {
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
        pose.pushPose();
        PlasticEntityRenderTransforms.apply(pose, entity);
        PlasticEntityRenderHelper.renderBlock(entity, this.dispatcher, pose, buffers, packedLight);
        flush(buffers);
        FluidStack fluid = entity.getSyncedFluid();
        float fill = fluid.isEmpty() ? 0.0F : (float) fluid.getAmount() / HardenedResinCauldronEntity.CAPACITY;
        this.renderItems(entity, fill, pose, buffers, packedLight);
        flush(buffers);
        if (!fluid.isEmpty()) {
            float innerMin = 0.126F;
            float innerMax = 0.874F;
            float fluidBottom = 0.251F;
            float fluidTop = fluidBottom + fill * 0.685F;
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
            flush(buffers);
        }
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, packedLight);
    }

    private static void flush(MultiBufferSource buffers) {
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    private void renderItems(
        HardenedResinCauldronEntity entity,
        float fluidFill,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        List<ItemStack> items = entity.getSyncedItems();
        if (items.isEmpty()) return;
        this.random.setSeed(ItemHandlerUtil.hash(entity.getSyncedItemHandler()));
        float randomOffset = this.random.nextIntBetweenInclusive(-25, 25);
        float y = Mth.clamp(0.31F + fluidFill * 0.56F, 0.31F, 0.81F);
        int itemCount = items.size();
        float partAngle = 360.0F / itemCount;
        int remaining = itemCount;
        for (ItemStack stack : items) {
            float angleDegrees = partAngle * remaining;
            float angle = angleDegrees * Mth.DEG_TO_RAD;
            float radius = itemCount == 1 ? 0.0F : 0.16F;
            pose.pushPose();
            pose.translate(
                0.5F + Mth.cos(angle) * radius,
                y,
                0.5F + Mth.sin(angle) * radius
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
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
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

}
