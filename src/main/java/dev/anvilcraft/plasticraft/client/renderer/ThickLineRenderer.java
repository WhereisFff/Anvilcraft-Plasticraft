package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 使用四棱柱渲染不受显卡线宽限制的粗线。 */
public final class ThickLineRenderer {
    public static final double SELECTION_WIDTH = 0.035D;
    private static final double LENGTH_EPSILON = 1.0E-8D;

    private ThickLineRenderer() {
    }

    public static void render(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        List<Vec3> points,
        int color,
        double width
    ) {
        if (points.size() < 2) return;
        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        render(pose, consumer, points, color, width);
        buffers.endBatch(renderType);
    }

    public static void renderSegments(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        List<Segment> segments,
        int color,
        double width
    ) {
        if (segments.isEmpty()) return;
        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        for (Segment segment : segments) {
            renderSegment(pose, consumer, segment.from(), segment.to(), color, width);
        }
        buffers.endBatch(renderType);
    }

    public static void renderDashed(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        List<Vec3> points,
        int color,
        double width,
        double dashLength,
        double gapLength,
        double phaseDistance
    ) {
        if (points.size() < 2 || dashLength <= LENGTH_EPSILON) return;
        if (gapLength <= LENGTH_EPSILON) {
            render(pose, buffers, points, color, width);
            return;
        }

        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        double patternLength = dashLength + gapLength;
        double patternPosition = positiveModulo(-phaseDistance, patternLength);
        boolean drawing = patternPosition < dashLength;
        double patternRemaining = drawing
            ? dashLength - patternPosition
            : patternLength - patternPosition;

        for (int index = 1; index < points.size(); index++) {
            Vec3 segmentStart = points.get(index - 1);
            Vec3 segmentEnd = points.get(index);
            Vec3 delta = segmentEnd.subtract(segmentStart);
            double segmentLength = delta.length();
            if (segmentLength <= LENGTH_EPSILON) continue;
            Vec3 direction = delta.scale(1.0D / segmentLength);
            double segmentOffset = 0.0D;
            while (segmentOffset < segmentLength - LENGTH_EPSILON) {
                double step = Math.min(patternRemaining, segmentLength - segmentOffset);
                if (drawing && step > LENGTH_EPSILON) {
                    Vec3 from = segmentOffset <= LENGTH_EPSILON
                        ? segmentStart
                        : segmentStart.add(direction.scale(segmentOffset));
                    double endOffset = segmentOffset + step;
                    Vec3 to = endOffset >= segmentLength - LENGTH_EPSILON
                        ? segmentEnd
                        : segmentStart.add(direction.scale(endOffset));
                    renderSegment(pose, consumer, from, to, color, width);
                }
                segmentOffset += step;
                patternRemaining -= step;
                if (patternRemaining <= LENGTH_EPSILON) {
                    drawing = !drawing;
                    patternRemaining = drawing ? dashLength : gapLength;
                }
            }
        }
        buffers.endBatch(renderType);
    }

    private static void render(
        PoseStack pose,
        VertexConsumer consumer,
        List<Vec3> points,
        int color,
        double width
    ) {
        Vec3[] previousRing = null;
        Vec3 previousSide = null;
        for (int index = 0; index < points.size(); index++) {
            Vec3 tangent;
            if (index == 0) {
                tangent = points.get(1).subtract(points.getFirst()).normalize();
            } else if (index == points.size() - 1) {
                tangent = points.getLast().subtract(points.get(index - 1)).normalize();
            } else {
                tangent = points.get(index + 1).subtract(points.get(index - 1)).normalize();
            }
            if (tangent.lengthSqr() < 1.0E-8D) continue;
            Vec3 side;
            if (previousSide == null) {
                Vec3 reference = Math.abs(tangent.y) < 0.9D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
                side = tangent.cross(reference).normalize();
            } else {
                side = previousSide.subtract(tangent.scale(previousSide.dot(tangent)));
                if (side.lengthSqr() < 1.0E-8D) {
                    Vec3 reference = Math.abs(tangent.y) < 0.9D
                        ? new Vec3(0.0D, 1.0D, 0.0D)
                        : new Vec3(1.0D, 0.0D, 0.0D);
                    side = tangent.cross(reference);
                }
                side = side.normalize();
            }
            previousSide = side;
            side = side.scale(width * 0.5D);
            Vec3 up = tangent.cross(side).normalize().scale(width * 0.5D);
            Vec3 point = points.get(index);
            Vec3[] ring = {
                point.add(side).add(up),
                point.add(side).subtract(up),
                point.subtract(side).subtract(up),
                point.subtract(side).add(up)
            };
            if (previousRing != null) {
                for (int face = 0; face < 4; face++) {
                    int next = (face + 1) & 3;
                    addQuad(pose, consumer, previousRing[face], previousRing[next], ring[next], ring[face], color);
                }
            } else {
                addQuad(pose, consumer, ring[3], ring[2], ring[1], ring[0], color);
            }
            previousRing = ring;
        }
        if (previousRing != null) {
            addQuad(pose, consumer, previousRing[0], previousRing[1], previousRing[2], previousRing[3], color);
        }
    }

    private static void renderSegment(
        PoseStack pose,
        VertexConsumer consumer,
        Vec3 from,
        Vec3 to,
        int color,
        double width
    ) {
        Vec3 tangent = to.subtract(from);
        if (tangent.lengthSqr() < LENGTH_EPSILON) return;
        tangent = tangent.normalize();
        Vec3 reference = Math.abs(tangent.y) < 0.9D
            ? new Vec3(0.0D, 1.0D, 0.0D)
            : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 side = tangent.cross(reference).normalize().scale(width * 0.5D);
        Vec3 up = tangent.cross(side).normalize().scale(width * 0.5D);
        Vec3[] fromRing = ring(from, side, up);
        Vec3[] toRing = ring(to, side, up);
        addQuad(pose, consumer, fromRing[3], fromRing[2], fromRing[1], fromRing[0], color);
        for (int face = 0; face < 4; face++) {
            int next = (face + 1) & 3;
            addQuad(pose, consumer, fromRing[face], fromRing[next], toRing[next], toRing[face], color);
        }
        addQuad(pose, consumer, toRing[0], toRing[1], toRing[2], toRing[3], color);
    }

    private static Vec3[] ring(Vec3 point, Vec3 side, Vec3 up) {
        return new Vec3[] {
            point.add(side).add(up),
            point.add(side).subtract(up),
            point.subtract(side).subtract(up),
            point.subtract(side).add(up)
        };
    }

    private static double positiveModulo(double value, double divisor) {
        double result = value % divisor;
        return result < 0.0D ? result + divisor : result;
    }

    private static void addQuad(
        PoseStack pose,
        VertexConsumer consumer,
        Vec3 first,
        Vec3 second,
        Vec3 third,
        Vec3 fourth,
        int color
    ) {
        consumer.addVertex(pose.last().pose(), (float) first.x, (float) first.y, (float) first.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) second.x, (float) second.y, (float) second.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) third.x, (float) third.y, (float) third.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) fourth.x, (float) fourth.y, (float) fourth.z).setColor(color);
    }

    public record Segment(Vec3 from, Vec3 to) {
    }
}
