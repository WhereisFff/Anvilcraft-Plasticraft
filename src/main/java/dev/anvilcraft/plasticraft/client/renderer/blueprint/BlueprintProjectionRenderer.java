package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.BlueprintPlacement;
import dev.anvilcraft.plasticraft.blueprint.ConstructionJob;
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
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 施工投影渲染器:为已放置蓝图与部署会话渲染半透明目标投影。
 * 方块走与机械动力投影相同的邻接面剔除({@code tesselateBlock} / {@code renderBatched});
 * 告示牌、机械臂等方块实体与矿车等实体分别走 BER / 实体渲染器,再映射到不写深度的半透明层;
 * 箱子和矿车先单独走深度预通道再上色,只保留朝向相机的外轮廓;
 * 流体按格平移后以 (0,0,0) 调用 {@code renderLiquid},避免原版 {@code pos & 15} 在负坐标/大结构上错位。
 * 快照尚未到达时仍画出包围盒,保证放置位置始终可见。
 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class BlueprintProjectionRenderer {
    /** 投影可见距离(格),超出的已放置蓝图不渲染。 */
    private static final double VIEW_DISTANCE = 160.0D;
    /** 投影统一着色:把方块原色向全息青略洗,避免直接乘青把红石和树叶滤成灰褐。 */
    private static final float TINT_ALPHA = 0.55F;
    private static final float BOX_RED = 0.25F;
    private static final float BOX_GREEN = 0.85F;
    private static final float BOX_BLUE = 1.0F;
    private static final float BOX_ALPHA = 0.95F;
    private static final int BOX_COLOR = packColor(BOX_RED, BOX_GREEN, BOX_BLUE, BOX_ALPHA);
    private static final int[][] BOX_EDGES = {
        {0, 1}, {1, 2}, {2, 3}, {3, 0},
        {4, 5}, {5, 6}, {6, 7}, {7, 4},
        {0, 4}, {1, 5}, {2, 6}, {3, 7}
    };
    private static final int MAX_CACHED_MESHES = 4;

    private static final Map<MeshKey, PreparedProjection> CACHE = new LinkedHashMap<>(MAX_CACHED_MESHES + 1, 0.75F, true);
    private static final Set<String> FAILED_RENDERERS = new HashSet<>();
    private static final BlueprintProjectionAnimator ANIMATOR = new BlueprintProjectionAnimator();

    private BlueprintProjectionRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;

        List<RenderItem> items = collectRenderItems(minecraft);
        if (items.isEmpty()) {
            ANIMATOR.clear();
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
        TintedBufferSource depthBuffers = new TintedBufferSource(buffers, TINT_ALPHA, true);
        TintedBufferSource colorBuffers = new TintedBufferSource(buffers, TINT_ALPHA, false);
        RenderType blockType = BlueprintProjectionRenderTypes.hologramBlock();
        VertexConsumer tintedBlocks = new TintedVertexConsumer(buffers.getBuffer(blockType), TINT_ALPHA);

        poseStack.pushPose();
        try {
            poseStack.translate(-camera.x, -camera.y, -camera.z);
            for (RenderItem item : items) {
                PreparedProjection prepared = preparedOrNull(minecraft, item, poseOf(item, sessionPose));
                if (prepared == null) continue;
                withMeshPose(poseStack, poseOf(item, sessionPose), () -> renderBlocksAndFluids(
                    minecraft,
                    poseStack,
                    tintedBlocks,
                    prepared
                ));
            }
            buffers.endBatch(blockType);
            for (RenderItem item : items) {
                PreparedProjection prepared = preparedOrNull(minecraft, item, poseOf(item, sessionPose));
                if (prepared == null) continue;
                withMeshPose(poseStack, poseOf(item, sessionPose), () -> {
                    renderBlockEntities(minecraft, poseStack, depthBuffers, prepared);
                    renderEntities(minecraft, poseStack, depthBuffers, prepared);
                });
            }
            BlueprintProjectionRenderTypes.endSilhouetteDepth(buffers);
            for (RenderItem item : items) {
                PreparedProjection prepared = preparedOrNull(minecraft, item, poseOf(item, sessionPose));
                if (prepared == null) continue;
                withMeshPose(poseStack, poseOf(item, sessionPose), () -> {
                    renderBlockEntities(minecraft, poseStack, colorBuffers, prepared);
                    renderEntities(minecraft, poseStack, colorBuffers, prepared);
                });
            }
            buffers.endBatch();
            for (RenderItem item : items) {
                renderBounds(poseStack, buffers, poseOf(item, sessionPose), item.size());
            }
        } catch (RuntimeException exception) {
            AnvilcraftPlasticraft.LOGGER.error("Blueprint projection failed", exception);
        } finally {
            restorePose(poseStack, origin);
        }
    }

    public static void clearCache() {
        CACHE.clear();
        FAILED_RENDERERS.clear();
        ANIMATOR.clear();
    }

    private record RenderItem(
        String hash,
        BlueprintPlacement placement,
        int layerView,
        Vec3i size,
        boolean session
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
            items.add(new RenderItem(
                sessionHash,
                sessionPlacement,
                BlueprintDeploySession.layerView(),
                sessionSize,
                true
            ));
        }
        assert minecraft.level != null && minecraft.player != null;
        for (ConstructionJob job : ClientBlueprintJobCache.jobs()) {
            if (!job.dimension().equals(minecraft.level.dimension())) continue;
            if (job.jobId().equals(sessionJobId)) continue;
            if (!job.anchor().closerToCenterThan(minecraft.player.position(), VIEW_DISTANCE)) continue;
            items.add(new RenderItem(
                job.hash(),
                BlueprintPlacement.of(job),
                BlueprintDeploySession.LAYERS_ALL,
                job.size(),
                false
            ));
        }
        return items;
    }

    private static void renderBounds(
        PoseStack poseStack,
        MultiBufferSource.BufferSource buffers,
        BlueprintProjectionAnimator.Pose pose,
        Vec3i size
    ) {
        AABB baked = AABB.of(pose.mesh().bounds(size));
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

    private static PreparedProjection prepared(
        Minecraft minecraft,
        RenderItem item,
        StructureSnapshot snapshot,
        BlueprintPlacement mesh
    ) {
        MeshKey key = new MeshKey(item.hash(), mesh.rotation(), mesh.mirror(), item.layerView());
        PreparedProjection cached = CACHE.get(key);
        if (cached != null) return cached;
        PreparedProjection built = build(minecraft, item, snapshot, mesh);
        CACHE.put(key, built);
        while (CACHE.size() > MAX_CACHED_MESHES) {
            MeshKey oldest = CACHE.keySet().iterator().next();
            CACHE.remove(oldest);
        }
        return built;
    }

    private static PreparedProjection build(
        Minecraft minecraft,
        RenderItem item,
        StructureSnapshot snapshot,
        BlueprintPlacement placement
    ) {
        ClientLevel level = minecraft.level;
        assert level != null && minecraft.player != null;
        BlueprintRenderView view = new BlueprintRenderView(level, minecraft.player.blockPosition());
        List<BlockPos> modelBlocks = new ArrayList<>();
        List<BlockPos> fluidBlocks = new ArrayList<>();
        List<BlockEntity> blockEntities = new ArrayList<>();
        HolderLookup.Provider registries = level.registryAccess();

        for (StructureSnapshot.BlockEntry entry : snapshot.blocks()) {
            if (item.layerView() != BlueprintDeploySession.LAYERS_ALL
                && entry.pos().getY() != item.layerView()) {
                continue;
            }
            BlockState state = snapshot.stateOf(entry);
            if (state.isAir()) continue;
            BlockState transformed = placement.stateOf(state);
            BlockPos local = placement.localOf(entry.pos());
            BlockEntity blockEntity = createBlockEntity(level, registries, local, transformed, entry.nbt().orElse(null));
            view.put(local, transformed, blockEntity);
            if (transformed.getRenderShape() == RenderShape.MODEL) {
                modelBlocks.add(local);
            }
            if (!transformed.getFluidState().isEmpty()) {
                fluidBlocks.add(local);
            }
            if (blockEntity != null) {
                blockEntities.add(blockEntity);
            }
        }

        List<PreparedEntity> entities = new ArrayList<>();
        for (StructureSnapshot.EntityEntry entry : snapshot.entities()) {
            if (item.layerView() != BlueprintDeploySession.LAYERS_ALL
                && Mth.floor(entry.pos().y) != item.layerView()) {
                continue;
            }
            PreparedEntity prepared = createPreviewEntity(level, placement, entry);
            if (prepared != null) {
                entities.add(prepared);
            }
        }
        return new PreparedProjection(view, modelBlocks, fluidBlocks, blockEntities, entities);
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
        // 实体留在世界外高空,避免 MinecartRenderer.getPos 去真实世界找铁轨并把全息矿车吸走。
        ListTag posTag = new ListTag();
        posTag.add(DoubleTag.valueOf(0.0D));
        posTag.add(DoubleTag.valueOf(4096.0D));
        posTag.add(DoubleTag.valueOf(0.0D));
        nbt.put("Pos", posTag);
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

    private static BlockPos snapshotBlockOf(StructureSnapshot.EntityEntry entry) {
        BlockPos contained = BlockPos.containing(entry.pos());
        BlockPos recorded = entry.blockPos();
        return contained.distManhattan(recorded) <= 1 ? recorded : contained;
    }

    private static void applyEntityPlacement(Entity entity, BlueprintPlacement placement) {
        float yRot = entity.rotate(placement.rotation());
        yRot += entity.mirror(placement.mirror()) - entity.getYRot();
        entity.moveTo(0.0D, 4096.0D, 0.0D, yRot, entity.getXRot());
        entity.xOld = 0.0D;
        entity.yOld = 4096.0D;
        entity.zOld = 0.0D;
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
        StructureSnapshot snapshot = ClientBlueprintSnapshotCache.snapshotOrRequest(item.hash());
        if (snapshot == null) return null;
        return prepared(minecraft, item, snapshot, pose.mesh());
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

    private static void renderBlocksAndFluids(
        Minecraft minecraft,
        PoseStack poseStack,
        VertexConsumer tintedBlocks,
        PreparedProjection prepared
    ) {
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        try {
            prepared.view().hideNonOccludingNeighbors(true);
            ModelBlockRenderer.enableCaching();
            tessellateModels(dispatcher, poseStack, tintedBlocks, prepared, true);
            ModelBlockRenderer.clearCache();
            prepared.view().hideNonOccludingNeighbors(false);
            ModelBlockRenderer.enableCaching();
            tessellateModels(dispatcher, poseStack, tintedBlocks, prepared, false);
            ModelBlockRenderer.clearCache();
            prepared.view().hideNonOccludingNeighbors(false);
            for (BlockPos pos : prepared.fluidBlocks()) {
                BlockState state = prepared.view().realState(pos);
                FluidState fluid = state.getFluidState();
                if (fluid.isEmpty()) continue;
                poseStack.pushPose();
                poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
                Matrix4f matrix = new Matrix4f(poseStack.last().pose());
                VertexConsumer fluidConsumer = new PoseVertexConsumer(tintedBlocks, matrix);
                dispatcher.renderLiquid(BlockPos.ZERO, prepared.view().shifted(pos), fluidConsumer, state, fluid);
                poseStack.popPose();
            }
        } finally {
            prepared.view().hideNonOccludingNeighbors(true);
            ModelBlockRenderer.clearCache();
        }
    }

    private static void tessellateModels(
        BlockRenderDispatcher dispatcher,
        PoseStack poseStack,
        VertexConsumer tintedBlocks,
        PreparedProjection prepared,
        boolean solidPass
    ) {
        RandomSource random = RandomSource.create();
        for (BlockPos pos : prepared.modelBlocks()) {
            BlockState state = prepared.view().realState(pos);
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            if (isSolidCube(state) != solidPass) continue;
            random.setSeed(state.getSeed(pos));
            poseStack.pushPose();
            poseStack.translate(pos.getX(), pos.getY(), pos.getZ());
            dispatcher.renderBatched(
                state,
                pos,
                prepared.view(),
                poseStack,
                tintedBlocks,
                true,
                random,
                prepared.view().getModelData(pos),
                null
            );
            poseStack.popPose();
        }
    }

    private static boolean isSolidCube(BlockState state) {
        return state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    private static void renderBlockEntities(
        Minecraft minecraft,
        PoseStack poseStack,
        MultiBufferSource buffers,
        PreparedProjection prepared
    ) {
        for (BlockEntity blockEntity : prepared.blockEntities()) {
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

    private record PreparedProjection(
        BlueprintRenderView view,
        List<BlockPos> modelBlocks,
        List<BlockPos> fluidBlocks,
        List<BlockEntity> blockEntities,
        List<PreparedEntity> entities
    ) {
    }

    private record PreparedEntity(Entity entity, Vec3 local, float yRot, float xRot) {
    }

    /**
     * {@code renderLiquid} 按区块内 {@code pos & 15} 写顶点。把查询原点挪到当前格并以 (0,0,0) 调用,
     * 顶点落在 0-1,再乘当前姿态,与方块共用旋转/镜像/锚点。
     */
    private record PoseVertexConsumer(VertexConsumer delegate, Matrix4f pose) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(this.pose, x, y, z);
            return this;
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

    /**
     * 主 BufferSource 对不在 fixedBuffers 里的自定义层一次只能打开一个 builder。
     * 深度和颜色必须分两遍取 buffer,不能 VertexMultiConsumer 双写,否则会先结束深度 builder 再往里提交顶点。
     */
    private record TintedBufferSource(MultiBufferSource delegate, float alpha, boolean depthPass)
        implements MultiBufferSource {
        private static final VertexConsumer DISCARDING = new DiscardingVertexConsumer();

        @Override
        public VertexConsumer getBuffer(RenderType type) {
            RenderType colorType = BlueprintProjectionRenderTypes.overlay(type);
            if (this.depthPass) {
                if (!BlueprintProjectionRenderTypes.needsSilhouette(type, colorType)) {
                    return DISCARDING;
                }
                return this.delegate.getBuffer(BlueprintProjectionRenderTypes.silhouetteDepth(type));
            }
            return new TintedVertexConsumer(this.delegate.getBuffer(colorType), this.alpha);
        }
    }

    private record DiscardingVertexConsumer() implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
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
