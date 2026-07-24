package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.dubhe.anvilcraft.client.init.ModRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;

/** 以多层加法混合火舌绘制高热燃料的蓝白色喷焰。 */
public final class HighHeatFuelFlameRenderer {
    private static final int SEGMENTS = 8;
    private static final int RIBBONS = 3;

    private HighHeatFuelFlameRenderer() {
    }

    public static void render(
        PoseStack poseStack,
        MultiBufferSource buffers,
        float surfaceY,
        float surfaceHalfWidth,
        float animationTime,
        long seed
    ) {
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.TRACTOR_BEAM);
        render(poseStack, consumer, surfaceY, surfaceHalfWidth, animationTime, seed, 1.0F);
    }

    public static void render(
        PoseStack poseStack,
        VertexConsumer consumer,
        float surfaceY,
        float surfaceHalfWidth,
        float animationTime,
        long seed,
        float heightScale
    ) {
        render(poseStack, consumer, surfaceY, surfaceHalfWidth, animationTime, seed, heightScale, false);
    }

    public static void render(
        PoseStack poseStack,
        VertexConsumer consumer,
        float surfaceY,
        float surfaceHalfWidth,
        float animationTime,
        long seed,
        float heightScale,
        boolean doubleSided
    ) {
        PoseStack.Pose pose = poseStack.last();
        float seedPhase = (seed & 0xFFFFL) * 0.013F;
        float pulse = Mth.sin(animationTime * 0.43F + seedPhase);
        float height = (1.90F + pulse * 0.10F) * heightScale;

        renderLayer(consumer, pose, surfaceY, height * 0.92F, surfaceHalfWidth,
            0.035F, 0.12F, 0.18F, animationTime, seedPhase + 0.7F, doubleSided);
        renderLayer(consumer, pose, surfaceY, height, surfaceHalfWidth * 0.68F,
            0.075F, 0.20F, 0.27F, animationTime, seedPhase + 2.1F, doubleSided);
        renderLayer(consumer, pose, surfaceY, height * 0.84F, surfaceHalfWidth * 0.34F,
            0.22F, 0.29F, 0.32F, animationTime, seedPhase + 4.3F, doubleSided);
    }

    private static void renderLayer(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float surfaceY,
        float height,
        float baseWidth,
        float red,
        float green,
        float blue,
        float animationTime,
        float phase,
        boolean doubleSided
    ) {
        for (int ribbon = 0; ribbon < RIBBONS; ribbon++) {
            float angle = ribbon * Mth.TWO_PI / RIBBONS + animationTime * 0.035F + phase * 0.2F;
            float axisX = Mth.cos(angle);
            float axisZ = Mth.sin(angle);
            for (int segment = 0; segment < SEGMENTS; segment++) {
                float lowerProgress = (float) segment / SEGMENTS;
                float upperProgress = (float) (segment + 1) / SEGMENTS;
                FlameSlice lower = slice(
                    surfaceY,
                    height,
                    baseWidth,
                    lowerProgress,
                    animationTime,
                    phase + ribbon * 1.9F
                );
                FlameSlice upper = slice(
                    surfaceY,
                    height,
                    baseWidth,
                    upperProgress,
                    animationTime,
                    phase + ribbon * 1.9F
                );
                float lowerFade = flameFade(lowerProgress);
                float upperFade = flameFade(upperProgress);
                emitRibbonSegment(
                    consumer,
                    pose,
                    lower,
                    upper,
                    axisX,
                    axisZ,
                    red * lowerFade,
                    green * lowerFade,
                    blue * lowerFade,
                    red * upperFade,
                    green * upperFade,
                    blue * upperFade,
                    doubleSided
                );
            }
        }
    }

    private static FlameSlice slice(
        float surfaceY,
        float height,
        float baseWidth,
        float progress,
        float animationTime,
        float phase
    ) {
        float wave = Mth.sin(animationTime * 0.58F + phase + progress * 7.5F);
        float crossWave = Mth.sin(animationTime * 0.41F + phase * 1.7F + progress * 5.2F);
        float sway = baseWidth * progress * 0.42F;
        float centerX = 0.5F + wave * sway;
        float centerZ = 0.5F + crossWave * sway;
        float taper = (float) Math.pow(1.0F - progress, 0.72D);
        float flutter = 1.0F
            + progress * 0.18F * Mth.sin(animationTime * 0.77F + phase + progress * 11.0F);
        return new FlameSlice(centerX, surfaceY + height * progress, centerZ, baseWidth * taper * flutter);
    }

    private static float flameFade(float progress) {
        float tipFade = 1.0F - progress;
        float baseFade = Math.min(1.0F, progress * 8.0F + 0.25F);
        return tipFade * baseFade;
    }

    private static void emitRibbonSegment(
        VertexConsumer consumer,
        PoseStack.Pose pose,
        FlameSlice lower,
        FlameSlice upper,
        float axisX,
        float axisZ,
        float lowerRed,
        float lowerGreen,
        float lowerBlue,
        float upperRed,
        float upperGreen,
        float upperBlue,
        boolean doubleSided
    ) {
        float lowerLeftX = lower.centerX() - axisX * lower.halfWidth();
        float lowerLeftZ = lower.centerZ() - axisZ * lower.halfWidth();
        float lowerRightX = lower.centerX() + axisX * lower.halfWidth();
        float lowerRightZ = lower.centerZ() + axisZ * lower.halfWidth();
        float upperLeftX = upper.centerX() - axisX * upper.halfWidth();
        float upperLeftZ = upper.centerZ() - axisZ * upper.halfWidth();
        float upperRightX = upper.centerX() + axisX * upper.halfWidth();
        float upperRightZ = upper.centerZ() + axisZ * upper.halfWidth();

        consumer.addVertex(pose, lowerLeftX, lower.y(), lowerLeftZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
        consumer.addVertex(pose, lowerRightX, lower.y(), lowerRightZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
        consumer.addVertex(pose, upperRightX, upper.y(), upperRightZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        consumer.addVertex(pose, lowerLeftX, lower.y(), lowerLeftZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
        consumer.addVertex(pose, upperRightX, upper.y(), upperRightZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        consumer.addVertex(pose, upperLeftX, upper.y(), upperLeftZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        if (!doubleSided) return;

        consumer.addVertex(pose, upperRightX, upper.y(), upperRightZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        consumer.addVertex(pose, lowerRightX, lower.y(), lowerRightZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
        consumer.addVertex(pose, lowerLeftX, lower.y(), lowerLeftZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
        consumer.addVertex(pose, upperLeftX, upper.y(), upperLeftZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        consumer.addVertex(pose, upperRightX, upper.y(), upperRightZ)
            .setColor(upperRed, upperGreen, upperBlue, 1.0F);
        consumer.addVertex(pose, lowerLeftX, lower.y(), lowerLeftZ)
            .setColor(lowerRed, lowerGreen, lowerBlue, 1.0F);
    }

    private record FlameSlice(float centerX, float y, float centerZ, float halfWidth) {
    }
}
