package dev.anvilcraft.plasticraft.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.ThickLineRenderer;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.item.AbstractPlasticEntityItem;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

/** 手持塑料制品时，在命中方块面显示与实际放置规则一致的四分区预览。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class PlasticPlacementPreviewRenderer {
    private static final long TRANSITION_MILLIS = 180L;
    private static final int GRID_DIVISIONS = 9;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SELECTED_GREEN = 0xFF40FF40;
    private static final int GRID_WHITE = 0x50FFFFFF;
    private static final int GRID_GREEN = 0x7040FF40;
    private static final double FACE_OUTWARD_OFFSET = 0.003D;
    private static final double GREEN_LINE_WIDTH = ThickLineRenderer.SELECTION_WIDTH + 0.001D;
    private static final double WHITE_ENDPOINT_EXTENSION = ThickLineRenderer.SELECTION_WIDTH * 0.5D;
    private static final double GREEN_ENDPOINT_EXTENSION = GREEN_LINE_WIDTH * 0.5D;
    private static final double CLIP_EPSILON = 1.0E-9D;
    private static final FacePoint FACE_CENTER = new FacePoint(0.0D, 0.0D);
    private static final List<List<FacePoint>> CHECKER_CELLS = createCheckerCells();
    private static @Nullable PreviewKey animatedTarget;
    private static @Nullable PreviewGeometry animationFrom;
    private static @Nullable PreviewGeometry animationTo;
    private static long animationStartedAt;

    private PlasticPlacementPreviewRenderer() {
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !isHoldingPlasticEntity(player)) {
            resetAnimation();
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit)
            || hit.getType() != HitResult.Type.BLOCK) {
            resetAnimation();
            return;
        }

        PlasticEntityOrientation orientation = PlasticEntityOrientation.forPlacement(hit, player);
        PreviewGeometry preview = animatedPreview(hit, orientation);
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        renderCheckerboard(pose, buffers, preview);
        renderFaceLines(pose, buffers, preview.frame());
        renderSelectedLines(pose, buffers, preview);
        pose.popPose();
    }

    private static boolean isHoldingPlasticEntity(LocalPlayer player) {
        return isPlasticEntityItem(player.getMainHandItem()) || isPlasticEntityItem(player.getOffhandItem());
    }

    private static boolean isPlasticEntityItem(ItemStack stack) {
        return stack.getItem() instanceof AbstractPlasticEntityItem<?>;
    }

    private static void renderFaceLines(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        FaceFrame frame
    ) {
        Vec3 first = frame.worldPosition(new FacePoint(0.5D, 0.5D));
        Vec3 second = frame.worldPosition(new FacePoint(0.5D, -0.5D));
        Vec3 third = frame.worldPosition(new FacePoint(-0.5D, -0.5D));
        Vec3 fourth = frame.worldPosition(new FacePoint(-0.5D, 0.5D));
        ThickLineRenderer.renderSegments(
            pose,
            buffers,
            List.of(
                new ThickLineRenderer.Segment(first, second),
                new ThickLineRenderer.Segment(second, third),
                new ThickLineRenderer.Segment(third, fourth),
                new ThickLineRenderer.Segment(fourth, first)
            ),
            WHITE,
            ThickLineRenderer.SELECTION_WIDTH,
            WHITE_ENDPOINT_EXTENSION
        );
        ThickLineRenderer.renderSegments(
            pose,
            buffers,
            List.of(
                new ThickLineRenderer.Segment(first, third),
                new ThickLineRenderer.Segment(second, fourth)
            ),
            WHITE,
            ThickLineRenderer.SELECTION_WIDTH
        );
    }

    private static void renderSelectedLines(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        PreviewGeometry preview
    ) {
        FaceFrame frame = preview.frame();
        Vec3 center = frame.center();
        Vec3 first = frame.worldPosition(preview.selection().first());
        Vec3 second = frame.worldPosition(preview.selection().second());
        ThickLineRenderer.renderSegments(
            pose,
            buffers,
            List.of(
                new ThickLineRenderer.Segment(first, second)
            ),
            SELECTED_GREEN,
            GREEN_LINE_WIDTH,
            GREEN_ENDPOINT_EXTENSION
        );
        ThickLineRenderer.renderSegments(
            pose,
            buffers,
            List.of(
                new ThickLineRenderer.Segment(center, first),
                new ThickLineRenderer.Segment(second, center)
            ),
            SELECTED_GREEN,
            GREEN_LINE_WIDTH
        );
    }

    private static void renderCheckerboard(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        PreviewGeometry preview
    ) {
        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        for (List<FacePoint> cell : CHECKER_CELLS) {
            renderCheckerCell(pose, consumer, preview, cell);
        }
        buffers.endBatch(renderType);
    }

    /** 将每个着色格切成互不重叠的白色区和绿色选区，避免共面网格互相争夺深度。 */
    private static void renderCheckerCell(
        PoseStack pose,
        VertexConsumer consumer,
        PreviewGeometry preview,
        List<FacePoint> cell
    ) {
        TrianglePoints selection = preview.selection();
        double area = signedLineDistance(FACE_CENTER, selection.first(), selection.second());
        if (Math.abs(area) <= CLIP_EPSILON) {
            addPolygon(pose, consumer, preview.frame(), cell, GRID_WHITE);
            return;
        }

        double insideSign = Math.copySign(1.0D, area);
        FacePoint[] triangle = {FACE_CENTER, selection.first(), selection.second()};
        List<FacePoint> selected = cell;
        for (int edgeIndex = 0; edgeIndex < triangle.length; edgeIndex++) {
            FacePoint edgeStart = triangle[edgeIndex];
            FacePoint edgeEnd = triangle[(edgeIndex + 1) % triangle.length];
            PolygonSplit split = splitPolygon(selected, edgeStart, edgeEnd, insideSign);
            addPolygon(pose, consumer, preview.frame(), split.outside(), GRID_WHITE);
            selected = split.inside();
            if (selected.isEmpty()) return;
        }
        addPolygon(pose, consumer, preview.frame(), selected, GRID_GREEN);
    }

    private static PolygonSplit splitPolygon(
        List<FacePoint> polygon,
        FacePoint edgeStart,
        FacePoint edgeEnd,
        double insideSign
    ) {
        if (polygon.isEmpty()) return new PolygonSplit(List.of(), List.of());
        List<FacePoint> inside = new ArrayList<>(polygon.size() + 2);
        List<FacePoint> outside = new ArrayList<>(polygon.size() + 2);
        for (int index = 0; index < polygon.size(); index++) {
            FacePoint current = polygon.get(index);
            FacePoint next = polygon.get((index + 1) % polygon.size());
            double currentDistance = signedLineDistance(edgeStart, edgeEnd, current) * insideSign;
            double nextDistance = signedLineDistance(edgeStart, edgeEnd, next) * insideSign;
            boolean currentInside = currentDistance >= -CLIP_EPSILON;
            boolean nextInside = nextDistance >= -CLIP_EPSILON;
            if (currentInside) {
                inside.add(current);
            } else {
                outside.add(current);
            }
            if (currentInside != nextInside) {
                double progress = currentDistance / (currentDistance - nextDistance);
                FacePoint intersection = current.lerp(next, progress);
                inside.add(intersection);
                outside.add(intersection);
            }
        }
        return new PolygonSplit(List.copyOf(inside), List.copyOf(outside));
    }

    private static double signedLineDistance(FacePoint lineStart, FacePoint lineEnd, FacePoint point) {
        return (lineEnd.forward() - lineStart.forward()) * (point.side() - lineStart.side())
            - (lineEnd.side() - lineStart.side()) * (point.forward() - lineStart.forward());
    }

    private static List<List<FacePoint>> createCheckerCells() {
        List<List<FacePoint>> cells = new ArrayList<>();
        double cellSize = 1.0D / GRID_DIVISIONS;
        for (int forwardIndex = 0; forwardIndex < GRID_DIVISIONS; forwardIndex++) {
            for (int sideIndex = 0; sideIndex < GRID_DIVISIONS; sideIndex++) {
                if (((forwardIndex + sideIndex) & 1) != 0) continue;
                double forwardMin = -0.5D + forwardIndex * cellSize;
                double forwardMax = forwardMin + cellSize;
                double sideMin = -0.5D + sideIndex * cellSize;
                double sideMax = sideMin + cellSize;
                cells.add(List.of(
                    new FacePoint(forwardMin, sideMin),
                    new FacePoint(forwardMax, sideMin),
                    new FacePoint(forwardMax, sideMax),
                    new FacePoint(forwardMin, sideMax)
                ));
            }
        }
        return List.copyOf(cells);
    }

    private static PreviewGeometry animatedPreview(
        BlockHitResult hit,
        PlasticEntityOrientation orientation
    ) {
        long now = Util.getMillis();
        PreviewKey targetKey = new PreviewKey(
            hit.getBlockPos().immutable(),
            hit.getDirection(),
            orientation.longAxis()
        );
        FaceFrame rawTargetFrame = faceFrame(hit);
        if (animatedTarget == null || animationFrom == null || animationTo == null) {
            PreviewGeometry target = new PreviewGeometry(
                rawTargetFrame,
                trianglePoints(rawTargetFrame, orientation)
            );
            animatedTarget = targetKey;
            animationFrom = target;
            animationTo = target;
            animationStartedAt = now;
            return target;
        }

        PreviewGeometry current = interpolatedPreview(now);
        if (!targetKey.equals(animatedTarget)) {
            FaceFrame targetFrame = rawTargetFrame.closestRotationTo(current.frame());
            TrianglePoints targetSelection = trianglePoints(targetFrame, orientation)
                .closestOrderTo(current.selection(), current.frame(), targetFrame);
            animatedTarget = targetKey;
            animationFrom = current;
            animationTo = new PreviewGeometry(targetFrame, targetSelection);
            animationStartedAt = now;
        }
        return current;
    }

    private static FaceFrame faceFrame(BlockHitResult hit) {
        PlasticEntityOrientation baseOrientation = new PlasticEntityOrientation(hit.getDirection(), 0);
        return new FaceFrame(
            faceCenter(hit),
            directionVector(baseOrientation.longAxis()),
            directionVector(baseOrientation.orthogonalAxis())
        );
    }

    private static TrianglePoints trianglePoints(
        FaceFrame frame,
        PlasticEntityOrientation orientation
    ) {
        Vec3 forward = directionVector(orientation.longAxis()).scale(0.5D);
        Vec3 side = directionVector(orientation.orthogonalAxis()).scale(0.5D);
        return new TrianglePoints(
            frame.facePoint(forward.add(side)),
            frame.facePoint(forward.subtract(side))
        );
    }

    private static PreviewGeometry interpolatedPreview(long now) {
        if (animationFrom == null || animationTo == null) {
            throw new IllegalStateException("Placement preview animation is not initialized");
        }
        double progress = Mth.clamp((now - animationStartedAt) / (double) TRANSITION_MILLIS, 0.0D, 1.0D);
        double eased = progress * progress * progress * (progress * (progress * 6.0D - 15.0D) + 10.0D);
        return animationFrom.lerp(animationTo, eased);
    }

    private static void resetAnimation() {
        animatedTarget = null;
        animationFrom = null;
        animationTo = null;
    }

    private static void addPolygon(
        PoseStack pose,
        VertexConsumer consumer,
        FaceFrame frame,
        List<FacePoint> polygon,
        int color
    ) {
        if (polygon.size() < 3) return;
        FacePoint first = polygon.getFirst();
        for (int index = 1; index < polygon.size() - 1; index++) {
            addTriangle(
                pose,
                consumer,
                frame.worldPosition(first),
                frame.worldPosition(polygon.get(index)),
                frame.worldPosition(polygon.get(index + 1)),
                color
            );
        }
    }

    private static void addTriangle(
        PoseStack pose,
        VertexConsumer consumer,
        Vec3 first,
        Vec3 second,
        Vec3 third,
        int color
    ) {
        consumer.addVertex(pose.last().pose(), (float) first.x, (float) first.y, (float) first.z)
            .setColor(color);
        consumer.addVertex(pose.last().pose(), (float) second.x, (float) second.y, (float) second.z)
            .setColor(color);
        consumer.addVertex(pose.last().pose(), (float) third.x, (float) third.y, (float) third.z)
            .setColor(color);
        consumer.addVertex(pose.last().pose(), (float) third.x, (float) third.y, (float) third.z)
            .setColor(color);
    }

    private static Vec3 faceCenter(BlockHitResult hit) {
        Direction face = hit.getDirection();
        return Vec3.atCenterOf(hit.getBlockPos()).add(
            face.getStepX() * (0.5D + FACE_OUTWARD_OFFSET),
            face.getStepY() * (0.5D + FACE_OUTWARD_OFFSET),
            face.getStepZ() * (0.5D + FACE_OUTWARD_OFFSET)
        );
    }

    private static Vec3 directionVector(Direction direction) {
        return Vec3.atLowerCornerOf(direction.getNormal());
    }

    private record FacePoint(double forward, double side) {
        private FacePoint lerp(FacePoint other, double progress) {
            return new FacePoint(
                this.forward + (other.forward - this.forward) * progress,
                this.side + (other.side - this.side) * progress
            );
        }
    }

    private record PolygonSplit(List<FacePoint> inside, List<FacePoint> outside) {
    }

    private record FaceFrame(Vec3 center, Vec3 forwardAxis, Vec3 sideAxis) {
        private Vec3 worldPosition(FacePoint point) {
            return this.center
                .add(this.forwardAxis.scale(point.forward()))
                .add(this.sideAxis.scale(point.side()));
        }

        private FacePoint facePoint(Vec3 offset) {
            return new FacePoint(offset.dot(this.forwardAxis), offset.dot(this.sideAxis));
        }

        private FaceFrame lerp(FaceFrame other, double progress) {
            return new FaceFrame(
                this.center.lerp(other.center, progress),
                this.forwardAxis.lerp(other.forwardAxis, progress),
                this.sideAxis.lerp(other.sideAxis, progress)
            );
        }

        /** 方形在旋转四分之一圈后几何不变，选择位移最短的顶点对应关系。 */
        private FaceFrame closestRotationTo(FaceFrame reference) {
            FaceFrame[] candidates = {
                this,
                new FaceFrame(this.center, this.sideAxis, this.forwardAxis.scale(-1.0D)),
                new FaceFrame(this.center, this.forwardAxis.scale(-1.0D), this.sideAxis.scale(-1.0D)),
                new FaceFrame(this.center, this.sideAxis.scale(-1.0D), this.forwardAxis)
            };
            FaceFrame closest = candidates[0];
            double closestDistance = closest.axisDistanceTo(reference);
            for (int index = 1; index < candidates.length; index++) {
                double distance = candidates[index].axisDistanceTo(reference);
                if (distance < closestDistance) {
                    closest = candidates[index];
                    closestDistance = distance;
                }
            }
            return closest;
        }

        private double axisDistanceTo(FaceFrame other) {
            return this.forwardAxis.distanceToSqr(other.forwardAxis)
                + this.sideAxis.distanceToSqr(other.sideAxis);
        }
    }

    private record TrianglePoints(FacePoint first, FacePoint second) {
        private TrianglePoints lerp(TrianglePoints other, double progress) {
            return new TrianglePoints(
                this.first.lerp(other.first, progress),
                this.second.lerp(other.second, progress)
            );
        }

        private TrianglePoints closestOrderTo(
            TrianglePoints reference,
            FaceFrame referenceFrame,
            FaceFrame targetFrame
        ) {
            Vec3 referenceFirst = referenceFrame.worldPosition(reference.first);
            Vec3 referenceSecond = referenceFrame.worldPosition(reference.second);
            Vec3 targetFirst = targetFrame.worldPosition(this.first);
            Vec3 targetSecond = targetFrame.worldPosition(this.second);
            double directDistance = targetFirst.distanceToSqr(referenceFirst)
                + targetSecond.distanceToSqr(referenceSecond);
            double swappedDistance = targetSecond.distanceToSqr(referenceFirst)
                + targetFirst.distanceToSqr(referenceSecond);
            return swappedDistance < directDistance ? new TrianglePoints(this.second, this.first) : this;
        }
    }

    private record PreviewGeometry(FaceFrame frame, TrianglePoints selection) {
        private PreviewGeometry lerp(PreviewGeometry other, double progress) {
            return new PreviewGeometry(
                this.frame.lerp(other.frame, progress),
                this.selection.lerp(other.selection, progress)
            );
        }
    }

    private record PreviewKey(BlockPos pos, Direction face, Direction selectedDirection) {
    }
}
