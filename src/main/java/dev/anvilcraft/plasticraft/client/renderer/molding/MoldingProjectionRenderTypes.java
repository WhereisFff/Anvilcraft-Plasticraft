package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.opengl.GL11;

/**
 * 成型舱世界投影的全息层。不能把每个 Cube 单独半透明叠加:
 * 重叠处会越叠越深,内部面也会填满负形空洞。
 * 先只写深度再上色,整份模型只留下朝向相机的外表面,立体层次与不透明时一致。
 */
public final class MoldingProjectionRenderTypes {
    private static final RenderType VOLUME_DEPTH = new HologramDepthRenderType(true);
    private static final RenderType PLANE_DEPTH = new HologramDepthRenderType(false);
    private static final RenderType VOLUME_COLOR = createColorType(
        "anvilcraftplasticraft:molding_projection_color",
        RenderStateShard.CULL
    );
    private static final RenderType PLANE_COLOR = createColorType(
        "anvilcraftplasticraft:molding_projection_plane_color",
        RenderStateShard.NO_CULL
    );

    private MoldingProjectionRenderTypes() {
    }

    public static RenderType depth(boolean volume) {
        return volume ? VOLUME_DEPTH : PLANE_DEPTH;
    }

    public static RenderType color(boolean volume) {
        return volume ? VOLUME_COLOR : PLANE_COLOR;
    }

    /** 深度预通道必须在半透明上色之前提交,内部面才会被外轮廓挡住。 */
    public static void endDepth(MultiBufferSource buffers) {
        end(buffers, VOLUME_DEPTH, PLANE_DEPTH);
    }

    public static void endColor(MultiBufferSource buffers) {
        end(buffers, VOLUME_COLOR, PLANE_COLOR);
    }

    private static void end(MultiBufferSource buffers, RenderType first, RenderType second) {
        if (!(buffers instanceof MultiBufferSource.BufferSource source)) return;
        source.endBatch(first);
        source.endBatch(second);
    }

    private static RenderType createColorType(String name, RenderStateShard.CullStateShard cull) {
        return RenderType.create(
            name,
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            RenderType.BIG_BUFFER_SIZE,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                .setCullState(cull)
                .createCompositeState(false)
        );
    }

    /** 只写深度、不写颜色。同一网格稍后用半透明上色时,更远的内部面通不过深度测试。 */
    private static final class HologramDepthRenderType extends RenderType {
        private HologramDepthRenderType(boolean cull) {
            super(
                "anvilcraftplasticraft:molding_projection_depth",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                RenderType.BIG_BUFFER_SIZE,
                false,
                false,
                () -> {
                    RenderSystem.setShader(GameRenderer::getPositionColorShader);
                    RenderSystem.disableBlend();
                    RenderSystem.colorMask(false, false, false, false);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    if (cull) {
                        RenderSystem.enableCull();
                    } else {
                        RenderSystem.disableCull();
                    }
                },
                () -> {
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableCull();
                    RenderSystem.defaultBlendFunc();
                }
            );
        }
    }
}
