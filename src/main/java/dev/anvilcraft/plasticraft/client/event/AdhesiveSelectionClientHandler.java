package dev.anvilcraft.plasticraft.client.event;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.ThickLineRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderTransforms;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveBondingService;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFaces;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveGroupTransform;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesivePathPlanner;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveSelectionManager;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveTransit;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.network.AdhesiveBondEntitiesPacket;
import dev.anvilcraft.plasticraft.network.AdhesiveBondEntityPacket;
import dev.anvilcraft.plasticraft.network.AdhesiveClearSelectionPacket;
import dev.anvilcraft.plasticraft.network.AdhesivePlacePatchPacket;
import dev.anvilcraft.plasticraft.network.AdhesivePreviewRequestPacket;
import dev.anvilcraft.plasticraft.network.AdhesiveSelectEntityPacket;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 处理树脂桶的实体选择、服务端请求和带路径动画的世界内预览。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID, value = Dist.CLIENT)
public final class AdhesiveSelectionClientHandler {
    private static final long BOX_ENTER_MILLIS = 180L;
    private static final long BOX_EXIT_MILLIS = 220L;
    private static final long PREVIEW_REQUEST_TIMEOUT_MILLIS = 3_000L;
    private static final double PREVIEW_RESTART_DISTANCE_SQR = 0.0625D;
    private static final double PATH_TUBE_WIDTH = 0.045D;
    private static final double SEARCH_DASH_LENGTH = 0.28D;
    private static final double SEARCH_DASH_GAP = 0.18D;
    private static final double SEARCH_DASH_SPEED = 1.2D;
    private static final double GRID_OUTWARD_OFFSET = 0.004D;
    private static final double SELECTED_GRID_INSET = 0.004D;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRID_WHITE = 0x70FFFFFF;
    private static final int SAFE_GREEN = 0xFF40FF40;
    private static final int WARNING_YELLOW = 0xFFFFFF40;
    private static final int DANGER_RED = 0xFFFF4040;
    private static final String OUT_OF_RANGE_MESSAGE =
        "message.anvilcraftplasticraft.adhesive.out_of_range";
    private static final String TOO_FAR_MESSAGE =
        "message.anvilcraftplasticraft.adhesive.too_far_disconnected";
    private static final long BLOCK_USE_HOLD_MILLIS = 500L;
    private static final ClassValue<Boolean> INTERACTIVE_BLOCK_CLASSES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            return overridesBlockInteraction(type);
        }
    };
    private static @Nullable UUID animatedUuid;
    private static @Nullable Entity animatedEntity;
    private static @Nullable AABB animatedBox;
    private static long boxEnterStarted;
    private static long boxExitStarted = -1L;
    private static @Nullable PreviewKey cachedPreviewKey;
    private static @Nullable AdhesivePathPlanner.Plan cachedPreview;
    private static @Nullable PreviewKey previewTaskKey;
    private static @Nullable AdhesivePathPlanner.Plan pendingPreview;
    private static int nextPreviewRequestId;
    private static int activePreviewRequestId = -1;
    private static long previewRequestStartedAt;
    private static boolean previewPathReady;
    private static boolean cachedPreviewGroupClear;
    private static Vec3 cachedPreviewStart = Vec3.ZERO;
    private static Vec3 cachedPreviewSupportStart = Vec3.ZERO;
    private static @Nullable List<Vec3> sampledPreviewInput;
    private static List<Vec3> sampledPreviewPath = List.of();
    private static @Nullable AdhesivePathPlanner.Plan cachedRenderPlan;
    private static @Nullable Entity cachedRenderEntity;
    private static @Nullable Entity cachedRenderSupportEntity;
    private static @Nullable BlockPos cachedRenderSupportPos;
    private static @Nullable Direction cachedRenderFace;
    private static Vec3 cachedRenderSupportPosition = Vec3.ZERO;
    private static List<Vec3> cachedRenderPath = List.of();
    private static @Nullable AdhesivePathPlanner.Plan cachedPreviewProjectionPlan;
    private static @Nullable Entity cachedPreviewProjectionRoot;
    private static @Nullable AdhesiveGroupTransform.Projection cachedPreviewProjection;
    private static @Nullable PendingBlockUse pendingBlockUse;

    private AdhesiveSelectionClientHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onUseKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isUseItem()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        InteractionHand hand = event.getHand();
        if (player == null
            || minecraft.getConnection() == null
            || !player.getItemInHand(hand).is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }
        if (player.isShiftKeyDown()) {
            pendingBlockUse = null;
            return;
        }

        HitResult hitResult = minecraft.hitResult;
        Entity selected = getSelectedEntity(minecraft);
        if (pendingBlockUse != null) {
            cancel(event);
            return;
        }
        AdhesivePathPlanner.Plan interactionPreview = selected == null
            ? null
            : previewAtHit(minecraft, selected, player);
        if (interactionPreview != null
            && AdhesivePathPlanner.isOutOfRange(interactionPreview.directDistance())) {
            clearSelection(minecraft, player);
            if (AdhesivePathPlanner.exceedsBreakDistance(interactionPreview.directDistance())) {
                displayRangeMessage(player, TOO_FAR_MESSAGE, "Too far away; selection disconnected");
            } else {
                displayRangeMessage(player, OUT_OF_RANGE_MESSAGE, "Too far away");
            }
            cancel(event);
            return;
        }
        if (hitResult instanceof EntityHitResult entityHit) {
            Entity target = entityHit.getEntity();
            Direction hitFace = AdhesiveFaces.hitFace(target, entityHit.getLocation());
            if (AdhesiveBondingService.hasAdhesiveOnFace(target, hitFace)) {
                AdhesiveSelectionManager.clear(player);
                clearPreviewTask();
                startExitAnimation();
                PacketDistributor.sendToServer(new AdhesiveSelectEntityPacket(target.getId(), hand, hitFace));
                cancel(event);
                return;
            }
            if (selected != null && selected != target) {
                PacketDistributor.sendToServer(new AdhesiveBondEntitiesPacket(
                    selected.getId(),
                    target.getId(),
                    hand,
                    hitFace
                ));
                cancel(event);
                return;
            }
            if (target != player
                && !target.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)) {
                AdhesiveSelectionManager.select(player, target, hitFace);
                beginSelectionAnimation(target);
                PacketDistributor.sendToServer(new AdhesiveSelectEntityPacket(target.getId(), hand, hitFace));
            }
            cancel(event);
            return;
        }

        if (selected != null
            && hitResult instanceof BlockHitResult blockHit
            && blockHit.getType() != HitResult.Type.MISS) {
            PacketDistributor.sendToServer(new AdhesiveBondEntityPacket(
                selected.getId(),
                hand,
                blockHit.getBlockPos(),
                blockHit.getDirection()
            ));
            cancel(event);
            return;
        }

        if (selected == null
            && hitResult instanceof BlockHitResult blockHit
            && blockHit.getType() != HitResult.Type.MISS) {
            PendingBlockUse pending = new PendingBlockUse(
                hand,
                copy(blockHit),
                Util.getMillis(),
                !canInteractWithBlock(minecraft, player, blockHit)
            );
            pendingBlockUse = pending;
            if (pending.patchPlaced()) placePatch(pending, player);
            cancel(event);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            pendingBlockUse = null;
            resetAnimation();
            return;
        }
        if (pendingBlockUse != null
            && (minecraft.screen != null
                || minecraft.getConnection() == null
                || player.isShiftKeyDown()
                || !player.getItemInHand(pendingBlockUse.hand())
                    .is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get()))) {
            pendingBlockUse = null;
        }
        boolean holdingBucket = player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
            || player.getOffhandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
        if (!holdingBucket) {
            AdhesiveSelectionManager.clear(player);
            clearPreviewTask();
            startExitAnimation();
            return;
        }
        Entity selected = getSelectedEntity(minecraft);
        if (selected == null || selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)) {
            AdhesiveSelectionManager.clear(player);
            clearPreviewTask();
            startExitAnimation();
            return;
        }
        updatePreview(minecraft, minecraft.level, player, selected);
        if (previewBreakDistanceExceeded(minecraft, selected, player)) {
            clearSelection(minecraft, player);
            displayRangeMessage(player, TOO_FAR_MESSAGE, "Too far away; selection disconnected");
        }
    }

    private static void updatePreview(
        Minecraft minecraft,
        @Nullable ClientLevel level,
        LocalPlayer player,
        Entity selected
    ) {
        if (level == null) return;
        if (!selected.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            && minecraft.hitResult instanceof BlockHitResult blockHit
            && blockHit.getType() != HitResult.Type.MISS) {
            PreviewKey key = new PreviewKey(
                selected.getUUID(),
                null,
                -1,
                blockHit.getBlockPos(),
                blockHit.getDirection(),
                AdhesiveSelectionManager.getSelectedFace(player),
                player.getDirection()
            );
            if (shouldRestartPreview(key, selected, null)) {
                startBlockPreviewRequest(key, level, selected, player, blockHit);
            }
            return;
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != selected) {
            Entity supportEntity = entityHit.getEntity();
            Direction supportFace = AdhesiveFaces.hitFace(supportEntity, entityHit.getLocation());
            Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
            boolean reverse = selected.hasData(PlasticraftAttachments.ENTITY_ADHESION);
            Entity movingEntity = reverse ? supportEntity : selected;
            Entity anchorEntity = reverse ? selected : supportEntity;
            Direction anchorFace = reverse ? AdhesiveFaces.worldFace(selected, selectedFace) : supportFace;
            Direction movingFace = reverse ? AdhesiveFaces.storedFace(supportEntity, supportFace) : selectedFace;
            PreviewKey key = new PreviewKey(
                selected.getUUID(),
                supportEntity.getUUID(),
                supportEntity.getId(),
                BlockPos.ZERO,
                supportFace,
                selectedFace,
                player.getDirection()
            );
            if (shouldRestartPreview(key, selected, supportEntity)) {
                startEntityPreviewRequest(
                    key,
                    level,
                    selected,
                    player,
                    supportEntity,
                    movingEntity,
                    anchorEntity,
                    anchorFace,
                    movingFace,
                    supportFace
                );
            }
            return;
        }
        clearPreviewTask();
    }

    private static boolean shouldRestartPreview(
        PreviewKey key,
        Entity selected,
        @Nullable Entity supportEntity
    ) {
        if (key.equals(previewTaskKey)) {
            return previewMoved(selected, cachedPreviewStart, supportEntity, cachedPreviewSupportStart)
                || activePreviewRequestId >= 0
                && Util.getMillis() - previewRequestStartedAt >= PREVIEW_REQUEST_TIMEOUT_MILLIS;
        }
        if (!key.equals(cachedPreviewKey)) return true;
        return previewMoved(selected, cachedPreviewStart, supportEntity, cachedPreviewSupportStart);
    }

    private static boolean previewMoved(
        Entity selected,
        Vec3 selectedStart,
        @Nullable Entity supportEntity,
        Vec3 supportStart
    ) {
        return selected.position().distanceToSqr(selectedStart) > PREVIEW_RESTART_DISTANCE_SQR
            || supportEntity != null
            && supportEntity.position().distanceToSqr(supportStart) > PREVIEW_RESTART_DISTANCE_SQR;
    }

    private static void startBlockPreviewRequest(
        PreviewKey key,
        ClientLevel level,
        Entity selected,
        LocalPlayer player,
        BlockHitResult hit
    ) {
        AdhesivePathPlanner.Plan direct = AdhesivePathPlanner.beginPreview(
            level,
            selected,
            player,
            hit.getBlockPos(),
            hit.getDirection(),
            AdhesiveSelectionManager.getSelectedFace(player)
        ).displayPlan();
        startPreviewRequest(key, selected, null, direct);
        PacketDistributor.sendToServer(new AdhesivePreviewRequestPacket(
            nextPreviewRequestId(),
            AdhesivePreviewRequestPacket.Mode.BLOCK,
            selected.getId(),
            -1,
            hit.getBlockPos(),
            hit.getDirection()
        ));
    }

    private static void startEntityPreviewRequest(
        PreviewKey key,
        ClientLevel level,
        Entity selected,
        LocalPlayer player,
        Entity supportEntity,
        Entity movingEntity,
        Entity anchorEntity,
        Direction anchorFace,
        Direction movingFace,
        Direction supportFace
    ) {
        AdhesivePathPlanner.Plan direct = AdhesivePathPlanner.beginPreviewToEntity(
            level,
            movingEntity,
            player,
            anchorEntity,
            anchorFace,
            movingFace
        ).displayPlan();
        startPreviewRequest(key, selected, supportEntity, direct);
        PacketDistributor.sendToServer(new AdhesivePreviewRequestPacket(
            nextPreviewRequestId(),
            AdhesivePreviewRequestPacket.Mode.ENTITY,
            selected.getId(),
            supportEntity.getId(),
            BlockPos.ZERO,
            supportFace
        ));
    }

    private static void startPreviewRequest(
        PreviewKey key,
        Entity selected,
        @Nullable Entity supportEntity,
        AdhesivePathPlanner.Plan direct
    ) {
        clearPreviewTask(false);
        previewTaskKey = key;
        pendingPreview = direct;
        cachedPreviewKey = null;
        cachedPreview = null;
        cachedPreviewGroupClear = false;
        cachedPreviewStart = selected.position();
        cachedPreviewSupportStart = supportEntity == null ? Vec3.ZERO : supportEntity.position();
        sampledPreviewInput = null;
        sampledPreviewPath = List.of();
        clearPreviewProjection();
        previewPathReady = false;
    }

    private static void clearPreviewTask() {
        clearPreviewTask(true);
    }

    private static void clearPreviewTask(boolean sendCancel) {
        if (sendCancel
            && activePreviewRequestId >= 0
            && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new AdhesivePreviewRequestPacket(
                activePreviewRequestId,
                AdhesivePreviewRequestPacket.Mode.CANCEL,
                -1,
                -1,
                BlockPos.ZERO,
                Direction.DOWN
            ));
        }
        previewTaskKey = null;
        pendingPreview = null;
        activePreviewRequestId = -1;
        previewRequestStartedAt = 0L;
        previewPathReady = false;
        cachedPreviewKey = null;
        cachedPreview = null;
        cachedPreviewGroupClear = false;
        cachedPreviewStart = Vec3.ZERO;
        cachedPreviewSupportStart = Vec3.ZERO;
        sampledPreviewInput = null;
        sampledPreviewPath = List.of();
        cachedRenderPlan = null;
        cachedRenderEntity = null;
        cachedRenderSupportEntity = null;
        cachedRenderSupportPos = null;
        cachedRenderFace = null;
        cachedRenderSupportPosition = Vec3.ZERO;
        cachedRenderPath = List.of();
        clearPreviewProjection();
    }

    private static int nextPreviewRequestId() {
        nextPreviewRequestId++;
        if (nextPreviewRequestId <= 0) nextPreviewRequestId = 1;
        activePreviewRequestId = nextPreviewRequestId;
        previewRequestStartedAt = Util.getMillis();
        return activePreviewRequestId;
    }

    private static void clearPreviewProjection() {
        cachedPreviewProjectionPlan = null;
        cachedPreviewProjectionRoot = null;
        cachedPreviewProjection = null;
    }

    @SubscribeEvent
    public static void onKeyReleased(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyUse.matches(event.getKey(), event.getScanCode())) releasePendingBlockUse();
    }

    @SubscribeEvent
    public static void onMouseReleased(InputEvent.MouseButton.Post event) {
        if (event.getAction() != GLFW.GLFW_RELEASE) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.keyUse.matchesMouse(event.getButton())) releasePendingBlockUse();
    }

    private static void releasePendingBlockUse() {
        PendingBlockUse pending = pendingBlockUse;
        pendingBlockUse = null;
        if (pending == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null
            || minecraft.gameMode == null
            || minecraft.getConnection() == null
            || minecraft.screen != null
            || player.isShiftKeyDown()
            || !player.getItemInHand(pending.hand()).is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) {
            return;
        }

        if (pending.patchPlaced()) return;
        if (Util.getMillis() - pending.startedAtMillis() > BLOCK_USE_HOLD_MILLIS) {
            placePatch(pending, player);
            return;
        }
        InteractionResult result = minecraft.gameMode.useItemOn(
            player,
            pending.hand(),
            pending.hit()
        );
        if (result == InteractionResult.PASS) {
            placePatch(pending, player);
        } else if (result.consumesAction()) {
            player.swing(pending.hand());
        }
    }

    private static void placePatch(PendingBlockUse pending, LocalPlayer player) {
        PacketDistributor.sendToServer(new AdhesivePlacePatchPacket(
            pending.hand(),
            pending.hit().getBlockPos(),
            pending.hit().getDirection()
        ));
        player.swing(pending.hand());
    }

    private static BlockHitResult copy(BlockHitResult hit) {
        return new BlockHitResult(hit.getLocation(), hit.getDirection(), hit.getBlockPos(), hit.isInside());
    }

    private static boolean canInteractWithBlock(
        Minecraft minecraft,
        LocalPlayer player,
        BlockHitResult hit
    ) {
        ClientLevel level = minecraft.level;
        if (level == null) return false;
        BlockPos pos = hit.getBlockPos();
        boolean heldItemSuppressesBlock = player.isSecondaryUseActive()
            && (!player.getMainHandItem().doesSneakBypassUse(level, pos, player)
                || !player.getOffhandItem().doesSneakBypassUse(level, pos, player));
        if (heldItemSuppressesBlock) return false;
        return INTERACTIVE_BLOCK_CLASSES.get(level.getBlockState(pos).getBlock().getClass());
    }

    private static boolean overridesBlockInteraction(Class<?> blockClass) {
        return declaresBeforeBlockBehaviour(
            blockClass,
            "useItemOn",
            ItemStack.class,
            BlockState.class,
            Level.class,
            BlockPos.class,
            Player.class,
            InteractionHand.class,
            BlockHitResult.class
        ) || declaresBeforeBlockBehaviour(
            blockClass,
            "useWithoutItem",
            BlockState.class,
            Level.class,
            BlockPos.class,
            Player.class,
            BlockHitResult.class
        );
    }

    private static boolean declaresBeforeBlockBehaviour(
        Class<?> blockClass,
        String methodName,
        Class<?>... parameterTypes
    ) {
        for (Class<?> type = blockClass;
             type != null && type != BlockBehaviour.class;
             type = type.getSuperclass()) {
            try {
                type.getDeclaredMethod(methodName, parameterTypes);
                return true;
            } catch (NoSuchMethodException ignored) {
                // 继续检查方块父类是否提供了实际交互。
            }
        }
        return false;
    }

    @SubscribeEvent
    public static void renderSelection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return;

        Entity selected = getSelectedEntity(minecraft);
        if (selected != null) {
            if (!selected.getUUID().equals(animatedUuid)) beginSelectionAnimation(selected);
            animatedEntity = selected;
            animatedBox = selected.getBoundingBox().inflate(0.006D);
            boxExitStarted = -1L;
        } else if (animatedEntity != null && animatedEntity.isAlive()) {
            animatedBox = animatedEntity.getBoundingBox().inflate(0.006D);
        }

        long now = Util.getMillis();
        float boxProgress = selectionBoxProgress(selected != null, now);
        AdhesiveTransit transit = animatedEntity == null
            ? null
            : animatedEntity.getExistingDataOrNull(PlasticraftAttachments.ADHESIVE_TRANSIT.get());
        Entity transitSupport = transit == null ? null : resolveTransitSupport(level, transit);
        List<Vec3> path = List.of();
        int pathColor = WHITE;
        @Nullable AdhesivePathPlanner.Plan preview = null;
        @Nullable Entity previewSupport = null;
        @Nullable Entity previewMovingEntity = selected;
        @Nullable BlockPos previewSupportPos = null;
        @Nullable Direction previewSupportFace = null;
        if (selected != null
            && !selected.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            && minecraft.hitResult instanceof BlockHitResult blockHit
            && blockHit.getType() != HitResult.Type.MISS) {
            preview = previewPath(level, selected, player, blockHit);
            previewSupportPos = blockHit.getBlockPos();
            previewSupportFace = blockHit.getDirection();
            if (preview != null) {
                path = previewRenderPath(
                    selected,
                    preview,
                    null,
                    blockHit.getBlockPos(),
                    blockHit.getDirection()
                );
            }
        } else if (selected != null
            && minecraft.hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != selected) {
            previewSupport = entityHit.getEntity();
            previewSupportFace = AdhesiveFaces.hitFace(previewSupport, entityHit.getLocation());
            preview = previewPath(level, selected, player, previewSupport, previewSupportFace);
            if (selected.hasData(PlasticraftAttachments.ENTITY_ADHESION)) {
                previewMovingEntity = previewSupport;
                previewSupport = selected;
                previewSupportFace = AdhesiveFaces.worldFace(
                    selected,
                    AdhesiveSelectionManager.getSelectedFace(player)
                );
            }
            if (preview != null) {
                path = previewRenderPath(
                    previewMovingEntity,
                    preview,
                    previewSupport,
                    null,
                    previewSupportFace
                );
            }
        } else if (transit != null) {
            path = transitRenderPath(animatedEntity, transit, transitSupport);
        }
        boolean previewSearching = preview != null && !previewPathReady;
        if (preview != null && previewPathReady) {
            pathColor = previewPathColor(preview, cachedPreviewGroupClear);
        }
        boolean previewClear = previewPathReady
            && preview != null
            && preview.valid()
            && cachedPreviewGroupClear;
        PoseStack pose = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (animatedBox != null && boxProgress > 0.0F) {
            renderAnimatedBox(pose, buffers, animatedBox, boxProgress);
        }
        if (selected != null) {
            Direction selectedWorldFace = AdhesiveFaces.worldFace(
                selected,
                AdhesiveSelectionManager.getSelectedFace(player)
            );
            renderFaceGrid(pose, buffers, selected.getBoundingBox(), selectedWorldFace);
            renderFaceGrid(
                pose,
                buffers,
                selected.getBoundingBox(),
                selectedWorldFace,
                -SELECTED_GRID_INSET
            );
        }
        if (!path.isEmpty()) {
            if (previewSearching) {
                ThickLineRenderer.renderDashed(
                    pose,
                    buffers,
                    path,
                    WHITE,
                    PATH_TUBE_WIDTH,
                    SEARCH_DASH_LENGTH,
                    SEARCH_DASH_GAP,
                    now * SEARCH_DASH_SPEED / 1_000.0D
                );
            } else {
                renderPath(pose, buffers, path, pathColor);
            }
        }
        if (preview != null && previewSupportFace != null) {
            renderPreviewEndpoint(
                pose,
                buffers,
                previewMovingEntity,
                preview,
                previewClear,
                previewSupport,
                previewSupportPos,
                previewSupportFace
            );
        } else if (transit != null && animatedEntity != null) {
            renderTransitEndpoint(pose, buffers, animatedEntity, transit, transitSupport);
        }
        pose.popPose();

        if (selected == null && transit == null && boxProgress <= 0.0F) resetAnimation();
    }

    private static @Nullable AdhesivePathPlanner.Plan previewPath(
        ClientLevel level,
        Entity selected,
        LocalPlayer player,
        BlockHitResult hit
    ) {
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        PreviewKey key = new PreviewKey(
            selected.getUUID(),
            null,
            -1,
            hit.getBlockPos(),
            hit.getDirection(),
            selectedFace,
            player.getDirection()
        );
        return previewForKey(key);
    }

    private static @Nullable AdhesivePathPlanner.Plan previewAtHit(
        Minecraft minecraft,
        Entity selected,
        LocalPlayer player
    ) {
        ClientLevel level = minecraft.level;
        if (level == null) return null;
        if (!selected.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            && minecraft.hitResult instanceof BlockHitResult blockHit
            && blockHit.getType() != HitResult.Type.MISS) {
            return previewPath(level, selected, player, blockHit);
        }
        if (minecraft.hitResult instanceof EntityHitResult entityHit
            && entityHit.getEntity() != selected) {
            Entity support = entityHit.getEntity();
            Direction supportFace = AdhesiveFaces.hitFace(support, entityHit.getLocation());
            return previewPath(level, selected, player, support, supportFace);
        }
        return null;
    }

    private static boolean previewBreakDistanceExceeded(
        Minecraft minecraft,
        Entity selected,
        LocalPlayer player
    ) {
        AdhesivePathPlanner.Plan preview = previewAtHit(minecraft, selected, player);
        return preview != null && AdhesivePathPlanner.exceedsBreakDistance(preview.directDistance());
    }

    private static void clearSelection(Minecraft minecraft, LocalPlayer player) {
        AdhesiveSelectionManager.clear(player);
        clearPreviewTask();
        if (minecraft.getConnection() != null) {
            PacketDistributor.sendToServer(new AdhesiveClearSelectionPacket());
        }
        startExitAnimation();
    }

    private static void displayRangeMessage(LocalPlayer player, String translationKey, String fallback) {
        player.displayClientMessage(
            Component.translatableWithFallback(translationKey, fallback).withStyle(ChatFormatting.RED),
            true
        );
    }

    private static int previewPathColor(AdhesivePathPlanner.Plan preview, boolean groupClear) {
        if (!preview.valid() || !groupClear) {
            return DANGER_RED;
        }
        return preview.directDistance() <= AdhesivePathPlanner.SAFE_DISTANCE
            ? SAFE_GREEN
            : WARNING_YELLOW;
    }

    private static @Nullable AdhesivePathPlanner.Plan previewPath(
        ClientLevel level,
        Entity selected,
        LocalPlayer player,
        Entity supportEntity,
        Direction supportFace
    ) {
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        PreviewKey key = new PreviewKey(
            selected.getUUID(),
            supportEntity.getUUID(),
            supportEntity.getId(),
            BlockPos.ZERO,
            supportFace,
            selectedFace,
            player.getDirection()
        );
        return previewForKey(key);
    }

    private static @Nullable AdhesivePathPlanner.Plan previewForKey(PreviewKey key) {
        if (key.equals(cachedPreviewKey)) return cachedPreview;
        return key.equals(previewTaskKey)
            ? pendingPreview
            : null;
    }

    public static void handlePreviewPath(
        int requestId,
        boolean groupClear,
        AdhesivePathPlanner.Plan plan
    ) {
        if (requestId != activePreviewRequestId || previewTaskKey == null) return;
        PreviewKey key = previewTaskKey;
        cachedPreviewKey = key;
        cachedPreview = plan;
        cachedPreviewGroupClear = groupClear;
        pendingPreview = null;
        previewTaskKey = null;
        activePreviewRequestId = -1;
        previewRequestStartedAt = 0L;
        previewPathReady = true;
        sampledPreviewInput = null;
        sampledPreviewPath = List.of();
        clearPreviewProjection();
    }

    public static void handlePreviewRejected(int requestId) {
        if (requestId != activePreviewRequestId || previewTaskKey == null) return;
        cachedPreviewKey = previewTaskKey;
        cachedPreview = null;
        cachedPreviewGroupClear = false;
        pendingPreview = null;
        previewTaskKey = null;
        activePreviewRequestId = -1;
        previewRequestStartedAt = 0L;
        previewPathReady = false;
        sampledPreviewInput = null;
        sampledPreviewPath = List.of();
        clearPreviewProjection();
    }

    private static List<Vec3> appendSurfaceEndpoint(List<Vec3> path, BlockPos supportPos, Direction face) {
        if (path.isEmpty()) return path;
        Vec3 surface = faceCenter(supportPos, face);
        if (path.getLast().distanceToSqr(surface) <= 0.0025D) return path;
        List<Vec3> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(surface);
        return result;
    }

    private static List<Vec3> previewRenderPath(
        Entity entity,
        AdhesivePathPlanner.Plan preview,
        @Nullable Entity supportEntity,
        @Nullable BlockPos supportPos,
        Direction face
    ) {
        boolean sameSupportPos = cachedRenderSupportPos == null
            ? supportPos == null
            : cachedRenderSupportPos.equals(supportPos);
        boolean sameSupportPosition = supportEntity == null
            || supportEntity.position().distanceToSqr(cachedRenderSupportPosition) <= 1.0E-10D;
        if (cachedRenderPlan != preview
            || cachedRenderEntity != entity
            || cachedRenderSupportEntity != supportEntity
            || !sameSupportPos
            || cachedRenderFace != face
            || !sameSupportPosition) {
            cachedRenderPlan = preview;
            cachedRenderEntity = entity;
            cachedRenderSupportEntity = supportEntity;
            cachedRenderSupportPos = supportPos;
            cachedRenderFace = face;
            cachedRenderSupportPosition = supportEntity == null ? Vec3.ZERO : supportEntity.position();
            cachedRenderPath = buildPreviewRenderPath(entity, preview, supportEntity, supportPos, face);
        }
        return cachedRenderPath;
    }

    private static List<Vec3> buildPreviewRenderPath(
        Entity entity,
        AdhesivePathPlanner.Plan preview,
        @Nullable Entity supportEntity,
        @Nullable BlockPos supportPos,
        Direction face
    ) {
        PlasticEntityOrientation startOrientation = entity instanceof AbstractPlasticEntity plastic
            ? plastic.getOrientation()
            : null;
        return buildRenderPath(
            entity,
            preview.points(),
            startOrientation,
            preview.targetOrientation(),
            preview.sourceFace(),
            supportEntity,
            supportPos,
            face
        );
    }

    private static List<Vec3> transitRenderPath(
        @Nullable Entity entity,
        AdhesiveTransit transit,
        @Nullable Entity supportEntity
    ) {
        List<Vec3> adjustedPath = offsetPath(transit.path(), transit.supportMovement(supportEntity));
        return buildRenderPath(
            entity,
            adjustedPath,
            transit.plastic() ? PlasticEntityOrientation.unpack(transit.startOrientation()) : null,
            transit.plastic() ? PlasticEntityOrientation.unpack(transit.targetOrientation()) : null,
            transit.plastic() ? transit.sourceFace() : null,
            supportEntity,
            transit.hasEntityTarget() ? null : transit.supportPos(),
            transit.attachmentFace()
        );
    }

    private static List<Vec3> buildRenderPath(
        @Nullable Entity entity,
        List<Vec3> path,
        @Nullable PlasticEntityOrientation startOrientation,
        @Nullable PlasticEntityOrientation targetOrientation,
        @Nullable Direction sourceFace,
        @Nullable Entity supportEntity,
        @Nullable BlockPos supportPos,
        Direction face
    ) {
        List<Vec3> renderedPath = path;
        if (entity instanceof AbstractPlasticEntity plastic
            && startOrientation != null
            && targetOrientation != null
            && sourceFace != null) {
            renderedPath = plasticFacePath(
                plastic,
                path,
                startOrientation,
                targetOrientation,
                sourceFace
            );
        }
        return appendEndpoint(renderedPath, supportEntity, supportPos, face);
    }

    private static List<Vec3> plasticFacePath(
        AbstractPlasticEntity entity,
        List<Vec3> entityPath,
        PlasticEntityOrientation startOrientation,
        PlasticEntityOrientation targetOrientation,
        Direction selectedFace
    ) {
        if (entityPath.size() < 2) return entityPath;
        double length = pathLength(entityPath);
        int samples = Math.clamp((int) Math.ceil(length * 4.0D), 12, 96);
        List<Vec3> result = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            double rawProgress = (double) index / samples;
            double movementProgress = smoothstep(rawProgress);
            double rotationProgress = smoothstep(Math.clamp((rawProgress - 0.72D) / 0.28D, 0.0D, 1.0D));
            Vec3 position = positionAt(entityPath, movementProgress);
            Vec3 faceOffset = PlasticEntityRenderTransforms.faceAlignmentOffset(
                entity,
                selectedFace,
                startOrientation,
                targetOrientation,
                (float) rotationProgress
            );
            result.add(position.add(faceOffset));
        }
        return result;
    }

    private static List<Vec3> appendEndpoint(
        List<Vec3> path,
        @Nullable Entity supportEntity,
        @Nullable BlockPos supportPos,
        Direction face
    ) {
        if (supportEntity != null) {
            return appendSurfaceEndpoint(path, AdhesiveFaces.worldFaceAlignmentPoint(supportEntity, face));
        }
        return supportPos == null ? path : appendSurfaceEndpoint(path, supportPos, face);
    }

    private static List<Vec3> appendSurfaceEndpoint(List<Vec3> path, AABB box, Direction face) {
        return appendSurfaceEndpoint(path, boxFaceCenter(box, face));
    }

    private static List<Vec3> appendSurfaceEndpoint(List<Vec3> path, Vec3 surface) {
        if (path.isEmpty()) return path;
        if (path.getLast().distanceToSqr(surface) <= 0.0025D) return path;
        List<Vec3> result = new ArrayList<>(path.size() + 1);
        result.addAll(path);
        result.add(surface);
        return result;
    }

    private static List<Vec3> offsetPath(List<Vec3> path, Vec3 offset) {
        if (offset.equals(Vec3.ZERO)) return path;
        List<Vec3> result = new ArrayList<>(path.size());
        for (Vec3 point : path) result.add(point.add(offset));
        return result;
    }

    private static double pathLength(List<Vec3> path) {
        double length = 0.0D;
        for (int index = 1; index < path.size(); index++) {
            length += path.get(index - 1).distanceTo(path.get(index));
        }
        return length;
    }

    private static Vec3 positionAt(List<Vec3> path, double progress) {
        if (progress <= 0.0D) return path.getFirst();
        if (progress >= 1.0D) return path.getLast();
        double length = pathLength(path);
        if (length <= 1.0E-6D) return path.getLast();
        double target = length * progress;
        double traversed = 0.0D;
        for (int index = 1; index < path.size(); index++) {
            Vec3 from = path.get(index - 1);
            Vec3 to = path.get(index);
            double segment = from.distanceTo(to);
            if (traversed + segment >= target) {
                double local = segment <= 1.0E-6D ? 1.0D : (target - traversed) / segment;
                return from.lerp(to, local);
            }
            traversed += segment;
        }
        return path.getLast();
    }

    private static double smoothstep(double progress) {
        return progress * progress * (3.0D - 2.0D * progress);
    }

    private static void renderAnimatedBox(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        AABB box,
        float progress
    ) {
        Vec3[] corners = {
            new Vec3(box.minX, box.minY, box.minZ),
            new Vec3(box.maxX, box.minY, box.minZ),
            new Vec3(box.maxX, box.minY, box.maxZ),
            new Vec3(box.minX, box.minY, box.maxZ),
            new Vec3(box.minX, box.maxY, box.minZ),
            new Vec3(box.maxX, box.maxY, box.minZ),
            new Vec3(box.maxX, box.maxY, box.maxZ),
            new Vec3(box.minX, box.maxY, box.maxZ)
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        float eased = progress * progress * (3.0F - 2.0F * progress);
        for (int[] edge : edges) {
            Vec3 from = corners[edge[0]];
            Vec3 to = corners[edge[1]];
            Vec3 center = from.lerp(to, 0.5D);
            Vec3 animatedFrom = center.lerp(from, eased);
            Vec3 animatedTo = center.lerp(to, eased);
            ThickLineRenderer.render(
                pose,
                buffers,
                List.of(animatedFrom, animatedTo),
                WHITE,
                ThickLineRenderer.SELECTION_WIDTH
            );
        }
    }

    private static void renderPath(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        List<Vec3> points,
        int color
    ) {
        if (points.size() < 2) return;
        if (points != sampledPreviewInput) {
            sampledPreviewInput = points;
            sampledPreviewPath = resamplePath(points);
        }
        ThickLineRenderer.render(pose, buffers, sampledPreviewPath, color, PATH_TUBE_WIDTH);
    }

    private static void addQuad(
        PoseStack pose,
        VertexConsumer consumer,
        Vec3 first,
        Vec3 second,
        Vec3 third,
        Vec3 fourth,
        int color
    ) {
        consumer.addVertex(pose.last().pose(), (float) first.x, (float) first.y, (float) first.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) second.x, (float) second.y, (float) second.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) third.x, (float) third.y, (float) third.z).setColor(color);
        consumer.addVertex(pose.last().pose(), (float) fourth.x, (float) fourth.y, (float) fourth.z).setColor(color);
    }

    private static List<Vec3> resamplePath(List<Vec3> path) {
        if (path.size() < 2) return path;
        double length = pathLength(path);
        int samples = Math.clamp((int) Math.ceil(length * 12.0D), 12, 192);
        List<Vec3> result = new ArrayList<>(samples + 1);
        for (int index = 0; index <= samples; index++) {
            result.add(positionAt(path, (double) index / samples));
        }
        return result;
    }

    private static void renderPreviewEndpoint(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        Entity selected,
        AdhesivePathPlanner.Plan preview,
        boolean previewClear,
        @Nullable Entity supportEntity,
        @Nullable BlockPos supportPos,
        Direction supportFace
    ) {
        AABB endpointBox = supportEntity != null
            ? supportEntity.getBoundingBox().inflate(0.006D)
            : selected instanceof FallingBlockEntity
                ? new AABB(preview.occupiedPos()).inflate(0.006D)
                : selected.getBoundingBox().move(
                    preview.targetPosition().subtract(selected.position())
                ).inflate(0.006D);
        renderAnimatedBox(pose, buffers, endpointBox, 1.0F);
        if (supportEntity != null) {
            renderFaceGrid(pose, buffers, supportEntity.getBoundingBox(), supportFace);
        } else if (supportPos != null) {
            renderFaceGrid(pose, buffers, new AABB(supportPos), supportFace);
        }
        if (previewClear) renderPreviewGroup(pose, buffers, previewProjection(selected, preview));
    }

    private static AdhesiveGroupTransform.Projection previewProjection(
        Entity root,
        AdhesivePathPlanner.Plan preview
    ) {
        if (cachedPreviewProjection == null
            || cachedPreviewProjectionPlan != preview
            || cachedPreviewProjectionRoot != root) {
            cachedPreviewProjectionPlan = preview;
            cachedPreviewProjectionRoot = root;
            cachedPreviewProjection = AdhesiveGroupTransform.projectTarget(root, preview);
        }
        return cachedPreviewProjection;
    }

    private static void renderTransitEndpoint(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        Entity movingEntity,
        AdhesiveTransit transit,
        @Nullable Entity supportEntity
    ) {
        AABB endpointBox = supportEntity != null
            ? supportEntity.getBoundingBox().inflate(0.006D)
            : movingEntity instanceof FallingBlockEntity
                ? new AABB(transit.supportPos().relative(transit.attachmentFace())).inflate(0.006D)
                : movingEntity.getBoundingBox().move(
                    transit.targetPosition(supportEntity).subtract(movingEntity.position())
                ).inflate(0.006D);
        renderAnimatedBox(pose, buffers, endpointBox, 1.0F);
        if (supportEntity != null) {
            renderFaceGrid(pose, buffers, supportEntity.getBoundingBox(), transit.attachmentFace());
        } else {
            renderFaceGrid(pose, buffers, new AABB(transit.supportPos()), transit.attachmentFace());
        }
        PlasticEntityOrientation orientation = transit.plastic()
            ? PlasticEntityOrientation.unpack(transit.targetOrientation())
            : null;
        renderPreviewGroup(
            pose,
            buffers,
            AdhesiveGroupTransform.project(movingEntity, transit.targetPosition(supportEntity), orientation)
        );
    }

    private static void renderPreviewGroup(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        AdhesiveGroupTransform.Projection projection
    ) {
        if (!projection.valid()) return;
        for (AdhesiveGroupTransform.Member member : projection.members()) {
            Entity entity = member.entity();
            if (entity instanceof FallingBlockEntity fallingBlock) {
                renderTargetGhost(pose, buffers, fallingBlock, member.position(), member.orientation());
            } else {
                renderAnimatedBox(pose, buffers, member.collisionBounds().inflate(0.006D), 1.0F);
            }
        }
    }

    private static void renderTargetGhost(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        FallingBlockEntity entity,
        Vec3 targetPosition,
        @Nullable PlasticEntityOrientation orientation
    ) {
        pose.pushPose();
        pose.translate(targetPosition.x, targetPosition.y, targetPosition.z);
        if (entity instanceof AbstractPlasticEntity plastic && orientation != null) {
            PlasticEntityRenderTransforms.applyPreview(pose, plastic, orientation);
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                plastic,
                Minecraft.getInstance().getBlockRenderer(),
                pose,
                buffers
            );
        } else {
            pose.translate(-0.5D, 0.0D, -0.5D);
            PlasticEntityRenderHelper.renderFallingPreviewModel(
                entity,
                Minecraft.getInstance().getBlockRenderer(),
                pose,
                buffers
            );
        }
        pose.popPose();
    }

    private static void renderFaceGrid(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        AABB box,
        Direction face
    ) {
        renderFaceGrid(pose, buffers, box, face, GRID_OUTWARD_OFFSET);
    }

    private static void renderFaceGrid(
        PoseStack pose,
        MultiBufferSource.BufferSource buffers,
        AABB box,
        Direction face,
        double normalOffset
    ) {
        RenderType renderType = RenderType.debugQuads();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        for (int first = 0; first < 4; first++) {
            for (int second = 0; second < 4; second++) {
                if (((first + second) & 1) != 0) continue;
                addGridCell(pose, consumer, box, face, normalOffset, first, second);
            }
        }
        buffers.endBatch(renderType);
    }

    private static void addGridCell(
        PoseStack pose,
        VertexConsumer consumer,
        AABB box,
        Direction face,
        double normalOffset,
        int first,
        int second
    ) {
        double firstMin;
        double firstMax;
        double secondMin;
        double secondMax;
        Vec3 a;
        Vec3 b;
        Vec3 c;
        Vec3 d;
        switch (face.getAxis()) {
            case X -> {
                firstMin = box.minY + box.getYsize() * first / 4.0D;
                firstMax = box.minY + box.getYsize() * (first + 1) / 4.0D;
                secondMin = box.minZ + box.getZsize() * second / 4.0D;
                secondMax = box.minZ + box.getZsize() * (second + 1) / 4.0D;
                double x = (face == Direction.EAST ? box.maxX : box.minX)
                    + face.getStepX() * normalOffset;
                a = new Vec3(x, firstMin, secondMin);
                b = new Vec3(x, firstMax, secondMin);
                c = new Vec3(x, firstMax, secondMax);
                d = new Vec3(x, firstMin, secondMax);
            }
            case Y -> {
                firstMin = box.minX + box.getXsize() * first / 4.0D;
                firstMax = box.minX + box.getXsize() * (first + 1) / 4.0D;
                secondMin = box.minZ + box.getZsize() * second / 4.0D;
                secondMax = box.minZ + box.getZsize() * (second + 1) / 4.0D;
                double y = (face == Direction.UP ? box.maxY : box.minY)
                    + face.getStepY() * normalOffset;
                a = new Vec3(firstMin, y, secondMin);
                b = new Vec3(firstMax, y, secondMin);
                c = new Vec3(firstMax, y, secondMax);
                d = new Vec3(firstMin, y, secondMax);
            }
            case Z -> {
                firstMin = box.minX + box.getXsize() * first / 4.0D;
                firstMax = box.minX + box.getXsize() * (first + 1) / 4.0D;
                secondMin = box.minY + box.getYsize() * second / 4.0D;
                secondMax = box.minY + box.getYsize() * (second + 1) / 4.0D;
                double z = (face == Direction.SOUTH ? box.maxZ : box.minZ)
                    + face.getStepZ() * normalOffset;
                a = new Vec3(firstMin, secondMin, z);
                b = new Vec3(firstMax, secondMin, z);
                c = new Vec3(firstMax, secondMax, z);
                d = new Vec3(firstMin, secondMax, z);
            }
            default -> throw new IllegalStateException("Unexpected direction axis");
        }
        addQuad(pose, consumer, a, b, c, d, GRID_WHITE);
    }

    private static float selectionBoxProgress(boolean active, long now) {
        if (animatedBox == null) return 0.0F;
        if (active) {
            return Math.clamp((now - boxEnterStarted) / (float) BOX_ENTER_MILLIS, 0.0F, 1.0F);
        }
        if (boxExitStarted < 0L) boxExitStarted = now;
        return 1.0F - Math.clamp((now - boxExitStarted) / (float) BOX_EXIT_MILLIS, 0.0F, 1.0F);
    }

    private static void beginSelectionAnimation(Entity target) {
        animatedUuid = target.getUUID();
        animatedEntity = target;
        animatedBox = target.getBoundingBox().inflate(0.006D);
        boxEnterStarted = Util.getMillis();
        boxExitStarted = -1L;
        clearPreviewTask();
    }

    private static void startExitAnimation() {
        if (animatedBox != null && boxExitStarted < 0L) boxExitStarted = Util.getMillis();
    }

    private static void resetAnimation() {
        animatedUuid = null;
        animatedEntity = null;
        animatedBox = null;
        boxExitStarted = -1L;
        clearPreviewTask();
    }

    private static void cancel(InputEvent.InteractionKeyMappingTriggered event) {
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    private static @Nullable Entity getSelectedEntity(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) return null;
        UUID selectedUuid = AdhesiveSelectionManager.getSelectedUuid(player);
        int selectedId = AdhesiveSelectionManager.getSelectedEntityId(player);
        if (selectedUuid == null || selectedId < 0) return null;
        Entity selected = level.getEntity(selectedId);
        return selected != null && selectedUuid.equals(selected.getUUID()) && selected.isAlive()
            ? selected
            : null;
    }

    private static Vec3 faceCenter(BlockPos pos, Direction face) {
        return Vec3.atCenterOf(pos).add(
            face.getStepX() * 0.501D,
            face.getStepY() * 0.501D,
            face.getStepZ() * 0.501D
        );
    }

    private static Vec3 boxFaceCenter(AABB box, Direction face) {
        Vec3 center = box.getCenter();
        return switch (face.getAxis()) {
            case X -> new Vec3(face == Direction.EAST ? box.maxX : box.minX, center.y, center.z);
            case Y -> new Vec3(center.x, face == Direction.UP ? box.maxY : box.minY, center.z);
            case Z -> new Vec3(center.x, center.y, face == Direction.SOUTH ? box.maxZ : box.minZ);
        };
    }

    private static @Nullable Entity resolveTransitSupport(ClientLevel level, AdhesiveTransit transit) {
        if (!transit.hasEntityTarget()) return null;
        Entity support = level.getEntity(transit.supportEntityId());
        return support != null
            && transit.supportEntityUuid().filter(support.getUUID()::equals).isPresent()
            ? support
            : null;
    }

    private record PreviewKey(
        UUID entityUuid,
        @Nullable UUID supportEntityUuid,
        int supportEntityId,
        BlockPos supportPos,
        Direction attachmentFace,
        Direction selectedFace,
        Direction playerDirection
    ) {
        private PreviewKey {
            supportPos = supportPos.immutable();
        }
    }

    private record PendingBlockUse(
        InteractionHand hand,
        BlockHitResult hit,
        long startedAtMillis,
        boolean patchPlaced
    ) {
    }
}
