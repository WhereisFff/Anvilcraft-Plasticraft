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

/** 高粘性树脂桶选择、固定和释放实体的服务端逻辑。 */
public final class AdhesiveBondingService {
    private static final String TAG_BLOCKIFICATION_HANDOFF =
        "anvilcraftplasticraft:adhesive_blockification_handoff";
    private static final int MIN_TRANSIT_TICKS = 8;
    private static final int MAX_TRANSIT_TICKS = 32;
    private static final double MIN_TRANSIT_DISTANCE = 2.0D;
    private static final double ELASTIC_SPRING = 0.42D;
    private static final double ELASTIC_DAMPING = 0.76D;
    private static final double ELASTIC_MAX_SPEED = 2.5D;
    public static final double MIN_ELASTIC_KNOCKBACK = 0.5D;

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

    public static void tickAdhesiveTransit(Entity entity, boolean validateAndComplete) {
        AdhesiveTransit transit = entity.getExistingDataOrNull(ModAttachments.ADHESIVE_TRANSIT.get());
        if (tickBlockificationHandoff(entity, transit, validateAndComplete)) return;
        if (transit == null) return;

        Entity supportEntity = resolveSupportEntity(entity, transit);
        if (transit.hasEntityTarget() && (supportEntity == null || !supportEntity.isAlive())) {
            if (validateAndComplete && !entity.level().isClientSide) cancelTransit(entity, transit);
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
        Vec3 position = transit.positionAt(
            transit.easedProgress(entity.level().getGameTime(), 0.0F),
            supportEntity
        );
        entity.setNoGravity(true);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.setPos(position);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        if (transit.plastic() && rawProgress >= 0.72D) {
            applyTransitOrientation(entity, transit, transit.targetOrientation());
        }
        if (validateAndComplete && !entity.level().isClientSide && rawProgress >= 1.0D) {
            completeTransit(entity, transit, supportEntity);
        }
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
        entity.setPos(transit.targetPosition());
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        applyTransitOrientation(entity, transit, transit.targetOrientation());
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
        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        AdhesiveElasticMotion elastic = entity.getExistingDataOrNull(ModAttachments.ADHESIVE_ELASTIC_MOTION.get());
        if (adhesion == null) {
            if (elastic != null && validateSupport && !entity.level().isClientSide) {
                tickElasticMotion(entity, elastic);
            }
            return;
        }

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
        if (elastic != null) {
            if (validateSupport && !entity.level().isClientSide) tickElasticMotion(entity, elastic);
            return;
        }
        enforce(entity, adhesion);
    }

    public static boolean beginElasticMotion(Entity entity) {
        return beginElasticMotion(entity, MIN_ELASTIC_KNOCKBACK);
    }

    public static boolean beginElasticMotion(Entity entity, double knockbackStrength) {
        EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
        EntityBondState bonds = EntityBondManager.get(entity);
        if (knockbackStrength < MIN_ELASTIC_KNOCKBACK
            || adhesion == null && (bonds == null || bonds.links().isEmpty())) {
            return false;
        }

        AdhesiveElasticMotion current = entity.getExistingDataOrNull(ModAttachments.ADHESIVE_ELASTIC_MOTION.get());
        long gameTime = entity.level().getGameTime();
        int duration = elasticDuration(knockbackStrength);
        AdhesiveElasticMotion motion = current == null
            ? new AdhesiveElasticMotion(
                adhesion == null ? entity.position() : adhesion.fixedPosition(),
                gameTime,
                duration,
                entity.isNoGravity()
            )
            : current.restarted(gameTime, duration);
        entity.setData(ModAttachments.ADHESIVE_ELASTIC_MOTION, motion);
        entity.setNoGravity(true);
        entity.hasImpulse = true;
        entity.hurtMarked = true;
        return true;
    }

    public static boolean isElasticMotion(Entity entity) {
        return entity.hasData(ModAttachments.ADHESIVE_ELASTIC_MOTION);
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
        entity.hasImpulse = true;
        entity.hurtMarked = true;
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

    private static boolean canUseSupport(
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
        entity.removeData(ModAttachments.ADHESIVE_TRANSIT);
        applyTransitOrientation(entity, transit, transit.startOrientation());
        entity.setNoGravity(transit.originalNoGravity());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
        entity.hasImpulse = true;
        entity.hurtMarked = true;
    }

    private static void applyTransitOrientation(Entity entity, AdhesiveTransit transit, byte orientation) {
        if (!transit.plastic() || !(entity instanceof AbstractPlasticEntity plasticEntity)) return;
        PlasticEntityOrientation targetOrientation = PlasticEntityOrientation.unpack(orientation);
        if (plasticEntity.getOrientation().equals(targetOrientation)) return;
        plasticEntity.setOrientation(targetOrientation);
        if (entity.level() instanceof ServerLevel level) {
            EntityBondManager.prepareAlignedLeader(level, plasticEntity);
        }
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
