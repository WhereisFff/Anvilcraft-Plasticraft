package dev.anvilcraft.plasticraft.entity;

import dev.anvilcraft.plasticraft.entity.physics.PlasticEntityPhysics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * 从锅的实际重力面查询工作方块。中心射线优先，随后才检查整个底面覆盖到的方块，
 * 使小锅能与工作方块同格，而任意尺寸的锅仍能使用底面边缘的加工方块。
 */
public final class PlasticCauldronWorkBlockFinder {
    private PlasticCauldronWorkBlockFinder() {
    }

    /** {@code matcher} 返回非空值时，该方块即为本次查询命中的工作方块。 */
    public static <T> @Nullable T find(PlasticCauldron cauldron, Function<BlockState, T> matcher) {
        return find(cauldron, cauldron.position(), matcher);
    }

    /** 按指定瞬时位置查询，供高速接触时仍以实际碰撞瞬间的锅底为准。 */
    public static <T> @Nullable T find(
        PlasticCauldron cauldron,
        Vec3 cauldronPosition,
        Function<BlockState, T> matcher
    ) {
        AbstractPlasticEntity entity = cauldron.plasticraft$cauldronEntity();
        for (BlockPos position : findWorkBlockPositions(cauldron, cauldronPosition)) {
            T result = matcher.apply(entity.level().getBlockState(position));
            if (result != null) return result;
        }
        return null;
    }

    /** 返回锅底一格内按中心优先顺序首先遇到的非空气方块。 */
    public static @Nullable BlockPos findFirstNonAir(PlasticCauldron cauldron) {
        return findFirstNonAir(cauldron, cauldron.position());
    }

    /** 按指定瞬时位置返回锅底一格内按中心优先顺序首先遇到的非空气方块。 */
    public static @Nullable BlockPos findFirstNonAir(PlasticCauldron cauldron, Vec3 cauldronPosition) {
        AbstractPlasticEntity entity = cauldron.plasticraft$cauldronEntity();
        for (BlockPos position : findWorkBlockPositions(cauldron, cauldronPosition)) {
            if (!entity.level().getBlockState(position).isAir()) return position;
        }
        return null;
    }

    /**
     * 返回锅底一格内的候选方块。先检查中心射线的近、远两格，再按底面覆盖范围检查其余射线，
     * 让中心下方的有效加工方块稳定优先，同时不遗漏底面边缘。
     */
    public static List<BlockPos> findWorkBlockPositions(PlasticCauldron cauldron, Vec3 cauldronPosition) {
        AbstractPlasticEntity entity = cauldron.plasticraft$cauldronEntity();
        Direction gravityDirection = PlasticEntityPhysics.gravityDirection(entity);
        AABB bounds = entity.plasticraft$getCollisionBox().bounds().move(cauldronPosition.subtract(entity.position()));
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>();
        addWorkBlockRay(positions, bottomFaceCenterCell(bounds, gravityDirection), gravityDirection);
        addBottomFaceCoverage(positions, bounds, gravityDirection);
        return List.copyOf(positions);
    }

    /** 以锅的当前位置查询候选方块。 */
    public static List<BlockPos> findWorkBlockPositions(PlasticCauldron cauldron) {
        return findWorkBlockPositions(cauldron, cauldron.position());
    }

    private static void addBottomFaceCoverage(
        LinkedHashSet<BlockPos> positions,
        AABB bounds,
        Direction gravityDirection
    ) {
        int normal = normalCell(bounds, gravityDirection);
        switch (gravityDirection.getAxis()) {
            case X -> {
                for (int y = minimumCell(bounds.minY); y <= maximumCell(bounds.maxY); y++) {
                    for (int z = minimumCell(bounds.minZ); z <= maximumCell(bounds.maxZ); z++) {
                        addWorkBlockRay(positions, new BlockPos(normal, y, z), gravityDirection);
                    }
                }
            }
            case Y -> {
                for (int x = minimumCell(bounds.minX); x <= maximumCell(bounds.maxX); x++) {
                    for (int z = minimumCell(bounds.minZ); z <= maximumCell(bounds.maxZ); z++) {
                        addWorkBlockRay(positions, new BlockPos(x, normal, z), gravityDirection);
                    }
                }
            }
            case Z -> {
                for (int x = minimumCell(bounds.minX); x <= maximumCell(bounds.maxX); x++) {
                    for (int y = minimumCell(bounds.minY); y <= maximumCell(bounds.maxY); y++) {
                        addWorkBlockRay(positions, new BlockPos(x, y, normal), gravityDirection);
                    }
                }
            }
        }
    }

    private static void addWorkBlockRay(
        LinkedHashSet<BlockPos> positions,
        BlockPos position,
        Direction gravityDirection
    ) {
        positions.add(position.immutable());
        positions.add(position.relative(gravityDirection));
    }

    private static int minimumCell(double coordinate) {
        return Mth.floor(coordinate + PlasticEntityPhysics.FACE_EPSILON);
    }

    private static int maximumCell(double coordinate) {
        return Mth.floor(coordinate - PlasticEntityPhysics.FACE_EPSILON);
    }

    private static int normalCell(AABB bounds, Direction gravityDirection) {
        double coordinate = switch (gravityDirection.getAxis()) {
            case X -> gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minX : bounds.maxX;
            case Y -> gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minY : bounds.maxY;
            case Z -> gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minZ : bounds.maxZ;
        };
        double offset = gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE
            ? -PlasticEntityPhysics.FACE_EPSILON
            : PlasticEntityPhysics.FACE_EPSILON;
        return Mth.floor(coordinate + offset);
    }

    /** 从底面向重力方向偏出极小距离，格边界上的锅会优先查询真正位于锅底外侧的方块。 */
    private static BlockPos bottomFaceCenterCell(AABB bounds, Direction gravityDirection) {
        Vec3 center = bounds.getCenter();
        Vec3 bottomFaceCenter = switch (gravityDirection.getAxis()) {
            case X -> new Vec3(
                gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minX : bounds.maxX,
                center.y,
                center.z
            );
            case Y -> new Vec3(
                center.x,
                gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minY : bounds.maxY,
                center.z
            );
            case Z -> new Vec3(
                center.x,
                center.y,
                gravityDirection.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? bounds.minZ : bounds.maxZ
            );
        };
        Vec3 outward = Vec3.atLowerCornerOf(gravityDirection.getNormal()).scale(PlasticEntityPhysics.FACE_EPSILON);
        return BlockPos.containing(bottomFaceCenter.add(outward));
    }
}
