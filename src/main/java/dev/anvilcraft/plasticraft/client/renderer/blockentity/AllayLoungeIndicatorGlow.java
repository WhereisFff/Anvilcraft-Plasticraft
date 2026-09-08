package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.allay.AllayLoungeStatus;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

final class AllayLoungeIndicatorGlow {
    private static final float[] X = {-2.25F / 16, -1.0F / 16, 0, 1.0F / 16, 2.25F / 16};
    private static final float[] Y = {-1.5F / 16, -0.5F / 16, 0, 0.5F / 16, 1.5F / 16};
    private static final float[] INTENSITY = {0, 0.45F, 1, 0.45F, 0};
    private static final RenderType GLOW = RenderType.create(
        "anvilcraftplasticraft:allay_lounge_indicator_glow",
        DefaultVertexFormat.POSITION_COLOR,
        VertexFormat.Mode.QUADS,
        4096,
        false,
        false,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .setCullState(RenderStateShard.CULL)
            .createCompositeState(false)
    );

    private AllayLoungeIndicatorGlow() {
    }

    static void render(AllayLoungeStatus status, PoseStack pose, MultiBufferSource buffers) {
        int color = switch (status) {
            case IDLE -> 0x8CDEFF;
            case RUNNING -> 0xA6FFB8;
            case INTERRUPTED -> 0xFFAAA0;
        };
        VertexConsumer vertices = buffers.getBuffer(GLOW);
        pose.pushPose();
        pose.translate(0.5F, 13.5F / 16, 0.5F);
        for (int side = 0; side < 4; side++) {
            for (int x = 0; x < X.length - 1; x++) {
                for (int y = 0; y < Y.length - 1; y++) {
                    vertex(vertices, pose.last(), x, y, color);
                    vertex(vertices, pose.last(), x, y + 1, color);
                    vertex(vertices, pose.last(), x + 1, y + 1, color);
                    vertex(vertices, pose.last(), x + 1, y, color);
                }
            }
            pose.mulPose(Axis.YP.rotationDegrees(90));
        }
        pose.popPose();
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose, int x, int y, int color) {
        // 稍微前移避免与灯面争抢深度；加色光晕只写颜色，让灯芯提亮且不遮挡后续透明内容。
        vertices.addVertex(pose, X[x], Y[y], -8.76F / 16)
            .setColor(color >> 16 & 255, color >> 8 & 255, color & 255,
                Math.round(96 * INTENSITY[x] * INTENSITY[y]));
    }
}
