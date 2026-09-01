package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.MoldingSceneBuilder;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprint;
import dev.anvilcraft.plasticraft.molding.blueprint.MoldingBlueprintDisk;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/** 在结构磁盘悬浮窗中渲染自包含成型蓝图。 */
public final class MoldingBlueprintDiskPreviewRenderer {
    private static final int PREVIEW_SIZE = 80;
    private static final float MODEL_SIZE = 60.0F;
    private static final float ROTATION_SPEED = 2.0F;
    private static final int MODEL_COLOR = 0xFF75BFFF;
    private static String cachedToken = "";
    private static Preview cachedPreview;

    private MoldingBlueprintDiskPreviewRenderer() {
    }

    public static boolean renderPreviewAt(GuiGraphics graphics, ItemStack stack, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return false;
        Preview preview = preview(stack);
        if (preview == null) return false;

        int previewX = mouseX - PREVIEW_SIZE / 2;
        int previewY = mouseY - PREVIEW_SIZE - 16;
        if (previewY < 0) previewY = mouseY + 30;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        if (previewX + PREVIEW_SIZE > screenWidth) previewX = screenWidth - PREVIEW_SIZE - 5;
        if (previewX < 0) previewX = 5;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0.0F, 0.0F, 5000.0F);
        graphics.fill(
            previewX - 2,
            previewY - 2,
            previewX + PREVIEW_SIZE + 2,
            previewY + PREVIEW_SIZE + 2,
            0xF0100010
        );
        graphics.renderOutline(previewX - 2, previewY - 2, PREVIEW_SIZE + 4, PREVIEW_SIZE + 4, 0x505000FF);
        renderModel(graphics, preview, previewX, previewY);
        pose.popPose();
        return true;
    }

    private static Preview preview(ItemStack stack) {
        String token = MoldingBlueprintDisk.previewToken(stack);
        if (token.equals(cachedToken)) return cachedPreview;
        cachedToken = token;
        cachedPreview = MoldingBlueprintDisk.read(stack)
            .flatMap(blueprint -> MoldingModelBounds.visible(blueprint.model())
                .map(bounds -> createPreview(blueprint, bounds)))
            .orElse(null);
        return cachedPreview;
    }

    private static Preview createPreview(MoldingBlueprint blueprint, MoldingModelBounds bounds) {
        return new Preview(
            bounds,
            MoldingSceneBuilder.buildWorldSurfaces(blueprint.modelHash().hashCode(), blueprint.model())
        );
    }

    private static void renderModel(
        GuiGraphics graphics,
        Preview preview,
        int previewX,
        int previewY
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        MoldingVec3 size = preview.bounds.sizePixels();
        MoldingVec3 center = preview.bounds.minimum().add(preview.bounds.maximum()).scale(0.5D);
        double horizontalSize = Math.max(1.0D, Math.max(size.x(), size.z()));
        double verticalSize = Math.max(1.0D, size.y());
        float scaleX = (float) (MODEL_SIZE / (horizontalSize * Mth.SQRT_OF_TWO));
        float scaleY = (float) (MODEL_SIZE / verticalSize);
        float scale = Math.min(scaleX, scaleY);

        DeltaTracker tracker = minecraft.getTimer();
        float rotation = (minecraft.level.getGameTime() + tracker.getGameTimeDeltaPartialTick(true))
            * ROTATION_SPEED + 45.0F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(previewX + PREVIEW_SIZE * 0.5F, previewY + PREVIEW_SIZE * 0.5F, 100.0F);
        pose.scale(-scale, -scale, -scale);
        pose.mulPose(Axis.XP.rotationDegrees(-30.0F));
        pose.mulPose(Axis.YP.rotationDegrees(rotation));
        pose.translate(-center.x(), -center.y(), -center.z());

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        for (EditorScenePart part : preview.mesh.parts()) {
            int[] indices = part.indices();
            for (int index : indices) {
                EditorVertex vertex = part.vertices().get(index);
                consumer.addVertex(pose.last().pose(), vertex.x(), vertex.y(), vertex.z())
                    .setColor(tint(vertex.color()));
            }
        }
        buffers.endBatch(renderType);
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
        pose.popPose();
    }

    private static int tint(int sourceColor) {
        int brightness = sourceColor >> 16 & 0xFF;
        int red = ((MODEL_COLOR >> 16) & 0xFF) * brightness / 255;
        int green = ((MODEL_COLOR >> 8) & 0xFF) * brightness / 255;
        int blue = (MODEL_COLOR & 0xFF) * brightness / 255;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private record Preview(MoldingModelBounds bounds, EditorSceneMesh mesh) {
    }
}
