package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Function;

/** 为运行时生成纹理复用 AnvilCraft 标准塑料预览着色器。 */
public final class PlasticPreviewRenderTypes {
    private static final ResourceLocation SHADER = AnvilcraftPlasticraft.of("rendertype_plastic_preview");
    private static final RenderStateShard.ShaderStateShard PREVIEW_SHADER =
        new RenderStateShard.ShaderStateShard(PlasticPreviewRenderTypes::requireShader) {
            @Override
            public void setupRenderState() {
                ShaderInstance current = requireShader();
                current.safeGetUniform("OverlayColor").set(
                    0x66 / 255.0F,
                    0xCC / 255.0F,
                    1.0F,
                    0xDD / 255.0F
                );
                RenderSystem.setShader(() -> current);
            }
        };
    private static final Function<ResourceLocation, RenderType> MOLDED_PREVIEW = Util.memoize(texture ->
        RenderType.create(
            "anvilcraftplasticraft:molded_plastic_preview",
            DefaultVertexFormat.BLOCK,
            VertexFormat.Mode.QUADS,
            786432,
            true,
            true,
            RenderType.CompositeState.builder()
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setShaderState(PREVIEW_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setOutputState(RenderStateShard.TRANSLUCENT_TARGET)
                .setCullState(RenderStateShard.CULL)
                .createCompositeState(true)
        )
    );

    private static ShaderInstance shader;

    private PlasticPreviewRenderTypes() {
    }

    public static void registerShader(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(event.getResourceProvider(), SHADER, DefaultVertexFormat.BLOCK),
                loaded -> shader = loaded
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load the molded plastic preview shader", exception);
        }
    }

    public static RenderType moldedPreview(ResourceLocation texture) {
        return MOLDED_PREVIEW.apply(Objects.requireNonNull(texture, "texture"));
    }

    private static ShaderInstance requireShader() {
        return Objects.requireNonNull(shader, "Molded plastic preview shader is unavailable");
    }
}
