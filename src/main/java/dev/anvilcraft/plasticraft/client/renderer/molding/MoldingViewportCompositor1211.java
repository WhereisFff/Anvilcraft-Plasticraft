package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

/** 当前版本唯一读取离屏颜色附件纹理 ID 的 GUI 合成适配器。 */
final class MoldingViewportCompositor1211 {
    void compose(GuiGraphics graphics, RenderTarget target, int x, int y, int width, int height) {
        RenderSystem.setShaderTexture(0, target.getColorTextureId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            Matrix4f matrix = graphics.pose().last().pose();
            BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS,
                DefaultVertexFormat.POSITION_TEX
            );
            builder.addVertex(matrix, x, y, 0.0F).setUv(0.0F, 1.0F);
            builder.addVertex(matrix, x, y + height, 0.0F).setUv(0.0F, 0.0F);
            builder.addVertex(matrix, x + width, y + height, 0.0F).setUv(1.0F, 0.0F);
            builder.addVertex(matrix, x + width, y, 0.0F).setUv(1.0F, 1.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally {
            RenderSystem.disableBlend();
            RenderSystem.setShaderTexture(0, 0);
        }
    }
}
