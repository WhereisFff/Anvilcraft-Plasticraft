package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;

/** 透明塑料在世界半透明阶段之后写入同一目标，并保留自身颜色进行 alpha 混合。 */
public final class ClearPlasticRenderTypes {
    private static boolean deferredPass;
    private static Vec3 deferredCamera = Vec3.ZERO;
    private static float deferredPartialTick;

    private static final RenderType BLOCK = RenderType.create(
        AnvilcraftPlasticraft.MOD_ID + ":clear_plastic_block",
        DefaultVertexFormat.BLOCK,
        VertexFormat.Mode.QUADS,
        786432,
        false,
        true,
        RenderType.CompositeState.builder()
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeTranslucentMovingBlockShader))
            .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true)
    );
    private static final RenderType FLUID = RenderType.create(
        AnvilcraftPlasticraft.MOD_ID + ":clear_plastic_fluid",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        1536,
        false,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(
                GameRenderer::getRendertypeItemEntityTranslucentCullShader
            ))
            .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, true))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            // 内部流体写入深度后，只有位于其前方的透明外壳会继续参与 alpha 混合。
            .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
            .createCompositeState(true)
    );
    private static final Function<ResourceLocation, RenderType> MOLDED = Util.memoize(texture -> RenderType.create(
        AnvilcraftPlasticraft.MOD_ID + ":clear_plastic_molded",
        DefaultVertexFormat.NEW_ENTITY,
        VertexFormat.Mode.QUADS,
        1536,
        true,
        true,
        RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(
                GameRenderer::getRendertypeItemEntityTranslucentCullShader
            ))
            .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
            .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
            .setLightmapState(RenderStateShard.LIGHTMAP)
            .setOverlayState(RenderStateShard.OVERLAY)
            .setCullState(RenderStateShard.CULL)
            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
            .createCompositeState(true)
    ));
    private ClearPlasticRenderTypes() {
    }

    public static RenderType block() {
        return BLOCK;
    }

    public static RenderType fluid() {
        return FLUID;
    }

    public static RenderType molded(ResourceLocation texture) {
        return MOLDED.apply(texture);
    }

    public static boolean isDeferredPassActive() {
        return deferredPass;
    }

    public static Vec3 deferredCamera() {
        return deferredCamera;
    }

    public static float deferredPartialTick() {
        return deferredPartialTick;
    }

    public static void beginDeferredPass(Vec3 camera, float partialTick) {
        deferredPass = true;
        deferredCamera = camera;
        deferredPartialTick = partialTick;
    }

    public static void endDeferredPass() {
        deferredPass = false;
    }
}
