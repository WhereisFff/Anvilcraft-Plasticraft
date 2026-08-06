package dev.anvilcraft.plasticraft.client.renderer.molding;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlConst;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawPhase;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorDrawState;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorSceneMesh;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorScenePart;
import dev.anvilcraft.plasticraft.client.molding.scene.EditorVertex;
import dev.anvilcraft.plasticraft.client.molding.scene.ViewportFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** 仅适用于 1.21.1 的 TextureTarget 与 VertexBuffer 后端。 */
public final class MoldingViewportBackend1211 implements MoldingViewportBackend {
    private static final int BLOCK_BUFFER_CAPACITY = 786432;
    private final Map<EditorDrawPhase, CachedBuffer> buffers = new EnumMap<>(EditorDrawPhase.class);
    private final MoldingViewportCompositor1211 compositor = new MoldingViewportCompositor1211();
    private final ByteBufferBuilder blockBuffer = new ByteBufferBuilder(BLOCK_BUFFER_CAPACITY);
    private final MultiBufferSource.BufferSource blockBuffers = MultiBufferSource.immediate(this.blockBuffer);
    private final MoldingViewportTargetLifecycle<TextureTarget> targetLifecycle =
        new MoldingViewportTargetLifecycle<>(new MoldingViewportTargetLifecycle.Adapter<>() {
            @Override
            public TextureTarget create(int width, int height) {
                TextureTarget target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
                target.setFilterMode(GlConst.GL_LINEAR);
                return target;
            }

            @Override
            public void resize(TextureTarget target, int width, int height) {
                target.resize(width, height, Minecraft.ON_OSX);
                target.setFilterMode(GlConst.GL_LINEAR);
            }

            @Override
            public void release(TextureTarget target) {
                target.destroyBuffers();
            }
        });
    private EditorSceneMesh uploadedMesh;
    private boolean closed;

    public MoldingViewportBackend1211() {
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
        setClearColor(frame.clearColor());
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        try {
            if (frame.controllerState() != null) renderController(frame, target);
            ShaderInstance shader = Objects.requireNonNull(
                GameRenderer.getPositionColorShader(),
                "Position-color shader is unavailable"
            );
            for (EditorDrawPhase phase : EditorDrawPhase.values()) {
                CachedBuffer cached = this.buffers.get(phase);
                if (cached == null) continue;
                configure(phase.drawState());
                cached.buffer.bind();
                cached.buffer.drawWithShader(
                    frame.transform().viewMatrix(),
                    frame.transform().projectionMatrix(),
                    shader
                );
            }
        } finally {
            VertexBuffer.unbind();
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    @Override
    public void compose(GuiGraphics graphics, int x, int y, int width, int height) {
        requireOpen();
        TextureTarget target = this.targetLifecycle.target();
        if (target != null) this.compositor.compose(graphics, target, x, y, width, height);
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
        MoldingViewportResources.INSTANCE.unregister(this);
    }

    private void setClearColor(int color) {
        float red = (float) ((color >> 16) & 0xFF) / 255.0F;
        float green = (float) ((color >> 8) & 0xFF) / 255.0F;
        float blue = (float) (color & 0xFF) / 255.0F;
        float alpha = (float) ((color >>> 24) & 0xFF) / 255.0F;
        Objects.requireNonNull(this.targetLifecycle.target()).setClearColor(red, green, blue, alpha);
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

    private void renderController(ViewportFrame frame, TextureTarget target) {
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        RenderSystem.backupProjectionMatrix();
        modelView.pushMatrix();
        try {
            RenderSystem.setProjectionMatrix(
                frame.transform().projectionMatrix(),
                VertexSorting.DISTANCE_TO_ORIGIN
            );
            modelView.identity();
            RenderSystem.applyModelViewMatrix();
            PoseStack pose = new PoseStack();
            pose.mulPose(frame.transform().viewMatrix());
            Vector3d controllerOrigin = Objects.requireNonNull(frame.controllerOrigin());
            pose.translate(controllerOrigin.x, controllerOrigin.y, controllerOrigin.z);
            pose.scale(16.0F, 16.0F, 16.0F);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                frame.controllerState(),
                pose,
                this.blockBuffers,
                frame.controllerLight(),
                OverlayTexture.NO_OVERLAY
            );
            this.blockBuffers.endBatch();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            target.bindWrite(false);
        }
    }

    private static CachedBuffer upload(EditorScenePart part, long fingerprint) {
        return new CachedBuffer(part, fingerprint, upload(part, part.indices()));
    }

    private static VertexBuffer upload(EditorScenePart part, int[] indices) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int index : indices) {
            EditorVertex vertex = part.vertices().get(index);
            builder.addVertex(vertex.x(), vertex.y(), vertex.z()).setColor(vertex.color());
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.buildOrThrow());
        VertexBuffer.unbind();
        return buffer;
    }

    private static void configure(EditorDrawState drawState) {
        switch (drawState) {
            case OPAQUE -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.enableCull();
                RenderSystem.disableBlend();
            }
            case DOUBLE_SIDED -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
                RenderSystem.disableCull();
                RenderSystem.disableBlend();
            }
            case TRANSLUCENT -> {
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            case ALWAYS_VISIBLE -> {
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.disableCull();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
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
        private final VertexBuffer buffer;

        private CachedBuffer(EditorScenePart part, long fingerprint, VertexBuffer buffer) {
            this.part = part;
            this.fingerprint = fingerprint;
            this.buffer = buffer;
        }

        @Override
        public void close() {
            this.buffer.close();
        }
    }
}
