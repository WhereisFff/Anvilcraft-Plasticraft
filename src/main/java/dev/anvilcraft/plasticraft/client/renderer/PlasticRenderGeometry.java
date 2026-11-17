package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 在提取阶段完成凸面裁剪和材质选择，提交阶段只读取顶点及嵌套模型的渲染状态。 */
public final class PlasticRenderGeometry implements MultiBufferSource {
    private final List<Part> parts = new ArrayList<>();
    private @Nullable Batch batch;

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        if (this.batch == null || this.batch.type != type) {
            this.batch = new Batch(type);
            this.parts.add(this.batch);
        }
        return this.batch;
    }

    public void submit(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (Part part : this.parts) {
            part.submit(pose, collector, camera);
        }
    }

    public void draw(PoseStack pose, MultiBufferSource.BufferSource buffers, CameraRenderState camera) {
        Minecraft minecraft = Minecraft.getInstance();
        FeatureRenderDispatcher features = null;
        try {
            for (Part part : this.parts) {
                if (part instanceof Batch batch) {
                    batch.emit(pose.last(), buffers.getBuffer(batch.type));
                } else {
                    if (features == null) {
                        var renderBuffers = minecraft.renderBuffers();
                        features = new FeatureRenderDispatcher(new SubmitNodeStorage(), minecraft.getModelManager(),
                            buffers, minecraft.getAtlasManager(), renderBuffers.outlineBufferSource(),
                            renderBuffers.crumblingBufferSource(), minecraft.font, minecraft.gameRenderer.getGameRenderState());
                    }
                    buffers.endBatch();
                    part.submit(pose, features.getSubmitNodeStorage(), camera);
                    features.renderAllFeatures();
                    buffers.endBatch();
                }
            }
        } finally {
            if (features != null) {
                features.close();
            }
        }
    }

    public void nested(PoseStack pose, Part part) {
        Matrix4f transform = new Matrix4f(pose.last().pose());
        this.batch = null;
        this.parts.add((parent, collector, camera) -> {
            parent.pushPose();
            parent.mulPose(transform);
            part.submit(parent, collector, camera);
            parent.popPose();
        });
    }

    public static void item(ItemStack stack, ItemDisplayContext context, int light, int overlay,
                            PoseStack pose, MultiBufferSource buffers, Level level, int seed) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        ((PlasticRenderGeometry) buffers).nested(pose, (parent, collector, camera) -> state.submit(parent, collector, light, overlay, 0));
    }

    @FunctionalInterface
    public interface Part {
        void submit(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera);
    }

    public static final class EntityState extends EntityRenderState {
        public int packedLightOverride = -1;
        public final PlasticRenderGeometry geometry = new PlasticRenderGeometry();
    }

    public static final class BlockState extends BlockEntityRenderState {
        public final PlasticRenderGeometry geometry = new PlasticRenderGeometry();
    }

    private static final class Batch implements VertexConsumer, Part {
        private static final int STRIDE = 12;
        private final RenderType type;
        private int[] vertices = new int[STRIDE * 16];
        private int size;

        private Batch(RenderType type) {
            this.type = type;
        }

        @Override
        public void submit(PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
            collector.submitCustomGeometry(pose, this.type, this::emit);
        }

        private void emit(PoseStack.Pose pose, VertexConsumer out) {
            for (int i = 0; i < this.size; i += STRIDE) {
                out.addVertex(pose, value(i), value(i + 1), value(i + 2)).setColor(this.vertices[i + 3])
                    .setUv(value(i + 4), value(i + 5)).setOverlay(this.vertices[i + 6]).setLight(this.vertices[i + 7])
                    .setNormal(pose, value(i + 8), value(i + 9), value(i + 10)).setLineWidth(value(i + 11));
            }
        }

        private float value(int index) {
            return Float.intBitsToFloat(this.vertices[index]);
        }

        private void put(int offset, float value) {
            this.vertices[this.size - STRIDE + offset] = Float.floatToRawIntBits(value);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (this.size == this.vertices.length) {
                this.vertices = Arrays.copyOf(this.vertices, this.size * 2);
            }
            this.size += STRIDE;
            put(0, x);
            put(1, y);
            put(2, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return setColor(ARGB.color(a, r, g, b));
        }

        @Override
        public VertexConsumer setColor(int argb) {
            this.vertices[this.size - STRIDE + 3] = argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            put(4, u);
            put(5, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.vertices[this.size - STRIDE + 6] = (u & 65535) | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.vertices[this.size - STRIDE + 7] = (u & 65535) | v << 16;
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            put(8, x);
            put(9, y);
            put(10, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            put(11, width);
            return this;
        }
    }
}
