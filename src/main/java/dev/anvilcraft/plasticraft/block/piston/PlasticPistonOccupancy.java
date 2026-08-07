package dev.anvilcraft.plasticraft.block.piston;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** 为活塞结构解析提供塑料实体锚点的只读方块占位，并同步占位随结构移动。 */
public final class PlasticPistonOccupancy {
    private static final int MOVEMENT_LIFETIME = 20;
    private static final double ENTITY_SEARCH_MARGIN = 3.0D;
    private static final Map<Level, MovementIndex> MOVEMENTS = new WeakHashMap<>();

    private PlasticPistonOccupancy() {
    }

    public static BlockState blockState(Level level, BlockPos pos, BlockState actualState) {
        if (!actualState.isAir()) return actualState;
        AbstractPlasticEntity plastic = level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            new AABB(pos).inflate(ENTITY_SEARCH_MARGIN),
            entity -> !entity.isRemoved() && entity.plasticraft$getAnchorBlockPos().equals(pos)
        ).stream().min(Comparator.comparingInt(AbstractPlasticEntity::getId)).orElse(null);
        if (plastic == null || !(plastic.getDisplayState().getBlock() instanceof AbstractPlasticEntityBlock<?>)) {
            return actualState;
        }

        // 占位只进入本次解析结果；活塞执行阶段会从世界重新读取为空气，因而不会落成重复制品。
        BlockState blockified = plastic.getOrientation().applyToState(plastic.getDisplayState());
        return blockified.hasProperty(AbstractPlasticEntityBlock.BONDED)
            ? blockified.setValue(AbstractPlasticEntityBlock.BONDED, true)
            : blockified;
    }

    public static void beginMovement(Level level, List<BlockPos> positions, Direction movementDirection) {
        if (positions.isEmpty()) return;
        long expiresAt = level.getGameTime() + MOVEMENT_LIFETIME;
        Map<BlockPos, PistonMovement> movementsBySource = new HashMap<>();
        for (BlockPos position : positions) {
            BlockPos source = position.immutable();
            movementsBySource.put(
                source,
                new PistonMovement(source.relative(movementDirection), movementDirection, expiresAt)
            );
        }

        AABB searchBounds = structureBounds(positions).inflate(ENTITY_SEARCH_MARGIN);
        List<AbstractPlasticEntity> occupants = level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            searchBounds,
            entity -> !entity.isRemoved()
                && movementsBySource.containsKey(entity.plasticraft$getAnchorBlockPos())
        );
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.computeIfAbsent(level, ignored -> new MovementIndex());
            index.cleanup(level.getGameTime());
            for (AbstractPlasticEntity occupant : occupants) {
                PistonMovement movement = movementsBySource.get(occupant.plasticraft$getAnchorBlockPos());
                if (movement == null) continue;
                index.byEntity.put(
                    occupant.getUUID(),
                    new EntityMovement(occupant.position(), movement)
                );
            }
        }
    }

    public static @Nullable Vec3 movementTarget(AbstractPlasticEntity entity) {
        EntityMovement entityMovement;
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(entity.level());
            if (index == null) return null;
            index.cleanup(entity.level().getGameTime());
            entityMovement = index.byEntity.get(entity.getUUID());
        }
        if (entityMovement == null || entity.isRemoved()) return null;

        PistonMovement movement = entityMovement.movement();
        if (!entity.level().hasChunkAt(movement.destination())) {
            return entityMovement.sourcePosition();
        }
        BlockEntity blockEntity = entity.level().getBlockEntity(movement.destination());
        if (blockEntity instanceof PistonMovingBlockEntity piston
            && !piston.isSourcePiston()
            && piston.getMovementDirection() == movement.direction()) {
            float progress = Math.min(piston.getProgress(1.0F) + 0.5F, 1.0F);
            Vec3 displacement = Vec3.atLowerCornerOf(movement.direction().getNormal()).scale(progress);
            return entityMovement.sourcePosition().add(displacement);
        }

        removeMovement(entity.level(), entity.getUUID());
        return entityMovement.sourcePosition().add(Vec3.atLowerCornerOf(movement.direction().getNormal()));
    }

    private static void removeMovement(Level level, UUID entityId) {
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(level);
            if (index != null) index.byEntity.remove(entityId);
        }
    }

    private static AABB structureBounds(List<BlockPos> positions) {
        AABB bounds = new AABB(positions.getFirst());
        for (int index = 1; index < positions.size(); index++) {
            bounds = bounds.minmax(new AABB(positions.get(index)));
        }
        return bounds;
    }

    private record PistonMovement(BlockPos destination, Direction direction, long expiresAt) {
        private PistonMovement {
            destination = destination.immutable();
        }
    }

    private record EntityMovement(Vec3 sourcePosition, PistonMovement movement) {
    }

    private static final class MovementIndex {
        private final Map<UUID, EntityMovement> byEntity = new HashMap<>();

        private void cleanup(long gameTime) {
            this.byEntity.values().removeIf(movement -> movement.movement().expiresAt() < gameTime);
        }
    }
}
