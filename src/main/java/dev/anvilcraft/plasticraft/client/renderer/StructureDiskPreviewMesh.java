package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.util.LevelLike;
import dev.dubhe.anvilcraft.util.VertexConsumerWithPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 磁盘缩略图的静态网格；邻接视图准备和模型烘焙分帧进行，特殊方块实体仍实时渲染。 */
final class StructureDiskPreviewMesh implements AutoCloseable {
    private static final long BUILD_BUDGET_NS = 2_000_000L;
    private static final int MAX_BLOCKS_PER_FRAME = 2048;
    private static final int BLOCKS_PER_BATCH = 4096;

    private final Source source;
    private final LevelLike view;
    private final RandomSource random = RandomSource.create();
    private final Map<RenderType, PendingBuffer> pending = new LinkedHashMap<>();
    private final Map<RenderType, List<CachedBuffer>> uploaded = new LinkedHashMap<>();
    private final List<CachedBuffer> translucentBuffers = new ArrayList<>();
    private final List<BlockEntity> blockEntities = new ArrayList<>();
    private int prepared;
    private int baked;
    private int batchStart;
    private int horizontalSize = 1;
    private int verticalSize = 1;
    private int sortCursor;

    StructureDiskPreviewMesh(ClientLevel level, Source source) {
        this.source = source;
        this.view = new LevelLike(level);
    }

    int horizontalSize() {
        return this.horizontalSize;
    }

    int verticalSize() {
        return this.verticalSize;
    }

    float progress() {
        if (this.source.size() == 0) return 1.0F;
        return (this.prepared + this.baked) / (2.0F * this.source.size());
    }

    void prepare() {
        long deadline = System.nanoTime() + BUILD_BUDGET_NS;
        int processed = 0;
        while (this.prepared < this.source.size() && processed++ < MAX_BLOCKS_PER_FRAME) {
            BlockPos pos = this.source.pos(this.prepared);
            BlockState state = this.source.state(this.prepared++);
            if (!state.isAir()) {
                this.view.setBlockState(pos, state);
                this.horizontalSize = Math.max(this.horizontalSize, Math.max(pos.getX(), pos.getZ()) + 1);
                this.verticalSize = Math.max(this.verticalSize, pos.getY() + 1);
                BlockEntity entity = this.view.getBlockEntity(pos);
                if (entity != null) this.blockEntities.add(entity);
            }
            if (System.nanoTime() >= deadline) return;
        }
        // 完整邻接视图建好后才烘焙，避免把尚未处理的邻块误认为空气而留下内部面。
        if (this.prepared < this.source.size()) return;
        PoseStack pose = new PoseStack();
        while (this.baked < this.source.size() && processed++ < MAX_BLOCKS_PER_FRAME) {
            this.bake(this.source.pos(this.baked++), pose);
            if (this.baked - this.batchStart >= BLOCKS_PER_BATCH || this.baked == this.source.size()) {
                this.upload();
                this.batchStart = this.baked;
                return;
            }
            if (System.nanoTime() >= deadline) return;
        }
    }

    private void bake(BlockPos pos, PoseStack pose) {
        BlockState state = this.view.getBlockState(pos);
        if (state.isAir()) return;
        BlockRenderDispatcher renderer = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        try {
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) {
                VertexConsumer consumer = this.buffer(ItemBlockRenderTypes.getRenderLayer(fluid));
                renderer.renderLiquid(pos, this.view, new VertexConsumerWithPose(consumer, pose.last(), pos), state, fluid);
            }
            if (state.getRenderShape() == RenderShape.MODEL) {
                this.random.setSeed(state.getSeed(pos));
                for (RenderType type : renderer.getBlockModel(state).getRenderTypes(state, this.random, ModelData.EMPTY)) {
                    renderer.renderBatched(state, pos, this.view, pose, this.buffer(type), true,
                        this.random, ModelData.EMPTY, type);
                }
            }
        } finally {
            pose.popPose();
        }
    }

    private VertexConsumer buffer(RenderType type) {
        return this.pending.computeIfAbsent(type, PendingBuffer::new).builder;
    }

    private void upload() {
        try {
            for (Map.Entry<RenderType, PendingBuffer> entry : this.pending.entrySet()) {
                MeshData mesh = entry.getValue().builder.build();
                if (mesh == null) continue;
                VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                try (ByteBufferBuilder indexOwner = new ByteBufferBuilder(65536)) {
                    MeshData.SortState sortState = entry.getKey().sortOnUpload()
                        ? mesh.sortQuads(indexOwner, VertexSorting.ORTHOGRAPHIC_Z) : null;
                    buffer.bind();
                    buffer.upload(mesh);
                    CachedBuffer cached = new CachedBuffer(buffer, sortState);
                    this.uploaded.computeIfAbsent(entry.getKey(), key -> new ArrayList<>())
                        .add(cached);
                    if (sortState != null) this.translucentBuffers.add(cached);
                } catch (RuntimeException exception) {
                    buffer.close();
                    throw exception;
                }
            }
        } finally {
            VertexBuffer.unbind();
            this.pending.values().forEach(PendingBuffer::close);
            this.pending.clear();
        }
    }

    void render(PoseStack pose) {
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose());
        try {
            this.sortTranslucent(modelView);
            this.renderLayers(modelView, false);
            this.renderLayers(modelView, true);
        } finally {
            VertexBuffer.unbind();
        }
        if (this.prepared < this.source.size()) return;
        Minecraft minecraft = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Iterator<BlockEntity> iterator = this.blockEntities.iterator();
        while (iterator.hasNext()) {
            BlockEntity entity = iterator.next();
            BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(entity);
            if (renderer == null) continue;
            BlockPos pos = entity.getBlockPos();
            pose.pushPose();
            try {
                pose.translate(pos.getX(), pos.getY(), pos.getZ());
                renderer.render(entity, minecraft.getTimer().getGameTimeDeltaPartialTick(true), pose, buffers,
                    0xF000F0, OverlayTexture.NO_OVERLAY);
            } catch (Exception exception) {
                // 第三方渲染器可能要求真实世界环境；一次失败后停止尝试，避免每帧异常拖慢悬浮窗。
                iterator.remove();
                AnvilcraftPlasticraft.LOGGER.warn("Cannot render structure disk block entity at {}", pos, exception);
            } finally {
                pose.popPose();
            }
        }
        buffers.endBatch();
    }

    private void renderLayers(Matrix4f modelView, boolean translucent) {
        Vector3f direction = sortDirection(modelView);
        for (Map.Entry<RenderType, List<CachedBuffer>> entry : this.uploaded.entrySet()) {
            RenderType type = entry.getKey();
            if (type.sortOnUpload() != translucent) continue;
            if (translucent) {
                entry.getValue().sort(Comparator.comparingDouble(buffer -> -buffer.center.dot(direction)));
            }
            type.setupRenderState();
            try {
                for (CachedBuffer cached : entry.getValue()) {
                    cached.buffer.bind();
                    cached.buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
                }
            } finally {
                type.clearRenderState();
            }
        }
    }

    private static Vector3f sortDirection(Matrix4f modelView) {
        return new Vector3f(-modelView.m02(), -modelView.m12(), -modelView.m22()).normalize();
    }

    private void sortTranslucent(Matrix4f modelView) {
        List<CachedBuffer> translucent = this.translucentBuffers;
        if (translucent.isEmpty()) return;
        Vector3f direction = sortDirection(modelView);
        // 透明面只重排索引；每帧最多处理一个批次，避免旋转时集中排序整座结构。
        for (int checked = 0; checked < translucent.size(); checked++) {
            this.sortCursor %= translucent.size();
            CachedBuffer cached = translucent.get(this.sortCursor++);
            if (cached.sortState == null || cached.direction.dot(direction) > 0.985F) continue;
            try (ByteBufferBuilder owner = new ByteBufferBuilder(65536)) {
                ByteBufferBuilder.Result indices = cached.sortState.buildSortedIndexBuffer(owner,
                    VertexSorting.byDistance(vector -> vector.dot(direction)));
                if (indices != null) {
                    cached.buffer.bind();
                    cached.buffer.uploadIndexBuffer(indices);
                }
            }
            cached.direction.set(direction);
            return;
        }
    }

    @Override
    public void close() {
        this.pending.values().forEach(PendingBuffer::close);
        this.pending.clear();
        this.uploaded.values().forEach(buffers -> buffers.forEach(cached -> cached.buffer.close()));
        this.uploaded.clear();
        this.translucentBuffers.clear();
        this.blockEntities.clear();
    }

    private static final class CachedBuffer {
        private final VertexBuffer buffer;
        private final @Nullable MeshData.SortState sortState;
        private final Vector3f center = new Vector3f();
        private final Vector3f direction = new Vector3f(0, 0, -1);

        private CachedBuffer(VertexBuffer buffer, @Nullable MeshData.SortState sortState) {
            this.buffer = buffer;
            this.sortState = sortState;
            if (sortState != null && sortState.centroids().length > 0) {
                for (Vector3f centroid : sortState.centroids()) this.center.add(centroid);
                this.center.div(sortState.centroids().length);
            }
        }
    }

    interface Source {
        int size();

        BlockPos pos(int index);

        BlockState state(int index);
    }

    private static final class PendingBuffer implements AutoCloseable {
        private final ByteBufferBuilder owner = new ByteBufferBuilder(65536);
        private final BufferBuilder builder;

        private PendingBuffer(RenderType type) {
            this.builder = new BufferBuilder(this.owner, type.mode(), type.format());
        }

        @Override
        public void close() {
            this.owner.close();
        }
    }
}
