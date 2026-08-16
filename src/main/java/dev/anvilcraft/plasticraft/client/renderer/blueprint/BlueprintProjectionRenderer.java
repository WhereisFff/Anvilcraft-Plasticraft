package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
import dev.anvilcraft.plasticraft.blueprint.ConstructionEntityProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.blueprint.StructureSnapshot;
import dev.anvilcraft.plasticraft.client.blueprint.BlueprintDeploySession;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintJobCache;
import dev.anvilcraft.plasticraft.client.blueprint.ClientBlueprintSnapshotCache;
import dev.anvilcraft.plasticraft.client.renderer.ThickLineRenderer;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 施工投影渲染器:为已放置蓝图与部署会话渲染半透明目标投影。
 * MODEL 方块与流体按区段异步烘焙并沿用模型声明的 RenderType,经典透明层按相机移动重排;
 * 全息材质在独立目标内按预乘透明度合成,不替换 BER、实体或自定义负体积的原始着色器;
 * 已交付 MODEL 格使用同一份区段缓存但直接画回主目标,不再按帧重新网格化;
 * 已交付 BER 在方块实体之后画,已交付流体跟世界半透明走,避免假方块叠在水面之上;
 * 快照尚未到达时仍画出包围盒,保证放置位置始终可见。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BlueprintProjectionRenderer {
    /** 投影可见距离(格),超出的已放置蓝图不渲染。 */
    private static final double VIEW_DISTANCE = 160.0D;
    /** 投影只统一透明度并保留材质原色,避免把红石、树叶和模组材质滤成灰褐。 */
    private static final float TINT_ALPHA = 0.55F;
    /** 流体本身已半透明,全息再乘 0.55 会变成纯透明;单独提高一档。 */
    private static final float FLUID_TINT_ALPHA = 0.85F;
    private static final float BOX_RED = 0.25F;
    private static final float BOX_GREEN = 0.85F;
    private static final float BOX_BLUE = 1.0F;
    private static final float BOX_ALPHA = 0.95F;
    private static final int BOX_COLOR = packColor(BOX_RED, BOX_GREEN, BOX_BLUE, BOX_ALPHA);
    /** 施工警示色取自原版黄色混凝土贴图的平均色。 */
    private static final int CONSTRUCTION_YELLOW_RED = 0xF1;
    private static final int CONSTRUCTION_YELLOW_GREEN = 0xAF;
    private static final int CONSTRUCTION_YELLOW_BLUE = 0x15;
    private static final float CONSTRUCTION_YELLOW_ALPHA = 0.98F;
    private static final int CONSTRUCTION_YELLOW_COLOR = packColor(
        CONSTRUCTION_YELLOW_RED / 255.0F,
        CONSTRUCTION_YELLOW_GREEN / 255.0F,
        CONSTRUCTION_YELLOW_BLUE / 255.0F,
        CONSTRUCTION_YELLOW_ALPHA
    );
    private static final int CONSTRUCTION_BLACK_COLOR = packColor(0.035F, 0.03F, 0.022F, 0.98F);
    private static final double CONSTRUCTION_RING_OUTSET = 0.075D;
    private static final double CONSTRUCTION_RING_LIFT = 0.018D;
    private static final double CONSTRUCTION_ZONE_HEIGHT = 1.25D;
    private static final double CONSTRUCTION_ZONE_BOTTOM_OFFSET = 0.006D;
    private static final float CONSTRUCTION_ZONE_BOTTOM_ALPHA = 0.32F;
    private static final float CONSTRUCTION_ZONE_FLOOR_ALPHA = 0.09F;
    private static final double CONSTRUCTION_TARGET_STRIPE_LENGTH = 0.5D;
    private static final int CONSTRUCTION_STRIPE_GROUP_SIZE = 4;
    private static final double CONSTRUCTION_RING_WIDTH = 0.046D;
    private static final int[][] BOX_EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0},
        {4, 5}, {5, 6}, {6, 7}, {7, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int MAX_CACHED_MESHES = 8;
    private static final int MAX_CACHED_DELIVERED_SCENES = 16;
    private static final int MAX_PREPARE_BUILDS = 8;
    private static final int PREPARE_ENTRIES_PER_FRAME = 8192;
    private static final int PREPARE_ENTRIES_PER_SCENE = 2048;
    private static final BlockPos PREVIEW_ENTITY_POSITION = new BlockPos(0, 4096, 0);

    private static final Map<MeshKey, PreparedProjection> CACHE = new LinkedHashMap<>(MAX_CACHED_MESHES + 1, 0.75F, true);
    private static final Map<MeshKey, PreparedBuild> PREPARE_BUILDS =
        new LinkedHashMap<>(MAX_PREPARE_BUILDS + 1, 0.75F, true);
    private static final Map<DeliveredSceneKey, DeliveredBlockEntityScene> DELIVERED_BLOCK_ENTITY_CACHE =
        new LinkedHashMap<>(MAX_CACHED_DELIVERED_SCENES + 1, 0.75F, true);
    private static final Map<DeliveredSceneKey, DeliveredEntityScene> DELIVERED_ENTITY_CACHE =
        new LinkedHashMap<>(MAX_CACHED_DELIVERED_SCENES + 1, 0.75F, true);
    private static final Set<String> FAILED_RENDERERS = new HashSet<>();
    private static final BlueprintProjectionAnimator ANIMATOR = new BlueprintProjectionAnimator();
    private static long preparationFrame;
    private static int remainingPrepareEntries;

    private BlueprintProjectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
            && stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
            && stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            BlueprintProjectionSectionRenderer.beginFrame();
            preparationFrame++;
            remainingPrepareEntries = PREPARE_ENTRIES_PER_FRAME;
        }

        List<RenderItem> items = collectRenderItems(minecraft);
        if (items.isEmpty()) {
            if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                ANIMATOR.clear();
            }
            return;
        }

        if (stage == RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            renderInWorldSpace(event, true, poseStack -> renderDeliveredBlockEntities(
                minecraft,
                poseStack,
                minecraft.renderBuffers().bufferSource(),
                items
            ));
            return;
        }
        if (stage == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            renderInWorldSpace(event, true, poseStack -> renderDeliveredFluids(
                minecraft,
                event,
                poseStack,
                items
            ));
            return;
        }
        BlueprintProjectionAnimator.Pose sessionPose = null;
        boolean hasSession = false;
        for (RenderItem item : items) {
            if (!item.session()) continue;
            sessionPose = ANIMATOR.tick(item.hash(), item.placement(), item.size(), 0.0F);
            hasSession = true;
            break;
        }
        if (!hasSession) {
            ANIMATOR.clear();
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        PoseStack.Pose origin = poseStack.last();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        TintedBufferSource colorBuffers = new TintedBufferSource(buffers, TINT_ALPHA);
        List<PreparedRenderItem> preparedItems = new ArrayList<>(items.size());
        for (RenderItem item : items) {
            BlueprintProjectionAnimator.Pose pose = poseOf(item, sessionPose);
            PreparedProjection prepared = preparedOrNull(minecraft, item, pose);
            if (prepared != null) preparedItems.add(new PreparedRenderItem(item, pose, prepared));
        }
        boolean projectionTargetActive = false;

        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            for (PreparedRenderItem preparedItem : preparedItems) {
                RenderItem item = preparedItem.item();
                BlueprintProjectionAnimator.Pose pose = preparedItem.pose();
                PreparedProjection prepared = preparedItem.prepared();
                withMeshPose(poseStack, pose, () -> BlueprintProjectionSectionRenderer.renderDelivered(
                    event,
                    poseStack,
                    pose,
                    item.hash(),
                    item.layerView(),
                    item.jobId(),
                    prepared.view(),
                    prepared.sections(),
                    TINT_ALPHA,
                    FLUID_TINT_ALPHA
                ));
            }
            projectionTargetActive = BlueprintProjectionTarget1211.begin();
            if (projectionTargetActive) {
                for (PreparedRenderItem preparedItem : preparedItems) {
                    RenderItem item = preparedItem.item();
                    BlueprintProjectionAnimator.Pose pose = preparedItem.pose();
                    PreparedProjection prepared = preparedItem.prepared();
                    withMeshPose(poseStack, pose, () -> BlueprintProjectionSectionRenderer.renderHologram(
                        event,
                        poseStack,
                        pose,
                        item.hash(),
                        item.layerView(),
                        item.jobId(),
                        prepared.view(),
                        prepared.sections(),
                        TINT_ALPHA,
                        FLUID_TINT_ALPHA
                    ));
                }
                for (PreparedRenderItem preparedItem : preparedItems) {
                    RenderItem item = preparedItem.item();
                    PreparedProjection prepared = preparedItem.prepared();
                    withMeshPose(poseStack, preparedItem.pose(), () -> {
                        renderBlockEntities(minecraft, poseStack, colorBuffers, prepared, item);
                        renderEntities(minecraft, poseStack, colorBuffers, prepared);
                    });
                    renderDeliveredEntities(minecraft, poseStack, colorBuffers, item);
                }
                buffers.endBatch();
                BlueprintProjectionTarget1211.finish();
                projectionTargetActive = false;
            }
            for (RenderItem item : items) {
                renderBounds(poseStack, buffers, poseOf(item, sessionPose), item.size(), item.construction());
            }
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Blueprint projection failed", exception);
        } finally {
            if (projectionTargetActive) {
                BlueprintProjectionTarget1211.abort();
            }
            restorePose(poseStack, origin);
        }
    }

    private static void renderInWorldSpace(
        RenderLevelStageEvent event,
        boolean subtractCamera,
        Consumer<PoseStack> renderer
    ) {
        PoseStack poseStack = event.getPoseStack();
        PoseStack.Pose origin = poseStack.last();
        poseStack.pushPose();
        try {
            if (subtractCamera) {
                Vec3 camera = event.getCamera().getPosition();
                poseStack.translate(-camera.x, -camera.y, -camera.z);
            }
            renderer.accept(poseStack);
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Blueprint projection failed", exception);
        } finally {
            restorePose(poseStack, origin);
        }
    }

    public static void clearCache() {
        BlueprintProjectionSectionRenderer.clear();
        BlueprintProjectionTarget1211.release();
        BlueprintProjectionRenderTypes.clearCache();
        CACHE.clear();
        PREPARE_BUILDS.clear();
        DELIVERED_BLOCK_ENTITY_CACHE.clear();
        DELIVERED_ENTITY_CACHE.clear();
        FAILED_RENDERERS.clear();
        ANIMATOR.clear();
    }

    private record RenderItem(
        String hash,
        BlueprintPlacement placement,
        int layerView,
        Vec3i size,
        boolean session,
        @Nullable UUID jobId,
        boolean construction
    ) {
    }

    private record PreparedRenderItem(
        RenderItem item,
        BlueprintProjectionAnimator.Pose pose,
        PreparedProjection prepared
    ) {
    }

    private static BlueprintProjectionAnimator.Pose poseOf(
        RenderItem item,
        @Nullable BlueprintProjectionAnimator.Pose sessionPose
    ) {
        if (item.session() && sessionPose != null) {
            return sessionPose;
        }
        return BlueprintProjectionAnimator.immediate(item.placement(), item.size());
    }

    private static List<RenderItem> collectRenderItems(Minecraft minecraft) {
        List<RenderItem> items = new ArrayList<>();
        String sessionHash = BlueprintDeploySession.activeHash();
        BlueprintPlacement sessionPlacement = BlueprintDeploySession.activePlacement();
        Vec3i sessionSize = BlueprintDeploySession.activeSize();
        UUID sessionJobId = BlueprintDeploySession.activeJobId();
        if (sessionHash != null && sessionPlacement != null && sessionSize != null) {
            ConstructionJob sessionJob = sessionJobId == null
                ? null
                : ClientBlueprintJobCache.job(sessionJobId);
            items.add(new RenderItem(
                sessionHash,
                sessionPlacement,
                BlueprintDeploySession.layerView(),
                sessionSize,
                true,
                sessionJobId,
                sessionJob != null && sessionJob.isActive()
            ));
        }
        assert minecraft.level != null && minecraft.player != null;
        for (ConstructionJob job : ClientBlueprintJobCache.jobs()) {
            if (!job.dimension().equals(minecraft.level.dimension())) continue;
            if (job.jobId().equals(sessionJobId)) continue;
            BlueprintPlacement placement = BlueprintPlacement.of(job);
            AABB bounds = AABB.of(placement.bounds(job.size()));
            if (bounds.distanceToSqr(minecraft.player.position()) > VIEW_DISTANCE * VIEW_DISTANCE) continue;
            items.add(new RenderItem(
                job.hash(),
                placement,
                BlueprintDeploySession.LAYERS_ALL,
                job.size(),
                false,
                job.jobId(),
                job.isActive()
            ));
        }
        return items;
    }

    private static void renderBounds(
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffers,
        BlueprintProjectionAnimator.Pose pose,
        Vec3i size,
        boolean construction
    ) {
        AABB baked = AABB.of(pose.mesh().bounds(size));
        if (construction) {
            renderConstructionArea(poseStack, buffers, pose, baked);
            return;
        }
        Vec3[] corners = {
            pose.worldOf(new Vec3(baked.minX, baked.minY, baked.minZ)),
            pose.worldOf(new Vec3(baked.maxX, baked.minY, baked.minZ)),
            pose.worldOf(new Vec3(baked.maxX, baked.minY, baked.maxZ)),
            pose.worldOf(new Vec3(baked.minX, baked.minY, baked.maxZ)),
            pose.worldOf(new Vec3(baked.minX, baked.maxY, baked.minZ)),
            pose.worldOf(new Vec3(baked.maxX, baked.maxY, baked.minZ)),
            pose.worldOf(new Vec3(baked.maxX, baked.maxY, baked.maxZ)),
            pose.worldOf(new Vec3(baked.minX, baked.maxY, baked.maxZ))
        };
        List<ThickLineRenderer.Segment> segments = new ArrayList<>(BOX_EDGES.length);
        for (int[] edge : BOX_EDGES) {
            segments.add(new ThickLineRenderer.Segment(corners[edge[0]], corners[edge[1]]));
        }
        ThickLineRenderer.renderSegments(
            poseStack,
            buffers,
            segments,
            BOX_COLOR,
            ThickLineRenderer.SELECTION_WIDTH
        );
    }

    /** 启动施工后用警示环和短距离渐隐围挡替换蓝图包围盒。 */
    private static void renderConstructionArea(
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffers,
        BlueprintProjectionAnimator.Pose pose,
        AABB baked
    ) {
        double minX = baked.minX - CONSTRUCTION_RING_OUTSET;
        double maxX = baked.maxX + CONSTRUCTION_RING_OUTSET;
        double minZ = baked.minZ - CONSTRUCTION_RING_OUTSET;
        double maxZ = baked.maxZ + CONSTRUCTION_RING_OUTSET;
        double bottomY = baked.minY + CONSTRUCTION_ZONE_BOTTOM_OFFSET;
        double topY = Math.min(baked.maxY, bottomY + CONSTRUCTION_ZONE_HEIGHT);
        Vec3[] bottom = {
            pose.worldOf(new Vec3(minX, bottomY, minZ)),
            pose.worldOf(new Vec3(maxX, bottomY, minZ)),
            pose.worldOf(new Vec3(maxX, bottomY, maxZ)),
            pose.worldOf(new Vec3(minX, bottomY, maxZ))
        };
        Vec3[] top = {
            pose.worldOf(new Vec3(minX, topY, minZ)),
            pose.worldOf(new Vec3(maxX, topY, minZ)),
            pose.worldOf(new Vec3(maxX, topY, maxZ)),
            pose.worldOf(new Vec3(minX, topY, maxZ))
        };

        RenderType zoneType = BlueprintProjectionRenderTypes.constructionZone();
        VertexConsumer zone = buffers.getBuffer(zoneType);
        addConstructionFloor(poseStack, zone, bottom);
        for (int index = 0; index < bottom.length; index++) {
            int next = (index + 1) & 3;
            addConstructionGradientQuad(poseStack, zone, bottom[index], bottom[next], top[next], top[index]);
        }
        buffers.endBatch(zoneType);

        double ringY = bottomY + CONSTRUCTION_RING_LIFT;
        Vec3[] ring = {
            pose.worldOf(new Vec3(minX, ringY, minZ)),
            pose.worldOf(new Vec3(maxX, ringY, minZ)),
            pose.worldOf(new Vec3(maxX, ringY, maxZ)),
            pose.worldOf(new Vec3(minX, ringY, maxZ))
        };
        List<ThickLineRenderer.Segment> ringSegments = List.of(
            new ThickLineRenderer.Segment(ring[0], ring[1]),
            new ThickLineRenderer.Segment(ring[1], ring[2]),
            new ThickLineRenderer.Segment(ring[2], ring[3]),
            new ThickLineRenderer.Segment(ring[3], ring[0])
        );
        double stripeLength = constructionStripeLength(ringSegments);
        double stripePhase = constructionStripePhase(ringSegments, stripeLength);
        ThickLineRenderer.renderAlternatingSegments(
            poseStack,
            buffers,
            ringSegments,
            CONSTRUCTION_YELLOW_COLOR,
            CONSTRUCTION_BLACK_COLOR,
            CONSTRUCTION_RING_WIDTH,
            stripeLength,
            stripePhase,
            CONSTRUCTION_RING_WIDTH * 0.5D
        );
    }

    /** 按整圈周长微调条纹长度，保证闭环内每节黄黑线完全等长。 */
    private static double constructionStripeLength(List<ThickLineRenderer.Segment> segments) {
        double perimeter = 0.0D;
        for (ThickLineRenderer.Segment segment : segments) {
            perimeter += segment.to().distanceTo(segment.from());
        }
        int groupCount = Math.max(
            1,
            (int) Math.round(
                perimeter / (CONSTRUCTION_TARGET_STRIPE_LENGTH * CONSTRUCTION_STRIPE_GROUP_SIZE)
            )
        );
        return perimeter / (groupCount * CONSTRUCTION_STRIPE_GROUP_SIZE);
    }

    /** 将换色点尽量远离四个转角，避免半线宽延长区跨色。 */
    private static double constructionStripePhase(
        List<ThickLineRenderer.Segment> segments,
        double stripeLength
    ) {
        List<Double> forbiddenPhases = new ArrayList<>(segments.size());
        double distance = 0.0D;
        for (ThickLineRenderer.Segment segment : segments) {
            forbiddenPhases.add(positiveModulo(-distance, stripeLength));
            distance += segment.to().distanceTo(segment.from());
        }
        Collections.sort(forbiddenPhases);

        double widestGapStart = forbiddenPhases.getFirst();
        double widestGap = -1.0D;
        for (int index = 0; index < forbiddenPhases.size(); index++) {
            double current = forbiddenPhases.get(index);
            double next = index + 1 < forbiddenPhases.size()
                ? forbiddenPhases.get(index + 1)
                : forbiddenPhases.getFirst() + stripeLength;
            double gap = next - current;
            if (gap > widestGap) {
                widestGapStart = current;
                widestGap = gap;
            }
        }
        return positiveModulo(widestGapStart + widestGap * 0.5D, stripeLength);
    }

    private static double positiveModulo(double value, double divisor) {
        double result = value % divisor;
        return result < 0.0D ? result + divisor : result;
    }

    private static void addConstructionFloor(
        PoseStack poseStack,
        VertexConsumer consumer,
        Vec3[] corners
    ) {
        addConstructionVertex(poseStack, consumer, corners[3], CONSTRUCTION_ZONE_FLOOR_ALPHA);
        addConstructionVertex(poseStack, consumer, corners[2], CONSTRUCTION_ZONE_FLOOR_ALPHA);
        addConstructionVertex(poseStack, consumer, corners[1], CONSTRUCTION_ZONE_FLOOR_ALPHA);
        addConstructionVertex(poseStack, consumer, corners[0], CONSTRUCTION_ZONE_FLOOR_ALPHA);
    }

    private static void addConstructionGradientQuad(
        PoseStack poseStack,
        VertexConsumer consumer,
        Vec3 bottomStart,
        Vec3 bottomEnd,
        Vec3 topEnd,
        Vec3 topStart
    ) {
        addConstructionVertex(poseStack, consumer, bottomStart, CONSTRUCTION_ZONE_BOTTOM_ALPHA);
        addConstructionVertex(poseStack, consumer, bottomEnd, CONSTRUCTION_ZONE_BOTTOM_ALPHA);
        addConstructionVertex(poseStack, consumer, topEnd, 0.0F);
        addConstructionVertex(poseStack, consumer, topStart, 0.0F);
    }

    private static void addConstructionVertex(
        PoseStack poseStack,
        VertexConsumer consumer,
        Vec3 point,
        float alpha
    ) {
        consumer.addVertex(
            poseStack.last().pose(),
            (float) point.x,
            (float) point.y,
            (float) point.z
        ).setColor(
            CONSTRUCTION_YELLOW_RED,
            CONSTRUCTION_YELLOW_GREEN,
            CONSTRUCTION_YELLOW_BLUE,
            (int) (alpha * 255.0F)
        );
    }

    @Nullable
    private static PreparedProjection prepared(
        Minecraft minecraft,
        RenderItem item,
        StructureSnapshot snapshot,
        BlueprintPlacement mesh
    ) {
        MeshKey key = meshKey(item, mesh);
        PreparedProjection cached = CACHE.get(key);
        if (cached != null) return cached;
        ClientLevel level = minecraft.level;
        assert level != null && minecraft.player != null;
        PreparedBuild pending = PREPARE_BUILDS.get(key);
        if (pending == null || pending.snapshot != snapshot) {
            pending = new PreparedBuild(
                level,
                minecraft.player.blockPosition(),
                snapshot,
                mesh,
                item.layerView()
            );
            PREPARE_BUILDS.put(key, pending);
            while (PREPARE_BUILDS.size() > MAX_PREPARE_BUILDS) {
                PREPARE_BUILDS.remove(PREPARE_BUILDS.keySet().iterator().next());
            }
        }
        if (pending.lastAdvancedFrame != preparationFrame && remainingPrepareEntries > 0) {
            pending.lastAdvancedFrame = preparationFrame;
            int budget = Math.min(PREPARE_ENTRIES_PER_SCENE, remainingPrepareEntries);
            remainingPrepareEntries -= pending.advance(budget);
        }
        if (!pending.complete()) return null;
        PreparedProjection built = pending.finish();
        PREPARE_BUILDS.remove(key);
        CACHE.put(key, built);
        while (CACHE.size() > MAX_CACHED_MESHES) {
            MeshKey oldest = CACHE.keySet().iterator().next();
            CACHE.remove(oldest);
        }
        return built;
    }

    @Nullable
    private static BlockEntity createBlockEntity(
        ClientLevel level,
        HolderLookup.Provider registries,
        BlockPos local,
        BlockState state,
        @Nullable CompoundTag nbt
    ) {
        BlockEntity blockEntity = null;
        if (nbt != null) {
            try {
                blockEntity = BlockEntity.loadStatic(local, state, nbt, registries);
            } catch (RuntimeException exception) {
                skipOnce("block-entity-nbt:" + state, exception);
            }
        }
        if (blockEntity == null && state.getBlock() instanceof EntityBlock entityBlock) {
            try {
                blockEntity = entityBlock.newBlockEntity(local, state);
            } catch (RuntimeException exception) {
                skipOnce("block-entity-create:" + state, exception);
            }
        }
        if (blockEntity == null) return null;
        blockEntity.setLevel(level);
        return blockEntity;
    }

    @Nullable
    private static PreparedEntity createPreviewEntity(
        ClientLevel level,
        BlueprintPlacement placement,
        StructureSnapshot.EntityEntry entry
    ) {
        CompoundTag nbt = entry.nbt().copy();
        if (nbt.getString("id").isEmpty()) return null;
        nbt.remove("UUID");
        Vec3 local = placement.localOf(entry.pos(), snapshotBlockOf(entry));
        movePreviewNbtOffWorld(nbt);
        try {
            return EntityType.create(nbt, level)
                .filter(entity -> !(entity instanceof Player))
                .map(entity -> {
                    applyEntityPlacement(entity, placement);
                    return new PreparedEntity(entity, local, entity.getYRot(), entity.getXRot());
                })
                .orElse(null);
        } catch (RuntimeException exception) {
            skipOnce("entity:" + nbt.getString("id"), exception);
            return null;
        }
    }

    private static void movePreviewNbtOffWorld(CompoundTag nbt) {
        // 实体留在世界外高空,避免 MinecartRenderer.getPos 去真实世界找铁轨并把全息矿车吸走。
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(PREVIEW_ENTITY_POSITION.getX()));
        posTag.add(DoubleTag.valueOf(PREVIEW_ENTITY_POSITION.getY()));
        posTag.add(DoubleTag.valueOf(PREVIEW_ENTITY_POSITION.getZ()));
        nbt.put("Pos", posTag);
        EntityType.by(nbt)
            .filter(type -> BlockAttachedEntity.class.isAssignableFrom(type.getBaseClass()))
            .ifPresent(type -> {
                nbt.putInt("TileX", PREVIEW_ENTITY_POSITION.getX());
                nbt.putInt("TileY", PREVIEW_ENTITY_POSITION.getY());
                nbt.putInt("TileZ", PREVIEW_ENTITY_POSITION.getZ());
            });
    }

    private static BlockPos snapshotBlockOf(StructureSnapshot.EntityEntry entry) {
        BlockPos contained = BlockPos.containing(entry.pos());
        BlockPos recorded = entry.blockPos();
        return contained.distManhattan(recorded) <= 1 ? recorded : contained;
    }

    private static void applyEntityPlacement(Entity entity, BlueprintPlacement placement) {
        float yRot = entity.rotate(placement.rotation());
        yRot += entity.mirror(placement.mirror()) - entity.getYRot();
        entity.moveTo(
            PREVIEW_ENTITY_POSITION.getX(),
            PREVIEW_ENTITY_POSITION.getY(),
            PREVIEW_ENTITY_POSITION.getZ(),
            yRot,
            entity.getXRot()
        );
        entity.xOld = PREVIEW_ENTITY_POSITION.getX();
        entity.yOld = PREVIEW_ENTITY_POSITION.getY();
        entity.zOld = PREVIEW_ENTITY_POSITION.getZ();
        entity.yRotO = yRot;
        entity.xRotO = entity.getXRot();
        entity.setDeltaMovement(Vec3.ZERO);
        if (entity instanceof LivingEntity living) {
            living.setYHeadRot(yRot);
            living.setYBodyRot(yRot);
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        if (entity instanceof AbstractPlasticEntity plastic) {
            applyPlasticPlacement(plastic, placement);
        }
    }

    private static void applyPlasticPlacement(AbstractPlasticEntity plastic, BlueprintPlacement placement) {
        PlasticEntityOrientation orientation = plastic.getOrientation();
        Direction face = placement.mirror().mirror(orientation.attachmentFace());
        face = placement.rotation().rotate(face);
        int turn = orientation.quarterTurn();
        if (orientation.attachmentFace().getAxis() == Direction.Axis.Y) {
            turn = switch (placement.mirror()) {
                case LEFT_RIGHT -> Math.floorMod(2 - turn, 4);
                case FRONT_BACK -> Math.floorMod(-turn, 4);
                case NONE -> turn;
            };
            turn = Math.floorMod(turn + rotationSteps(placement.rotation()), 4);
        }
        plastic.setOrientation(new PlasticEntityOrientation(face, turn));
        plastic.setDisplayState(placement.stateOf(plastic.getDisplayState()));
    }

    private static int rotationSteps(Rotation rotation) {
        return switch (rotation) {
            case NONE -> 0;
            case CLOCKWISE_90 -> 1;
            case CLOCKWISE_180 -> 2;
            case COUNTERCLOCKWISE_90 -> 3;
        };
    }

    @Nullable
    private static PreparedProjection preparedOrNull(
        Minecraft minecraft,
        RenderItem item,
        BlueprintProjectionAnimator.Pose pose
    ) {
        MeshKey key = meshKey(item, pose.mesh());
        PreparedProjection cached = CACHE.get(key);
        if (cached != null) return cached;
        PreparedBuild pending = PREPARE_BUILDS.get(key);
        StructureSnapshot snapshot = pending == null
            ? ClientBlueprintSnapshotCache.snapshotOrRequest(item.hash())
            : pending.snapshot;
        if (snapshot == null) return null;
        return prepared(minecraft, item, snapshot, pose.mesh());
    }

    private static MeshKey meshKey(RenderItem item, BlueprintPlacement mesh) {
        return new MeshKey(item.hash(), mesh.rotation(), mesh.mirror(), item.layerView());
    }

    private static void withMeshPose(
        PoseStack poseStack,
        BlueprintProjectionAnimator.Pose pose,
        Runnable action
    ) {
        PoseStack.Pose origin = poseStack.last();
        poseStack.pushPose();
        try {
            applyMeshPose(poseStack, pose);
            action.run();
        } finally {
            restorePose(poseStack, origin);
        }
    }

    private static void applyMeshPose(PoseStack poseStack, BlueprintProjectionAnimator.Pose pose) {
        Vec3 displayedCenter = pose.displayedCenter();
        Vec3 bakedCenter = pose.bakedCenter();
        BlockPos bakedAnchor = pose.mesh().anchor();
        poseStack.translate(displayedCenter.x, displayedCenter.y, displayedCenter.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(pose.extraYaw()));
        poseStack.scale(pose.extraMirrorX(), 1.0F, pose.extraMirrorZ());
        poseStack.translate(
            bakedAnchor.getX() - bakedCenter.x,
            bakedAnchor.getY() - bakedCenter.y,
            bakedAnchor.getZ() - bakedCenter.z
        );
    }

    private static boolean isDelivered(@Nullable ClientLevel level, RenderItem item, BlockPos local) {
        if (level == null || item.jobId() == null) return false;
        return ConstructionProjectionIndex.has(
            level,
            item.jobId(),
            local.offset(item.placement().anchor())
        );
    }

    /**
     * 箱子、告示牌等没有 MODEL 网格,交付后必须走 BER,否则假方块会直接消失。
     */
    private static void renderDeliveredBlockEntities(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffers,
        List<RenderItem> items
    ) {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        for (RenderItem item : items) {
            if (item.jobId() == null) continue;
            PreparedProjection prepared = preparedOrNull(
                minecraft,
                item,
                BlueprintProjectionAnimator.immediate(item.placement(), item.size())
            );
            for (BlockEntity blockEntity : deliveredBlockEntities(level, item, prepared)) {
                BlockEntityRenderer<?> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
                if (renderer == null) continue;
                if (!blockEntity.getType().isValid(blockEntity.getBlockState())) continue;
                BlockPos pos = blockEntity.getBlockPos();
                PoseStack.Pose origin = poseStack.last();
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                try {
                    renderBlockEntity(renderer, blockEntity, poseStack, buffers);
                } catch (RuntimeException exception) {
                    skipOnce("delivered-ber:" + blockEntity.getType(), exception);
                } finally {
                    restorePose(poseStack, origin);
                }
            }
        }
        buffers.endBatch();
    }

    private static List<BlockEntity> deliveredBlockEntities(
        ClientLevel level,
        RenderItem item,
        @Nullable PreparedProjection prepared
    ) {
        UUID jobId = item.jobId();
        if (jobId == null) return List.of();
        DeliveredSceneKey key = new DeliveredSceneKey(level, jobId);
        ConstructionProjectionIndex.DeliveredSnapshot snapshot =
            ConstructionProjectionIndex.deliveredSnapshot(level, jobId);
        DeliveredBlockEntityScene cached = DELIVERED_BLOCK_ENTITY_CACHE.get(key);
        if (cached != null && cached.snapshot() == snapshot && cached.prepared() == prepared) {
            return cached.blockEntities();
        }
        HolderLookup.Provider registries = level.registryAccess();
        Map<Long, DeliveredBlockEntitySection> sections = new LinkedHashMap<>();
        boolean canReuse = cached != null && cached.prepared() == prepared;
        for (ConstructionProjectionIndex.DeliveredSection section : snapshot.sections()) {
            DeliveredBlockEntitySection previous = canReuse
                ? cached.sections().get(section.section())
                : null;
            if (previous != null && previous.revision() == section.revision()) {
                sections.put(section.section(), previous);
            } else {
                sections.put(
                    section.section(),
                    buildDeliveredBlockEntitySection(level, registries, item, prepared, section)
                );
            }
        }
        List<BlockEntity> result = new ArrayList<>();
        for (DeliveredBlockEntitySection section : sections.values()) {
            result.addAll(section.blockEntities());
        }
        DeliveredBlockEntityScene built = new DeliveredBlockEntityScene(
            snapshot,
            prepared,
            Collections.unmodifiableMap(sections),
            List.copyOf(result)
        );
        DELIVERED_BLOCK_ENTITY_CACHE.put(key, built);
        trimDeliveredCache(DELIVERED_BLOCK_ENTITY_CACHE);
        return built.blockEntities();
    }

    private static DeliveredBlockEntitySection buildDeliveredBlockEntitySection(
        ClientLevel level,
        HolderLookup.Provider registries,
        RenderItem item,
        @Nullable PreparedProjection prepared,
        ConstructionProjectionIndex.DeliveredSection section
    ) {
        List<BlockEntity> result = new ArrayList<>();
        for (ConstructionProjectionIndex.Collision collision : section.entries()) {
            BlockState state = collision.state();
            if (!(state.getBlock() instanceof EntityBlock)) continue;
            BlockPos worldPos = collision.pos();
            CompoundTag nbt = null;
            BlockEntity source = prepared == null
                ? null
                : prepared.blockEntitiesByPos().get(worldPos.subtract(item.placement().anchor()));
            if (source != null) {
                try {
                    nbt = source.saveWithFullMetadata(registries);
                } catch (RuntimeException exception) {
                    skipOnce("delivered-ber-nbt:" + state, exception);
                }
            }
            BlockEntity blockEntity = createBlockEntity(level, registries, worldPos, state, nbt);
            if (blockEntity != null) result.add(blockEntity);
        }
        return new DeliveredBlockEntitySection(section.revision(), List.copyOf(result));
    }

    private static <T> void trimDeliveredCache(Map<DeliveredSceneKey, T> cache) {
        while (cache.size() > MAX_CACHED_DELIVERED_SCENES) {
            cache.remove(cache.keySet().iterator().next());
        }
    }

    /**
     * 已交付流体是 INVISIBLE 方块,不能走 MODEL 批次;按真实流体画,避免交付后变成纯透明。
     */
    private static void renderDeliveredFluids(
        Minecraft minecraft,
        RenderLevelStageEvent event,
        PoseStack poseStack,
        List<RenderItem> items
    ) {
        ClientLevel level = minecraft.level;
        if (level == null) return;
        for (RenderItem item : items) {
            if (item.jobId() == null) continue;
            BlueprintProjectionAnimator.Pose pose = BlueprintProjectionAnimator.immediate(item.placement(), item.size());
            PreparedProjection prepared = preparedOrNull(minecraft, item, pose);
            if (prepared == null) continue;
            withMeshPose(poseStack, pose, () -> BlueprintProjectionSectionRenderer.renderDeliveredFluids(
                event,
                poseStack,
                pose,
                item.hash(),
                item.layerView(),
                item.jobId(),
                prepared.view(),
                prepared.sections(),
                TINT_ALPHA,
                FLUID_TINT_ALPHA
            ));
        }
    }

    private static void renderBlockEntities(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource buffers,
        PreparedProjection prepared,
        RenderItem item
    ) {
        for (BlockEntity blockEntity : prepared.blockEntities()) {
            if (isDelivered(minecraft.level, item, blockEntity.getBlockPos())) continue;
            BlockEntityRenderer<?> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(blockEntity);
            if (renderer == null) continue;
            if (!blockEntity.getType().isValid(blockEntity.getBlockState())) continue;
            BlockPos pos = blockEntity.getBlockPos();
            PoseStack.Pose origin = poseStack.last();
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            try {
                renderBlockEntity(renderer, blockEntity, poseStack, buffers);
            } catch (RuntimeException exception) {
                skipOnce("ber:" + blockEntity.getType(), exception);
            } finally {
                restorePose(poseStack, origin);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void renderBlockEntity(
        BlockEntityRenderer<?> renderer,
        T blockEntity,
        PoseStack poseStack,
        MultiBufferSource buffers
    ) {
        ((BlockEntityRenderer<T>) renderer).render(
            blockEntity,
            0.0F,
            poseStack,
            buffers,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY
        );
    }

    private static void renderEntities(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource buffers,
        PreparedProjection prepared
    ) {
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (PreparedEntity preparedEntity : prepared.entities()) {
                Entity entity = preparedEntity.entity();
                Vec3 local = preparedEntity.local();
                PoseStack.Pose origin = poseStack.last();
                poseStack.pushPose();
                poseStack.translate(local.x, local.y, local.z);
                try {
                    dispatcher.render(
                        entity,
                        0.0D,
                        0.0D,
                        0.0D,
                        preparedEntity.yRot(),
                        0.0F,
                        poseStack,
                        buffers,
                        LightTexture.FULL_BRIGHT
                    );
                } catch (RuntimeException exception) {
                    skipOnce("entity-render:" + entity.getType(), exception);
                } finally {
                    restorePose(poseStack, origin);
                }
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    private static void renderDeliveredEntities(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource buffers,
        RenderItem item
    ) {
        ClientLevel level = minecraft.level;
        if (level == null || item.jobId() == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = minecraft.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        try {
            for (DeliveredPreparedEntity prepared : deliveredEntities(level, item.jobId())) {
                ConstructionEntityProjectionIndex.Entry entry = prepared.entry();
                Entity entity = prepared.entity();
                PoseStack.Pose origin = poseStack.last();
                poseStack.pushPose();
                poseStack.translate(entry.pos().x, entry.pos().y, entry.pos().z);
                try {
                    dispatcher.render(
                        entity,
                        0.0D,
                        0.0D,
                        0.0D,
                        entity.getYRot(),
                        0.0F,
                        poseStack,
                        buffers,
                        LightTexture.FULL_BRIGHT
                    );
                } catch (RuntimeException exception) {
                    skipOnce("delivered-entity:" + entity.getType(), exception);
                } finally {
                    restorePose(poseStack, origin);
                }
            }
        } finally {
            dispatcher.setRenderShadow(true);
        }
    }

    private static List<DeliveredPreparedEntity> deliveredEntities(ClientLevel level, UUID jobId) {
        DeliveredSceneKey key = new DeliveredSceneKey(level, jobId);
        List<ConstructionEntityProjectionIndex.Entry> entries =
            ConstructionEntityProjectionIndex.delivered(level, jobId);
        DeliveredEntityScene cached = DELIVERED_ENTITY_CACHE.get(key);
        if (cached != null && cached.entries() == entries) return cached.entities();
        List<DeliveredPreparedEntity> entities = new ArrayList<>(entries.size());
        for (ConstructionEntityProjectionIndex.Entry entry : entries) {
            CompoundTag nbt = entry.nbt().copy();
            nbt.remove("UUID");
            movePreviewNbtOffWorld(nbt);
            try {
                EntityType.create(nbt, level)
                    .filter(entity -> !(entity instanceof Player))
                    .ifPresent(entity -> {
                        entity.moveTo(
                            PREVIEW_ENTITY_POSITION.getX(),
                            PREVIEW_ENTITY_POSITION.getY(),
                            PREVIEW_ENTITY_POSITION.getZ(),
                            entity.getYRot(),
                            entity.getXRot()
                        );
                        if (entity instanceof Mob mob) mob.setNoAi(true);
                        entities.add(new DeliveredPreparedEntity(entry, entity));
                    });
            } catch (RuntimeException exception) {
                skipOnce("delivered-entity-create:" + entry.opId(), exception);
            }
        }
        DeliveredEntityScene built = new DeliveredEntityScene(entries, List.copyOf(entities));
        DELIVERED_ENTITY_CACHE.put(key, built);
        trimDeliveredCache(DELIVERED_ENTITY_CACHE);
        return built.entities();
    }

    private static void skipOnce(String key, RuntimeException exception) {
        if (FAILED_RENDERERS.add(key)) {
            AnvilcraftPlasticraft.LOGGER.warn("Blueprint projection cannot render {}", key, exception);
        }
    }

    /** BER / 实体渲染器抛错时可能已经 push 却没 pop,按进入时的栈帧弹回到原深度。 */
    private static void restorePose(PoseStack poseStack, PoseStack.Pose origin) {
        int guard = 64;
        while (poseStack.last() != origin && guard-- > 0) {
            poseStack.popPose();
        }
    }

    private static int packColor(float red, float green, float blue, float alpha) {
        return ((int) (alpha * 255.0F) << 24)
            | ((int) (red * 255.0F) << 16)
            | ((int) (green * 255.0F) << 8)
            | (int) (blue * 255.0F);
    }

    private record MeshKey(String hash, Rotation rotation, Mirror mirror, int layerView) {
    }

    private record DeliveredSceneKey(ClientLevel level, UUID jobId) {
    }

    private record DeliveredBlockEntityScene(
        ConstructionProjectionIndex.DeliveredSnapshot snapshot,
        @Nullable PreparedProjection prepared,
        Map<Long, DeliveredBlockEntitySection> sections,
        List<BlockEntity> blockEntities
    ) {
    }

    private record DeliveredBlockEntitySection(long revision, List<BlockEntity> blockEntities) {
    }

    private record DeliveredEntityScene(
        List<ConstructionEntityProjectionIndex.Entry> entries,
        List<DeliveredPreparedEntity> entities
    ) {
    }

    private record DeliveredPreparedEntity(ConstructionEntityProjectionIndex.Entry entry, Entity entity) {
    }

    private static final class PreparedBuild {
        private final ClientLevel level;
        private final StructureSnapshot snapshot;
        private final BlueprintPlacement placement;
        private final int layerView;
        private final BlueprintRenderView view;
        private final HolderLookup.Provider registries;
        private final BlueprintProjectionSectionRenderer.PreparedSections sections =
            new BlueprintProjectionSectionRenderer.PreparedSections();
        private final List<BlockEntity> blockEntities = new ArrayList<>();
        private final Map<BlockPos, BlockEntity> blockEntitiesByPos = new LinkedHashMap<>();
        private final List<PreparedEntity> entities = new ArrayList<>();
        private int blockIndex;
        private int entityIndex;
        private long lastAdvancedFrame = Long.MIN_VALUE;

        private PreparedBuild(
            ClientLevel level,
            BlockPos tintPos,
            StructureSnapshot snapshot,
            BlueprintPlacement placement,
            int layerView
        ) {
            this.level = level;
            this.snapshot = snapshot;
            this.placement = placement;
            this.layerView = layerView;
            this.view = new BlueprintRenderView(level, tintPos);
            this.registries = level.registryAccess();
        }

        private int advance(int budget) {
            int remaining = budget;
            List<StructureSnapshot.BlockEntry> blocks = this.snapshot.blocks();
            while (remaining > 0 && this.blockIndex < blocks.size()) {
                this.prepareBlock(blocks.get(this.blockIndex++));
                remaining--;
            }
            List<StructureSnapshot.EntityEntry> snapshotEntities = this.snapshot.entities();
            while (remaining > 0 && this.blockIndex >= blocks.size()
                && this.entityIndex < snapshotEntities.size()) {
                this.prepareEntity(snapshotEntities.get(this.entityIndex++));
                remaining--;
            }
            return budget - remaining;
        }

        private void prepareBlock(StructureSnapshot.BlockEntry entry) {
            if (this.layerView != BlueprintDeploySession.LAYERS_ALL
                && entry.pos().getY() != this.layerView) {
                return;
            }
            BlockState state = this.snapshot.stateOf(entry);
            if (state.isAir()) return;
            BlockState transformed = this.placement.stateOf(state);
            BlockPos local = this.placement.localOf(entry.pos());
            BlockEntity blockEntity = createBlockEntity(
                this.level,
                this.registries,
                local,
                transformed,
                entry.nbt().orElse(null)
            );
            this.view.put(local, transformed, blockEntity);
            if (transformed.getRenderShape() == RenderShape.MODEL) this.sections.addModel(local);
            if (!transformed.getFluidState().isEmpty()) this.sections.addFluid(local);
            if (blockEntity != null) {
                this.blockEntities.add(blockEntity);
                this.blockEntitiesByPos.put(local.immutable(), blockEntity);
            }
        }

        private void prepareEntity(StructureSnapshot.EntityEntry entry) {
            if (this.layerView != BlueprintDeploySession.LAYERS_ALL
                && Mth.floor(entry.pos().y) != this.layerView) {
                return;
            }
            PreparedEntity prepared = createPreviewEntity(this.level, this.placement, entry);
            if (prepared != null) this.entities.add(prepared);
        }

        private boolean complete() {
            return this.blockIndex >= this.snapshot.blocks().size()
                && this.entityIndex >= this.snapshot.entities().size();
        }

        private PreparedProjection finish() {
            this.sections.freeze();
            return new PreparedProjection(
                this.view,
                this.sections,
                Collections.unmodifiableList(this.blockEntities),
                Collections.unmodifiableMap(this.blockEntitiesByPos),
                Collections.unmodifiableList(this.entities)
            );
        }
    }

    private record PreparedProjection(
        BlueprintRenderView view,
        BlueprintProjectionSectionRenderer.PreparedSections sections,
        List<BlockEntity> blockEntities,
        Map<BlockPos, BlockEntity> blockEntitiesByPos,
        List<PreparedEntity> entities
    ) {
    }

    private record PreparedEntity(Entity entity, Vec3 local, float yRot, float xRot) {
    }

    private record TintedBufferSource(MultiBufferSource delegate, float alpha) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType colorType = BlueprintProjectionRenderTypes.overlay(type);
            return new TintedVertexConsumer(this.delegate.getBuffer(colorType), this.alpha);
        }
    }

    /** 只改透明度,保留方块/流体/实体原色,树叶与红石粉才能着色。 */
    private record TintedVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {
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
            this.delegate.addVertex(x, y, z, withAlpha(color), u, v, overlay, light, normalX, normalY, normalZ);
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(red, green, blue, (int) (alpha * this.alpha));
            return this;
        }

        private int withAlpha(int color) {
            int alpha = (int) (FastColor.ARGB32.alpha(color) * this.alpha);
            return FastColor.ARGB32.color(
                alpha,
                FastColor.ARGB32.red(color),
                FastColor.ARGB32.green(color),
                FastColor.ARGB32.blue(color)
            );
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
