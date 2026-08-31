package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureLayout;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager.MoldedTextureRef;
import dev.anvilcraft.plasticraft.client.renderer.entity.TransparentPlasticFaceCullingVertexConsumer;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 物品、实体和胶合方块态共用的动态制品网格渲染。 */
public final class MoldedPlasticMeshRenderer {
    private static final float PIXELS_PER_BLOCK = 16.0F;
    private static final int INVALID_PREVIEW_COLOR = 0xFFFF1A1A;
    private static final int PREPARED_MESH_CACHE_LIMIT = 128;
    private static final int VERTEX_STRIDE = 8;
    private static final double PIXEL = 1.0D / PIXELS_PER_BLOCK;
    private static final double OUTLET_WIDTH_RATIO = 0.57D;
    private static final double OUTLET_HEIGHT_RATIO = 0.60D;
    private static final double OUTLET_MINIMUM_WIDTH = 4.0D * PIXEL;
    private static final double OUTLET_MINIMUM_HEIGHT = 4.0D * PIXEL;
    private static final double OUTLET_MAXIMUM_WIDTH = 16.0D * PIXEL;
    private static final double OUTLET_MAXIMUM_HEIGHT = 12.0D * PIXEL;
    private static final double OUTLET_MINIMUM_EXTENSION = 2.0D * PIXEL;
    private static final double OUTLET_MAXIMUM_EXTENSION = 8.0D * PIXEL;
    private static final double OUTLET_INNER_INSET_RATIO = 0.125D;
    private static final double OUTLET_MAXIMUM_INNER_INSET = 2.0D * PIXEL;
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

    public enum FluidLayerRenderPass {
        ALL,
        OPAQUE_ONLY,
        TRANSLUCENT_ONLY;

        public boolean includes(boolean opaque) {
            return switch (this) {
                case ALL -> true;
                case OPAQUE_ONLY -> opaque;
                case TRANSLUCENT_ONLY -> !opaque;
            };
        }
    }

    public static RenderType render(
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int color,
        boolean translucent
    ) {
        return render(data, pose, buffers, packedLight, color, translucent, null);
    }

    public static RenderType render(
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int color,
        boolean translucent,
        UniversalPlasticEntity cullingEntity
    ) {
        MoldedTextureRef textureRef = DynamicPlasticTextureManager.INSTANCE.textureRef(data);
        PreparedMesh mesh = preparedMesh(data);
        boolean deferredTransparent = translucent && ClearPlasticRenderTypes.isDeferredPassActive();
        RenderType renderType = deferredTransparent
            ? ClearPlasticRenderTypes.molded(textureRef.texture())
            : translucent
                ? RenderType.entityTranslucentCull(textureRef.texture())
                : RenderType.entityCutoutNoCull(textureRef.texture());
        if (deferredTransparent) {
            renderDeferredTransparentMesh(
                mesh,
                pose,
                buffers,
                packedLight,
                color,
                cullingEntity,
                renderType,
                textureRef
            );
            return renderType;
        }
        VertexConsumer consumer = buffers.getBuffer(renderType);
        if (cullingEntity != null) {
            consumer = TransparentPlasticFaceCullingVertexConsumer.wrap(cullingEntity, consumer);
        }
        renderMesh(mesh.volumeVertices, pose, consumer, packedLight, color, true, textureRef);
        renderMesh(mesh.zeroThicknessVertices, pose, consumer, packedLight, color, false, textureRef);
        TransparentPlasticFaceCullingVertexConsumer.finish(consumer);
        return renderType;
    }

    private static void renderDeferredTransparentMesh(
        PreparedMesh mesh,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int color,
        UniversalPlasticEntity cullingEntity,
        RenderType renderType,
        MoldedTextureRef textureRef
    ) {
        renderDeferredMeshPart(
            mesh.volumeVertices,
            pose,
            buffers,
            packedLight,
            color,
            cullingEntity,
            renderType,
            true,
            textureRef
        );
        if (mesh.zeroThicknessVertices.length > 0) {
            renderDeferredMeshPart(
                mesh.zeroThicknessVertices,
                pose,
                buffers,
                packedLight,
                color,
                cullingEntity,
                renderType,
                false,
                textureRef
            );
        }
    }

    private static void renderDeferredMeshPart(
        float[] vertices,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int color,
        UniversalPlasticEntity cullingEntity,
        RenderType renderType,
        boolean volumeCullable,
        MoldedTextureRef textureRef
    ) {
        if (vertices.length == 0) return;
        VertexConsumer consumer = buffers.getBuffer(renderType);
        if (cullingEntity != null) {
            consumer = TransparentPlasticFaceCullingVertexConsumer.wrap(cullingEntity, consumer);
        }
        renderMesh(vertices, pose, consumer, packedLight, color, volumeCullable, textureRef);
        TransparentPlasticFaceCullingVertexConsumer.finish(consumer);
    }

    public static RenderType renderPreview(
        MoldedPlasticData data,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        MoldedTextureRef textureRef = DynamicPlasticTextureManager.INSTANCE.textureRef(data);
        RenderType renderType = PlasticPreviewRenderTypes.moldedPreview(textureRef.texture());
        VertexConsumer consumer = buffers.getBuffer(renderType);
        PreparedMesh mesh = preparedMesh(data);
        renderMesh(
            mesh.volumeVertices,
            pose,
            consumer,
            LightTexture.FULL_BLOCK,
            valid ? 0xFFFFFFFF : INVALID_PREVIEW_COLOR,
            true,
            textureRef
        );
        renderMesh(
            mesh.zeroThicknessVertices,
            pose,
            consumer,
            LightTexture.FULL_BLOCK,
            valid ? 0xFFFFFFFF : INVALID_PREVIEW_COLOR,
            false,
            textureRef
        );
        return renderType;
    }

    /** 绘制与鱼缸同类、但按制品内腔和外壳尺寸伸缩的炼药锅出料口。 */
    public static void renderCauldronOutlet(
        MoldedPlasticData data,
        Direction localDirection,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        boolean translucent
    ) {
        if (!localDirection.getAxis().isHorizontal()) return;
        OutletGeometry outlet = OutletGeometry.create(data, localDirection);
        MoldedTextureRef textureRef = DynamicPlasticTextureManager.INSTANCE.textureRef(data);
        boolean deferredTransparent = translucent && ClearPlasticRenderTypes.isDeferredPassActive();
        RenderType renderType = deferredTransparent
            ? ClearPlasticRenderTypes.molded(textureRef.texture())
            : translucent
                ? RenderType.entityTranslucentCull(textureRef.texture())
                : RenderType.entityCutoutNoCull(textureRef.texture());
        renderMesh(
            outlet.vertices,
            pose,
            buffers.getBuffer(renderType),
            packedLight,
            0xFFFFFFFF,
            true,
            textureRef
        );
    }

    /** 锤击旋转预览也要显示当前已安装的出料口，避免预览与实体落点不一致。 */
    public static void renderCauldronOutletPreview(
        MoldedPlasticData data,
        Direction localDirection,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        if (!localDirection.getAxis().isHorizontal()) return;
        OutletGeometry outlet = OutletGeometry.create(data, localDirection);
        MoldedTextureRef textureRef = DynamicPlasticTextureManager.INSTANCE.textureRef(data);
        RenderType renderType = PlasticPreviewRenderTypes.moldedPreview(textureRef.texture());
        renderMesh(
            outlet.vertices,
            pose,
            buffers.getBuffer(renderType),
            LightTexture.FULL_BLOCK,
            valid ? 0xFFFFFFFF : INVALID_PREVIEW_COLOR,
            true,
            textureRef
        );
    }

    /** 物品渲染缩放时把出料口伸出外壳的部分也纳入包围盒。 */
    public static AABB cauldronOutletBounds(MoldedPlasticData data, Direction localDirection) {
        return localDirection.getAxis().isHorizontal()
            ? OutletGeometry.create(data, localDirection).bounds
            : data.surfaceBounds();
    }

    /** 将液面求解与顶点换算集中到显示状态变化时执行。 */
    public static PreparedTankFluids prepareTankFluids(MoldedPlasticData data, Vec3 localUp) {
        return PreparedTankFluids.create(MoldedTankFluidGeometry.solve(data, localUp));
    }

    /** 绘制按内腔体积裁切的多流体层。 */
    public static void renderTankFluids(
        PreparedTankFluids prepared,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        renderTankFluids(prepared, pose, buffers, packedLight, FluidLayerRenderPass.ALL);
    }

    public static void renderTankFluids(
        PreparedTankFluids prepared,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        FluidLayerRenderPass renderPass
    ) {
        if (prepared.layers.isEmpty()) return;
        PoseStack.Pose lastPose = pose.last();
        boolean deferredPass = ClearPlasticRenderTypes.isDeferredPassActive();
        for (PreparedFluidLayer layer : prepared.layers) {
            FluidStack fluid = layer.fluid;
            IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid.getFluid());
            boolean opaque = FluidRenderOpacity.isOpaque(fluid);
            if (!renderPass.includes(opaque)) continue;
            VertexConsumer consumer = buffers.getBuffer(
                deferredPass
                    ? ClearPlasticRenderTypes.moldedFluid()
                    : opaque
                        ? RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS)
                        : RenderType.translucent()
            );
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
        Matrix4f transform = pose.pose();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (int offset = 0; offset < vertices.length; offset += VERTEX_STRIDE) {
            transform.transformPosition(vertices[offset], vertices[offset + 1], vertices[offset + 2], position);
            pose.transformNormal(vertices[offset + 3], vertices[offset + 4], vertices[offset + 5], normal);
            consumer.addVertex(
                position.x,
                position.y,
                position.z,
                color,
                sprite.getU(vertices[offset + 6]),
                sprite.getV(vertices[offset + 7]),
                OverlayTexture.NO_OVERLAY,
                packedLight,
                normal.x,
                normal.y,
                normal.z
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

        public boolean isEmpty() {
            return this.layers.isEmpty();
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

    /**
     * 逐帧重发制品网格。
     *
     * <p>vanilla 的 {@code VertexConsumer} 默认方法会为每个顶点的位置和法线各新建一个
     * {@code Vector3f}，几百个制品实体同屏时这是渲染线程最大的分配来源；因此这里复用暂存向量，
     * 并改用一次写满整个顶点的重载，让 {@code BufferBuilder} 走直接写内存的快路径。</p>
     */
    private static void renderMesh(
        float[] vertices,
        PoseStack pose,
        VertexConsumer consumer,
        int packedLight,
        int color,
        boolean volumeCullable,
        MoldedTextureRef textureRef
    ) {
        PoseStack.Pose lastPose = pose.last();
        Matrix4f transform = lastPose.pose();
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        int quadStride = VERTEX_STRIDE * 4;
        for (int quad = 0; quad < vertices.length; quad += quadStride) {
            TransparentPlasticFaceCullingVertexConsumer.beginQuad(consumer, !volumeCullable);
            int end = Math.min(quad + quadStride, vertices.length);
            for (int offset = quad; offset < end; offset += VERTEX_STRIDE) {
                transform.transformPosition(
                    vertices[offset],
                    vertices[offset + 1],
                    vertices[offset + 2],
                    position
                );
                lastPose.transformNormal(
                    vertices[offset + 3],
                    vertices[offset + 4],
                    vertices[offset + 5],
                    normal
                );
                consumer.addVertex(
                    position.x,
                    position.y,
                    position.z,
                    color,
                    vertices[offset + 6],
                    textureRef.mapV(vertices[offset + 7]),
                    OverlayTexture.NO_OVERLAY,
                    packedLight,
                    normal.x,
                    normal.y,
                    normal.z
                );
            }
        }
    }

    private static int putOutletVertex(
        float[] target,
        int offset,
        double x,
        double y,
        double z,
        float normalX,
        float normalY,
        float normalZ,
        float u,
        float v
    ) {
        target[offset] = (float) x;
        target[offset + 1] = (float) y;
        target[offset + 2] = (float) z;
        target[offset + 3] = normalX;
        target[offset + 4] = normalY;
        target[offset + 5] = normalZ;
        target[offset + 6] = u;
        target[offset + 7] = v;
        return offset + VERTEX_STRIDE;
    }

    private static int putOutletQuad(
        float[] target,
        int offset,
        double firstX,
        double firstY,
        double firstZ,
        double secondX,
        double secondY,
        double secondZ,
        double thirdX,
        double thirdY,
        double thirdZ,
        double fourthX,
        double fourthY,
        double fourthZ,
        float normalX,
        float normalY,
        float normalZ,
        float u0,
        float v0,
        float u1,
        float v1
    ) {
        offset = putOutletVertex(
            target, offset, firstX, firstY, firstZ, normalX, normalY, normalZ, u0, v0
        );
        offset = putOutletVertex(
            target, offset, fourthX, fourthY, fourthZ, normalX, normalY, normalZ, u1, v0
        );
        offset = putOutletVertex(
            target, offset, thirdX, thirdY, thirdZ, normalX, normalY, normalZ, u1, v1
        );
        return putOutletVertex(
            target, offset, secondX, secondY, secondZ, normalX, normalY, normalZ, u0, v1
        );
    }

    private static final class OutletGeometry {
        private final AABB bounds;
        private final float[] vertices;

        private OutletGeometry(AABB bounds, float[] vertices) {
            this.bounds = bounds;
            this.vertices = vertices;
        }

        private static OutletGeometry create(MoldedPlasticData data, Direction direction) {
            AABB shell = data.surfaceBounds();
            AABB cavity = data.cavityBounds().orElse(shell);
            boolean alongZ = direction.getAxis() == Direction.Axis.Z;
            double tangentMinimum = alongZ ? cavity.minX : cavity.minZ;
            double tangentMaximum = alongZ ? cavity.maxX : cavity.maxZ;
            double tangentSpan = tangentMaximum - tangentMinimum;
            double cavityHeight = cavity.maxY - cavity.minY;
            double width = scaledDimension(
                tangentSpan,
                OUTLET_WIDTH_RATIO,
                OUTLET_MINIMUM_WIDTH,
                OUTLET_MAXIMUM_WIDTH
            );
            double height = scaledDimension(
                cavityHeight,
                OUTLET_HEIGHT_RATIO,
                OUTLET_MINIMUM_HEIGHT,
                OUTLET_MAXIMUM_HEIGHT
            );
            double tangentCenter = clampToSpan(
                (tangentMinimum + tangentMaximum) * 0.5D,
                alongZ ? shell.minX : shell.minZ,
                alongZ ? shell.maxX : shell.maxZ,
                width
            );
            double verticalCenter = clampToSpan(
                (shell.minY + shell.maxY) * 0.5D - PIXEL,
                cavity.minY,
                cavity.maxY,
                height
            );
            double innerInset = Math.min(
                OUTLET_MAXIMUM_INNER_INSET,
                Math.min(width, height) * OUTLET_INNER_INSET_RATIO
            );
            double extension = Math.min(
                OUTLET_MAXIMUM_EXTENSION,
                Math.max(OUTLET_MINIMUM_EXTENSION, Math.min(width, height) * 0.25D)
            );
            AABB bounds = createBounds(
                shell,
                cavity,
                direction,
                tangentCenter,
                verticalCenter,
                width,
                height,
                innerInset,
                extension
            );
            return new OutletGeometry(bounds, createVertices(bounds, data.textureLayout()));
        }

        private static double scaledDimension(double span, double ratio, double minimum, double maximum) {
            return Math.min(span, Math.min(maximum, Math.max(minimum, span * ratio)));
        }

        private static double clampToSpan(double center, double minimum, double maximum, double size) {
            double lower = minimum + size * 0.5D;
            double upper = maximum - size * 0.5D;
            return lower > upper ? (minimum + maximum) * 0.5D : Math.clamp(center, lower, upper);
        }

        private static AABB createBounds(
            AABB shell,
            AABB cavity,
            Direction direction,
            double tangentCenter,
            double verticalCenter,
            double width,
            double height,
            double innerInset,
            double extension
        ) {
            double tangentMinimum = tangentCenter - width * 0.5D;
            double tangentMaximum = tangentCenter + width * 0.5D;
            double bottom = verticalCenter - height * 0.5D;
            double top = verticalCenter + height * 0.5D;
            // 管体从内腔壁的内侧起步，外壳越厚时便会自然拥有更长的穿壁段。
            return switch (direction) {
                case NORTH -> new AABB(
                    tangentMinimum,
                    bottom,
                    shell.minZ - extension,
                    tangentMaximum,
                    top,
                    Math.max(shell.minZ, cavity.minZ) + innerInset
                );
                case SOUTH -> new AABB(
                    tangentMinimum,
                    bottom,
                    Math.min(shell.maxZ, cavity.maxZ) - innerInset,
                    tangentMaximum,
                    top,
                    shell.maxZ + extension
                );
                case WEST -> new AABB(
                    shell.minX - extension,
                    bottom,
                    tangentMinimum,
                    Math.max(shell.minX, cavity.minX) + innerInset,
                    top,
                    tangentMaximum
                );
                case EAST -> new AABB(
                    Math.min(shell.maxX, cavity.maxX) - innerInset,
                    bottom,
                    tangentMinimum,
                    shell.maxX + extension,
                    top,
                    tangentMaximum
                );
                default -> throw new IllegalArgumentException("Cauldron outlet must be horizontal");
            };
        }

        private static float[] createVertices(AABB bounds, PlasticTextureLayout layout) {
            PlasticTextureLayout.UvRegion region = largestRegion(layout);
            float u0 = region.x() / (float) layout.atlasWidth();
            float v0 = region.y() / (float) layout.atlasHeight();
            float u1 = (region.x() + region.width()) / (float) layout.atlasWidth();
            float v1 = (region.y() + region.height()) / (float) layout.atlasHeight();
            float[] vertices = new float[6 * 4 * VERTEX_STRIDE];
            int offset = 0;
            offset = putOutletQuad(
                vertices, offset,
                bounds.minX, bounds.minY, bounds.maxZ,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.minY, bounds.maxZ,
                0.0F, -1.0F, 0.0F, u0, v0, u1, v1
            );
            offset = putOutletQuad(
                vertices, offset,
                bounds.minX, bounds.maxY, bounds.minZ,
                bounds.minX, bounds.maxY, bounds.maxZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                bounds.maxX, bounds.maxY, bounds.minZ,
                0.0F, 1.0F, 0.0F, u0, v0, u1, v1
            );
            offset = putOutletQuad(
                vertices, offset,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.minX, bounds.maxY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.minZ,
                bounds.maxX, bounds.minY, bounds.minZ,
                0.0F, 0.0F, -1.0F, u0, v0, u1, v1
            );
            offset = putOutletQuad(
                vertices, offset,
                bounds.minX, bounds.minY, bounds.maxZ,
                bounds.maxX, bounds.minY, bounds.maxZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                bounds.minX, bounds.maxY, bounds.maxZ,
                0.0F, 0.0F, 1.0F, u0, v0, u1, v1
            );
            offset = putOutletQuad(
                vertices, offset,
                bounds.minX, bounds.minY, bounds.minZ,
                bounds.minX, bounds.minY, bounds.maxZ,
                bounds.minX, bounds.maxY, bounds.maxZ,
                bounds.minX, bounds.maxY, bounds.minZ,
                -1.0F, 0.0F, 0.0F, u0, v0, u1, v1
            );
            putOutletQuad(
                vertices, offset,
                bounds.maxX, bounds.minY, bounds.maxZ,
                bounds.maxX, bounds.minY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.minZ,
                bounds.maxX, bounds.maxY, bounds.maxZ,
                1.0F, 0.0F, 0.0F, u0, v0, u1, v1
            );
            return vertices;
        }

        private static PlasticTextureLayout.UvRegion largestRegion(PlasticTextureLayout layout) {
            PlasticTextureLayout.UvRegion selected = null;
            long largestArea = Long.MIN_VALUE;
            for (PlasticTextureLayout.UvRegion region : layout.regions().values()) {
                long area = (long) region.width() * region.height();
                if (area > largestArea) {
                    selected = region;
                    largestArea = area;
                }
            }
            if (selected == null) throw new IllegalStateException("Molded plastic texture layout is empty");
            return selected;
        }
    }

    private static PreparedMesh preparedMesh(MoldedPlasticData data) {
        String shapeHash = data.shapeHash();
        synchronized (PREPARED_MESH_CACHE) {
            return PREPARED_MESH_CACHE.computeIfAbsent(shapeHash, ignored -> PreparedMesh.create(data));
        }
    }

    private static final class PreparedMesh {
        private final float[] volumeVertices;
        private final float[] zeroThicknessVertices;

        private PreparedMesh(float[] volumeVertices, float[] zeroThicknessVertices) {
            this.volumeVertices = volumeVertices;
            this.zeroThicknessVertices = zeroThicknessVertices;
        }

        private static PreparedMesh create(MoldedPlasticData data) {
            List<MoldingQuad> quads = data.surfaceMesh();
            PlasticTextureLayout layout = data.textureLayout();
            return new PreparedMesh(
                createVertices(quads, layout, true, false),
                createVertices(quads, layout, false, true)
            );
        }

        private static float[] createVertices(
            List<MoldingQuad> quads,
            PlasticTextureLayout layout,
            boolean includeVolume,
            boolean includeZeroThickness
        ) {
            int quadCount = 0;
            for (MoldingQuad quad : quads) {
                if (quad.doubleSided() ? includeZeroThickness : includeVolume) {
                    quadCount += quad.doubleSided() ? 2 : 1;
                }
            }
            float[] vertices = new float[quadCount * 4 * VERTEX_STRIDE];
            int offset = 0;
            for (int index = 0; index < quads.size(); index++) {
                MoldingQuad quad = quads.get(index);
                if (quad.doubleSided() ? !includeZeroThickness : !includeVolume) continue;
                PlasticTextureLayout.UvRegion region = layout.regions().get("surface_" + index);
                if (region == null) throw new IllegalStateException("Molded plastic texture layout is incomplete");
                float u0 = region.x() / (float) layout.atlasWidth();
                float v0 = region.y() / (float) layout.atlasHeight();
                float u1 = (region.x() + region.width()) / (float) layout.atlasWidth();
                float v1 = (region.y() + region.height()) / (float) layout.atlasHeight();
                offset = putVertex(vertices, offset, quad.first(), quad.normal(), u0, v0);
                offset = putVertex(vertices, offset, quad.second(), quad.normal(), u1, v0);
                offset = putVertex(vertices, offset, quad.third(), quad.normal(), u1, v1);
                offset = putVertex(vertices, offset, quad.fourth(), quad.normal(), u0, v1);
                if (quad.doubleSided()) {
                    MoldingVec3 reverseNormal = quad.normal().scale(-1.0D);
                    offset = putVertex(vertices, offset, quad.first(), reverseNormal, u0, v0);
                    offset = putVertex(vertices, offset, quad.fourth(), reverseNormal, u0, v1);
                    offset = putVertex(vertices, offset, quad.third(), reverseNormal, u1, v1);
                    offset = putVertex(vertices, offset, quad.second(), reverseNormal, u1, v0);
                }
            }
            if (offset != vertices.length) {
                throw new IllegalStateException("Molded plastic texture layout is incomplete");
            }
            return vertices;
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
