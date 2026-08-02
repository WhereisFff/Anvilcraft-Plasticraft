package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.network.AdhesivePreviewPathPacket;
import dev.anvilcraft.plasticraft.network.AdhesivePreviewRejectedPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 管理服务端权威的树脂牵引预览，使预览与确认共用同一条异步搜索路径。 */
public final class AdhesivePreviewService {
    private static final double POSITION_EPSILON_SQR = 1.0E-6D;
    private static final Map<UUID, PendingPreview> PREVIEWS = new LinkedHashMap<>();

    private AdhesivePreviewService() {
    }

    public static void requestBlock(
        Player player,
        int requestId,
        int selectedEntityId,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PendingPreview current = PREVIEWS.get(player.getUUID());
        if (current != null && current.confirmation != null) {
            reject(serverPlayer, requestId);
            return;
        }
        PendingPreview replacement = createBlock(
            serverPlayer,
            requestId,
            selectedEntityId,
            supportPos,
            attachmentFace,
            true
        );
        replace(serverPlayer, replacement);
        if (replacement == null) reject(serverPlayer, requestId);
    }

    public static void requestEntity(
        Player player,
        int requestId,
        int selectedEntityId,
        int targetEntityId,
        Direction targetFace
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        PendingPreview current = PREVIEWS.get(player.getUUID());
        if (current != null && current.confirmation != null) {
            reject(serverPlayer, requestId);
            return;
        }
        PendingPreview replacement = createEntity(
            serverPlayer,
            requestId,
            selectedEntityId,
            targetEntityId,
            targetFace,
            true
        );
        replace(serverPlayer, replacement);
        if (replacement == null) reject(serverPlayer, requestId);
    }

    public static void cancel(Player player) {
        PendingPreview current = PREVIEWS.get(player.getUUID());
        if (current != null && current.confirmation != null) return;
        clear(player);
    }

    public static boolean confirmBlock(
        Player player,
        InteractionHand hand,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null) return false;

        PendingPreview pending = PREVIEWS.get(player.getUUID());
        if (pending == null || !pending.matchesBlock(selected, supportPos, attachmentFace)) {
            pending = createBlock(
                serverPlayer,
                0,
                selected.getId(),
                supportPos,
                attachmentFace,
                false
            );
            replace(serverPlayer, pending);
        }
        if (pending == null) return false;
        pending.confirmation = new Confirmation(hand);
        return prepareConfirmation(pending);
    }

    public static boolean confirmEntity(
        Player player,
        InteractionHand hand,
        Entity target,
        Direction targetFace
    ) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null) return false;

        PendingPreview pending = PREVIEWS.get(player.getUUID());
        if (pending == null || !pending.matchesEntity(selected, target, targetFace)) {
            pending = createEntity(
                serverPlayer,
                0,
                selected.getId(),
                target.getId(),
                targetFace,
                false
            );
            replace(serverPlayer, pending);
        }
        if (pending == null) return false;
        pending.confirmation = new Confirmation(hand);
        return prepareConfirmation(pending);
    }

    public static void tick(MinecraftServer server) {
        if (PREVIEWS.isEmpty()) return;
        for (PendingPreview pending : new ArrayList<>(PREVIEWS.values())) {
            if (pending == null) continue;
            if (pending.player.getServer() != server || !pending.isUsable()) {
                if (pending.player.getServer() == server) reject(pending);
                remove(pending);
                continue;
            }
            if (pending.result == null) {
                pending.task.advance();
                if (pending.task.isComplete()) finish(pending);
            }
        }
    }

    public static void clear(Player player) {
        PendingPreview pending = PREVIEWS.remove(player.getUUID());
        if (pending != null) pending.task.cancel();
    }

    private static boolean prepareConfirmation(PendingPreview pending) {
        if (!pending.contextUnchanged()) {
            PendingPreview replacement = pending.recreate();
            if (replacement == null) {
                reject(pending);
                remove(pending);
                return false;
            }
            replacement.confirmation = pending.confirmation;
            replace(pending.player, replacement);
            return true;
        }
        return pending.result == null || executeConfirmation(pending);
    }

    private static void finish(PendingPreview pending) {
        AdhesivePathPlanner.Plan result = pending.task.result();
        if (PREVIEWS.get(pending.player.getUUID()) != pending) return;
        if (result == null) {
            reject(pending);
            remove(pending);
            return;
        }
        if (pending.confirmation != null && !pending.contextUnchanged()) {
            PendingPreview replacement = pending.recreate();
            if (replacement == null) {
                reject(pending);
                remove(pending);
                return;
            }
            replacement.confirmation = pending.confirmation;
            replace(pending.player, replacement);
            return;
        }

        Entity root = pending.resolveMovingEntity();
        boolean groupClear = root != null
            && result.valid()
            && AdhesiveGroupTransform.isPlanClear(pending.level, root, result, pending.player);
        pending.result = result;
        if (pending.sendResult) {
            PacketDistributor.sendToPlayer(
                pending.player,
                new AdhesivePreviewPathPacket(pending.requestId, groupClear, result)
            );
        }
        if (pending.confirmation != null) executeConfirmation(pending);
    }

    private static boolean executeConfirmation(PendingPreview pending) {
        AdhesivePathPlanner.Plan result = pending.result;
        Confirmation confirmation = pending.confirmation;
        if (result == null || confirmation == null) return false;
        pending.confirmation = null;
        boolean bonded;
        if (pending.targetType == TargetType.BLOCK) {
            bonded = AdhesiveBondingService.bondSelectedWithPlan(
                pending.player,
                confirmation.hand,
                pending.supportPos,
                pending.targetFace,
                result
            );
        } else {
            Entity target = pending.resolveTargetEntity();
            bonded = target != null && AdhesiveBondingService.bondSelectedToEntityWithPlan(
                pending.player,
                confirmation.hand,
                target,
                pending.targetFace,
                result
            );
        }
        remove(pending);
        return bonded;
    }

    private static void replace(ServerPlayer player, @Nullable PendingPreview replacement) {
        PendingPreview previous = PREVIEWS.remove(player.getUUID());
        if (previous != null) previous.task.cancel();
        if (replacement != null) PREVIEWS.put(player.getUUID(), replacement);
    }

    private static void remove(PendingPreview pending) {
        if (PREVIEWS.remove(pending.player.getUUID(), pending)) pending.task.cancel();
    }

    private static void reject(PendingPreview pending) {
        if (pending.sendResult) reject(pending.player, pending.requestId);
    }

    private static void reject(ServerPlayer player, int requestId) {
        if (!player.isRemoved()) {
            PacketDistributor.sendToPlayer(player, new AdhesivePreviewRejectedPacket(requestId));
        }
    }

    private static @Nullable PendingPreview createBlock(
        ServerPlayer player,
        int requestId,
        int selectedEntityId,
        BlockPos supportPos,
        Direction attachmentFace,
        boolean sendResult
    ) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null
            || selected.getId() != selectedEntityId
            || selected.hasData(PlasticraftAttachments.ENTITY_ADHESION)
            || selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
            || !AdhesiveBondingService.canUseSupport(player, level, supportPos, attachmentFace)) {
            return null;
        }
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        AdhesivePathPlanner.PreviewTask task = AdhesivePathPlanner.beginPreview(
            level,
            selected,
            player,
            supportPos,
            attachmentFace,
            selectedFace
        );
        return new PendingPreview(
            player,
            level,
            requestId,
            sendResult,
            TargetType.BLOCK,
            selected.getUUID(),
            selected.getId(),
            null,
            -1,
            supportPos.immutable(),
            attachmentFace,
            selectedFace,
            player.getDirection(),
            selected.getUUID(),
            null,
            selected.position(),
            Vec3.ZERO,
            task
        );
    }

    private static @Nullable PendingPreview createEntity(
        ServerPlayer player,
        int requestId,
        int selectedEntityId,
        int targetEntityId,
        Direction targetFace,
        boolean sendResult
    ) {
        if (!(player.level() instanceof ServerLevel level)) return null;
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        Entity target = level.getEntity(targetEntityId);
        if (selected == null
            || selected.getId() != selectedEntityId
            || target == null
            || target == selected
            || !target.isAlive()
            || target.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
            || !player.canInteractWithEntity(target, 0.0D)) {
            return null;
        }

        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        boolean reverse = selected.hasData(PlasticraftAttachments.ENTITY_ADHESION);
        if (reverse && target.hasData(PlasticraftAttachments.ENTITY_ADHESION)) return null;
        Entity movingEntity = reverse ? target : selected;
        Entity anchorEntity = reverse ? selected : target;
        Direction anchorFace = reverse ? AdhesiveFaces.worldFace(selected, selectedFace) : targetFace;
        Direction movingFace = reverse ? AdhesiveFaces.storedFace(target, targetFace) : selectedFace;
        AdhesivePathPlanner.PreviewTask task = AdhesivePathPlanner.beginPreviewToEntity(
            level,
            movingEntity,
            player,
            anchorEntity,
            anchorFace,
            movingFace
        );
        return new PendingPreview(
            player,
            level,
            requestId,
            sendResult,
            TargetType.ENTITY,
            selected.getUUID(),
            selected.getId(),
            target.getUUID(),
            target.getId(),
            BlockPos.ZERO,
            targetFace,
            selectedFace,
            player.getDirection(),
            movingEntity.getUUID(),
            anchorEntity.getUUID(),
            movingEntity.position(),
            anchorEntity.position(),
            task
        );
    }

    private enum TargetType {
        BLOCK,
        ENTITY
    }

    private record Confirmation(InteractionHand hand) {
    }

    private static final class PendingPreview {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final int requestId;
        private final boolean sendResult;
        private final TargetType targetType;
        private final UUID selectedUuid;
        private final int selectedEntityId;
        private final @Nullable UUID targetUuid;
        private final int targetEntityId;
        private final BlockPos supportPos;
        private final Direction targetFace;
        private final Direction selectedFace;
        private final Direction playerDirection;
        private final UUID movingUuid;
        private final @Nullable UUID anchorUuid;
        private final Vec3 movingStart;
        private final Vec3 anchorStart;
        private final AdhesivePathPlanner.PreviewTask task;
        private @Nullable AdhesivePathPlanner.Plan result;
        private @Nullable Confirmation confirmation;

        private PendingPreview(
            ServerPlayer player,
            ServerLevel level,
            int requestId,
            boolean sendResult,
            TargetType targetType,
            UUID selectedUuid,
            int selectedEntityId,
            @Nullable UUID targetUuid,
            int targetEntityId,
            BlockPos supportPos,
            Direction targetFace,
            Direction selectedFace,
            Direction playerDirection,
            UUID movingUuid,
            @Nullable UUID anchorUuid,
            Vec3 movingStart,
            Vec3 anchorStart,
            AdhesivePathPlanner.PreviewTask task
        ) {
            this.player = player;
            this.level = level;
            this.requestId = requestId;
            this.sendResult = sendResult;
            this.targetType = targetType;
            this.selectedUuid = selectedUuid;
            this.selectedEntityId = selectedEntityId;
            this.targetUuid = targetUuid;
            this.targetEntityId = targetEntityId;
            this.supportPos = supportPos;
            this.targetFace = targetFace;
            this.selectedFace = selectedFace;
            this.playerDirection = playerDirection;
            this.movingUuid = movingUuid;
            this.anchorUuid = anchorUuid;
            this.movingStart = movingStart;
            this.anchorStart = anchorStart;
            this.task = task;
        }

        private boolean isUsable() {
            Entity selected = AdhesiveSelectionManager.resolveServerSelection(this.player);
            Entity moving = this.resolveMovingEntity();
            Entity anchor = this.resolveAnchorEntity();
            boolean holdingBucket = this.player.getMainHandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
                || this.player.getOffhandItem().is(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get());
            return !this.player.isRemoved()
                && this.player.isAlive()
                && this.player.level() == this.level
                && holdingBucket
                && selected != null
                && selected.getUUID().equals(this.selectedUuid)
                && selected.getId() == this.selectedEntityId
                && !selected.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && moving != null
                && moving.isAlive()
                && !moving.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT)
                && (this.anchorUuid == null
                    || anchor != null
                    && anchor.isAlive()
                    && !anchor.hasData(PlasticraftAttachments.ADHESIVE_TRANSIT));
        }

        private boolean contextUnchanged() {
            Entity moving = this.resolveMovingEntity();
            Entity anchor = this.resolveAnchorEntity();
            return moving != null
                && moving.position().distanceToSqr(this.movingStart) <= POSITION_EPSILON_SQR
                && (anchor == null || anchor.position().distanceToSqr(this.anchorStart) <= POSITION_EPSILON_SQR)
                && this.player.getDirection() == this.playerDirection
                && AdhesiveSelectionManager.getSelectedFace(this.player) == this.selectedFace;
        }

        private boolean matchesBlock(Entity selected, BlockPos supportPos, Direction attachmentFace) {
            return this.targetType == TargetType.BLOCK
                && selected.getUUID().equals(this.selectedUuid)
                && this.supportPos.equals(supportPos)
                && this.targetFace == attachmentFace;
        }

        private boolean matchesEntity(Entity selected, Entity target, Direction targetFace) {
            return this.targetType == TargetType.ENTITY
                && selected.getUUID().equals(this.selectedUuid)
                && this.targetUuid != null
                && target.getUUID().equals(this.targetUuid)
                && this.targetFace == targetFace;
        }

        private @Nullable Entity resolveMovingEntity() {
            return this.level.getEntity(this.movingUuid);
        }

        private @Nullable Entity resolveAnchorEntity() {
            return this.anchorUuid == null ? null : this.level.getEntity(this.anchorUuid);
        }

        private @Nullable Entity resolveTargetEntity() {
            Entity target = this.level.getEntity(this.targetEntityId);
            return target != null && this.targetUuid != null && target.getUUID().equals(this.targetUuid)
                ? target
                : null;
        }

        private @Nullable PendingPreview recreate() {
            return this.targetType == TargetType.BLOCK
                ? createBlock(
                    this.player,
                    this.requestId,
                    this.selectedEntityId,
                    this.supportPos,
                    this.targetFace,
                    this.sendResult
                )
                : createEntity(
                    this.player,
                    this.requestId,
                    this.selectedEntityId,
                    this.targetEntityId,
                    this.targetFace,
                    this.sendResult
                );
        }
    }
}
