package dev.anvilcraft.plasticraft.entity.adhesive;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.block.BlockAdhesionState;
import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.entity.FallingGiantAnvilEntity;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** 高粘性树脂桶选择、固定和释放实体的服务端逻辑。 */
public final class AdhesiveBondingService {
    private static final String TAG_BLOCKIFICATION_HANDOFF =
        "anvilcraftplasticraft:adhesive_blockification_handoff";
    private static final int MIN_TRANSIT_TICKS = 8;
    private static final int MAX_TRANSIT_TICKS = 32;
    private static final double MIN_TRANSIT_DISTANCE = 2.0D;
    private static final double TRANSIT_COLLISION_SAMPLE_STEP = 0.05D;
    private static final int TRANSIT_COLLISION_REFINEMENT_STEPS = 8;
    private static final double TRANSIT_PATH_ALIGNMENT_DISTANCE_SQR = 0.25D;
    private static final double TRANSIT_PATH_PROGRESS_EPSILON = 1.0E-6D;
    private static final double ELASTIC_SPRING = 0.42D;
    private static final double ELASTIC_DAMPING = 0.76D;
    private static final double ELASTIC_MAX_SPEED = 2.5D;
    public static final double MIN_ELASTIC_KNOCKBACK = 0.5D;
    private static final Map<Entity, TransitValidation> TRANSIT_VALIDATIONS = new WeakHashMap<>();

    private AdhesiveBondingService() {
    }

    public static boolean select(Player player, InteractionHand hand, Entity target) {
        if (!canSelect(player, hand, target)) return false;
        AdhesiveSelectionManager.select(player, target);
        return true;
    }

    public static boolean select(
        Player player,
        InteractionHand hand,
        Entity target,
        Direction clickedWorldFace
    ) {
        if (!canSelect(player, hand, target)) return false;
        AdhesiveSelectionManager.select(player, target, clickedWorldFace);
        return true;
    }

    private static boolean canSelect(Player player, InteractionHand hand, Entity target) {
        if (player.isShiftKeyDown()
            || !player.getItemInHand(hand).is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!target.isAlive()
            || target == player
            || target.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
            return false;
        }
        return player.canInteractWithEntity(target, 0.0D);
    }

    public static boolean bondSelected(
        Player player,
        InteractionHand hand,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        ItemStack bucket = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !bucket.is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        if (reclaimClickedPatch(player, level, supportPos, attachmentFace)) {
            AdhesiveSelectionManager.clear(player);
            return true;
        }
        if (!canUseSupport(player, level, supportPos, attachmentFace)) return false;

        Entity target = AdhesiveSelectionManager.resolveServerSelection(player);
        if (target == null) return false;
        if (target.hasData(ModAttachments.ENTITY_ADHESION)
            || target.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
            return false;
        }
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.plan(
            level,
            target,
            player,
            supportPos,
            attachmentFace,
            selectedFace
        );
        if (!plan.valid()) {
            if (AdhesivePathPlanner.isOutOfRange(plan.directDistance())) {
                AdhesiveSelectionManager.clear(player);
            }
            return false;
        }
        return applyBlockPlan(player, hand, level, target, supportPos, attachmentFace, plan);
    }

    static boolean bondSelectedWithPlan(
        Player player,
        InteractionHand hand,
        BlockPos supportPos,
        Direction attachmentFace,
        AdhesivePathPlanner.Plan plan
    ) {
        ItemStack bucket = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !bucket.is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;
        if (reclaimClickedPatch(player, level, supportPos, attachmentFace)) {
            AdhesiveSelectionManager.clear(player);
            return true;
        }
        if (!canUseSupport(player, level, supportPos, attachmentFace)) return false;
        Entity target = AdhesiveSelectionManager.resolveServerSelection(player);
        if (target == null
            || target.hasData(ModAttachments.ENTITY_ADHESION)
            || target.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
            return false;
        }
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        if (!matchesPlanStart(plan, target)
            || !plan.valid()
            || plan.sourceFace() != (target instanceof AbstractPlasticEntity ? selectedFace : attachmentFace.getOpposite())
            || !plan.occupiedPos().equals(supportPos.relative(attachmentFace))) {
            return false;
        }
        return applyBlockPlan(player, hand, level, target, supportPos, attachmentFace, plan);
    }

    private static boolean applyBlockPlan(
        Player player,
        InteractionHand hand,
        ServerLevel level,
        Entity target,
        BlockPos supportPos,
        Direction attachmentFace,
        AdhesivePathPlanner.Plan plan
    ) {
        if (!plan.valid() || !AdhesiveGroupTransform.isPlanClear(level, target, plan, player)) return false;
        EntityBondManager.prepareAlignedLeader(level, target);
        boolean plastic = target instanceof AbstractPlasticEntity;
        byte startOrientation = plastic
            ? ((AbstractPlasticEntity) target).getOrientation().pack()
            : PlasticEntityOrientation.DEFAULT.pack();
        byte targetOrientation = plan.targetOrientation() == null
            ? PlasticEntityOrientation.DEFAULT.pack()
            : plan.targetOrientation().pack();
        AdhesiveTransit transit = new AdhesiveTransit(
            supportPos,
            attachmentFace,
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(supportPos).getBlock()),
            plan.sourceFace(),
            Optional.empty(),
            -1,
            Vec3.ZERO,
            plan.points(),
            level.getGameTime(),
            transitDuration(plan),
            target.isNoGravity(),
            plastic,
            startOrientation,
            targetOrientation
        );
        target.setData(ModAttachments.ADHESIVE_TRANSIT, transit);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0.0F;
        target.hasImpulse = true;
        target.hurtMarked = true;
        AdhesiveSelectionManager.clear(player);
        consumeBucket(player, hand);
        return true;
    }

    public static boolean placePatch(
        Player player,
        InteractionHand hand,
        BlockPos supportPos,
        Direction face
    ) {
        ItemStack bucket = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !bucket.is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!(player.level() instanceof ServerLevel level)
            || AdhesiveSelectionManager.hasSelection(player)) {
            return false;
        }
        if (reclaimClickedPatch(player, level, supportPos, face)) return true;
        if (!canUseSupport(player, level, supportPos, face)) return false;
        BlockPos patchSpace = supportPos.relative(face);
        if (!level.getBlockState(patchSpace).canBeReplaced()
            || !BondedFallingBlocks.putPatch(level, supportPos, face)) {
            return false;
        }
        consumeBucket(player, hand);
        level.playSound(
            null,
            supportPos,
            SoundEvents.HONEY_BLOCK_PLACE,
            SoundSource.PLAYERS,
            1.0F,
            0.9F + level.random.nextFloat() * 0.2F
        );
        return true;
    }

    public static boolean hasAdhesiveOnFace(Entity target, Direction clickedWorldFace) {
        EntityAdhesion adhesion = target.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion != null && adhesion.attachmentFace().getOpposite() == clickedWorldFace) return true;
        return EntityBondManager.isFaceOccupied(target, AdhesiveFaces.storedFace(target, clickedWorldFace));
    }

    public static boolean reclaimEntity(
        Player player,
        InteractionHand hand,
        Entity target,
        Direction clickedWorldFace
    ) {
        if (player.isShiftKeyDown()
            || !player.getItemInHand(hand).is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
            || !target.isAlive()
            || target == player
            || !player.canInteractWithEntity(target, 0.0D)
            || !hasAdhesiveOnFace(target, clickedWorldFace)) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) return true;

        EntityAdhesion adhesion = target.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion != null && adhesion.attachmentFace().getOpposite() == clickedWorldFace) {
            release(target);
            Entity leader = EntityBondManager.resolveLeader(level, target);
            if (leader != null && EntityBondManager.hasBonds(target)) EntityBondManager.prepareLeader(level, leader);
        } else {
            EntityBondManager.disconnectFace(
                level,
                target,
                AdhesiveFaces.storedFace(target, clickedWorldFace)
            );
            if (target.hasData(ModAttachments.ENTITY_ADHESION)) target.setNoGravity(true);
        }
        AdhesiveSelectionManager.clear(player);
        level.playSound(
            null,
            target.blockPosition(),
            SoundEvents.HONEY_BLOCK_BREAK,
            SoundSource.PLAYERS,
            1.0F,
            0.9F + level.random.nextFloat() * 0.2F
        );
        return true;
    }

    private static boolean reclaimClickedPatch(
        Player player,
        ServerLevel level,
        BlockPos clickedPos,
        Direction clickedFace
    ) {
        BlockAdhesionState state = BondedFallingBlocks.getAdhesion(level, clickedPos);
        if (state == null
            || !state.hasPatch(clickedFace)
                && !state.hasBlockBond(clickedFace)
                && !state.hasEntityBond(clickedFace)
            || !canUseSupport(player, level, clickedPos, clickedFace)) {
            return false;
        }
        reclaimFace(level, clickedPos, clickedFace);
        level.playSound(
            null,
            clickedPos,
            SoundEvents.HONEY_BLOCK_BREAK,
            SoundSource.PLAYERS,
            1.0F,
            0.9F + level.random.nextFloat() * 0.2F
        );
        return true;
    }

    private static void reclaimFace(ServerLevel level, BlockPos supportPos, Direction face) {
        BlockAdhesionState state = BondedFallingBlocks.getAdhesion(level, supportPos);
        if (state == null) return;
        if (state.hasEntityBond(face)) {
            List<Entity> attached = new ArrayList<>();
            for (Entity entity : level.getAllEntities()) {
                EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
                if (adhesion != null
                    && adhesion.supportPos().equals(supportPos)
                    && adhesion.attachmentFace() == face) {
                    attached.add(entity);
                }
            }
            for (Entity entity : attached) release(entity);
            BondedFallingBlocks.setEntityBond(level, supportPos, face, false);
        }
        if (state.hasBlockBond(face)) {
            BlockPos otherPos = supportPos.relative(face);
            BondedFallingBlocks.disconnect(level, supportPos, face);
            releaseBondedBlockAt(level, supportPos, otherPos);
            releaseBondedBlockAt(level, otherPos, supportPos);
        }
        BondedFallingBlocks.removePatch(level, supportPos, face);
    }

    private static void releaseBondedBlockAt(ServerLevel level, BlockPos pos, BlockPos supportPos) {
        if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.getSupportPos().equals(supportPos)) {
            bonded.release();
        }
    }

    public static boolean bondSelectedToEntity(
        Player player,
        InteractionHand hand,
        Entity supportEntity,
        Direction supportWorldFace
    ) {
        ItemStack bucket = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !bucket.is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!(player.level() instanceof ServerLevel level)
            || !supportEntity.isAlive()
            || supportEntity.hasData(ModAttachments.ADHESIVE_TRANSIT)
            || !player.canInteractWithEntity(supportEntity, 0.0D)) {
            return false;
        }
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null || selected == supportEntity || selected.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
            return false;
        }
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        Entity movingEntity = selected;
        Entity anchorEntity = supportEntity;
        Direction anchorWorldFace = supportWorldFace;
        Direction movingStoredFace = selectedFace;
        if (selected.hasData(ModAttachments.ENTITY_ADHESION)) {
            if (supportEntity.hasData(ModAttachments.ENTITY_ADHESION)) return false;
            movingEntity = supportEntity;
            anchorEntity = selected;
            anchorWorldFace = AdhesiveFaces.worldFace(selected, selectedFace);
            movingStoredFace = AdhesiveFaces.storedFace(supportEntity, supportWorldFace);
            if (hasAdhesiveOnFace(selected, anchorWorldFace)) return false;
        }
        AdhesivePathPlanner.Plan plan = AdhesivePathPlanner.planToEntity(
            level,
            movingEntity,
            player,
            anchorEntity,
            anchorWorldFace,
            movingStoredFace
        );
        if (!plan.valid()) {
            if (AdhesivePathPlanner.isOutOfRange(plan.directDistance())) {
                AdhesiveSelectionManager.clear(player);
            }
            return false;
        }
        return applyEntityPlan(
            player,
            hand,
            level,
            movingEntity,
            anchorEntity,
            anchorWorldFace,
            plan
        );
    }

    static boolean bondSelectedToEntityWithPlan(
        Player player,
        InteractionHand hand,
        Entity supportEntity,
        Direction supportWorldFace,
        AdhesivePathPlanner.Plan plan
    ) {
        ItemStack bucket = player.getItemInHand(hand);
        if (player.isShiftKeyDown() || !bucket.is(ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())) return false;
        if (!(player.level() instanceof ServerLevel level)
            || !supportEntity.isAlive()
            || supportEntity.hasData(ModAttachments.ADHESIVE_TRANSIT)
            || !player.canInteractWithEntity(supportEntity, 0.0D)) {
            return false;
        }
        Entity selected = AdhesiveSelectionManager.resolveServerSelection(player);
        if (selected == null || selected == supportEntity || selected.hasData(ModAttachments.ADHESIVE_TRANSIT)) {
            return false;
        }
        Direction selectedFace = AdhesiveSelectionManager.getSelectedFace(player);
        Entity movingEntity = selected;
        Entity anchorEntity = supportEntity;
        Direction anchorWorldFace = supportWorldFace;
        Direction movingStoredFace = selectedFace;
        if (selected.hasData(ModAttachments.ENTITY_ADHESION)) {
            if (supportEntity.hasData(ModAttachments.ENTITY_ADHESION)) return false;
            movingEntity = supportEntity;
            anchorEntity = selected;
            anchorWorldFace = AdhesiveFaces.worldFace(selected, selectedFace);
            movingStoredFace = AdhesiveFaces.storedFace(supportEntity, supportWorldFace);
            if (hasAdhesiveOnFace(selected, anchorWorldFace)) return false;
        }
        if (!matchesPlanStart(plan, movingEntity)
            || !plan.valid()
            || plan.sourceFace() != (movingEntity instanceof AbstractPlasticEntity
                ? movingStoredFace
                : anchorWorldFace.getOpposite())) {
            return false;
        }
        return applyEntityPlan(
            player,
            hand,
            level,
            movingEntity,
            anchorEntity,
            anchorWorldFace,
            plan
        );
    }

    private static boolean applyEntityPlan(
        Player player,
        InteractionHand hand,
        ServerLevel level,
        Entity movingEntity,
        Entity anchorEntity,
        Direction anchorWorldFace,
        AdhesivePathPlanner.Plan plan
    ) {
        if (!plan.valid() || !AdhesiveGroupTransform.isPlanClear(level, movingEntity, plan, player)) return false;
        EntityBondManager.prepareAlignedLeader(level, movingEntity);
        boolean plastic = movingEntity instanceof AbstractPlasticEntity;
        byte startOrientation = plastic
            ? ((AbstractPlasticEntity) movingEntity).getOrientation().pack()
            : PlasticEntityOrientation.DEFAULT.pack();
        byte targetOrientation = plan.targetOrientation() == null
            ? PlasticEntityOrientation.DEFAULT.pack()
            : plan.targetOrientation().pack();
        AdhesiveTransit transit = new AdhesiveTransit(
            anchorEntity.blockPosition(),
            anchorWorldFace,
            BuiltInRegistries.BLOCK.getKey(Blocks.AIR),
            plan.sourceFace(),
            Optional.of(anchorEntity.getUUID()),
            anchorEntity.getId(),
            anchorEntity.position(),
            plan.points(),
            level.getGameTime(),
            transitDuration(plan),
            movingEntity.isNoGravity(),
            plastic,
            startOrientation,
            targetOrientation
        );
        movingEntity.setData(ModAttachments.ADHESIVE_TRANSIT, transit);
        movingEntity.setNoGravity(true);
        movingEntity.setDeltaMovement(Vec3.ZERO);
        movingEntity.fallDistance = 0.0F;
        movingEntity.hasImpulse = true;
        movingEntity.hurtMarked = true;
        AdhesiveSelectionManager.clear(player);
        consumeBucket(player, hand);
        return true;
    }

    private static boolean matchesPlanStart(AdhesivePathPlanner.Plan plan, Entity entity) {
        return !plan.points().isEmpty()
            && plan.points().getFirst().distanceToSqr(entity.position()) <= 0.01D;
    }

    public static void tickAdhesiveTransit(Entity entity, boolean validateAndComplete) {
        AdhesiveTransit transit = entity.getExistingDataOrNull(ModAttachments.ADHESIVE_TRANSIT.get());
        if (tickBlockificationHandoff(entity, transit, validateAndComplete)) return;
        if (transit == null) return;

        Entity supportEntity = resolveSupportEntity(entity, transit);
        if (transit.hasEntityTarget() && (supportEntity == null || !supportEntity.isAlive())) {
            if (validateAndComplete && !entity.level().isClientSide) cancelTransit(entity, transit);
            return;
        }

        if (!validateAndComplete
            && !entity.level().isClientSide
            && applyCachedTransitProjection(entity, transit, supportEntity)) {
            return;
        }

        if (validateAndComplete && !entity.level().isClientSide && !transit.hasEntityTarget()) {
            BlockState supportState = entity.level().getBlockState(transit.supportPos());
            if (!transit.supportBlockId().equals(BuiltInRegistries.BLOCK.getKey(supportState.getBlock()))) {
                cancelTransit(entity, transit);
                return;
            }
        }

        double rawProgress = transit.rawProgress(entity.level().getGameTime(), 0.0F);
        double movementProgress = transit.easedProgress(entity.level().getGameTime(), 0.0F);
        Vec3 position = transit.positionAt(movementProgress, supportEntity);
        byte orientation = transit.plastic() && rawProgress >= 0.72D
            ? transit.targetOrientation()
            : transit.startOrientation();
        if (!applyTransitProjectionSafely(entity, transit, position, movementProgress, supportEntity, orientation)) return;
        if (validateAndComplete && !entity.level().isClientSide && rawProgress < 1.0D) {
            TRANSIT_VALIDATIONS.put(
                entity,
                new TransitValidation(
                    transit,
                    entity.level().getGameTime(),
                    supportEntity == null ? Vec3.ZERO : supportEntity.position(),
                    position,
                    orientation
                )
            );
        }
        if (validateAndComplete && !entity.level().isClientSide && rawProgress >= 1.0D) {
            TRANSIT_VALIDATIONS.remove(entity);
            completeTransit(entity, transit, supportEntity);
        }
    }

    private static boolean applyCachedTransitProjection(
        Entity entity,
        AdhesiveTransit transit,
        @Nullable Entity supportEntity
    ) {
        TransitValidation validation = TRANSIT_VALIDATIONS.get(entity);
        if (validation == null
            || validation.transit() != transit
            || validation.gameTime() != entity.level().getGameTime()
            || supportEntity != null
                && supportEntity.position().distanceToSqr(validation.supportPosition()) > 1.0E-10D) {
            return false;
        }
        applyTransitProjection(entity, validation.position(), validation.orientation());
        return true;
    }

    private static boolean applyTransitProjectionSafely(
        Entity entity,
        AdhesiveTransit transit,
        Vec3 desiredPosition,
        double desiredProgress,
        @Nullable Entity supportEntity,
        byte orientation
    ) {
        if (entity.level().isClientSide) {
            applyTransitProjection(entity, desiredPosition, orientation);
            return true;
        }
        TransitAdvance advance = findTransitAdvance(
            entity,
            transit,
            desiredPosition,
            desiredProgress,
            supportEntity,
            orientation
        );
        if (!advance.blocked()) {
            applyTransitProjection(entity, desiredPosition, orientation);
            return true;
        }
        byte stopOrientation = isTransitProjectionClear(entity, advance.position(), transit.startOrientation())
            ? transit.startOrientation()
            : orientation;
        cancelTransit(entity, transit, advance.position(), stopOrientation);
        return false;
    }

    private static TransitAdvance findTransitAdvance(
        Entity entity,
        AdhesiveTransit transit,
        Vec3 desiredPosition,
        double desiredProgress,
        @Nullable Entity supportEntity,
        byte orientation
    ) {
        Vec3 currentPosition = entity.position();
        if (!isTransitProjectionClear(entity, transit, currentPosition, orientation)) {
            return new TransitAdvance(currentPosition, true);
        }

        double currentProgress = transit.closestPathProgress(currentPosition, supportEntity);
        Vec3 currentPathPosition = transit.positionAt(currentProgress, supportEntity);
        if (currentProgress <= desiredProgress + TRANSIT_PATH_PROGRESS_EPSILON
            && currentPosition.distanceToSqr(currentPathPosition) <= TRANSIT_PATH_ALIGNMENT_DISTANCE_SQR) {
            return findTransitAdvanceAlongPath(
                entity,
                transit,
                currentPosition,
                currentPathPosition,
                currentProgress,
                desiredPosition,
                desiredProgress,
                supportEntity,
                orientation
            );
        }
        return findStraightTransitAdvance(entity, currentPosition, desiredPosition, orientation, transit);
    }

    private static TransitAdvance findTransitAdvanceAlongPath(
        Entity entity,
        AdhesiveTransit transit,
        Vec3 currentPosition,
        Vec3 currentPathPosition,
        double currentProgress,
        Vec3 desiredPosition,
        double desiredProgress,
        @Nullable Entity supportEntity,
        byte orientation
    ) {
        TransitAdvance alignment = findStraightTransitAdvance(
            entity,
            currentPosition,
            currentPathPosition,
            orientation,
            null
        );
        if (alignment.blocked() || desiredProgress - currentProgress <= TRANSIT_PATH_PROGRESS_EPSILON) {
            return alignment;
        }

        double pathDistance = transit.pathLength() * (desiredProgress - currentProgress);
        int steps = Math.max(1, (int) Math.ceil(pathDistance / TRANSIT_COLLISION_SAMPLE_STEP));
        double lastClearProgress = currentProgress;
        for (int step = 1; step <= steps; step++) {
            double candidateProgress = currentProgress
                + (desiredProgress - currentProgress) * step / (double) steps;
            Vec3 candidate = transit.positionAt(candidateProgress, supportEntity);
            if (isTransitProjectionClear(entity, transit, candidate, orientation)) {
                lastClearProgress = candidateProgress;
                continue;
            }
            return new TransitAdvance(
                refineTransitStopProgress(
                    entity,
                    transit,
                    lastClearProgress,
                    candidateProgress,
                    supportEntity,
                    orientation
                ),
                true
            );
        }
        return new TransitAdvance(desiredPosition, false);
    }

    private static TransitAdvance findStraightTransitAdvance(
        Entity entity,
        Vec3 currentPosition,
        Vec3 desiredPosition,
        byte orientation,
        @Nullable AdhesiveTransit transit
    ) {
        double distance = currentPosition.distanceTo(desiredPosition);
        if (distance <= 1.0E-10D) return new TransitAdvance(currentPosition, false);

        int steps = Math.max(1, (int) Math.ceil(distance / TRANSIT_COLLISION_SAMPLE_STEP));
        Vec3 lastClear = currentPosition;
        for (int step = 1; step <= steps; step++) {
            Vec3 candidate = currentPosition.lerp(desiredPosition, step / (double) steps);
            if (isTransitProjectionClear(entity, transit, candidate, orientation)) {
                lastClear = candidate;
                continue;
            }
            return new TransitAdvance(
                refineTransitStopPosition(entity, lastClear, candidate, orientation, transit),
                true
            );
        }
        return new TransitAdvance(desiredPosition, false);
    }

    private static Vec3 refineTransitStopProgress(
        Entity entity,
        AdhesiveTransit transit,
        double clearProgress,
        double blockedProgress,
        @Nullable Entity supportEntity,
        byte orientation
    ) {
        double clear = clearProgress;
        double blocked = blockedProgress;
        for (int step = 0; step < TRANSIT_COLLISION_REFINEMENT_STEPS; step++) {
            double candidate = (clear + blocked) * 0.5D;
            if (isTransitProjectionClear(entity, transit, transit.positionAt(candidate, supportEntity), orientation)) {
                clear = candidate;
            } else {
                blocked = candidate;
            }
        }
        return transit.positionAt(clear, supportEntity);
    }

    private static Vec3 refineTransitStopPosition(
        Entity entity,
        Vec3 clearPosition,
        Vec3 blockedPosition,
        byte orientation,
        @Nullable AdhesiveTransit transit
    ) {
        Vec3 clear = clearPosition;
        Vec3 blocked = blockedPosition;
        for (int step = 0; step < TRANSIT_COLLISION_REFINEMENT_STEPS; step++) {
            Vec3 candidate = clear.lerp(blocked, 0.5D);
            if (isTransitProjectionClear(entity, transit, candidate, orientation)) {
                clear = candidate;
            } else {
                blocked = candidate;
            }
        }
        return clear;
    }

    private static boolean isTransitProjectionClear(Entity entity, Vec3 position, byte orientation) {
        return isTransitProjectionClear(entity, null, position, orientation);
    }

    private static boolean isTransitProjectionClear(
        Entity entity,
        @Nullable AdhesiveTransit transit,
        Vec3 position,
        byte orientation
    ) {
        PlasticEntityOrientation rootOrientation = entity instanceof AbstractPlasticEntity
            ? PlasticEntityOrientation.unpack(orientation)
            : null;
        AdhesiveGroupTransform.Projection projection = AdhesiveGroupTransform.project(entity, position, rootOrientation);
        return transit != null && position.distanceToSqr(transit.targetPosition()) < 1.0E-8D
            ? AdhesiveGroupTransform.isProjectionClearOfBlocksAtFinalContact(entity.level(), projection)
            : AdhesiveGroupTransform.isProjectionClearOfBlocks(entity.level(), projection);
    }

    private record TransitAdvance(Vec3 position, boolean blocked) {
    }

    private record TransitValidation(
        AdhesiveTransit transit,
        long gameTime,
        Vec3 supportPosition,
        Vec3 position,
        byte orientation
    ) {
    }

    private static void completeTransit(
        Entity entity,
        AdhesiveTransit transit,
        Entity supportEntity
    ) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        if (transit.hasEntityTarget()) {
            if (supportEntity == null) {
                cancelTransit(entity, transit);
                return;
            }
            if (entity instanceof AbstractPlasticEntity plasticEntity
                && !plasticEntity.plasticraft$canOccupyBlocks(
                    plasticEntity.getOrientation(),
                    plasticEntity.position()
                )) {
                cancelTransit(entity, transit);
                return;
            }
            Direction supportStoredFace = AdhesiveFaces.storedFace(supportEntity, transit.attachmentFace());
            if (AdhesiveGroupBlockifier.shouldBlockifyEntityTarget(level, entity, supportEntity)) {
                if (!AdhesiveGroupBlockifier.blockifyEntityTarget(
                    level,
                    entity,
                    supportEntity,
                    transit.originalNoGravity()
                )) {
                    cancelTransit(entity, transit);
                    return;
                }
                level.playSound(
                    null,
                    supportEntity.blockPosition(),
                    SoundEvents.HONEY_BLOCK_PLACE,
                    SoundSource.PLAYERS,
                    1.0F,
                    0.9F + level.random.nextFloat() * 0.2F
                );
                return;
            }
            if (!EntityBondManager.connect(
                level,
                entity,
                transit.sourceFace(),
                supportEntity,
                supportStoredFace,
                transit.originalNoGravity()
            )) {
                cancelTransit(entity, transit);
                return;
            }
            Entity leader = EntityBondManager.resolveLeader(level, supportEntity);
            if (leader != null) EntityBondManager.prepareAlignedLeader(level, leader);
            entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
            level.playSound(
                null,
                supportEntity.blockPosition(),
                SoundEvents.HONEY_BLOCK_PLACE,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + level.random.nextFloat() * 0.2F
            );
            return;
        }
        if (entity instanceof AbstractPlasticEntity plasticEntity
            && AdhesiveGroupBlockifier.isPlasticGroup(level, plasticEntity)) {
            if (!AdhesiveGroupBlockifier.blockifyPlasticGroup(level, plasticEntity, transit)) {
                cancelTransit(entity, transit);
                return;
            }
            level.playSound(
                null,
                transit.supportPos(),
                SoundEvents.HONEY_BLOCK_PLACE,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + level.random.nextFloat() * 0.2F
            );
            return;
        }
        if (entity instanceof FallingGiantAnvilEntity giantAnvil) {
            if (!bondGiantAnvil(
                level,
                giantAnvil,
                transit.supportPos(),
                transit.attachmentFace()
            )) {
                cancelTransit(entity, transit);
                return;
            }
            entity.getPersistentData().putLong(TAG_BLOCKIFICATION_HANDOFF, level.getGameTime());
            holdAtTransitTarget(entity, transit);
            level.playSound(
                null,
                transit.supportPos(),
                SoundEvents.HONEY_BLOCK_PLACE,
                SoundSource.PLAYERS,
                1.0F,
                0.9F + level.random.nextFloat() * 0.2F
            );
            return;
        }
        boolean blockifies = AdhesiveFallingBlockBehavior.canBlockifyComponent(level, entity);
        if (!blockifies) entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
        boolean bonded = blockifies && entity instanceof AbstractPlasticEntity plasticEntity
            ? bondPlasticEntity(
                level,
                plasticEntity,
                transit.supportPos(),
                transit.attachmentFace(),
                PlasticEntityOrientation.unpack(transit.targetOrientation()),
                transit.originalNoGravity()
            )
            : blockifies
                ? bondFallingBlock(
                    level,
                    (FallingBlockEntity) entity,
                    transit.supportPos(),
                    transit.attachmentFace()
                )
                : bondOrdinaryEntity(level, entity, transit);
        if (!bonded) {
            cancelTransit(entity, transit);
            return;
        }

        if (blockifies) {
            // 先让目标方块同步一整刻，再移除实体，避免客户端在两种渲染之间出现空帧。
            entity.getPersistentData().putLong(TAG_BLOCKIFICATION_HANDOFF, level.getGameTime());
            holdAtTransitTarget(entity, transit);
        }

        level.playSound(
            null,
            transit.supportPos(),
            SoundEvents.HONEY_BLOCK_PLACE,
            SoundSource.PLAYERS,
            1.0F,
            0.9F + level.random.nextFloat() * 0.2F
        );
    }

    private static int transitDuration(AdhesivePathPlanner.Plan plan) {
        double pathLength = 0.0D;
        for (int index = 1; index < plan.points().size(); index++) {
            pathLength += plan.points().get(index - 1).distanceTo(plan.points().get(index));
        }
        double normalizedDistance = Math.clamp(
            (pathLength - MIN_TRANSIT_DISTANCE)
                / (AdhesivePathPlanner.MAX_DISTANCE - MIN_TRANSIT_DISTANCE),
            0.0D,
            1.0D
        );
        return MIN_TRANSIT_TICKS
            + (int) Math.ceil(normalizedDistance * (MAX_TRANSIT_TICKS - MIN_TRANSIT_TICKS));
    }

    private static boolean tickBlockificationHandoff(
        Entity entity,
        @Nullable AdhesiveTransit transit,
        boolean validateAndComplete
    ) {
        if (entity.level().isClientSide) return false;
        CompoundTag persistentData = entity.getPersistentData();
        if (!persistentData.contains(TAG_BLOCKIFICATION_HANDOFF, Tag.TAG_ANY_NUMERIC)) return false;

        if (transit == null) {
            holdAtCurrentPosition(entity);
        } else {
            holdAtTransitTarget(entity, transit);
        }
        long blockPlacedGameTime = persistentData.getLong(TAG_BLOCKIFICATION_HANDOFF);
        if (validateAndComplete && entity.level().getGameTime() > blockPlacedGameTime) {
            persistentData.remove(TAG_BLOCKIFICATION_HANDOFF);
            entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
            entity.discard();
        }
        return true;
    }

    static void markBlockificationHandoff(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return;
        entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
        entity.getPersistentData().putLong(TAG_BLOCKIFICATION_HANDOFF, level.getGameTime());
        holdAtCurrentPosition(entity);
    }

    private static void holdAtCurrentPosition(Entity entity) {
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void holdAtTransitTarget(Entity entity, AdhesiveTransit transit) {
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setOnGround(false);
        entity.fallDistance = 0.0F;
        applyTransitProjection(entity, transit.targetPosition(), transit.targetOrientation());
    }

    private static Entity resolveSupportEntity(Entity movingEntity, AdhesiveTransit transit) {
        if (!transit.hasEntityTarget()) return null;
        Entity byId = movingEntity.level().getEntity(transit.supportEntityId());
        if (byId != null
            && transit.supportEntityUuid().filter(byId.getUUID()::equals).isPresent()) {
            return byId;
        }
        return movingEntity.level() instanceof ServerLevel serverLevel
            ? transit.supportEntityUuid().map(serverLevel::getEntity).orElse(null)
            : null;
    }

    public static void tickBondedEntity(Entity entity, boolean validateSupport) {
        Entity elasticRoot = elasticRoot(entity);
        AdhesiveElasticMotion elastic = elasticRoot.getExistingDataOrNull(
            ModAttachments.ADHESIVE_ELASTIC_MOTION.get()
        );
        if (elastic != null) {
            if (elasticRoot == entity && validateSupport && !entity.level().isClientSide) {
                tickElasticMotion(elasticRoot, elastic);
            }
            return;
        }

        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion == null) return;

        PistonAdhesionController.MotionTarget pistonTarget = PistonAdhesionController.entityTarget(entity, adhesion);
        if (pistonTarget != null) {
            enforce(entity, pistonTarget.position());
            return;
        }

        if (validateSupport
            && !entity.level().isClientSide) {
            boolean movedAway = elastic == null
                && entity.position().distanceToSqr(adhesion.fixedPosition()) > 0.25D;
            boolean supportChanged = entity.level().hasChunkAt(adhesion.supportPos())
                && !adhesion.supportBlockId().equals(BuiltInRegistries.BLOCK.getKey(
                    entity.level().getBlockState(adhesion.supportPos()).getBlock()
                ));
            if (movedAway || supportChanged) {
                release(entity, adhesion);
                return;
            }
        }
        enforce(entity, adhesion);
    }

    public static boolean beginElasticMotion(Entity entity) {
        return beginElasticMotion(entity, MIN_ELASTIC_KNOCKBACK);
    }

    public static boolean beginElasticMotion(Entity entity, double knockbackStrength) {
        Entity reboundRoot = prepareKnockback(entity, knockbackStrength);
        return reboundRoot.hasData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
    }

    public static boolean isElasticMotion(Entity entity) {
        return entity.hasData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
    }

    public static boolean isGroupElasticMotion(Entity entity) {
        return elasticRoot(entity).hasData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
    }

    public static Entity prepareKnockback(Entity entity, double knockbackStrength) {
        if (!(entity.level() instanceof ServerLevel level)) return entity;

        Entity blockAnchor = findBlockAnchor(entity);
        if (blockAnchor == null) {
            if (EntityBondManager.hasBonds(entity)) EntityBondManager.prepareLeader(level, entity);
            return entity;
        }

        if (EntityBondManager.hasBonds(blockAnchor)) EntityBondManager.prepareLeader(level, blockAnchor);
        if (knockbackStrength < MIN_ELASTIC_KNOCKBACK) return blockAnchor;

        EntityAdhesion adhesion = blockAnchor.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion == null) return blockAnchor;
        AdhesiveElasticMotion current = blockAnchor.getExistingDataOrNull(
            ModAttachments.ADHESIVE_ELASTIC_MOTION.get()
        );
        long gameTime = blockAnchor.level().getGameTime();
        int duration = elasticDuration(knockbackStrength);
        AdhesiveElasticMotion motion = current == null
            ? new AdhesiveElasticMotion(
                adhesion.fixedPosition(),
                gameTime,
                duration,
                blockAnchor.isNoGravity()
            )
            : current.restarted(gameTime, duration);
        blockAnchor.setData(ModAttachments.ADHESIVE_ELASTIC_MOTION, motion);
        blockAnchor.setNoGravity(true);
        blockAnchor.hasImpulse = true;
        blockAnchor.hurtMarked = true;
        return blockAnchor;
    }

    public static void applyKnockback(Entity entity, double strength, double ratioX, double ratioZ) {
        if (entity instanceof LivingEntity living) {
            living.knockback(strength, ratioX, ratioZ);
        } else {
            entity.push(-ratioX * strength, 0.1D, -ratioZ * strength);
        }
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void tickElasticMotion(Entity entity, AdhesiveElasticMotion motion) {
        Vec3 anchor = elasticAnchor(entity, motion);
        long elapsed = entity.level().getGameTime() - motion.startedGameTime();
        Vec3 displacement = entity.position().subtract(anchor);
        if (elapsed >= motion.durationTicks()
            || elapsed >= 4
                && displacement.lengthSqr() < 2.5E-3D
                && entity.getDeltaMovement().lengthSqr() < 2.5E-3D) {
            finishElasticMotion(entity, motion, anchor);
            return;
        }

        Vec3 velocity = entity.getDeltaMovement()
            .scale(ELASTIC_DAMPING)
            .add(displacement.scale(-ELASTIC_SPRING));
        double speed = velocity.length();
        if (speed > ELASTIC_MAX_SPEED) velocity = velocity.scale(ELASTIC_MAX_SPEED / speed);
        entity.setDeltaMovement(velocity);
        entity.setNoGravity(true);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static int elasticDuration(double knockbackStrength) {
        double ratio = Math.max(1.0D, knockbackStrength / MIN_ELASTIC_KNOCKBACK);
        return Math.min(20, 9 + (int) Math.ceil(Math.log(ratio) * 4.0D));
    }

    private static void finishElasticMotion(Entity entity, AdhesiveElasticMotion motion, Vec3 anchor) {
        entity.setPos(anchor);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.removeData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
        entity.setNoGravity(motion.originalNoGravity());
        entity.fallDistance = 0.0F;
        EntityBondManager.synchronizeComponent(entity);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static Entity elasticRoot(Entity entity) {
        Entity leader = EntityBondManager.resolveLeader(entity.level(), entity);
        return leader == null || !leader.isAlive() ? entity : leader;
    }

    private static @Nullable Entity findBlockAnchor(Entity entity) {
        if (entity.hasData(ModAttachments.ENTITY_ADHESION)) return entity;
        for (Entity member : EntityBondManager.component(entity.level(), entity)) {
            if (member.hasData(ModAttachments.ENTITY_ADHESION)) return member;
        }
        return null;
    }

    private static Vec3 elasticAnchor(Entity entity, AdhesiveElasticMotion motion) {
        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion != null) return adhesion.fixedPosition();
        EntityBondState bonds = EntityBondManager.get(entity);
        if (bonds != null && !bonds.leaderUuid().equals(entity.getUUID())) {
            Entity leader = EntityBondManager.resolveLeader(entity.level(), entity);
            if (leader != null) return leader.position().add(bonds.offsetFromLeader());
        }
        return motion.anchor();
    }

    public static void release(Entity entity) {
        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        if (adhesion != null) release(entity, adhesion);
    }

    private static boolean bondPlasticEntity(
        ServerLevel level,
        AbstractPlasticEntity entity,
        BlockPos supportPos,
        Direction attachmentFace,
        PlasticEntityOrientation orientation,
        boolean originalNoGravity
    ) {
        BlockPos occupiedPos = supportPos.relative(attachmentFace);
        BlockState replacedState = level.getBlockState(occupiedPos);
        if (!replacedState.canBeReplaced()) return false;

        BlockState displayState = entity.getDisplayState();
        if (!(displayState.getBlock() instanceof AbstractPlasticEntityBlock<?>)) return false;
        BlockState fixedState = orientation.applyToState(displayState)
            .setValue(AbstractPlasticEntityBlock.BONDED, true);
        Set<UUID> ignoredEntities = new HashSet<>();
        for (Entity member : EntityBondManager.component(level, entity)) {
            ignoredEntities.add(member.getUUID());
        }
        if (!isPlacementUnobstructed(level, occupiedPos, fixedState, ignoredEntities)) return false;
        if (!level.setBlock(occupiedPos, fixedState, Block.UPDATE_ALL)) return false;
        if (!(level.getBlockEntity(occupiedPos) instanceof BondedEntityBlockEntity bonded)
            || !bonded.initialize(entity, displayState, attachmentFace, orientation, originalNoGravity)) {
            level.setBlock(occupiedPos, replacedState, Block.UPDATE_ALL);
            return false;
        }
        if (!BondedFallingBlocks.connect(level, supportPos, occupiedPos)) {
            level.setBlock(occupiedPos, replacedState, Block.UPDATE_ALL);
            return false;
        }
        EntityBondManager.disconnectEntity(level, entity);
        return true;
    }

    private static boolean bondFallingBlock(
        ServerLevel level,
        FallingBlockEntity entity,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        BlockPos occupiedPos = supportPos.relative(attachmentFace);
        BlockState replacedState = level.getBlockState(occupiedPos);
        if (!replacedState.canBeReplaced()) return false;

        BlockState fixedState = entity.blockState;
        if (!isPlacementUnobstructed(level, entity, occupiedPos, fixedState)) return false;
        boolean pistonMovable = fixedState.getPistonPushReaction() == PushReaction.NORMAL
            && entity.getPistonPushReaction() == PushReaction.NORMAL
            && !(fixedState.getBlock() instanceof AnvilBlock);
        if (!level.setBlock(occupiedPos, fixedState, Block.UPDATE_ALL)) {
            return false;
        }
        BondedFallingBlocks.put(level, occupiedPos, new BondedFallingBlockInfo(
            fixedState,
            supportPos,
            BuiltInRegistries.BLOCK.getKey(level.getBlockState(supportPos).getBlock()),
            pistonMovable
        ));
        if (!BondedFallingBlocks.connect(level, supportPos, occupiedPos)) {
            BondedFallingBlocks.remove(level, occupiedPos);
            level.setBlock(occupiedPos, replacedState, Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    private static boolean bondOrdinaryEntity(
        ServerLevel level,
        Entity target,
        AdhesiveTransit transit
    ) {
        if (!level.hasChunkAt(transit.supportPos())
            || !transit.supportBlockId().equals(BuiltInRegistries.BLOCK.getKey(
                level.getBlockState(transit.supportPos()).getBlock()
            ))) {
            return false;
        }
        AABB box = target.getBoundingBox();
        Vec3 fixedPosition = transit.targetPosition();
        AABB movedBox = box.move(fixedPosition.subtract(target.position()));
        if (!level.getWorldBorder().isWithinBounds(movedBox)) return false;
        if (!level.noCollision(target, movedBox.deflate(1.0E-5D))) return false;

        EntityAdhesion adhesion = new EntityAdhesion(
            transit.supportPos(),
            transit.attachmentFace(),
            transit.supportBlockId(),
            fixedPosition,
            transit.originalNoGravity()
        );
        target.setData(ModAttachments.ENTITY_ADHESION, adhesion);
        if (!BondedFallingBlocks.setEntityBond(level, transit.supportPos(), transit.attachmentFace(), true)) {
            target.removeData(ModAttachments.ENTITY_ADHESION);
            return false;
        }
        enforce(target, adhesion);
        return true;
    }

    public static boolean bondEntityFromPatch(
        ServerLevel level,
        Entity target,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        if (!target.isAlive()
            || target instanceof Player
            || target.hasData(ModAttachments.ENTITY_ADHESION)
            || target.hasData(ModAttachments.ADHESIVE_TRANSIT)
            || EntityBondManager.hasBonds(target)
            || !BondedFallingBlocks.hasPatch(level, supportPos, attachmentFace)
            || !SurfaceAdhesiveService.hasUncoveredContact(level, target, supportPos, attachmentFace)) {
            return false;
        }

        boolean bonded;
        switch (target) {
            case FallingGiantAnvilEntity giantAnvil -> bonded = bondGiantAnvil(level, giantAnvil, supportPos, attachmentFace);
            case AbstractPlasticEntity plasticEntity -> bonded = bondPlasticEntity(
                level,
                plasticEntity,
                supportPos,
                attachmentFace,
                plasticEntity.getOrientation(),
                plasticEntity.isNoGravity()
            );
            case FallingBlockEntity fallingBlock -> bonded = bondFallingBlock(level, fallingBlock, supportPos, attachmentFace);
            default -> {
                Vec3 fixedPosition = alignToSupportFace(target, supportPos, attachmentFace);
                EntityAdhesion adhesion = new EntityAdhesion(
                    supportPos,
                    attachmentFace,
                    BuiltInRegistries.BLOCK.getKey(level.getBlockState(supportPos).getBlock()),
                    fixedPosition,
                    target.isNoGravity()
                );
                target.setData(ModAttachments.ENTITY_ADHESION, adhesion);
                bonded = BondedFallingBlocks.setEntityBond(level, supportPos, attachmentFace, true);
                if (bonded) {
                    enforce(target, adhesion);
                } else {
                    target.removeData(ModAttachments.ENTITY_ADHESION);
                }
            }
        }
        if (!bonded) return false;

        if (target instanceof FallingBlockEntity) target.discard();
        level.playSound(
            null,
            supportPos,
            SoundEvents.HONEY_BLOCK_PLACE,
            SoundSource.BLOCKS,
            0.8F,
            0.9F + level.random.nextFloat() * 0.2F
        );
        return true;
    }

    private static Vec3 alignToSupportFace(Entity entity, BlockPos supportPos, Direction face) {
        AABB box = entity.getBoundingBox();
        double plane = switch (face.getAxis()) {
            case X -> face == Direction.EAST ? supportPos.getX() + 1.0D : supportPos.getX();
            case Y -> face == Direction.UP ? supportPos.getY() + 1.0D : supportPos.getY();
            case Z -> face == Direction.SOUTH ? supportPos.getZ() + 1.0D : supportPos.getZ();
        };
        double offset = switch (face.getAxis()) {
            case X -> plane - (face == Direction.EAST ? box.minX : box.maxX);
            case Y -> plane - (face == Direction.UP ? box.minY : box.maxY);
            case Z -> plane - (face == Direction.SOUTH ? box.minZ : box.maxZ);
        };
        return entity.position().add(
            face.getAxis() == Direction.Axis.X ? offset : 0.0D,
            face.getAxis() == Direction.Axis.Y ? offset : 0.0D,
            face.getAxis() == Direction.Axis.Z ? offset : 0.0D
        );
    }

    private static boolean bondGiantAnvil(
        ServerLevel level,
        FallingGiantAnvilEntity entity,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        if (!(entity.getBlockState().getBlock() instanceof GiantAnvilBlock giantAnvil)
            || EntityBondManager.component(level, entity).size() != 1) {
            return false;
        }
        BlockPos bottomCenter = BlockPos.containing(
            entity.getX(),
            entity.getBoundingBox().minY + 1.0E-4D,
            entity.getZ()
        );
        BlockPos adhesivePart = supportPos.relative(attachmentFace);
        Map<BlockPos, BlockState> placedStates = new LinkedHashMap<>();
        Map<BlockPos, BlockState> replacedStates = new LinkedHashMap<>();
        Set<UUID> ignored = Set.of(entity.getUUID());
        BlockState baseState = entity.getBlockState();
        for (Cube3x3PartHalf part : giantAnvil.getParts()) {
            BlockPos pos = bottomCenter.offset(part.getOffset());
            BlockState replaced = level.getBlockState(pos);
            BlockState placed = giantAnvil.placedState(part, baseState);
            if (!level.hasChunkAt(pos)
                || !level.getWorldBorder().isWithinBounds(pos)
                || !replaced.canBeReplaced()
                || !isPlacementUnobstructed(level, pos, placed, ignored)) {
                return false;
            }
            placedStates.put(pos.immutable(), placed);
            replacedStates.put(pos.immutable(), replaced);
        }
        if (!placedStates.containsKey(adhesivePart)) return false;

        List<BlockPos> placed = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : placedStates.entrySet()) {
            if (!level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL)) {
                rollbackGiantAnvil(level, placed, replacedStates);
                return false;
            }
            placed.add(entry.getKey());
        }
        if (!BondedFallingBlocks.connect(level, supportPos, adhesivePart)) {
            rollbackGiantAnvil(level, placed, replacedStates);
            return false;
        }
        return true;
    }

    private static void rollbackGiantAnvil(
        ServerLevel level,
        List<BlockPos> placed,
        Map<BlockPos, BlockState> replacedStates
    ) {
        for (BlockPos pos : placed) {
            BondedFallingBlocks.removeAll(level, pos);
            level.setBlock(pos, replacedStates.getOrDefault(pos, Blocks.AIR.defaultBlockState()), Block.UPDATE_ALL);
        }
    }

    static boolean canUseSupport(
        Player player,
        ServerLevel level,
        BlockPos supportPos,
        Direction attachmentFace
    ) {
        if (!level.isLoaded(supportPos) || level.getBlockState(supportPos).isAir()) return false;
        BlockPos occupiedPos = supportPos.relative(attachmentFace);
        if (!level.isLoaded(occupiedPos)
            || !level.getWorldBorder().isWithinBounds(occupiedPos)
            || !level.mayInteract(player, supportPos)
            || !level.mayInteract(player, occupiedPos)
            || !player.getAbilities().mayBuild) {
            return false;
        }
        AttributeInstance rangeAttribute = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        double range = rangeAttribute == null ? 5.0D : rangeAttribute.getValue();
        return supportPos.getCenter().distanceToSqr(player.getEyePosition()) <= range * range + 2.0D;
    }

    private static boolean isPlacementUnobstructed(
        ServerLevel level,
        Entity target,
        BlockPos occupiedPos,
        BlockState fixedState
    ) {
        return isPlacementUnobstructed(level, occupiedPos, fixedState, Set.of(target.getUUID()));
    }

    private static boolean isPlacementUnobstructed(
        ServerLevel level,
        BlockPos occupiedPos,
        BlockState fixedState,
        Set<UUID> ignoredEntities
    ) {
        VoxelShape collision = fixedState.getCollisionShape(level, occupiedPos);
        if (collision.isEmpty()) return true;
        for (AABB localBounds : collision.toAabbs()) {
            AABB bounds = localBounds.move(occupiedPos.getX(), occupiedPos.getY(), occupiedPos.getZ());
            if (!level.getEntities(
                (Entity) null,
                bounds,
                candidate -> candidate.isAlive()
                    && candidate.blocksBuilding
                    && !ignoredEntities.contains(candidate.getUUID())
            ).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void enforce(Entity entity, EntityAdhesion adhesion) {
        enforce(entity, adhesion.fixedPosition());
    }

    private static void enforce(Entity entity, Vec3 fixedPosition) {
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        if (entity.position().distanceToSqr(fixedPosition) > 1.0E-10D) {
            entity.setPos(fixedPosition);
        }
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void release(Entity entity, EntityAdhesion adhesion) {
        entity.removeData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
        entity.removeData(ModAttachments.ENTITY_ADHESION);
        if (entity.level() instanceof ServerLevel level) {
            if (!hasAttachedEntity(level, adhesion.supportPos(), adhesion.attachmentFace())) {
                BondedFallingBlocks.setEntityBond(level, adhesion.supportPos(), adhesion.attachmentFace(), false);
            }
            BlockState supportState = level.getBlockState(adhesion.supportPos());
            if (supportState.getBlock() instanceof FallingBlock) {
                level.scheduleTick(adhesion.supportPos(), supportState.getBlock(), 1);
            }
        }
        entity.setNoGravity(adhesion.originalNoGravity());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static boolean hasAttachedEntity(ServerLevel level, BlockPos supportPos, Direction attachmentFace) {
        for (Entity candidate : level.getAllEntities()) {
            EntityAdhesion adhesion = candidate.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
            if (adhesion != null
                && adhesion.supportPos().equals(supportPos)
                && adhesion.attachmentFace() == attachmentFace) {
                return true;
            }
        }
        return false;
    }

    private static void cancelTransit(Entity entity, AdhesiveTransit transit) {
        cancelTransit(entity, transit, entity.position(), transit.startOrientation());
    }

    private static void cancelTransit(Entity entity, AdhesiveTransit transit, Vec3 stopPosition) {
        cancelTransit(entity, transit, stopPosition, transit.startOrientation());
    }

    private static void cancelTransit(Entity entity, AdhesiveTransit transit, Vec3 stopPosition, byte orientation) {
        TRANSIT_VALIDATIONS.remove(entity);
        entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
        applyTransitProjection(entity, stopPosition, orientation);
        entity.setNoGravity(transit.originalNoGravity());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void applyTransitProjection(Entity entity, Vec3 rootPosition, byte orientation) {
        PlasticEntityOrientation rootOrientation = entity instanceof AbstractPlasticEntity
            ? PlasticEntityOrientation.unpack(orientation)
            : null;
        PlasticEntityOrientation currentOrientation = entity instanceof AbstractPlasticEntity plastic
            ? plastic.getOrientation()
            : null;
        AdhesiveGroupTransform.Projection projection = AdhesiveGroupTransform.project(
            entity,
            rootPosition,
            rootOrientation
        );
        if (currentOrientation != null
            && rootOrientation != null
            && !currentOrientation.equals(rootOrientation)) {
            EntityBondManager.transformOrdinaryMemberFaces(
                entity.level(),
                entity,
                currentOrientation,
                rootOrientation
            );
        }
        for (AdhesiveGroupTransform.Member member : projection.members()) {
            if (member.entity() instanceof AbstractPlasticEntity plastic
                && member.orientation() != null
                && !plastic.getOrientation().equals(member.orientation())) {
                plastic.setOrientation(member.orientation());
            }
        }
        for (AdhesiveGroupTransform.Member member : projection.members()) {
            Entity projectedEntity = member.entity();
            projectedEntity.setNoGravity(true);
            projectedEntity.setDeltaMovement(Vec3.ZERO);
            projectedEntity.fallDistance = 0.0F;
            if (projectedEntity.position().distanceToSqr(member.position()) > 1.0E-10D) {
                projectedEntity.setPos(member.position());
            }
            projectedEntity.hasImpulse = true;
            projectedEntity.hurtMarked = true;
        }
        EntityBondManager.updateLeaderOffsets(entity.level(), entity);
    }

    private static void consumeBucket(Player player, InteractionHand hand) {
        if (player.getAbilities().instabuild) return;
        ItemStack bucket = player.getItemInHand(hand);
        bucket.shrink(1);
        ItemStack emptyBucket = new ItemStack(Items.BUCKET);
        if (bucket.isEmpty()) {
            player.setItemInHand(hand, emptyBucket);
        } else if (!player.getInventory().add(emptyBucket)) {
            player.drop(emptyBucket, false);
        }
    }

}
