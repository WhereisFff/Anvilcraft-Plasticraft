package dev.anvilcraft.plasticraft.client.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Vector2f;

/** 用带透明羽化边缘的四边形绘制 GUI 粗线。 */
public final class AntialiasedGuiLineRenderer {
    private static final double LENGTH_EPSILON = 1.0E-6D;

    private AntialiasedGuiLineRenderer() {
    }

    public static void render(
        GuiGraphicsExtractor graphics,
        double startX,
        double startY,
        double endX,
        double endY,
        int color,
        double width,
        double feather
    ) {
        add(graphics, startX, startY, endX, endY, color, width, feather);
    }

    public static void add(
        GuiGraphicsExtractor graphics,
        double startX,
        double startY,
        double endX,
        double endY,
        int color,
        double width,
        double feather
    ) {
        add(graphics, startX, startY, endX, endY, color, width, feather, 0.0D);
    }

    public static void add(
        GuiGraphicsExtractor graphics,
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
        addLineQuad(graphics, startX, startY, endX, endY, normalX, normalY, inner, color);
        addLineFeather(
            graphics, startX, startY, endX, endY, normalX, normalY,
            inner, outer, color, transparent, false
        );
        addLineFeather(
            graphics, startX, startY, endX, endY, normalX, normalY,
            inner, outer, color, transparent, true
        );
    }

    private static void addLineQuad(
        GuiGraphicsExtractor graphics,
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
        GuiGraphicsExtractor graphics,
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
        GuiGraphicsExtractor graphics,
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
        PlasticGuiQuad.add(graphics, new Vector2f[] {
            new Vector2f((float) fourthX, (float) fourthY), new Vector2f((float) thirdX, (float) thirdY),
            new Vector2f((float) secondX, (float) secondY), new Vector2f((float) firstX, (float) firstY)
        }, outerColor, outerColor, innerColor, innerColor);
    }
}
