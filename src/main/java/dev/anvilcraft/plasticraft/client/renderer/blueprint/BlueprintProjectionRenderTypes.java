package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 保留原始材质状态,只把全息颜色改送到投影目标。透明排序仍由经典区段后端完成。
 */
final class BlueprintProjectionRenderTypes {
    private static final int MAX_MATERIAL_WRAPPERS = 512;
    private static final RenderType CONSTRUCTION_ZONE = RenderType.create(
        "anvilcraftplasticraft:blueprint_construction_zone",
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
            .setCullState(RenderStateShard.NO_CULL)
            .createCompositeState(false)
    );
    private static final Map<RenderType, RenderType> COLOR_OVERLAYS = new LinkedHashMap<>();

    private BlueprintProjectionRenderTypes() {
    }

    static RenderType constructionZone() {
        return CONSTRUCTION_ZONE;
    }

    static RenderType overlay(RenderType type) {
        RenderType cached = COLOR_OVERLAYS.get(type);
        if (cached != null) return cached;
        if (COLOR_OVERLAYS.size() >= MAX_MATERIAL_WRAPPERS) {
            COLOR_OVERLAYS.remove(COLOR_OVERLAYS.keySet().iterator().next());
        }
        RenderType created = new HologramColorRenderType(type);
        COLOR_OVERLAYS.put(type, created);
        return created;
    }

    static void setupDelivered(RenderType original) {
        original.setupRenderState();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        if (needsProjectionNoCull(original)) RenderSystem.disableCull();
    }

    static void clearDelivered(RenderType original) {
        if (needsProjectionNoCull(original)) RenderSystem.enableCull();
        original.clearRenderState();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    static void setupInWorld(RenderType original) {
        original.setupRenderState();
        if (needsProjectionNoCull(original)) RenderSystem.disableCull();
    }

    static void clearInWorld(RenderType original) {
        if (needsProjectionNoCull(original)) RenderSystem.enableCull();
        original.clearRenderState();
    }

    static void clearCache() {
        COLOR_OVERLAYS.clear();
    }

    private static boolean needsProjectionNoCull(RenderType original) {
        return original.format() == DefaultVertexFormat.BLOCK && original.sortOnUpload();
    }

    private static void setupProjectionBlend() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );
    }

    /**
     * 输出目标与预乘混合之外不替换着色器、深度或材质层。标准透明方块临时关闭背面剔除,
     * 让相机进入玻璃等投影内部时仍能看到内表面。
     */
    private static final class HologramColorRenderType extends RenderType {
        private HologramColorRenderType(RenderType original) {
            super(
                "anvilcraftplasticraft:blueprint_hologram_overlay",
                original.format(),
                original.mode(),
                Math.max(original.bufferSize(), TRANSIENT_BUFFER_SIZE),
                original.affectsCrumbling(),
                original.sortOnUpload(),
                () -> {
                    original.setupRenderState();
                    BlueprintProjectionTarget1211.bindWrite();
                    setupProjectionBlend();
                    if (needsProjectionNoCull(original)) RenderSystem.disableCull();
                },
                () -> {
                    if (needsProjectionNoCull(original)) RenderSystem.enableCull();
                    original.clearRenderState();
                }
            );
        }
    }
}
