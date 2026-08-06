package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 物品、实体和胶合方块态共用的动态制品网格渲染。 */
public final class MoldedPlasticMeshRenderer {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final int INVALID_PREVIEW_COLOR = 0xFFFF1A1A;
    private static final int PREPARED_MESH_CACHE_LIMIT = 32;
    private static final int VERTEX_STRIDE = 8;
    private static final Map<String, PreparedMesh> PREPARED_MESH_CACHE = new LinkedHashMap<>(
        PREPARED_MESH_CACHE_LIMIT,
        0.75F,
        true
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PreparedMesh> eldest) {
            return this.size() > PREPARED_MESH_CACHE_LIMIT;
        }
    };

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
        renderMesh(data, pose, consumer, packedLight, color, true);
        return renderType;
    }

    public static RenderType renderPreview(
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        ResourceLocation texture = DynamicPlasticTextureManager.INSTANCE.texture(data);
        RenderType renderType = PlasticPreviewRenderTypes.moldedPreview(texture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        renderMesh(data, pose, consumer, LightTexture.FULL_BLOCK, valid ? 0xFFFFFFFF : INVALID_PREVIEW_COLOR, false);
        return renderType;
    }

    private static void renderMesh(
        MoldedPlasticData data,
        PoseStack pose,
        VertexConsumer consumer,
        int packedLight,
        int color,
        boolean entityFormat
    ) {
        PreparedMesh mesh = preparedMesh(data);
        PoseStack.Pose lastPose = pose.last();
        float[] vertices = mesh.vertices;
        for (int offset = 0; offset < vertices.length; offset += VERTEX_STRIDE) {
            VertexConsumer vertex = consumer.addVertex(
                    lastPose.pose(),
                    vertices[offset],
                    vertices[offset + 1],
                    vertices[offset + 2]
                )
                .setColor(color)
                .setUv(vertices[offset + 6], vertices[offset + 7]);
            if (entityFormat) vertex.setOverlay(OverlayTexture.NO_OVERLAY);
            vertex
                .setLight(packedLight)
                .setNormal(
                    lastPose,
                    vertices[offset + 3],
                    vertices[offset + 4],
                    vertices[offset + 5]
                );
        }
    }

    private static PreparedMesh preparedMesh(MoldedPlasticData data) {
        String shapeHash = data.shapeHash();
        synchronized (PREPARED_MESH_CACHE) {
            return PREPARED_MESH_CACHE.computeIfAbsent(shapeHash, ignored -> PreparedMesh.create(data));
        }
    }

    private static final class PreparedMesh {
        private final float[] vertices;

        private PreparedMesh(float[] vertices) {
            this.vertices = vertices;
        }

        private static PreparedMesh create(MoldedPlasticData data) {
            List<MoldingQuad> quads = data.surfaceMesh();
            PlasticTextureLayout layout = data.textureLayout();
            float[] vertices = new float[quads.size() * 4 * VERTEX_STRIDE];
            int offset = 0;
            for (int index = 0; index < quads.size(); index++) {
                MoldingQuad quad = quads.get(index);
                PlasticTextureLayout.UvRegion region = layout.regions().get("surface_" + index);
                if (region == null) continue;
                float u0 = region.x() / (float) layout.atlasWidth();
                float v0 = region.y() / (float) layout.atlasHeight();
                float u1 = (region.x() + region.width()) / (float) layout.atlasWidth();
                float v1 = (region.y() + region.height()) / (float) layout.atlasHeight();
                offset = putVertex(vertices, offset, quad.first(), quad.normal(), u0, v0);
                offset = putVertex(vertices, offset, quad.second(), quad.normal(), u1, v0);
                offset = putVertex(vertices, offset, quad.third(), quad.normal(), u1, v1);
                offset = putVertex(vertices, offset, quad.fourth(), quad.normal(), u0, v1);
            }
            if (offset != vertices.length) {
                throw new IllegalStateException("Molded plastic texture layout is incomplete");
            }
            return new PreparedMesh(vertices);
        }

        private static int putVertex(
            float[] target,
            int offset,
            MoldingVec3 position,
            MoldingVec3 normal,
            float u,
            float v
        ) {
            target[offset] = (float) position.x() / PIXELS_PER_BLOCK;
            target[offset + 1] = (float) position.y() / PIXELS_PER_BLOCK;
            target[offset + 2] = (float) position.z() / PIXELS_PER_BLOCK;
            target[offset + 3] = (float) normal.x();
            target[offset + 4] = (float) normal.y();
            target[offset + 5] = (float) normal.z();
            target[offset + 6] = u;
            target[offset + 7] = v;
            return offset + VERTEX_STRIDE;
        }
    }
}
