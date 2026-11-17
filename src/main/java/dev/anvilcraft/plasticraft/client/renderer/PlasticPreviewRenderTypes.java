package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.client.init.ModRenderPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.function.Function;

/** 为运行时生成纹理复用 AnvilCraft 标准塑料预览着色器。 */
public final class PlasticPreviewRenderTypes {
    private static final RenderPipeline COLORED_PIPELINE = RenderPipelines.DEBUG_QUADS.toBuilder()
        .withLocation(AnvilcraftPlasticraft.of("pipeline/preview_quads"))
        .withDepthStencilState(DepthStencilState.DEFAULT)
        .build();
    private static final RenderType COLORED_QUADS = RenderType.create("anvilcraftplasticraft:preview_quads",
        RenderSetup.builder(COLORED_PIPELINE).sortOnUpload().createRenderSetup());

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(COLORED_PIPELINE);
    }

    /** 同一预览的前后表面依赖深度写入，避免背面颜色覆盖遮挡它的正面。 */
    public static RenderType coloredQuads() {
        return COLORED_QUADS;
    }

    private static final Function<Identifier, RenderType> MOLDED_PREVIEW = Util.memoize(texture ->
        RenderType.create("anvilcraftplasticraft:molded_plastic_preview",
            RenderSetup.builder(ModRenderPipelines.COLORED_OVERLAY).useLightmap().sortOnUpload()
                .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .bufferSize(786432).createRenderSetup())
    );

    private PlasticPreviewRenderTypes() {
    }

    public static RenderType moldedPreview(Identifier texture) {
        return MOLDED_PREVIEW.apply(texture);
    }
}
