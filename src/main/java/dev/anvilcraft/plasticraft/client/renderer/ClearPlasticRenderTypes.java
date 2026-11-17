package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

import java.util.function.Function;

/** 透明塑料在世界半透明阶段之后写入同一目标，并保留自身颜色进行 alpha 混合。 */
public final class ClearPlasticRenderTypes {
    private static boolean deferredPass;
    private static Vec3 deferredCamera = Vec3.ZERO;
    private static float deferredPartialTick;

    private static final RenderPipeline BLOCK_SHELL = shell("clear_block", RenderPipelines.TRANSLUCENT_BLOCK);
    private static final RenderPipeline MOLDED_SHELL = shell("clear_molded", RenderPipelines.ENTITY_TRANSLUCENT_CULL);
    private static final RenderType BLOCK = type("clear_block", BLOCK_SHELL, TextureAtlas.LOCATION_BLOCKS, false);
    private static final RenderType FLUID = type("clear_fluid", RenderPipelines.TRANSLUCENT_BLOCK, TextureAtlas.LOCATION_BLOCKS, false);
    private static final RenderType MOLDED_FLUID = type("molded_fluid", RenderPipelines.ENTITY_TRANSLUCENT_CULL, TextureAtlas.LOCATION_BLOCKS, true);
    private static final Function<Identifier, RenderType> MOLDED = Util.memoize(texture -> type("clear_molded", MOLDED_SHELL, texture, true));

    private static RenderPipeline shell(String name, RenderPipeline base) {
        return base.toBuilder().withLocation(AnvilcraftPlasticraft.of("pipeline/" + name))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)).build();
    }

    private static RenderType type(String name, RenderPipeline pipeline, Identifier texture, boolean overlay) {
        var setup = RenderSetup.builder(pipeline).withTexture("Sampler0", texture).useLightmap().sortOnUpload()
            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET);
        if (overlay) {
            setup.useOverlay();
        }
        return RenderType.create("anvilcraftplasticraft:" + name, setup.createRenderSetup());
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(BLOCK_SHELL);
        event.registerPipeline(MOLDED_SHELL);
    }

    private ClearPlasticRenderTypes() {
    }

    public static RenderType block() {
        return BLOCK;
    }

    public static RenderType fluid() {
        return FLUID;
    }

    public static RenderType moldedFluid() {
        return MOLDED_FLUID;
    }

    public static RenderType molded(Identifier texture) {
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
