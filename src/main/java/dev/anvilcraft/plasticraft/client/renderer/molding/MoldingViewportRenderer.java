package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawState;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.PrintingViewportScene;
import dev.anvilcraft.plasticraft.client.molding.scene.ViewportFrame;
import dev.anvilcraft.plasticraft.client.renderer.PlasticBlockModel;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.Plastic3DPrintingComponentRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.PlasticMoldingChamberRenderer;
import dev.anvilcraft.plasticraft.molding.machine.MoldingPrinterMotion;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/** 在离屏颜色与深度附件中绘制成型视口，按阶段缓存静态顶点。 */
public final class MoldingViewportRenderer implements MoldingViewportBackend {
    private static final int BLOCK_BUFFER_CAPACITY = 786432;
    private final Map<EditorDrawPhase, CachedBuffer> buffers = new EnumMap<>(EditorDrawPhase.class);
    private static final Map<EditorDrawState, RenderPipeline> PIPELINES = new EnumMap<>(EditorDrawState.class);
    private final ProjectionMatrixBuffer projection = new ProjectionMatrixBuffer("Plastic molding viewport");

    static {
        for (EditorDrawState state : EditorDrawState.values()) {
            boolean solid = state == EditorDrawState.OPAQUE || state == EditorDrawState.DOUBLE_SIDED;
            var builder = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(AnvilcraftPlasticraft.of("pipeline/editor_" + state.name().toLowerCase(Locale.ROOT)))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                .withCull(state == EditorDrawState.OPAQUE)
                .withColorTargetState(solid ? ColorTargetState.DEFAULT : new ColorTargetState(BlendFunction.TRANSLUCENT));
            if (state == EditorDrawState.ALWAYS_VISIBLE) {
                builder.withDepthStencilState(Optional.empty());
            } else {
                builder.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, solid));
            }
            PIPELINES.put(state, builder.build());
        }
    }

    public static void register(RegisterRenderPipelinesEvent event) {
        PIPELINES.values().forEach(event::registerPipeline);
    }
    private final ByteBufferBuilder blockBuffer = new ByteBufferBuilder(BLOCK_BUFFER_CAPACITY);
    private final MultiBufferSource.BufferSource blockBuffers = MultiBufferSource.immediate(this.blockBuffer);
    private final MoldingViewportTargetLifecycle<TextureTarget> targetLifecycle =
        new MoldingViewportTargetLifecycle<>(new MoldingViewportTargetLifecycle.Adapter<>() {
            @Override
            public TextureTarget create(int width, int height) {
                return new TextureTarget("Plastic molding viewport", width, height, true);
            }

            @Override
            public void resize(TextureTarget target, int width, int height) {
                target.resize(width, height);
            }

            @Override
            public void release(TextureTarget target) {
                target.destroyBuffers();
            }
        });
    private EditorSceneMesh uploadedMesh;
    private boolean closed;

    public MoldingViewportRenderer() {
        MoldingViewportResources.INSTANCE.register(this);
    }

    @Override
    public void ensureTarget(int width, int height) {
        requireOpen();
        RenderSystem.assertOnRenderThread();
        this.targetLifecycle.ensure(width, height);
    }

    @Override
    public void updateMesh(EditorSceneMesh mesh) {
        requireOpen();
        RenderSystem.assertOnRenderThread();
        if (this.uploadedMesh == mesh) return;
        Map<EditorDrawPhase, EditorScenePart> replacements = new EnumMap<>(EditorDrawPhase.class);
        for (EditorScenePart part : mesh.parts()) replacements.put(part.phase(), part);
        this.buffers.entrySet().removeIf(entry -> {
            if (replacements.containsKey(entry.getKey())) return false;
            entry.getValue().close();
            return true;
        });
        for (Map.Entry<EditorDrawPhase, EditorScenePart> entry : replacements.entrySet()) {
            CachedBuffer current = this.buffers.get(entry.getKey());
            EditorScenePart replacement = entry.getValue();
            if (current != null && current.part == replacement) continue;
            long fingerprint = fingerprint(replacement);
            if (current != null && current.fingerprint == fingerprint) {
                current.part = replacement;
                continue;
            }
            if (current != null) current.close();
            this.buffers.put(entry.getKey(), upload(replacement, fingerprint));
        }
        this.uploadedMesh = mesh;
    }

    @Override
    public void render(ViewportFrame frame) {
        requireOpen();
        RenderSystem.assertOnRenderThread();
        TextureTarget target = Objects.requireNonNull(
            this.targetLifecycle.target(),
            "Viewport target was not allocated"
        );
        this.updateMesh(frame.scene());
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.clearColorAndDepthTextures(target.getColorTexture(), frame.clearColor(), target.getDepthTexture(), 1);
        var previousColor = RenderSystem.outputColorTextureOverride;
        var previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = target.getColorTextureView();
        RenderSystem.outputDepthTextureOverride = target.getDepthTextureView();
        RenderSystem.backupProjectionMatrix();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        try {
            modelView.identity();
            Matrix4f projectionMatrix = frame.transform().projectionMatrix();
            if (RenderSystem.getDevice().isZZeroToOne()) {
                projectionMatrix = new Matrix4f().translation(0, 0, 0.5f).scale(1, 1, 0.5f).mul(projectionMatrix);
            }
            RenderSystem.setProjectionMatrix(this.projection.getBuffer(projectionMatrix),
                frame.transform().perspective() ? ProjectionType.PERSPECTIVE : ProjectionType.ORTHOGRAPHIC);
            PoseStack pose = new PoseStack();
            pose.mulPose(frame.transform().viewMatrix());
            if (frame.controllerState() != null) {
                renderController(frame, pose);
            }
            if (frame.printingScene() != null) {
                renderPrinterOpaque(frame, pose);
            }
            this.blockBuffers.endBatch();
            var transform = RenderSystem.getDynamicUniforms().writeTransform(frame.transform().viewMatrix(),
                new Vector4f(1), new Vector3f(), new Matrix4f());
            var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            int maxIndexCount = this.buffers.values().stream().mapToInt(buffer -> buffer.indexCount).max().orElse(0);
            if (maxIndexCount > 0) {
                var indexBuffer = indices.getBuffer(maxIndexCount);
                try (var pass = encoder.createRenderPass(() -> "Plastic molding mesh", target.getColorTextureView(),
                        OptionalInt.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
                    for (EditorDrawPhase phase : EditorDrawPhase.values()) {
                        var batch = this.buffers.get(phase);
                        if (batch == null) {
                            continue;
                        }
                        pass.setPipeline(PIPELINES.get(phase.drawState()));
                        RenderSystem.bindDefaultUniforms(pass);
                        pass.setUniform("DynamicTransforms", transform);
                        pass.setVertexBuffer(0, batch.buffer);
                        pass.setIndexBuffer(indexBuffer, indices.type());
                        pass.drawIndexed(0, 0, batch.indexCount, 1);
                    }
                }
            }
        } finally {
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
        }
    }

    @Override
    public void compose(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        requireOpen();
        TextureTarget target = this.targetLifecycle.target();
        if (target != null) {
            graphics.blit(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                x, y, x + width, y + height, 0, 1, 1, 0);
        }
    }

    @Override
    public void reloadResources() {
        if (this.closed) return;
        RenderSystem.assertOnRenderThread();
        releaseResources();
    }

    @Override
    public void close() {
        if (this.closed) return;
        RenderSystem.assertOnRenderThread();
        this.closed = true;
        releaseBuffers();
        this.targetLifecycle.close();
        this.blockBuffer.close();
        this.projection.close();
        MoldingViewportResources.INSTANCE.unregister(this);
    }

    private void releaseResources() {
        releaseBuffers();
        this.targetLifecycle.reload();
    }

    private void releaseBuffers() {
        this.buffers.values().forEach(CachedBuffer::close);
        this.buffers.clear();
        this.uploadedMesh = null;
    }

    private void requireOpen() {
        if (this.closed) throw new IllegalStateException("Viewport backend is closed");
    }

    private void renderController(ViewportFrame frame, PoseStack rootPose) {
        PoseStack pose = copyPose(rootPose);
        Vector3d controllerOrigin = Objects.requireNonNull(frame.controllerOrigin());
        pose.translate(controllerOrigin.x, controllerOrigin.y, controllerOrigin.z);
        pose.scale(16.0F, 16.0F, 16.0F);
        PlasticBlockModel.render(
            frame.controllerState(),
            pose,
            this.blockBuffers,
            frame.controllerLight(),
            OverlayTexture.NO_OVERLAY
        );
    }

    private void renderPrinterOpaque(ViewportFrame frame, PoseStack rootPose) {
        PrintingViewportScene scene = Objects.requireNonNull(frame.printingScene());
        Vector3d componentOrigin = scene.controllerOrigin().add(0.0D, 16.0D, 0.0D);
        PoseStack componentPose = copyPose(rootPose);
        componentPose.translate(componentOrigin.x, componentOrigin.y, componentOrigin.z);
        componentPose.scale(16.0F, 16.0F, 16.0F);
        PlasticBlockModel.render(
            scene.componentState(),
            componentPose,
            this.blockBuffers,
            scene.packedLight(),
            OverlayTexture.NO_OVERLAY
        );
        Plastic3DPrintingComponentRenderer.renderDynamicParts(
            scene.componentState(),
            scene.componentFluid(),
            componentPose,
            this.blockBuffers,
            scene.packedLight()
        );

        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_GUI_MODEL,
            PlasticMoldingChamberRenderer.PRINTER_MODEL_ORIGIN,
            scene.packedLight(), Sheets.cutoutBlockSheet()
        );
        MoldingVec3 head = scene.headPosition();
        MoldingVec3 movement = head.subtract(MoldingPrinterMotion.DEFAULT_POSITION);
        MoldingVec3 modelOrigin = PlasticMoldingChamberRenderer.PRINTER_MODEL_ORIGIN;
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_Y_MODEL,
            modelOrigin.add(new MoldingVec3(0.0D, movement.y(), 0.0D)),
            scene.packedLight(), Sheets.cutoutBlockSheet()
        );
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_X_MODEL,
            modelOrigin.add(new MoldingVec3(movement.x(), movement.y(), 0.0D)),
            scene.packedLight(), Sheets.cutoutBlockSheet()
        );
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_Z_MODEL,
            modelOrigin.add(movement), scene.packedLight(), Sheets.cutoutBlockSheet()
        );
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_Z_EYE_MODEL,
            modelOrigin.add(movement), LightCoordsUtil.pack(15, 0), Sheets.cutoutBlockSheet()
        );
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_DOOR_LEFT_MODEL,
            modelOrigin.add(new MoldingVec3(scene.doorProgress() * 16.0D, 0.0D, 0.0D)),
            scene.packedLight(), Sheets.cutoutBlockSheet()
        );
        renderPrinterModel(
            rootPose, scene, PlasticMoldingChamberRenderer.PRINTER_DOOR_RIGHT_MODEL,
            modelOrigin.add(new MoldingVec3(scene.doorProgress() * -16.0D, 0.0D, 0.0D)),
            scene.packedLight(), Sheets.cutoutBlockSheet()
        );
    }

    private void renderPrinterModel(
        PoseStack rootPose,
        PrintingViewportScene scene,
        PlasticBlockModel location,
        MoldingVec3 origin,
        int packedLight,
        RenderType renderType
    ) {
        BlockStateModel model = location.baked();
        PoseStack pose = copyPose(rootPose);
        pose.translate(origin.x(), origin.y(), origin.z());
        pose.scale(16.0F, 16.0F, 16.0F);
        PlasticBlockModel.render(
            pose.last(),
            this.blockBuffers.getBuffer(renderType),
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
    }

    private static PoseStack copyPose(PoseStack source) {
        PoseStack copy = new PoseStack();
        copy.mulPose(source.last().pose());
        return copy;
    }

    private static CachedBuffer upload(EditorScenePart part, long fingerprint) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int index : part.indices()) {
            EditorVertex vertex = part.vertices().get(index);
            builder.addVertex(vertex.x(), vertex.y(), vertex.z()).setColor(vertex.color());
        }
        try (MeshData mesh = builder.buildOrThrow()) {
            GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Molding " + part.phase(),
                GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            return new CachedBuffer(part, fingerprint, buffer, mesh.drawState().indexCount());
        }
    }

    private static long fingerprint(EditorScenePart part) {
        long hash = 0xCBF29CE484222325L;
        hash = mix(hash, part.phase().ordinal());
        for (EditorVertex vertex : part.vertices()) {
            hash = mix(hash, Float.floatToIntBits(vertex.x()));
            hash = mix(hash, Float.floatToIntBits(vertex.y()));
            hash = mix(hash, Float.floatToIntBits(vertex.z()));
            hash = mix(hash, vertex.color());
        }
        for (int index : part.indices()) hash = mix(hash, index);
        return hash;
    }

    private static long mix(long hash, int value) {
        return (hash ^ Integer.toUnsignedLong(value)) * 0x100000001B3L;
    }

    private static final class CachedBuffer implements AutoCloseable {
        private EditorScenePart part;
        private final long fingerprint;
        private final GpuBuffer buffer;
        private final int indexCount;

        private CachedBuffer(EditorScenePart part, long fingerprint, GpuBuffer buffer, int indexCount) {
            this.part = part;
            this.fingerprint = fingerprint;
            this.buffer = buffer;
            this.indexCount = indexCount;
        }

        @Override
        public void close() {
            this.buffer.close();
        }
    }
}
