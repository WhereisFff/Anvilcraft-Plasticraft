package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 1.21.1 投影静态网格后端。方块与流体按 16 方块区段异步烘焙,渲染线程只负责有界上传和绘制。
 */
final class BlueprintProjectionSectionRenderer {
    private enum StaticPass {
        DELIVERED_BLOCKS,
        DELIVERED_FLUIDS,
        HOLOGRAM
    }

    private static final int SECTION_SIZE = 16;
    private static final int MAX_SCENES = 8;
    private static final long MAX_GPU_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_OUTSTANDING_WORK = 16;
    private static final int MAX_SCENE_COMPILES_PER_FRAME = 4;
    private static final int MAX_UPLOADS_PER_FRAME = 2;
    private static final long MAX_UPLOAD_BYTES_PER_FRAME = 8L * 1024L * 1024L;
    private static final double TRANSLUCENT_RESORT_DISTANCE_SQUARED = 4.0D;

    private static final Map<SceneKey, Scene> SCENES = new LinkedHashMap<>(16, 0.75F, true);
    private static final ConcurrentLinkedQueue<ReadyWork> READY_WORK = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger OUTSTANDING_WORK = new AtomicInteger();
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final Set<String> FAILED_SCENES = new HashSet<>();
    private static final ConstructionProjectionIndex.DeliveredSnapshot EMPTY_DELIVERED =
        new ConstructionProjectionIndex.DeliveredSnapshot(0L, List.of());
    private static long frameId;

    private BlueprintProjectionSectionRenderer() {
    }

    static void beginFrame() {
        RenderSystem.assertOnRenderThread();
        frameId++;
        int uploads = 0;
        long uploadedBytes = 0L;
        try {
            while (uploads < MAX_UPLOADS_PER_FRAME) {
                ReadyWork ready = READY_WORK.peek();
                if (ready == null) break;
                long bytes = ready.bytes();
                if (uploads > 0 && uploadedBytes + bytes > MAX_UPLOAD_BYTES_PER_FRAME) break;
                READY_WORK.remove();
                try {
                    ready.upload();
                } catch (Throwable throwable) {
                    ready.close();
                    AnvilcraftPlasticraft.LOGGER.error("Failed to upload blueprint projection section", throwable);
                } finally {
                    OUTSTANDING_WORK.decrementAndGet();
                }
                uploads++;
                uploadedBytes += bytes;
            }
        } finally {
            try {
                VertexBuffer.unbind();
            } finally {
                trimCache(mostRecentScene());
            }
        }
    }

    @Nullable
    private static Scene mostRecentScene() {
        Scene recent = null;
        for (Scene scene : SCENES.values()) recent = scene;
        return recent;
    }

    static void renderDelivered(
        RenderLevelStageEvent event,
        PoseStack poseStack,
        BlueprintProjectionAnimator.Pose pose,
        String hash,
        int layerView,
        @Nullable UUID jobId,
        BlueprintRenderView view,
        PreparedSections sections,
        float blockAlpha,
        float fluidAlpha
    ) {
        render(
            event,
            poseStack,
            pose,
            hash,
            layerView,
            jobId,
            view,
            sections,
            blockAlpha,
            fluidAlpha,
            StaticPass.DELIVERED_BLOCKS
        );
    }

    static void renderDeliveredFluids(
        RenderLevelStageEvent event,
        PoseStack poseStack,
        BlueprintProjectionAnimator.Pose pose,
        String hash,
        int layerView,
        @Nullable UUID jobId,
        BlueprintRenderView view,
        PreparedSections sections,
        float blockAlpha,
        float fluidAlpha
    ) {
        render(
            event,
            poseStack,
            pose,
            hash,
            layerView,
            jobId,
            view,
            sections,
            blockAlpha,
            fluidAlpha,
            StaticPass.DELIVERED_FLUIDS
        );
    }

    static void renderHologram(
        RenderLevelStageEvent event,
        PoseStack poseStack,
        BlueprintProjectionAnimator.Pose pose,
        String hash,
        int layerView,
        @Nullable UUID jobId,
        BlueprintRenderView view,
        PreparedSections sections,
        float blockAlpha,
        float fluidAlpha
    ) {
        render(
            event,
            poseStack,
            pose,
            hash,
            layerView,
            jobId,
            view,
            sections,
            blockAlpha,
            fluidAlpha,
            StaticPass.HOLOGRAM
        );
    }

    private static void render(
        RenderLevelStageEvent event,
        PoseStack poseStack,
        BlueprintProjectionAnimator.Pose pose,
        String hash,
        int layerView,
        @Nullable UUID jobId,
        BlueprintRenderView view,
        PreparedSections sections,
        float blockAlpha,
        float fluidAlpha,
        StaticPass pass
    ) {
        RenderSystem.assertOnRenderThread();
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        BlueprintPlacement placement = pose.mesh();
        SceneKey key = new SceneKey(
            level,
            hash,
            placement.rotation(),
            placement.mirror(),
            jobId == null ? BlockPos.ZERO : placement.anchor(),
            layerView,
            jobId
        );
        ConstructionProjectionIndex.DeliveredSnapshot delivered = jobId == null
            ? EMPTY_DELIVERED
            : ConstructionProjectionIndex.deliveredSnapshot(level, jobId);
        Scene scene = scene(key, view, sections, delivered, blockAlpha, fluidAlpha);
        scene.refreshDelivered(delivered);

        List<VisibleSection> visible = scene.visibleSections(event, pose);
        if (visible.isEmpty()) return;
        Matrix4f localToScreen = new Matrix4f(event.getModelViewMatrix()).mul(poseStack.last().pose());
        Vector3f cameraLocal = cameraInLocalSpace(poseStack);
        boolean schedule = scene.lastScheduleFrame != frameId;
        if (schedule) {
            scene.lastScheduleFrame = frameId;
            scheduleCompiles(scene, visible, cameraLocal);
        }
        draw(scene, visible, localToScreen, event.getProjectionMatrix(), pass);
        if (schedule) scheduleResort(scene, cameraLocal);
        trimCache(scene);
    }

    static void clear() {
        GENERATION.incrementAndGet();
        List<Scene> scenes = new ArrayList<>(SCENES.values());
        SCENES.clear();
        for (Scene scene : scenes) {
            scene.close();
        }
        ReadyWork ready;
        while ((ready = READY_WORK.poll()) != null) {
            ready.close();
            OUTSTANDING_WORK.decrementAndGet();
        }
        FAILED_SCENES.clear();
    }

    private static Scene scene(
        SceneKey key,
        BlueprintRenderView view,
        PreparedSections sections,
        ConstructionProjectionIndex.DeliveredSnapshot delivered,
        float blockAlpha,
        float fluidAlpha
    ) {
        Scene cached = SCENES.get(key);
        if (cached != null) return cached;
        Scene created = new Scene(key, view, sections, delivered, blockAlpha, fluidAlpha);
        SCENES.put(key, created);
        trimCache(created);
        return created;
    }

    private static void trimCache(@Nullable Scene retained) {
        while (SCENES.size() > MAX_SCENES || totalGpuBytes() > MAX_GPU_BYTES) {
            Scene oldest = null;
            for (Scene candidate : SCENES.values()) {
                if (candidate != retained || SCENES.size() == 1) {
                    oldest = candidate;
                    break;
                }
            }
            if (oldest == null || oldest == retained && SCENES.size() == 1) break;
            SCENES.remove(oldest.key);
            oldest.close();
        }
    }

    private static long totalGpuBytes() {
        long bytes = 0L;
        for (Scene scene : SCENES.values()) {
            bytes += scene.gpuBytes;
        }
        return bytes;
    }

    private static void scheduleCompiles(Scene scene, List<VisibleSection> visible, Vector3f cameraLocal) {
        int scheduled = 0;
        for (VisibleSection visibleSection : visible) {
            if (scheduled >= MAX_SCENE_COMPILES_PER_FRAME || !reserveWork()) break;
            SectionState state = visibleSection.state();
            if (state.inFlight || state.uploadedRevision == state.revision) {
                releaseWork();
                continue;
            }
            state.inFlight = true;
            int revision = state.revision;
            DeliveredLookup delivered = scene.delivered;
            Vector3f sortOrigin = new Vector3f(cameraLocal);
            int generation = GENERATION.get();
            try {
                CompletableFuture.runAsync(
                    () -> publishReady(
                        scene,
                        generation,
                        compile(scene, state, revision, delivered, sortOrigin)
                    ),
                    Util.backgroundExecutor()
                );
            } catch (RuntimeException exception) {
                state.inFlight = false;
                releaseWork();
                logFailure(scene, exception);
                continue;
            }
            scheduled++;
        }
    }

    private static ReadyWork compile(
        Scene scene,
        SectionState state,
        int revision,
        DeliveredLookup delivered,
        Vector3f sortOrigin
    ) {
        try {
            return new CompiledSection(
                scene,
                state,
                revision,
                compileLayers(scene, state.definition, delivered, sortOrigin),
                null
            );
        } catch (Throwable throwable) {
            return new CompiledSection(scene, state, revision, PendingLayers.EMPTY, throwable);
        }
    }

    private static PendingLayers compileLayers(
        Scene scene,
        SectionDefinition definition,
        DeliveredLookup delivered,
        Vector3f sortOrigin
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        Map<RenderType, LayerBuilder> hologramBuilders = new IdentityHashMap<>();
        Map<RenderType, LayerBuilder> deliveredBuilders = new IdentityHashMap<>();
        Map<RenderType, LayerBuilder> deliveredFluidBuilders = new IdentityHashMap<>();
        BlockAndTintGetter hiddenNeighbors = NeighborView.hologram(scene.view, true);
        BlockAndTintGetter allNeighbors = NeighborView.hologram(scene.view, false);
        BlockAndTintGetter hiddenDelivered = NeighborView.delivered(scene.view, scene.key, delivered, true);
        BlockAndTintGetter allDelivered = NeighborView.delivered(scene.view, scene.key, delivered, false);
        RandomSource random = RandomSource.create();
        PoseStack poseStack = new PoseStack();
        ModelBlockRenderer.enableCaching();
        try {
            for (BlockPos pos : definition.modelBlocks) {
                BlockState deliveredState = delivered.stateAt(scene.key, pos);
                boolean isDelivered = deliveredState != null;
                BlockState state = isDelivered ? deliveredState : scene.view.realState(pos);
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                BlockAndTintGetter renderView;
                if (isDelivered) {
                    renderView = isSolidCube(state) ? hiddenDelivered : allDelivered;
                } else {
                    renderView = isSolidCube(state) ? hiddenNeighbors : allNeighbors;
                }
                BakedModel model = dispatcher.getBlockModel(state);
                ModelData modelData = model.getModelData(
                    renderView,
                    pos,
                    state,
                    renderView.getModelData(pos)
                );
                random.setSeed(state.getSeed(pos));
                var renderTypes = model.getRenderTypes(state, random, modelData);
                for (RenderType renderType : renderTypes) {
                    Map<RenderType, LayerBuilder> builders = isDelivered ? deliveredBuilders : hologramBuilders;
                    LayerBuilder layer = builders.computeIfAbsent(renderType, LayerBuilder::new);
                    random.setSeed(state.getSeed(pos));
                    poseStack.pushPose();
                    poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                    dispatcher.renderBatched(
                        state,
                        pos,
                        renderView,
                        poseStack,
                        isDelivered ? layer.builder : new AlphaVertexConsumer(layer.builder, scene.blockAlpha),
                        true,
                        random,
                        modelData,
                        renderType
                    );
                    poseStack.popPose();
                }
            }
            for (BlockPos pos : definition.fluidBlocks) {
                BlockState deliveredState = delivered.stateAt(scene.key, pos);
                boolean isDelivered = deliveredState != null;
                BlockState state = isDelivered ? deliveredState : scene.view.realState(pos);
                FluidState fluid = state.getFluidState();
                if (fluid.isEmpty()) continue;
                RenderType renderType = ItemBlockRenderTypes.getRenderLayer(fluid);
                Map<RenderType, LayerBuilder> builders = isDelivered
                    ? deliveredFluidBuilders
                    : hologramBuilders;
                LayerBuilder layer = builders.computeIfAbsent(renderType, LayerBuilder::new);
                VertexConsumer target = isDelivered
                    ? layer.builder
                    : new AlphaVertexConsumer(layer.builder, scene.fluidAlpha);
                VertexConsumer consumer = new PositionOffsetVertexConsumer(
                    target,
                    pos.getX(),
                    pos.getY(),
                    pos.getZ()
                );
                BlockAndTintGetter fluidView = isDelivered ? allDelivered : allNeighbors;
                dispatcher.renderLiquid(
                    BlockPos.ZERO,
                    new ShiftedView(fluidView, pos.immutable()),
                    consumer,
                    state,
                    fluid
                );
            }
            Map<RenderType, PendingLayer> hologram = Map.of();
            Map<RenderType, PendingLayer> deliveredLayers = Map.of();
            try {
                hologram = buildPendingLayers(hologramBuilders, sortOrigin);
                deliveredLayers = buildPendingLayers(deliveredBuilders, sortOrigin);
                Map<RenderType, PendingLayer> deliveredFluids =
                    buildPendingLayers(deliveredFluidBuilders, sortOrigin);
                return new PendingLayers(hologram, deliveredLayers, deliveredFluids);
            } catch (Throwable throwable) {
                closePendingLayers(hologram);
                closePendingLayers(deliveredLayers);
                throw throwable;
            }
        } finally {
            ModelBlockRenderer.clearCache();
            for (LayerBuilder builder : hologramBuilders.values()) {
                builder.closeIfUnbuilt();
            }
            for (LayerBuilder builder : deliveredBuilders.values()) {
                builder.closeIfUnbuilt();
            }
            for (LayerBuilder builder : deliveredFluidBuilders.values()) {
                builder.closeIfUnbuilt();
            }
        }
    }

    private static Map<RenderType, PendingLayer> buildPendingLayers(
        Map<RenderType, LayerBuilder> builders,
        Vector3f sortOrigin
    ) {
        Map<RenderType, PendingLayer> result = new IdentityHashMap<>();
        try {
            for (Map.Entry<RenderType, LayerBuilder> entry : builders.entrySet()) {
                PendingLayer pending = entry.getValue().build(sortOrigin);
                if (pending != null) result.put(entry.getKey(), pending);
            }
            return result;
        } catch (Throwable throwable) {
            closePendingLayers(result);
            throw throwable;
        }
    }

    private static void closePendingLayers(Map<RenderType, PendingLayer> layers) {
        for (PendingLayer layer : layers.values()) layer.close();
    }

    private static void draw(
        Scene scene,
        List<VisibleSection> visible,
        Matrix4f modelView,
        Matrix4f projection,
        StaticPass pass
    ) {
        boolean hologram = pass == StaticPass.HOLOGRAM;
        boolean worldTarget = pass == StaticPass.DELIVERED_FLUIDS;
        Set<RenderType> renderTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (VisibleSection section : visible) {
            renderTypes.addAll(uploadedLayers(section.state, pass).keySet());
        }
        List<RenderType> orderedTypes = new ArrayList<>(renderTypes);
        orderedTypes.sort(Comparator
            .comparingInt(BlueprintProjectionSectionRenderer::renderTypeOrder)
            .thenComparing(Object::toString));
        for (RenderType original : orderedTypes) {
            List<VisibleSection> orderedSections = original.sortOnUpload()
                ? scene.visibleFarCache
                : visible;
            RenderType mapped = hologram ? BlueprintProjectionRenderTypes.overlay(original) : original;
            ShaderInstance shader = null;
            try {
                if (hologram) {
                    mapped.setupRenderState();
                } else if (worldTarget) {
                    BlueprintProjectionRenderTypes.setupInWorld(original);
                } else {
                    BlueprintProjectionRenderTypes.setupDelivered(original);
                }
                shader = RenderSystem.getShader();
                if (shader == null) continue;
                shader.setDefaultUniforms(
                    VertexFormat.Mode.QUADS,
                    modelView,
                    projection,
                    Minecraft.getInstance().getWindow()
                );
                shader.apply();
                for (VisibleSection section : orderedSections) {
                    UploadedLayer layer = uploadedLayers(section.state, pass).get(original);
                    if (layer == null) continue;
                    layer.buffer.bind();
                    layer.buffer.draw();
                }
            } finally {
                if (shader != null) shader.clear();
                VertexBuffer.unbind();
                if (hologram) {
                    mapped.clearRenderState();
                } else if (worldTarget) {
                    BlueprintProjectionRenderTypes.clearInWorld(original);
                } else {
                    BlueprintProjectionRenderTypes.clearDelivered(original);
                }
            }
        }
    }

    private static Map<RenderType, UploadedLayer> uploadedLayers(SectionState state, StaticPass pass) {
        return switch (pass) {
            case DELIVERED_BLOCKS -> state.deliveredUploaded;
            case DELIVERED_FLUIDS -> state.deliveredFluidUploaded;
            case HOLOGRAM -> state.hologramUploaded;
        };
    }

    private static int renderTypeOrder(RenderType type) {
        List<RenderType> chunkLayers = RenderType.chunkBufferLayers();
        for (int index = 0; index < chunkLayers.size(); index++) {
            if (chunkLayers.get(index) == type) return index;
        }
        return chunkLayers.size();
    }

    private static void scheduleResort(Scene scene, Vector3f cameraLocal) {
        if (OUTSTANDING_WORK.get() >= MAX_OUTSTANDING_WORK) return;
        for (VisibleSection visibleSection : scene.visibleFarCache) {
            SectionState state = visibleSection.state;
            for (Map<RenderType, UploadedLayer> layers
                : List.of(state.hologramUploaded, state.deliveredUploaded, state.deliveredFluidUploaded)) {
                for (UploadedLayer layer : layers.values()) {
                    if (layer.sortState == null || layer.sortPending.get()) continue;
                    if (layer.lastSortOrigin.distanceSquared(cameraLocal)
                        <= TRANSLUCENT_RESORT_DISTANCE_SQUARED) continue;
                    if (!reserveWork()) return;
                    if (!layer.sortPending.compareAndSet(false, true)) {
                        releaseWork();
                        continue;
                    }
                    Vector3f sortOrigin = new Vector3f(cameraLocal);
                    int generation = GENERATION.get();
                    try {
                        CompletableFuture.runAsync(
                            () -> publishReady(scene, generation, sort(scene, state, layer, sortOrigin)),
                            Util.backgroundExecutor()
                        );
                    } catch (RuntimeException exception) {
                        layer.sortPending.set(false);
                        releaseWork();
                        logFailure(scene, exception);
                    }
                    return;
                }
            }
        }
    }

    private static void publishReady(Scene scene, int generation, ReadyWork ready) {
        if (generation != GENERATION.get() || scene.closed) {
            ready.close();
            releaseWork();
            return;
        }
        READY_WORK.add(ready);
        if ((generation != GENERATION.get() || scene.closed) && READY_WORK.remove(ready)) {
            ready.close();
            releaseWork();
        }
    }

    private static ReadyWork sort(
        Scene scene,
        SectionState state,
        UploadedLayer layer,
        Vector3f sortOrigin
    ) {
        ByteBufferBuilder owner = new ByteBufferBuilder(Math.max(4096, layer.quadCount * 6 * 4));
        try {
            ByteBufferBuilder.Result indices = Objects.requireNonNull(layer.sortState)
                .buildSortedIndexBuffer(owner, VertexSorting.byDistance(sortOrigin));
            if (indices == null) {
                owner.close();
                return new SortedIndices(scene, state, layer, sortOrigin, null, null, null);
            }
            return new SortedIndices(scene, state, layer, sortOrigin, owner, indices, null);
        } catch (Throwable throwable) {
            owner.close();
            return new SortedIndices(scene, state, layer, sortOrigin, null, null, throwable);
        }
    }

    private static boolean reserveWork() {
        while (true) {
            int current = OUTSTANDING_WORK.get();
            if (current >= MAX_OUTSTANDING_WORK) return false;
            if (OUTSTANDING_WORK.compareAndSet(current, current + 1)) return true;
        }
    }

    private static void releaseWork() {
        OUTSTANDING_WORK.decrementAndGet();
    }

    private static Vector3f cameraInLocalSpace(PoseStack poseStack) {
        Matrix4f inverse = new Matrix4f(poseStack.last().pose());
        if (Math.abs(inverse.determinant()) < 1.0E-6F) return new Vector3f();
        inverse.invert();
        return inverse.transformPosition(new Vector3f());
    }

    private static boolean isSolidCube(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static AABB transformedBounds(
        BlueprintProjectionAnimator.Pose pose,
        SectionCoord coord
    ) {
        int minX = coord.x * SECTION_SIZE;
        int minY = coord.y * SECTION_SIZE;
        int minZ = coord.z * SECTION_SIZE;
        int maxX = minX + SECTION_SIZE;
        int maxY = minY + SECTION_SIZE;
        int maxZ = minZ + SECTION_SIZE;
        BlockPos anchor = pose.mesh().anchor();
        if (pose.extraYaw() == 0.0F && pose.extraMirrorX() == 1.0F && pose.extraMirrorZ() == 1.0F) {
            Vec3 offset = pose.displayedCenter().subtract(pose.bakedCenter());
            return new AABB(
                minX + anchor.getX() + offset.x,
                minY + anchor.getY() + offset.y,
                minZ + anchor.getZ() + offset.z,
                maxX + anchor.getX() + offset.x,
                maxY + anchor.getY() + offset.y,
                maxZ + anchor.getZ() + offset.z
            ).inflate(0.01D);
        }
        double worldMinX = Double.POSITIVE_INFINITY;
        double worldMinY = Double.POSITIVE_INFINITY;
        double worldMinZ = Double.POSITIVE_INFINITY;
        double worldMaxX = Double.NEGATIVE_INFINITY;
        double worldMaxY = Double.NEGATIVE_INFINITY;
        double worldMaxZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            int x = (corner & 1) == 0 ? minX : maxX;
            int y = (corner & 2) == 0 ? minY : maxY;
            int z = (corner & 4) == 0 ? minZ : maxZ;
            Vec3 transformed = pose.worldOf(new Vec3(
                x + anchor.getX(),
                y + anchor.getY(),
                z + anchor.getZ()
            ));
            worldMinX = Math.min(worldMinX, transformed.x);
            worldMinY = Math.min(worldMinY, transformed.y);
            worldMinZ = Math.min(worldMinZ, transformed.z);
            worldMaxX = Math.max(worldMaxX, transformed.x);
            worldMaxY = Math.max(worldMaxY, transformed.y);
            worldMaxZ = Math.max(worldMaxZ, transformed.z);
        }
        return new AABB(worldMinX, worldMinY, worldMinZ, worldMaxX, worldMaxY, worldMaxZ).inflate(0.01D);
    }

    private static void closeBuffer(VertexBuffer buffer) {
        if (RenderSystem.isOnRenderThread()) {
            buffer.close();
        } else {
            RenderSystem.recordRenderCall(buffer::close);
        }
    }

    private static void logFailure(Scene scene, Throwable throwable) {
        String failureKey = scene.key.hash + ":" + scene.key.rotation + ":" + scene.key.mirror;
        if (FAILED_SCENES.add(failureKey)) {
            AnvilcraftPlasticraft.LOGGER.error("Failed to compile blueprint projection section {}", failureKey, throwable);
        }
    }

    private record SceneKey(
        ClientLevel level,
        String hash,
        Rotation rotation,
        Mirror mirror,
        BlockPos anchor,
        int layerView,
        @Nullable UUID jobId
    ) {
        private SceneKey {
            anchor = anchor.immutable();
        }
    }

    private static final class Scene {
        private final SceneKey key;
        private final BlueprintRenderView view;
        private final Map<SectionCoord, SectionState> sections;
        private final float blockAlpha;
        private final float fluidAlpha;
        private DeliveredLookup delivered;
        private long gpuBytes;
        private volatile boolean closed;
        private long lastScheduleFrame = Long.MIN_VALUE;
        private long visibleFrame = Long.MIN_VALUE;
        private @Nullable BlueprintProjectionAnimator.Pose visiblePose;
        private List<VisibleSection> visibleCache = List.of();
        private List<VisibleSection> visibleFarCache = List.of();

        private Scene(
            SceneKey key,
            BlueprintRenderView view,
            PreparedSections sections,
            ConstructionProjectionIndex.DeliveredSnapshot delivered,
            float blockAlpha,
            float fluidAlpha
        ) {
            this.key = key;
            this.view = view;
            this.delivered = DeliveredLookup.from(delivered);
            this.blockAlpha = blockAlpha;
            this.fluidAlpha = fluidAlpha;
            this.sections = sections.instantiate();
        }

        private List<VisibleSection> visibleSections(
            RenderLevelStageEvent event,
            BlueprintProjectionAnimator.Pose pose
        ) {
            if (this.visibleFrame == frameId && Objects.equals(this.visiblePose, pose)) return this.visibleCache;
            Vec3 camera = event.getCamera().getPosition();
            List<VisibleSection> result = new ArrayList<>();
            for (SectionState section : this.sections.values()) {
                AABB bounds = transformedBounds(pose, section.definition.coord);
                if (!event.getFrustum().isVisible(bounds)) continue;
                result.add(new VisibleSection(section, bounds.distanceToSqr(camera)));
            }
            this.visibleFrame = frameId;
            this.visiblePose = pose;
            result.sort(Comparator.comparingDouble(VisibleSection::distanceSquared));
            this.visibleCache = List.copyOf(result);
            List<VisibleSection> far = new ArrayList<>(result);
            Collections.reverse(far);
            this.visibleFarCache = List.copyOf(far);
            return this.visibleCache;
        }

        private void refreshDelivered(ConstructionProjectionIndex.DeliveredSnapshot snapshot) {
            if (this.delivered.revision == snapshot.revision()) return;
            Map<Long, DeliveredLookupSection> nextSections = new HashMap<>();
            Set<SectionCoord> invalidated = new HashSet<>();
            for (ConstructionProjectionIndex.DeliveredSection section : snapshot.sections()) {
                DeliveredLookupSection previous = this.delivered.sections.get(section.section());
                DeliveredLookupSection next;
                if (previous != null && previous.revision == section.revision()) {
                    next = previous;
                } else {
                    next = DeliveredLookupSection.from(section);
                    collectInvalidated(previous, invalidated);
                    collectInvalidated(next, invalidated);
                }
                nextSections.put(section.section(), next);
            }
            for (Map.Entry<Long, DeliveredLookupSection> previous : this.delivered.sections.entrySet()) {
                if (!nextSections.containsKey(previous.getKey())) {
                    collectInvalidated(previous.getValue(), invalidated);
                }
            }
            this.delivered = new DeliveredLookup(snapshot.revision(), Map.copyOf(nextSections));
            for (SectionCoord coord : invalidated) {
                SectionState section = this.sections.get(coord);
                if (section != null) section.revision++;
            }
        }

        private void collectInvalidated(
            @Nullable DeliveredLookupSection deliveredSection,
            Set<SectionCoord> invalidated
        ) {
            if (deliveredSection == null) return;
            for (ConstructionProjectionIndex.Collision collision : deliveredSection.entries) {
                BlockPos local = collision.pos().subtract(this.key.anchor);
                invalidated.add(SectionCoord.of(local));
                for (Direction direction : Direction.values()) {
                    invalidated.add(SectionCoord.of(local.relative(direction)));
                }
            }
        }

        private void close() {
            if (this.closed) return;
            this.closed = true;
            for (SectionState section : this.sections.values()) {
                section.close();
            }
            this.gpuBytes = 0L;
        }
    }

    static final class PreparedSections {
        private @Nullable Map<SectionCoord, MutableSection> grouped = new TreeMap<>(Comparator
            .comparingInt(SectionCoord::x)
            .thenComparingInt(SectionCoord::y)
            .thenComparingInt(SectionCoord::z));
        private @Nullable List<SectionDefinition> definitions;

        void addModel(BlockPos pos) {
            this.section(pos).modelBlocks.add(pos.immutable());
        }

        void addFluid(BlockPos pos) {
            this.section(pos).fluidBlocks.add(pos.immutable());
        }

        void freeze() {
            if (this.definitions != null) return;
            Map<SectionCoord, MutableSection> sections = Objects.requireNonNull(this.grouped);
            List<SectionDefinition> definitions = new ArrayList<>(sections.size());
            for (Map.Entry<SectionCoord, MutableSection> entry : sections.entrySet()) {
                MutableSection section = entry.getValue();
                definitions.add(new SectionDefinition(
                    entry.getKey(),
                    Collections.unmodifiableList(section.modelBlocks),
                    Collections.unmodifiableList(section.fluidBlocks)
                ));
            }
            this.definitions = List.copyOf(definitions);
            this.grouped = null;
        }

        private MutableSection section(BlockPos pos) {
            Map<SectionCoord, MutableSection> sections = this.grouped;
            if (sections == null) throw new IllegalStateException("Blueprint projection sections are frozen");
            return sections.computeIfAbsent(SectionCoord.of(pos), ignored -> new MutableSection());
        }

        private Map<SectionCoord, SectionState> instantiate() {
            this.freeze();
            Map<SectionCoord, SectionState> result = new LinkedHashMap<>();
            for (SectionDefinition definition : Objects.requireNonNull(this.definitions)) {
                result.put(definition.coord, new SectionState(definition));
            }
            return result;
        }
    }

    private static final class MutableSection {
        private final List<BlockPos> modelBlocks = new ArrayList<>();
        private final List<BlockPos> fluidBlocks = new ArrayList<>();
    }

    private record SectionDefinition(
        SectionCoord coord,
        List<BlockPos> modelBlocks,
        List<BlockPos> fluidBlocks
    ) {
    }

    private static final class SectionState {
        private final SectionDefinition definition;
        private Map<RenderType, UploadedLayer> hologramUploaded = new IdentityHashMap<>();
        private Map<RenderType, UploadedLayer> deliveredUploaded = new IdentityHashMap<>();
        private Map<RenderType, UploadedLayer> deliveredFluidUploaded = new IdentityHashMap<>();
        private int revision;
        private int uploadedRevision = -1;
        private boolean inFlight;

        private SectionState(SectionDefinition definition) {
            this.definition = definition;
        }

        private void close() {
            closeUploadedLayers(this.hologramUploaded);
            closeUploadedLayers(this.deliveredUploaded);
            closeUploadedLayers(this.deliveredFluidUploaded);
            this.hologramUploaded = new IdentityHashMap<>();
            this.deliveredUploaded = new IdentityHashMap<>();
            this.deliveredFluidUploaded = new IdentityHashMap<>();
        }
    }

    private static void closeUploadedLayers(Map<RenderType, UploadedLayer> layers) {
        for (UploadedLayer layer : layers.values()) layer.close();
    }

    private static long uploadedBytes(Map<RenderType, UploadedLayer> layers) {
        long bytes = 0L;
        for (UploadedLayer layer : layers.values()) bytes += layer.bytes;
        return bytes;
    }

    private static void discardUploadedRevision(Scene scene, SectionState state, int revision) {
        scene.gpuBytes -= uploadedBytes(state.hologramUploaded);
        scene.gpuBytes -= uploadedBytes(state.deliveredUploaded);
        scene.gpuBytes -= uploadedBytes(state.deliveredFluidUploaded);
        closeUploadedLayers(state.hologramUploaded);
        closeUploadedLayers(state.deliveredUploaded);
        closeUploadedLayers(state.deliveredFluidUploaded);
        state.hologramUploaded = new IdentityHashMap<>();
        state.deliveredUploaded = new IdentityHashMap<>();
        state.deliveredFluidUploaded = new IdentityHashMap<>();
        state.uploadedRevision = revision;
    }

    private record DeliveredLookup(long revision, Map<Long, DeliveredLookupSection> sections) {
        private static DeliveredLookup from(ConstructionProjectionIndex.DeliveredSnapshot snapshot) {
            Map<Long, DeliveredLookupSection> sections = new HashMap<>();
            for (ConstructionProjectionIndex.DeliveredSection section : snapshot.sections()) {
                sections.put(section.section(), DeliveredLookupSection.from(section));
            }
            return new DeliveredLookup(snapshot.revision(), Map.copyOf(sections));
        }

        @Nullable
        private BlockState stateAt(SceneKey key, BlockPos local) {
            BlockPos world = local.offset(key.anchor);
            DeliveredLookupSection section = this.sections.get(SectionPos.asLong(world));
            return section == null ? null : section.stateAt(world.asLong());
        }
    }

    private record DeliveredLookupSection(long revision, List<ConstructionProjectionIndex.Collision> entries) {
        private static DeliveredLookupSection from(ConstructionProjectionIndex.DeliveredSection section) {
            return new DeliveredLookupSection(section.revision(), section.entries());
        }

        @Nullable
        private BlockState stateAt(long position) {
            int low = 0;
            int high = this.entries.size() - 1;
            while (low <= high) {
                int middle = low + (high - low) / 2;
                ConstructionProjectionIndex.Collision collision = this.entries.get(middle);
                int comparison = Long.compare(collision.pos().asLong(), position);
                if (comparison < 0) {
                    low = middle + 1;
                } else if (comparison > 0) {
                    high = middle - 1;
                } else {
                    return collision.state();
                }
            }
            return null;
        }
    }

    private record SectionCoord(int x, int y, int z) {
        private static SectionCoord of(BlockPos pos) {
            return new SectionCoord(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getY()),
                SectionPos.blockToSectionCoord(pos.getZ())
            );
        }
    }

    private record VisibleSection(SectionState state, double distanceSquared) {
    }

    private static final class LayerBuilder {
        private final RenderType renderType;
        private final ByteBufferBuilder owner;
        private final BufferBuilder builder;
        private boolean built;

        private LayerBuilder(RenderType renderType) {
            this.renderType = renderType;
            int initialSize = Math.max(16 * 1024, Math.min(256 * 1024, renderType.bufferSize() / 16));
            this.owner = new ByteBufferBuilder(initialSize);
            this.builder = new BufferBuilder(this.owner, VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        }

        @Nullable
        private PendingLayer build(Vector3f sortOrigin) {
            MeshData mesh = this.builder.build();
            this.built = true;
            if (mesh == null) {
                this.owner.close();
                return null;
            }
            ByteBufferBuilder indexOwner = null;
            MeshData.SortState sortState = null;
            try {
                if (this.renderType.sortOnUpload()) {
                    indexOwner = new ByteBufferBuilder(Math.max(4096, mesh.drawState().indexCount() * 4));
                    sortState = mesh.sortQuads(indexOwner, VertexSorting.byDistance(sortOrigin));
                }
                long bytes = mesh.vertexBuffer().remaining();
                if (mesh.indexBuffer() != null) bytes += mesh.indexBuffer().remaining();
                return new PendingLayer(
                    mesh,
                    this.owner,
                    indexOwner,
                    sortState,
                    new Vector3f(sortOrigin),
                    mesh.drawState().vertexCount() / 4,
                    bytes
                );
            } catch (Throwable throwable) {
                mesh.close();
                this.owner.close();
                if (indexOwner != null) indexOwner.close();
                throw throwable;
            }
        }

        private void closeIfUnbuilt() {
            if (!this.built) this.owner.close();
        }
    }

    private static final class PendingLayer implements AutoCloseable {
        private final MeshData mesh;
        private final ByteBufferBuilder vertexOwner;
        private final @Nullable ByteBufferBuilder indexOwner;
        private final @Nullable MeshData.SortState sortState;
        private final Vector3f sortOrigin;
        private final int quadCount;
        private final long bytes;
        private boolean consumed;

        private PendingLayer(
            MeshData mesh,
            ByteBufferBuilder vertexOwner,
            @Nullable ByteBufferBuilder indexOwner,
            @Nullable MeshData.SortState sortState,
            Vector3f sortOrigin,
            int quadCount,
            long bytes
        ) {
            this.mesh = mesh;
            this.vertexOwner = vertexOwner;
            this.indexOwner = indexOwner;
            this.sortState = sortState;
            this.sortOrigin = sortOrigin;
            this.quadCount = quadCount;
            this.bytes = bytes;
        }

        private UploadedLayer upload() {
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            try {
                buffer.bind();
                buffer.upload(this.mesh);
                this.consumed = true;
                return new UploadedLayer(
                    buffer,
                    this.sortState,
                    this.sortOrigin,
                    this.quadCount,
                    this.bytes
                );
            } catch (Throwable throwable) {
                buffer.close();
                throw throwable;
            } finally {
                this.vertexOwner.close();
                if (this.indexOwner != null) this.indexOwner.close();
            }
        }

        @Override
        public void close() {
            if (!this.consumed) this.mesh.close();
            this.vertexOwner.close();
            if (this.indexOwner != null) this.indexOwner.close();
            this.consumed = true;
        }
    }

    private static final class UploadedLayer implements AutoCloseable {
        private final VertexBuffer buffer;
        private final @Nullable MeshData.SortState sortState;
        private final AtomicBoolean sortPending = new AtomicBoolean();
        private final Vector3f lastSortOrigin;
        private final int quadCount;
        private final long bytes;

        private UploadedLayer(
            VertexBuffer buffer,
            @Nullable MeshData.SortState sortState,
            Vector3f lastSortOrigin,
            int quadCount,
            long bytes
        ) {
            this.buffer = buffer;
            this.sortState = sortState;
            this.lastSortOrigin = new Vector3f(lastSortOrigin);
            this.quadCount = quadCount;
            this.bytes = bytes;
        }

        @Override
        public void close() {
            closeBuffer(this.buffer);
        }
    }

    private interface ReadyWork extends AutoCloseable {
        long bytes();

        void upload();

        @Override
        void close();
    }

    private record PendingLayers(
        Map<RenderType, PendingLayer> hologram,
        Map<RenderType, PendingLayer> delivered,
        Map<RenderType, PendingLayer> deliveredFluids
    ) {
        private static final PendingLayers EMPTY = new PendingLayers(Map.of(), Map.of(), Map.of());

        private long bytes() {
            long bytes = 0L;
            for (PendingLayer layer : this.hologram.values()) bytes += layer.bytes;
            for (PendingLayer layer : this.delivered.values()) bytes += layer.bytes;
            for (PendingLayer layer : this.deliveredFluids.values()) bytes += layer.bytes;
            return bytes;
        }

        private void close() {
            closePendingLayers(this.hologram);
            closePendingLayers(this.delivered);
            closePendingLayers(this.deliveredFluids);
        }
    }

    private static final class CompiledSection implements ReadyWork {
        private final Scene scene;
        private final SectionState state;
        private final int revision;
        private final PendingLayers layers;
        private final @Nullable Throwable failure;

        private CompiledSection(
            Scene scene,
            SectionState state,
            int revision,
            PendingLayers layers,
            @Nullable Throwable failure
        ) {
            this.scene = scene;
            this.state = state;
            this.revision = revision;
            this.layers = layers;
            this.failure = failure;
        }

        @Override
        public long bytes() {
            return this.layers.bytes();
        }

        @Override
        public void upload() {
            this.state.inFlight = false;
            if (this.scene.closed || SCENES.get(this.scene.key) != this.scene || this.state.revision != this.revision) {
                close();
                return;
            }
            if (this.failure != null) {
                discardUploadedRevision(this.scene, this.state, this.revision);
                logFailure(this.scene, this.failure);
                return;
            }
            Map<RenderType, UploadedLayer> hologram = new IdentityHashMap<>();
            Map<RenderType, UploadedLayer> delivered = new IdentityHashMap<>();
            Map<RenderType, UploadedLayer> deliveredFluids = new IdentityHashMap<>();
            try {
                uploadLayers(this.layers.hologram, hologram);
                uploadLayers(this.layers.delivered, delivered);
                uploadLayers(this.layers.deliveredFluids, deliveredFluids);
            } catch (Throwable throwable) {
                closeUploadedLayers(hologram);
                closeUploadedLayers(delivered);
                closeUploadedLayers(deliveredFluids);
                close();
                discardUploadedRevision(this.scene, this.state, this.revision);
                logFailure(this.scene, throwable);
                return;
            }
            discardUploadedRevision(this.scene, this.state, this.revision);
            this.state.hologramUploaded = hologram;
            this.state.deliveredUploaded = delivered;
            this.state.deliveredFluidUploaded = deliveredFluids;
            this.scene.gpuBytes += uploadedBytes(hologram);
            this.scene.gpuBytes += uploadedBytes(delivered);
            this.scene.gpuBytes += uploadedBytes(deliveredFluids);
        }

        @Override
        public void close() {
            this.layers.close();
        }

        private static void uploadLayers(
            Map<RenderType, PendingLayer> pending,
            Map<RenderType, UploadedLayer> uploaded
        ) {
            for (Map.Entry<RenderType, PendingLayer> entry : pending.entrySet()) {
                uploaded.put(entry.getKey(), entry.getValue().upload());
            }
        }
    }

    private static final class SortedIndices implements ReadyWork {
        private final Scene scene;
        private final SectionState state;
        private final UploadedLayer layer;
        private final Vector3f sortOrigin;
        private final @Nullable ByteBufferBuilder owner;
        private final @Nullable ByteBufferBuilder.Result indices;
        private final @Nullable Throwable failure;

        private SortedIndices(
            Scene scene,
            SectionState state,
            UploadedLayer layer,
            Vector3f sortOrigin,
            @Nullable ByteBufferBuilder owner,
            @Nullable ByteBufferBuilder.Result indices,
            @Nullable Throwable failure
        ) {
            this.scene = scene;
            this.state = state;
            this.layer = layer;
            this.sortOrigin = sortOrigin;
            this.owner = owner;
            this.indices = indices;
            this.failure = failure;
        }

        @Override
        public long bytes() {
            return this.indices == null ? 0L : this.indices.byteBuffer().remaining();
        }

        @Override
        public void upload() {
            this.layer.sortPending.set(false);
            if (this.failure != null) {
                close();
                logFailure(this.scene, this.failure);
                return;
            }
            if (this.scene.closed
                || SCENES.get(this.scene.key) != this.scene
                || !this.state.hologramUploaded.containsValue(this.layer)
                    && !this.state.deliveredUploaded.containsValue(this.layer)
                    && !this.state.deliveredFluidUploaded.containsValue(this.layer)
                || this.indices == null) {
                close();
                return;
            }
            try {
                this.layer.buffer.bind();
                this.layer.buffer.uploadIndexBuffer(this.indices);
                this.layer.lastSortOrigin.set(this.sortOrigin);
            } finally {
                if (this.owner != null) this.owner.close();
            }
        }

        @Override
        public void close() {
            if (this.indices != null) this.indices.close();
            if (this.owner != null) this.owner.close();
            this.layer.sortPending.set(false);
        }
    }

    private record NeighborView(
        BlueprintRenderView view,
        @Nullable SceneKey key,
        @Nullable DeliveredLookup delivered,
        boolean hideNonOccluding
    ) implements BlockAndTintGetter {
        private static NeighborView hologram(BlueprintRenderView view, boolean hideNonOccluding) {
            return new NeighborView(view, null, null, hideNonOccluding);
        }

        private static NeighborView delivered(
            BlueprintRenderView view,
            SceneKey key,
            DeliveredLookup delivered,
            boolean hideNonOccluding
        ) {
            return new NeighborView(view, key, delivered, hideNonOccluding);
        }

        private BlockState realState(BlockPos pos) {
            if (this.delivered == null || this.key == null) return this.view.realState(pos);
            BlockState state = this.delivered.stateAt(this.key, pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            BlockState state = this.realState(pos);
            if (this.hideNonOccluding
                && state.getFluidState().isEmpty()
                && !isSolidCube(state)) {
                return Blocks.AIR.defaultBlockState();
            }
            return state;
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.realState(pos).getFluidState();
        }

        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos pos) {
            if (this.delivered != null && this.realState(pos).isAir()) return null;
            return this.view.getBlockEntity(pos);
        }

        @Override
        public int getHeight() {
            return this.view.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.view.getMinBuildHeight();
        }

        @Override
        public boolean isOutsideBuildHeight(int y) {
            return false;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.view.getShade(direction, shade);
        }

        @Override
        public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
            return this.view.getShade(normalX, normalY, normalZ, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.view.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.view.getBlockTint(pos, colorResolver);
        }

        @Override
        public int getBrightness(LightLayer type, BlockPos pos) {
            return this.view.getBrightness(type, pos);
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return this.view.getRawBrightness(pos, amount);
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            if (this.delivered != null && this.realState(pos).isAir()) return ModelData.EMPTY;
            return this.view.getModelData(pos);
        }
    }

    private record ShiftedView(BlockAndTintGetter view, BlockPos origin) implements BlockAndTintGetter {
        private BlockPos map(BlockPos pos) {
            return pos.offset(this.origin);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.view.getBlockState(this.map(pos));
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.view.getFluidState(this.map(pos));
        }

        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos pos) {
            return this.view.getBlockEntity(this.map(pos));
        }

        @Override
        public int getHeight() {
            return this.view.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return this.view.getMinBuildHeight();
        }

        @Override
        public boolean isOutsideBuildHeight(int y) {
            return false;
        }

        @Override
        public float getShade(Direction direction, boolean shade) {
            return this.view.getShade(direction, shade);
        }

        @Override
        public float getShade(float normalX, float normalY, float normalZ, boolean shade) {
            return this.view.getShade(normalX, normalY, normalZ, shade);
        }

        @Override
        public LevelLightEngine getLightEngine() {
            return this.view.getLightEngine();
        }

        @Override
        public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
            return this.view.getBlockTint(this.map(pos), colorResolver);
        }

        @Override
        public int getBrightness(LightLayer type, BlockPos pos) {
            return this.view.getBrightness(type, this.map(pos));
        }

        @Override
        public int getRawBrightness(BlockPos pos, int amount) {
            return this.view.getRawBrightness(this.map(pos), amount);
        }

        @Override
        public ModelData getModelData(BlockPos pos) {
            return this.view.getModelData(this.map(pos));
        }
    }

    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int overlay,
            int light,
            float normalX,
            float normalY,
            float normalZ
        ) {
            int alpha = (int) (FastColor.ARGB32.alpha(color) * this.alpha);
            int tinted = FastColor.ARGB32.color(
                alpha,
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color)
            );
            this.delegate.addVertex(x, y, z, tinted, u, v, overlay, light, normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, (int) (alpha * this.alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }

    private record PositionOffsetVertexConsumer(
        VertexConsumer delegate,
        float offsetX,
        float offsetY,
        float offsetZ
    ) implements VertexConsumer {
        private PositionOffsetVertexConsumer(VertexConsumer delegate, int offsetX, int offsetY, int offsetZ) {
            this(delegate, (float) offsetX, (float) offsetY, (float) offsetZ);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x + this.offsetX, y + this.offsetY, z + this.offsetZ);
            return this;
        }

        @Override
        public void addVertex(
            float x,
            float y,
            float z,
            int color,
            float u,
            float v,
            int overlay,
            int light,
            float normalX,
            float normalY,
            float normalZ
        ) {
            this.delegate.addVertex(
                x + this.offsetX,
                y + this.offsetY,
                z + this.offsetZ,
                color,
                u,
                v,
                overlay,
                light,
                normalX,
                normalY,
                normalZ
            );
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
