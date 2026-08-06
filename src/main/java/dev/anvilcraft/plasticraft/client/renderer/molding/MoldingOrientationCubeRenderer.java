package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportTransform;
import dev.anvilcraft.plasticraft.client.renderer.AntialiasedGuiLineRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** 在小型 GUI 区域内绘制与建模相机朝向同步的正交方向立方体。 */
public final class MoldingOrientationCubeRenderer {
    private static final double HALF_WIDTH = 8.0D;
    private static final double HALF_HEIGHT = 7.0D;
    private static final double LINE_WIDTH = 0.65D;
    private static final double LINE_FEATHER = 0.25D;
    private static final double PROJECTION_EPSILON = 1.0E-9D;
    private static final int EDGE_COLOR = 0xFF41464A;
    private static final int X_COLOR = 0xFFEC4B4B;
    private static final int Y_COLOR = 0xFF55C86A;
    private static final int Z_COLOR = 0xFF4C83E8;
    private static final Vector3d[] CORNERS = {
        new Vector3d(-HALF_WIDTH, -HALF_HEIGHT, -HALF_WIDTH),
        new Vector3d(HALF_WIDTH, -HALF_HEIGHT, -HALF_WIDTH),
        new Vector3d(-HALF_WIDTH, HALF_HEIGHT, -HALF_WIDTH),
        new Vector3d(HALF_WIDTH, HALF_HEIGHT, -HALF_WIDTH),
        new Vector3d(-HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH),
        new Vector3d(HALF_WIDTH, -HALF_HEIGHT, HALF_WIDTH),
        new Vector3d(-HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH),
        new Vector3d(HALF_WIDTH, HALF_HEIGHT, HALF_WIDTH)
    };
    private static final Edge[] EDGES = {
        new Edge(0, 1), new Edge(2, 3), new Edge(4, 5), new Edge(6, 7),
        new Edge(0, 2), new Edge(1, 3), new Edge(4, 6), new Edge(5, 7),
        new Edge(0, 4), new Edge(1, 5), new Edge(2, 6), new Edge(3, 7)
    };

    private MoldingOrientationCubeRenderer() {
    }

    public static void render(
        GuiGraphics graphics,
        ViewportTransform transform,
        double x,
        double y,
        double width,
        double height
    ) {
        Vector3d[] projected = new Vector3d[CORNERS.length];
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        int frontCorner = 0;
        for (int index = 0; index < CORNERS.length; index++) {
            Vector3d point = transform.directionInView(CORNERS[index]);
            projected[index] = point;
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minY = Math.min(minY, point.y);
            maxY = Math.max(maxY, point.y);
            if (point.z < projected[frontCorner].z) frontCorner = index;
        }

        int rearCorner = frontCorner ^ 7;
        double outerRadius = Math.hypot(LINE_WIDTH * 0.5D, LINE_WIDTH * 0.5D + LINE_FEATHER);
        double contentWidth = Math.max(PROJECTION_EPSILON, width - outerRadius * 2.0D);
        double contentHeight = Math.max(PROJECTION_EPSILON, height - outerRadius * 2.0D);
        double scale = Math.min(contentWidth / (maxX - minX), contentHeight / (maxY - minY));
        double centerX = x + width * 0.5D - (minX + maxX) * 0.5D * scale;
        double centerY = y + height * 0.5D - (minY + maxY) * 0.5D * scale;
        MultiBufferSource.BufferSource buffers = graphics.bufferSource();
        addFrontFaces(graphics, buffers, projected, frontCorner, centerX, centerY, scale);
        graphics.flush();
        for (Edge edge : EDGES) {
            if (edge.includes(rearCorner)) continue;
            Vector3d from = projected[edge.from];
            Vector3d to = projected[edge.to];
            AntialiasedGuiLineRenderer.add(
                graphics,
                buffers,
                centerX + from.x * scale,
                centerY + from.y * scale,
                centerX + to.x * scale,
                centerY + to.y * scale,
                EDGE_COLOR,
                LINE_WIDTH,
                LINE_FEATHER,
                LINE_WIDTH * 0.5D
            );
        }
        graphics.flush();
    }

    private static void addFrontFaces(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        Vector3d[] projected,
        int frontCorner,
        double centerX,
        double centerY,
        double scale
    ) {
        addFace(
            graphics,
            buffers,
            projected,
            (frontCorner & 1) == 0 ? new int[] {0, 2, 6, 4} : new int[] {1, 5, 7, 3},
            centerX,
            centerY,
            scale,
            X_COLOR
        );
        addFace(
            graphics,
            buffers,
            projected,
            (frontCorner & 2) == 0 ? new int[] {0, 4, 5, 1} : new int[] {2, 3, 7, 6},
            centerX,
            centerY,
            scale,
            Y_COLOR
        );
        addFace(
            graphics,
            buffers,
            projected,
            (frontCorner & 4) == 0 ? new int[] {0, 1, 3, 2} : new int[] {4, 6, 7, 5},
            centerX,
            centerY,
            scale,
            Z_COLOR
        );
    }

    private static void addFace(
        GuiGraphics graphics,
        MultiBufferSource.BufferSource buffers,
        Vector3d[] projected,
        int[] corners,
        double centerX,
        double centerY,
        double scale,
        int color
    ) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.guiOverlay());
        for (int index = corners.length - 1; index >= 0; index--) {
            Vector3d point = projected[corners[index]];
            Vector3f transformed = graphics.pose().last().pose().transformPosition(
                (float) (centerX + point.x * scale),
                (float) (centerY + point.y * scale),
                0.0F,
                new Vector3f()
            );
            consumer.addVertex(transformed.x, transformed.y, transformed.z).setColor(color);
        }
    }

    private record Edge(int from, int to) {
        private boolean includes(int corner) {
            return this.from == corner || this.to == corner;
        }
    }
}
