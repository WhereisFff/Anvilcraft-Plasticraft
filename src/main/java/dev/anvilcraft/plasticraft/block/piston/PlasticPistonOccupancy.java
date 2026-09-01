package dev.anvilcraft.plasticraft.block.piston;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** 为活塞结构解析提供塑料实体碰撞体的只读方块占位，并让实体跟随真实活塞头移动。 */
public final class PlasticPistonOccupancy {
    private static final int MOVEMENT_LIFETIME = 20;
    private static final double OCCUPANCY_EPSILON = 1.0E-6D;
    private static final Map<Level, MovementIndex> MOVEMENTS = new WeakHashMap<>();

    private PlasticPistonOccupancy() {
    }

    public static BlockState blockState(Level level, BlockPos pos, BlockState actualState) {
        AbstractPlasticEntity plastic = plasticEntityAt(level, pos, actualState);
        if (plastic == null) return actualState;

        BlockState blockified = plastic.getOrientation().applyToState(plastic.getDisplayState());
        return blockified.hasProperty(AbstractPlasticEntityBlock.BONDED)
            ? blockified.setValue(AbstractPlasticEntityBlock.BONDED, true)
            : blockified;
    }

    public static @Nullable AbstractPlasticEntity plasticEntityAt(Level level, BlockPos pos) {
        return plasticEntityAt(level, pos, level.getBlockState(pos));
    }

    public static List<BlockPos> occupiedPositions(AbstractPlasticEntity plastic) {
        VoxelShape collision = plastic.plasticraft$getCollisionBox().shape();
        if (collision.isEmpty()) return List.of();
        AABB bounds = collision.bounds();
        int minX = Mth.floor(bounds.minX + OCCUPANCY_EPSILON);
        int minY = Mth.floor(bounds.minY + OCCUPANCY_EPSILON);
        int minZ = Mth.floor(bounds.minZ + OCCUPANCY_EPSILON);
        int maxX = Mth.floor(bounds.maxX - OCCUPANCY_EPSILON);
        int maxY = Mth.floor(bounds.maxY - OCCUPANCY_EPSILON);
        int maxZ = Mth.floor(bounds.maxZ - OCCUPANCY_EPSILON);
        if (minX > maxX || minY > maxY || minZ > maxZ) return List.of();

        List<BlockPos> positions = new ArrayList<>();
        for (BlockPos mutablePos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockPos pos = mutablePos.immutable();
            if (occupies(collision, pos)) positions.add(pos);
        }
        return List.copyOf(positions);
    }

    public static Set<BlockPos> beginMovement(
        Level level,
        List<BlockPos> positions,
        BlockPos pistonPos,
        Direction facing,
        boolean extending
    ) {
        if (positions.isEmpty()) return Set.of();
        Direction movementDirection = extending ? facing : facing.getOpposite();
        BlockPos driverPos = extending ? pistonPos.relative(facing) : pistonPos;
        long expiresAt = level.getGameTime() + MOVEMENT_LIFETIME;
        Set<BlockPos> virtualPositions = new LinkedHashSet<>();
        Map<UUID, AbstractPlasticEntity> occupants = new LinkedHashMap<>();
        for (BlockPos position : positions) {
            AbstractPlasticEntity plastic = plasticEntityAt(level, position);
            if (plastic == null) continue;
            virtualPositions.add(position.immutable());
            occupants.putIfAbsent(plastic.getUUID(), plastic);
        }

        // 活塞接管的实体必须立刻退出休眠：tick 的活塞分支在休眠检查之前返回，
        // 留在索引里的登记格会随实体被推走而失效。在 MOVEMENTS 锁之外唤醒，避免与索引锁嵌套。
        for (AbstractPlasticEntity occupant : occupants.values()) occupant.plasticraft$wakeFromRest();

        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.computeIfAbsent(level, ignored -> new MovementIndex());
            index.cleanup(level.getGameTime());
            for (AbstractPlasticEntity occupant : occupants.values()) {
                EntityMovement previous = index.byEntity.get(occupant.getUUID());
                if (previous != null
                    && previous.movement().pistonPos().equals(pistonPos)
                    && previous.movement().direction() == movementDirection) {
                    continue;
                }

                Vec3 sourcePosition = occupant.position();
                Vec3 movement = Vec3.atLowerCornerOf(movementDirection.getNormal());
                Vec3 targetPosition = sourcePosition.add(movement);
                Vec3 reverseTarget = sourcePosition;
                if (previous != null
                    && previous.movement().pistonPos().equals(pistonPos)
                    && previous.movement().direction() == movementDirection.getOpposite()) {
                    targetPosition = previous.reverseTarget();
                    reverseTarget = previous.targetPosition();
                }
                index.byEntity.put(
                    occupant.getUUID(),
                    new EntityMovement(
                        sourcePosition,
                        targetPosition,
                        reverseTarget,
                        new PistonMovement(
                            pistonPos,
                            driverPos,
                            movementDirection,
                            expiresAt
                        )
                    )
                );
            }
        }
        return Set.copyOf(virtualPositions);
    }

    public static boolean shouldDropOnRetraction(Level level, BlockPos pistonPos, Direction facing) {
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(level);
            if (index == null) return false;
            index.cleanup(level.getGameTime());
            boolean hasExtendingPlastic = index.byEntity.values().stream().anyMatch(entityMovement -> {
                PistonMovement movement = entityMovement.movement();
                return movement.pistonPos().equals(pistonPos) && movement.direction() == facing;
            });
            if (!hasExtendingPlastic) return false;
        }

        // 原版读取被推动方块的移动活塞判断吐块；塑料占位不生成该方块，因此改读同步的活塞头。
        BlockPos driverPos = pistonPos.relative(facing);
        if (!level.hasChunkAt(driverPos)) return false;
        BlockEntity blockEntity = level.getBlockEntity(driverPos);
        if (!(blockEntity instanceof PistonMovingBlockEntity piston)
            || !piston.isSourcePiston()
            || !piston.isExtending()
            || piston.getDirection() != facing) {
            return false;
        }
        return piston.getProgress(0.0F) < 0.5F
            || level.getGameTime() == piston.getLastTicked()
            || level instanceof ServerLevel serverLevel && serverLevel.isHandlingTick();
    }

    public static boolean isMoving(AbstractPlasticEntity entity) {
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(entity.level());
            return index != null && index.byEntity.containsKey(entity.getUUID());
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
        if (!entity.level().hasChunkAt(movement.driverPos())) {
            return entityMovement.sourcePosition();
        }
        BlockEntity blockEntity = entity.level().getBlockEntity(movement.driverPos());
        if (blockEntity instanceof PistonMovingBlockEntity piston
            && piston.isSourcePiston()
            && piston.getMovementDirection() == movement.direction()) {
            return entityMovement.sourcePosition().lerp(
                entityMovement.targetPosition(),
                followProgress(entity.level(), piston)
            );
        }

        removeMovement(entity.level(), entity.getUUID());
        return entityMovement.targetPosition();
    }

    /** 实体 tick 早于活塞方块实体；不预读下一拍进度时，碰撞会在粘液顶面前方留下半格可站缝。 */
    private static double followProgress(Level level, PistonMovingBlockEntity piston) {
        double progress = Mth.clamp(piston.getProgress(1.0F), 0.0F, 1.0F);
        if (level.getGameTime() != piston.getLastTicked()) {
            progress = Math.min(progress + 0.5D, 1.0D);
        }
        return progress;
    }

    private static @Nullable AbstractPlasticEntity plasticEntityAt(
        Level level,
        BlockPos pos,
        BlockState actualState
    ) {
        if (!actualState.isAir()) return null;
        AABB cell = new AABB(pos).deflate(OCCUPANCY_EPSILON);
        return level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            cell,
            entity -> !entity.isRemoved()
                && entity.getDisplayState().getBlock() instanceof AbstractPlasticEntityBlock<?>
                && !isActiveDriverPosition(level, entity, pos)
                && occupies(entity.plasticraft$getCollisionBox().shape(), pos)
        ).stream().min(Comparator.comparingInt(AbstractPlasticEntity::getId)).orElse(null);
    }

    private static boolean isActiveDriverPosition(Level level, AbstractPlasticEntity entity, BlockPos pos) {
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(level);
            EntityMovement movement = index == null ? null : index.byEntity.get(entity.getUUID());
            return movement != null && movement.movement().driverPos().equals(pos);
        }
    }

    private static boolean occupies(VoxelShape collision, BlockPos pos) {
        if (collision.isEmpty()) return false;
        return Shapes.joinIsNotEmpty(
            collision,
            Shapes.create(new AABB(pos).deflate(OCCUPANCY_EPSILON)),
            BooleanOp.AND
        );
    }

    private static void removeMovement(Level level, UUID entityId) {
        synchronized (MOVEMENTS) {
            MovementIndex index = MOVEMENTS.get(level);
            if (index != null) index.byEntity.remove(entityId);
        }
    }

    private record PistonMovement(
        BlockPos pistonPos,
        BlockPos driverPos,
        Direction direction,
        long expiresAt
    ) {
        private PistonMovement {
            pistonPos = pistonPos.immutable();
            driverPos = driverPos.immutable();
        }
    }

    private record EntityMovement(
        Vec3 sourcePosition,
        Vec3 targetPosition,
        Vec3 reverseTarget,
        PistonMovement movement
    ) {
    }

    private static final class MovementIndex {
        private final Map<UUID, EntityMovement> byEntity = new LinkedHashMap<>();

        private void cleanup(long gameTime) {
            this.byEntity.values().removeIf(movement -> movement.movement().expiresAt() < gameTime);
        }
    }
}
