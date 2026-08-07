package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTankFluidGeometry;
import dev.dubhe.anvilcraft.util.LiquidEnchantmentClientFluidTypeExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

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

    /** 将液面求解与顶点换算集中到显示状态变化时执行。 */
    public static PreparedTankFluids prepareTankFluids(MoldedPlasticData data, Vec3 localUp) {
        return PreparedTankFluids.create(MoldedTankFluidGeometry.solve(data, localUp));
    }

    /** 在制品网格之后绘制按内腔体积裁切的多流体层。 */
    public static void renderTankFluids(
        PreparedTankFluids prepared,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        if (prepared.layers.isEmpty()) return;
        PoseStack.Pose lastPose = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.translucent());
        for (PreparedFluidLayer layer : prepared.layers) {
            FluidStack fluid = layer.fluid;
            IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
            TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(properties.getStillTexture(fluid));
            int light = withFluidLight(packedLight, fluid);
            if (properties instanceof LiquidEnchantmentClientFluidTypeExtension extension) {
                for (int color : extension.getLayerColors(fluid)) {
                    renderFluidVertices(layer.vertices, sprite, consumer, lastPose, light, color);
                }
            } else {
                renderFluidVertices(
                    layer.vertices,
                    sprite,
                    consumer,
                    lastPose,
                    light,
                    properties.getTintColor(fluid)
                );
            }
        }
    }

    private static void renderFluidVertices(
        float[] vertices,
        TextureAtlasSprite sprite,
        VertexConsumer consumer,
        PoseStack.Pose pose,
        int packedLight,
        int color
    ) {
        for (int offset = 0; offset < vertices.length; offset += VERTEX_STRIDE) {
            consumer.addVertex(
                    pose.pose(),
                    vertices[offset],
                    vertices[offset + 1],
                    vertices[offset + 2]
                )
                .setColor(color)
                .setUv(sprite.getU(vertices[offset + 6]), sprite.getV(vertices[offset + 7]))
                .setLight(packedLight)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setNormal(
                    pose,
                    vertices[offset + 3],
                    vertices[offset + 4],
                    vertices[offset + 5]
                );
        }
    }

    private static int withFluidLight(int packedLight, FluidStack fluid) {
        int blockLight = Math.max(packedLight >> 4 & 0xF, fluid.getFluidType().getLightLevel());
        return packedLight & 0xF00000 | blockLight << 4;
    }

    public static final class PreparedTankFluids {
        private static final PreparedTankFluids EMPTY = new PreparedTankFluids(List.of());
        private final List<PreparedFluidLayer> layers;

        private PreparedTankFluids(List<PreparedFluidLayer> layers) {
            this.layers = layers;
        }

        private static PreparedTankFluids create(List<MoldedTankFluidGeometry.Layer> layers) {
            if (layers.isEmpty()) return EMPTY;
            return new PreparedTankFluids(layers.stream().map(PreparedFluidLayer::create).toList());
        }
    }

    private static final class PreparedFluidLayer {
        private final FluidStack fluid;
        private final float[] vertices;

        private PreparedFluidLayer(FluidStack fluid, float[] vertices) {
            this.fluid = fluid;
            this.vertices = vertices;
        }

        private static PreparedFluidLayer create(MoldedTankFluidGeometry.Layer layer) {
            int vertexCount = 0;
            for (MoldedTankFluidGeometry.Polygon polygon : layer.polygons()) {
                int size = polygon.vertices().size();
                vertexCount += size == 4 ? 4 : (size - 2) * 4;
            }
            float[] vertices = new float[vertexCount * VERTEX_STRIDE];
            int offset = 0;
            for (MoldedTankFluidGeometry.Polygon polygon : layer.polygons()) {
                List<Vec3> polygonVertices = polygon.vertices();
                Vec3 normal = polygon.normal();
                if (polygonVertices.size() == 4) {
                    for (Vec3 vertex : polygonVertices) {
                        offset = putFluidVertex(vertices, offset, vertex, normal);
                    }
                    continue;
                }
                Vec3 first = polygonVertices.getFirst();
                for (int index = 1; index < polygonVertices.size() - 1; index++) {
                    offset = putFluidVertex(vertices, offset, first, normal);
                    offset = putFluidVertex(vertices, offset, polygonVertices.get(index), normal);
                    offset = putFluidVertex(vertices, offset, polygonVertices.get(index + 1), normal);
                    offset = putFluidVertex(vertices, offset, first, normal);
                }
            }
            return new PreparedFluidLayer(layer.fluid(), vertices);
        }

        private static int putFluidVertex(
            float[] target,
            int offset,
            Vec3 position,
            Vec3 normal
        ) {
            float blockX = (float) position.x() / PIXELS_PER_BLOCK;
            float blockY = (float) position.y() / PIXELS_PER_BLOCK;
            float blockZ = (float) position.z() / PIXELS_PER_BLOCK;
            float u;
            float v;
            if (Math.abs(normal.y) >= Math.abs(normal.x) && Math.abs(normal.y) >= Math.abs(normal.z)) {
                u = Mth.frac(blockX);
                v = Mth.frac(blockZ);
            } else if (Math.abs(normal.x) >= Math.abs(normal.z)) {
                u = Mth.frac(blockZ);
                v = Mth.frac(blockY);
            } else {
                u = Mth.frac(blockX);
                v = Mth.frac(blockY);
            }
            target[offset] = blockX;
            target[offset + 1] = blockY;
            target[offset + 2] = blockZ;
            target[offset + 3] = (float) normal.x;
            target[offset + 4] = (float) normal.y;
            target[offset + 5] = (float) normal.z;
            target[offset + 6] = u;
            target[offset + 7] = v;
            return offset + VERTEX_STRIDE;
        }
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
