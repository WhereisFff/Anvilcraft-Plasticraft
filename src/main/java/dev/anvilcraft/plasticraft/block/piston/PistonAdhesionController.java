package dev.anvilcraft.plasticraft.block.piston;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.adhesive.EntityAdhesion;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 在活塞生成移动方块前迁移胶粘数据，并让粘在方块上的实体跟随活塞进度。 */
public final class PistonAdhesionController {
    private static final int MOVEMENT_LIFETIME = 20;
    private static final double ENTITY_SEARCH_MARGIN = 16.0D;
    private static final Map<Level, MovementIndex> MOVEMENTS = new WeakHashMap<>();

    private PistonAdhesionController() {
    }

    public static void beginMovement(Level level, List<BlockPos> positions, Direction movementDirection) {
        if (positions.isEmpty()) return;
        // 客户端在原版活塞事件前已经收到服务端迁移后的区块和实体附件。
        if (level.isClientSide) return;
        long expiresAt = level.getGameTime() + MOVEMENT_LIFETIME;
        MovementIndex index = MOVEMENTS.computeIfAbsent(level, ignored -> new MovementIndex());
        index.cleanup(level.getGameTime());
        Map<BlockPos, PistonMovement> movementsBySource = new HashMap<>();
        for (BlockPos position : positions) {
            PistonMovement movement = new PistonMovement(
                position,
                position.relative(movementDirection),
                movementDirection,
                expiresAt
            );
            movementsBySource.put(movement.source(), movement);
        }

        BondedFallingBlocks.moveAll(level, positions, movementDirection);
        AABB searchBounds = structureBounds(positions).inflate(ENTITY_SEARCH_MARGIN);
        for (Entity entity : level.getEntities(
            (Entity) null,
            searchBounds,
            candidate -> candidate.hasData(ModAttachments.ENTITY_ADHESION)
        )) {
            EntityAdhesion adhesion = entity.getExistingDataOrNull(ModAttachments.ENTITY_ADHESION.get());
            if (adhesion == null) continue;
            PistonMovement movement = movementsBySource.get(adhesion.supportPos());
            if (movement == null) continue;
            entity.setData(ModAttachments.ENTITY_ADHESION, adhesion.moved(movement.direction()));
            index.byEntity.put(entity.getUUID(), movement);
        }
    }

    public static @Nullable MotionTarget entityTarget(Entity entity, EntityAdhesion adhesion) {
        MovementIndex index = MOVEMENTS.get(entity.level());
        PistonMovement movement = index == null ? null : index.byEntity.get(entity.getUUID());
        if (movement == null) {
            movement = findMovement(entity.level(), adhesion.supportPos(), adhesion.supportBlockId());
            if (movement == null) return null;
            index = MOVEMENTS.computeIfAbsent(entity.level(), ignored -> new MovementIndex());
            index.byEntity.put(entity.getUUID(), movement);
        }

        EntityAdhesion movedAdhesion = adhesion;
        if (adhesion.supportPos().equals(movement.source())) {
            movedAdhesion = adhesion.moved(movement.direction());
            entity.setData(ModAttachments.ENTITY_ADHESION, movedAdhesion);
        } else if (!adhesion.supportPos().equals(movement.destination())) {
            index.byEntity.remove(entity.getUUID());
            return null;
        }

        Vec3 offset = movingOffset(entity.level(), movement, movedAdhesion.supportBlockId(), 1.0F, true);
        if (offset != null) {
            return new MotionTarget(movedAdhesion, movedAdhesion.fixedPosition().add(offset));
        }
        if (isCompletedSupport(entity.level(), movement, movedAdhesion)) {
            index.byEntity.remove(entity.getUUID());
            return new MotionTarget(movedAdhesion, movedAdhesion.fixedPosition());
        }
        if (levelTimeWithinMovement(entity.level(), movement)) {
            Vec3 waitingOffset = new Vec3(
                -movement.direction().getStepX(),
                -movement.direction().getStepY(),
                -movement.direction().getStepZ()
            );
            return new MotionTarget(movedAdhesion, movedAdhesion.fixedPosition().add(waitingOffset));
        }
        index.byEntity.remove(entity.getUUID());
        return null;
    }

    public static Vec3 movementOffset(
        Level level,
        Entity entity,
        EntityAdhesion adhesion,
        float partialTick
    ) {
        MovementIndex index = MOVEMENTS.get(level);
        PistonMovement movement = index == null ? null : index.byEntity.get(entity.getUUID());
        if (movement == null) {
            movement = findMovement(level, adhesion.supportPos(), adhesion.supportBlockId());
        }
        if (movement == null) return Vec3.ZERO;

        Vec3 offset = movingOffset(level, movement, adhesion.supportBlockId(), partialTick, false);
        if (offset == null) return Vec3.ZERO;
        if (adhesion.supportPos().equals(movement.destination())) return offset;
        if (!adhesion.supportPos().equals(movement.source())) return Vec3.ZERO;
        return offset.add(
            movement.direction().getStepX(),
            movement.direction().getStepY(),
            movement.direction().getStepZ()
        );
    }

    private static @Nullable PistonMovement findMovement(
        Level level,
        BlockPos supportPos,
        ResourceLocation supportBlockId
    ) {
        PistonMovement atDestination = movementFromPiston(level, supportPos, supportBlockId);
        if (atDestination != null) return atDestination;
        for (Direction direction : Direction.values()) {
            BlockPos candidate = supportPos.relative(direction);
            PistonMovement nearby = movementFromPiston(level, candidate, supportBlockId);
            if (nearby != null && nearby.source().equals(supportPos)) return nearby;
        }
        return null;
    }

    private static @Nullable PistonMovement movementFromPiston(
        Level level,
        BlockPos destination,
        ResourceLocation supportBlockId
    ) {
        if (!level.hasChunkAt(destination)) return null;
        BlockEntity blockEntity = level.getBlockEntity(destination);
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)
            || piston.isSourcePiston()
            || !supportBlockId.equals(BuiltInRegistries.BLOCK.getKey(piston.getMovedState().getBlock()))) {
            return null;
        }
        Direction movementDirection = piston.getMovementDirection();
        return new PistonMovement(
            destination.relative(movementDirection.getOpposite()),
            destination,
            movementDirection,
            level.getGameTime() + MOVEMENT_LIFETIME
        );
    }

    private static @Nullable Vec3 movingOffset(
        Level level,
        PistonMovement movement,
        ResourceLocation supportBlockId,
        float partialTick,
        boolean advanceToNextTick
    ) {
        if (!level.hasChunkAt(movement.destination())) return null;
        BlockEntity blockEntity = level.getBlockEntity(movement.destination());
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)
            || piston.isSourcePiston()
            || piston.getMovementDirection() != movement.direction()
            || !supportBlockId.equals(BuiltInRegistries.BLOCK.getKey(piston.getMovedState().getBlock()))) {
            return null;
        }
        if (!advanceToNextTick) {
            return new Vec3(
                piston.getXOff(partialTick),
                piston.getYOff(partialTick),
                piston.getZOff(partialTick)
            );
        }
        float progress = Math.min(piston.getProgress(1.0F) + 0.5F, 1.0F);
        float extendedProgress = piston.isExtending() ? progress - 1.0F : 1.0F - progress;
        Direction pistonDirection = piston.getDirection();
        return new Vec3(
            pistonDirection.getStepX() * extendedProgress,
            pistonDirection.getStepY() * extendedProgress,
            pistonDirection.getStepZ() * extendedProgress
        );
    }

    private static boolean isCompletedSupport(Level level, PistonMovement movement, EntityAdhesion adhesion) {
        if (!level.hasChunkAt(movement.destination())) return false;
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(
            level.getBlockState(movement.destination()).getBlock()
        );
        return adhesion.supportBlockId().equals(blockId)
            && BondedFallingBlocks.hasEntityBond(
                level,
                movement.destination(),
                adhesion.attachmentFace()
            );
    }

    private static boolean levelTimeWithinMovement(Level level, PistonMovement movement) {
        return level.getGameTime() <= movement.expiresAt();
    }

    private static AABB structureBounds(List<BlockPos> positions) {
        AABB bounds = new AABB(positions.getFirst());
        for (int index = 1; index < positions.size(); index++) {
            bounds = bounds.minmax(new AABB(positions.get(index)));
        }
        return bounds;
    }

    public record MotionTarget(EntityAdhesion adhesion, Vec3 position) {
    }

    private record PistonMovement(
        BlockPos source,
        BlockPos destination,
        Direction direction,
        long expiresAt
    ) {
        private PistonMovement {
            source = source.immutable();
            destination = destination.immutable();
        }
    }

    private static final class MovementIndex {
        private final Map<UUID, PistonMovement> byEntity = new HashMap<>();

        private void cleanup(long gameTime) {
            this.byEntity.values().removeIf(movement -> movement.expiresAt() < gameTime);
        }
    }
}
