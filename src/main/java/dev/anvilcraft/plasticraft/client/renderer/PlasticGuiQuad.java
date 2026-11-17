package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/** 自由四边形保存变换后的 GUI 顶点和裁剪区域，供线条羽化和方向立方体使用。 */
public record PlasticGuiQuad(Vector2f[] points, int[] colors, @Nullable ScreenRectangle scissorArea,
                            @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
    public static void add(GuiGraphicsExtractor graphics, Vector2f[] points, int... colors) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        for (Vector2f point : points) {
            graphics.pose().transformPosition(point);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);
        var bounds = new ScreenRectangle(x, y, (int) Math.ceil(maxX) - x, (int) Math.ceil(maxY) - y);
        var clip = graphics.peekScissorStack();
        graphics.submitGuiElementRenderState(new PlasticGuiQuad(points, colors, clip,
            clip == null ? bounds : bounds.intersection(clip)));
    }

    @Override
    public void buildVertices(VertexConsumer buffer) {
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(this.points[i].x, this.points[i].y, 0).setColor(this.colors[i]);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
