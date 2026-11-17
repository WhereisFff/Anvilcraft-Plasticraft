package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 成型舱世界投影的全息层。不能把每个 Cube 单独半透明叠加:
 * 重叠处会越叠越深,内部面也会填满负形空洞。
 * 先只写深度再上色,整份模型只留下朝向相机的外表面,立体层次与不透明时一致。
 */
public final class MoldingProjectionRenderTypes {
    private static final List<RenderPipeline> PIPELINES = new ArrayList<>();
    private static final RenderType VOLUME_DEPTH = projection("volume_depth", true, true);
    private static final RenderType PLANE_DEPTH = projection("plane_depth", false, true);
    private static final RenderType VOLUME_COLOR = projection("volume_color", true, false);
    private static final RenderType PLANE_COLOR = projection("plane_color", false, false);

    private static RenderType projection(String name, boolean cull, boolean depthOnly) {
        RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(AnvilcraftPlasticraft.of("pipeline/projection_" + name))
            .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
            .withCull(cull).withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, depthOnly))
            .withColorTargetState(depthOnly ? new ColorTargetState(Optional.empty(), ColorTargetState.WRITE_NONE)
                : new ColorTargetState(BlendFunction.TRANSLUCENT)).build();
        PIPELINES.add(pipeline);
        return RenderType.create("anvilcraftplasticraft:projection_" + name, RenderSetup.builder(pipeline).createRenderSetup());
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        PIPELINES.forEach(event::registerPipeline);
    }

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

}
