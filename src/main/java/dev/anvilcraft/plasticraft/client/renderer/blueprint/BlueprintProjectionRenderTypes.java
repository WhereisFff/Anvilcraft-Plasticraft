package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 施工投影专用层。原版 {@code translucentMovingBlock} 会按顶点到相机的距离排序并写入深度:
 * 红石粉和它所在的顶面被拆成随视角变化的碎片,全息方块还会挡住后面的矿车和 BER。
 * 这里关闭排序、方块只写颜色;箱子/矿车先单独提交深度预通道再上色,只留下朝向相机的外轮廓。
 */
final class BlueprintProjectionRenderTypes {
    private static final RenderType HOLOGRAM_BLOCK = RenderType.create(
        "anvilcraftplasticraft:blueprint_hologram_block",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        RenderType.BIG_BUFFER_SIZE,
        false,
        false,
        RenderType.CompositeState.builder()
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeTranslucentMovingBlockShader))
            .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
            .createCompositeState(true)
    );
    private static final Map<RenderType, RenderType> COLOR_OVERLAYS = new IdentityHashMap<>();
    private static final Map<RenderType, RenderType> DEPTH_OVERLAYS = new IdentityHashMap<>();

    private BlueprintProjectionRenderTypes() {
    }

    static RenderType hologramBlock() {
        return HOLOGRAM_BLOCK;
    }

    static RenderType overlay(RenderType type) {
        if (isHologramType(type)) {
            return type;
        }
        if (usesBlockAtlas(type)) {
            return HOLOGRAM_BLOCK;
        }
        return COLOR_OVERLAYS.computeIfAbsent(type, HologramColorRenderType::new);
    }

    static boolean needsSilhouette(RenderType original, RenderType mapped) {
        return original.format() == DefaultVertexFormat.NEW_ENTITY && mapped != HOLOGRAM_BLOCK;
    }

    static RenderType silhouetteDepth(RenderType original) {
        return DEPTH_OVERLAYS.computeIfAbsent(original, HologramDepthRenderType::new);
    }

    /** 先提交深度预通道,再画半透明颜色,内部面才会被外轮廓挡住。 */
    static void endSilhouetteDepth(MultiBufferSource.BufferSource buffers) {
        List<RenderType> depthTypes = new ArrayList<>(DEPTH_OVERLAYS.values());
        for (RenderType depthType : depthTypes) {
            buffers.endBatch(depthType);
        }
    }

    private static boolean isHologramType(RenderType type) {
        return type == HOLOGRAM_BLOCK
            || type instanceof HologramColorRenderType
            || type instanceof HologramDepthRenderType;
    }

    private static boolean usesBlockAtlas(RenderType type) {
        return type == RenderType.solid()
            || type == RenderType.cutout()
            || type == RenderType.cutoutMipped()
            || type == RenderType.translucent()
            || type == RenderType.translucentMovingBlock()
            || type == RenderType.tripwire()
            || (type.format() == DefaultVertexFormat.BLOCK && type.mode() == VertexFormat.Mode.QUADS);
    }

    private static void bindHologramTarget() {
        if (Minecraft.useShaderTransparency()) {
            Minecraft.getInstance().levelRenderer.getItemEntityTarget().bindWrite(false);
        }
    }

    private static void restoreMainTarget() {
        if (Minecraft.useShaderTransparency()) {
            Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        }
    }

    /**
     * 只写深度、不写颜色。同一网格稍后用半透明上色时,更远的内部面通不过深度测试。
     */
    private static final class HologramDepthRenderType extends RenderType {
        private HologramDepthRenderType(RenderType original) {
            super(
                "anvilcraftplasticraft:blueprint_hologram_depth",
                original.format(),
                original.mode(),
                Math.max(original.bufferSize(), TRANSIENT_BUFFER_SIZE),
                false,
                false,
                () -> {
                    original.setupRenderState();
                    bindHologramTarget();
                    RenderSystem.disableBlend();
                    RenderSystem.colorMask(false, false, false, false);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    if (original.format() == DefaultVertexFormat.NEW_ENTITY) {
                        RenderSystem.setShader(GameRenderer::getRendertypeEntitySolidShader);
                    }
                },
                () -> {
                    RenderSystem.colorMask(true, true, true, true);
                    RenderSystem.depthMask(true);
                    original.clearRenderState();
                    restoreMainTarget();
                }
            );
        }
    }

    /**
     * 复用原层贴图,打开混合并关掉深度写入。
     * 实体格式改用半透明着色器,顶点 alpha 才会变淡。
     */
    private static final class HologramColorRenderType extends RenderType {
        private HologramColorRenderType(RenderType original) {
            super(
                "anvilcraftplasticraft:blueprint_hologram_overlay",
                original.format(),
                original.mode(),
                Math.max(original.bufferSize(), TRANSIENT_BUFFER_SIZE),
                false,
                false,
                () -> {
                    original.setupRenderState();
                    bindHologramTarget();
                    RenderSystem.enableBlend();
                    RenderSystem.blendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
                    );
                    RenderSystem.depthMask(false);
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    if (original.format() == DefaultVertexFormat.NEW_ENTITY) {
                        RenderSystem.setShader(GameRenderer::getRendertypeEntityTranslucentShader);
                    }
                },
                () -> {
                    RenderSystem.depthMask(true);
                    RenderSystem.defaultBlendFunc();
                    original.clearRenderState();
                    restoreMainTarget();
                }
            );
        }
    }
}
