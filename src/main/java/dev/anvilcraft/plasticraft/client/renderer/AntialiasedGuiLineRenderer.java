package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3f;

/** 用带透明羽化边缘的四边形绘制 GUI 粗线。 */
public final class AntialiasedGuiLineRenderer {
    private static final double LENGTH_EPSILON = 1.0E-6D;

    private AntialiasedGuiLineRenderer() {
    }

    public static void render(
        GuiGraphics graphics,
        double startX,
        double startY,
        double endX,
        double endY,
        int color,
        double width,
        double feather
    ) {
        add(graphics, graphics.bufferSource(), startX, startY, endX, endY, color, width, feather);
        graphics.flush();
    }

    public static void add(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        int color,
        double width,
        double feather
    ) {
        add(graphics, buffers, startX, startY, endX, endY, color, width, feather, 0.0D);
    }

    public static void add(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        int color,
        double width,
        double feather,
        double endExtension
    ) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double length = Math.hypot(deltaX, deltaY);
        if (length < LENGTH_EPSILON) return;
        if (endExtension > 0.0D) {
            double extensionX = deltaX / length * endExtension;
            double extensionY = deltaY / length * endExtension;
            startX -= extensionX;
            startY -= extensionY;
            endX += extensionX;
            endY += extensionY;
        }
        double normalX = -deltaY / length;
        double normalY = deltaX / length;
        double inner = width * 0.5D;
        double outer = inner + feather;
        int transparent = color & 0x00FFFFFF;
        addLineQuad(graphics, buffers, startX, startY, endX, endY, normalX, normalY, inner, color);
        addLineFeather(
            graphics, buffers, startX, startY, endX, endY, normalX, normalY,
            inner, outer, color, transparent, false
        );
        addLineFeather(
            graphics, buffers, startX, startY, endX, endY, normalX, normalY,
            inner, outer, color, transparent, true
        );
    }

    private static void addLineQuad(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        double normalX,
        double normalY,
        double width,
        int color
    ) {
        addQuad(
            graphics,
            buffers,
            startX - normalX * width,
            startY - normalY * width,
            endX - normalX * width,
            endY - normalY * width,
            endX + normalX * width,
            endY + normalY * width,
            startX + normalX * width,
            startY + normalY * width,
            color,
            color
        );
    }

    private static void addLineFeather(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double startX,
        double startY,
        double endX,
        double endY,
        double normalX,
        double normalY,
        double inner,
        double outer,
        int color,
        int transparent,
        boolean positive
    ) {
        double sign = positive ? 1.0D : -1.0D;
        int innerColor = positive ? color : transparent;
        int outerColor = positive ? transparent : color;
        addQuad(
            graphics,
            buffers,
            startX + normalX * (positive ? inner : outer) * sign,
            startY + normalY * (positive ? inner : outer) * sign,
            endX + normalX * (positive ? inner : outer) * sign,
            endY + normalY * (positive ? inner : outer) * sign,
            endX + normalX * (positive ? outer : inner) * sign,
            endY + normalY * (positive ? outer : inner) * sign,
            startX + normalX * (positive ? outer : inner) * sign,
            startY + normalY * (positive ? outer : inner) * sign,
            innerColor,
            outerColor
        );
    }

    private static void addQuad(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        double firstX,
        double firstY,
        double secondX,
        double secondY,
        double thirdX,
        double thirdY,
        double fourthX,
        double fourthY,
        int innerColor,
        int outerColor
    ) {
        Vector3f first = transform(graphics, firstX, firstY);
        Vector3f second = transform(graphics, secondX, secondY);
        Vector3f third = transform(graphics, thirdX, thirdY);
        Vector3f fourth = transform(graphics, fourthX, fourthY);
        VertexConsumer consumer = buffers.getBuffer(RenderType.guiOverlay());
        consumer.addVertex(fourth.x, fourth.y, fourth.z).setColor(outerColor);
        consumer.addVertex(third.x, third.y, third.z).setColor(outerColor);
        consumer.addVertex(second.x, second.y, second.z).setColor(innerColor);
        consumer.addVertex(first.x, first.y, first.z).setColor(innerColor);
    }

    private static Vector3f transform(GuiGraphics graphics, double x, double y) {
        return graphics.pose().last().pose().transformPosition((float) x, (float) y, 0.0F, new Vector3f());
    }
}
