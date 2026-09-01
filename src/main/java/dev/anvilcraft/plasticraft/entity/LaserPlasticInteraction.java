package dev.anvilcraft.plasticraft.entity;

import dev.dubhe.anvilcraft.init.entity.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** 将没有方块占位的塑料实体并入 AnvilCraft 激光的路径与伤害判定。 */
public final class LaserPlasticInteraction {
    private static final double BEAM_MIN = 7.0D / 16.0D;
    private static final double BEAM_MAX = 9.0D / 16.0D;

    private LaserPlasticInteraction() {
    }

    public static BlockPos findFirstBlockingPlastic(
        Level level,
        Direction direction,
        BlockPos origin,
        BlockPos blockLimit,
        boolean checkImpact
    ) {
        int distance = axisDistance(origin, blockLimit, direction);
        if (distance <= 0) return blockLimit;
        BlockPos first = origin.relative(direction);
        AABB searchBounds = new AABB(first).minmax(new AABB(blockLimit));
        List<UniversalPlasticEntity> plastics = level.getEntitiesOfClass(
            UniversalPlasticEntity.class,
            searchBounds,
            entity -> entity.isAlive() && !entity.canLaserPassThrough()
        );
        if (plastics.isEmpty()) return blockLimit;
        for (int offset = 1; offset <= distance; offset++) {
            BlockPos position = origin.relative(direction, offset);
            if (plastics.stream().anyMatch(entity -> intersects(entity, direction, position, checkImpact))) {
                return position;
            }
        }
        return blockLimit;
    }

    public static void hurtBlockingPlastic(
        ServerLevel level,
        Direction direction,
        BlockPos position,
        boolean checkImpact,
        float amount
    ) {
        if (amount <= 0.0F) return;
        AABB searchBounds = checkImpact ? beamBounds(direction, position) : new AABB(position);
        List<UniversalPlasticEntity> plastics = level.getEntitiesOfClass(
            UniversalPlasticEntity.class,
            searchBounds,
            entity -> entity.isAlive()
                && !entity.canLaserPassThrough()
                && intersects(entity, direction, position, checkImpact)
        );
        if (plastics.isEmpty()) return;
        DamageSource source = ModDamageTypes.laser(level);
        plastics.forEach(entity -> entity.hurt(source, amount));
    }

    private static boolean intersects(
        UniversalPlasticEntity entity,
        Direction direction,
        BlockPos position,
        boolean checkImpact
    ) {
        AABB target = checkImpact ? beamBounds(direction, position) : new AABB(position);
        if (!checkImpact) return entity.plasticraft$getCollisionBox().bounds().intersects(target);
        return entity.plasticraft$getCollisionBox().components().stream().anyMatch(target::intersects);
    }

    private static AABB beamBounds(Direction direction, BlockPos position) {
        double x = position.getX();
        double y = position.getY();
        double z = position.getZ();
        return switch (direction.getAxis()) {
            case X -> new AABB(x, y + BEAM_MIN, z + BEAM_MIN, x + 1.0D, y + BEAM_MAX, z + BEAM_MAX);
            case Y -> new AABB(x + BEAM_MIN, y, z + BEAM_MIN, x + BEAM_MAX, y + 1.0D, z + BEAM_MAX);
            case Z -> new AABB(x + BEAM_MIN, y + BEAM_MIN, z, x + BEAM_MAX, y + BEAM_MAX, z + 1.0D);
        };
    }

    private static int axisDistance(BlockPos origin, BlockPos target, Direction direction) {
        return switch (direction.getAxis()) {
            case X -> Math.abs(target.getX() - origin.getX());
            case Y -> Math.abs(target.getY() - origin.getY());
            case Z -> Math.abs(target.getZ() - origin.getZ());
        };
    }
}
