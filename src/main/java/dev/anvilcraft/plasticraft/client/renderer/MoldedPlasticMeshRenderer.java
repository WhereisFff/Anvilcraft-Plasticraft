package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** 物品、实体和胶合方块态共用的动态制品网格渲染。 */
public final class MoldedPlasticMeshRenderer {
    private static final float PIXELS_PER_BLOCK = 16.0F;

    private MoldedPlasticMeshRenderer() {
    }

    public static RenderType render(
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int color,
        boolean translucent
    ) {
        ResourceLocation texture = DynamicPlasticTextureManager.INSTANCE.texture(data);
        RenderType renderType = translucent
            ? RenderType.entityTranslucent(texture)
            : RenderType.entityCutoutNoCull(texture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        List<MoldingQuad> quads = data.surfaceMesh();
        PlasticTextureLayout layout = data.textureLayout();
        for (int index = 0; index < quads.size(); index++) {
            MoldingQuad quad = quads.get(index);
            PlasticTextureLayout.UvRegion region = layout.regions().get("surface_" + index);
            if (region == null) continue;
            float u0 = region.x() / (float) layout.atlasWidth();
            float v0 = region.y() / (float) layout.atlasHeight();
            float u1 = (region.x() + region.width()) / (float) layout.atlasWidth();
            float v1 = (region.y() + region.height()) / (float) layout.atlasHeight();
            vertex(consumer, pose, quad.first(), quad.normal(), u0, v0, packedLight, color);
            vertex(consumer, pose, quad.second(), quad.normal(), u1, v0, packedLight, color);
            vertex(consumer, pose, quad.third(), quad.normal(), u1, v1, packedLight, color);
            vertex(consumer, pose, quad.fourth(), quad.normal(), u0, v1, packedLight, color);
        }
        return renderType;
    }

    private static void vertex(
        VertexConsumer consumer,
        PoseStack pose,
        MoldingVec3 position,
        MoldingVec3 normal,
        float u,
        float v,
        int packedLight,
        int color
    ) {
        consumer.addVertex(
                pose.last().pose(),
                (float) position.x() / PIXELS_PER_BLOCK,
                (float) position.y() / PIXELS_PER_BLOCK,
                (float) position.z() / PIXELS_PER_BLOCK
            )
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(packedLight)
            .setNormal(
                pose.last(),
                (float) normal.x(),
                (float) normal.y(),
                (float) normal.z()
            );
    }
}
