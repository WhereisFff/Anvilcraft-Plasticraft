package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 让整格大小且顶面对齐网格的塑料实体支撑普通下落方块。 */
public final class PlasticFallingBlockSupport {
    private static final double ALIGNMENT_EPSILON = 1.0E-3D;
    private static final double PROBE_DEPTH = 0.05D;

    private PlasticFallingBlockSupport() {
    }

    public static boolean hasSupport(Level level, BlockPos fallingBlockPos, @Nullable Entity fallingEntity) {
        return !findSupports(level, fallingBlockPos, fallingEntity).isEmpty();
    }

    public static List<AbstractPlasticEntity> findSupports(
        Level level,
        BlockPos fallingBlockPos,
        @Nullable Entity fallingEntity
    ) {
        double supportY = fallingBlockPos.getY();
        AABB probe = new AABB(
            fallingBlockPos.getX(),
            supportY - PROBE_DEPTH,
            fallingBlockPos.getZ(),
            fallingBlockPos.getX() + 1.0D,
            supportY + ALIGNMENT_EPSILON,
            fallingBlockPos.getZ() + 1.0D
        );
        return level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            probe,
            candidate -> candidate != fallingEntity && isAlignedFullBlockSupport(candidate, fallingBlockPos)
        );
    }

    public static Set<BlockPos> updateSupportChecks(
        AbstractPlasticEntity support,
        Set<BlockPos> previousPositions
    ) {
        if (!(support.level() instanceof ServerLevel level) || !isFullBlockSize(support)) return Set.of();
        Set<BlockPos> currentPositions = fallingBlockPositions(level, support.getBoundingBox());
        Set<BlockPos> trackedPositions = new HashSet<>(currentPositions);
        for (BlockPos pos : previousPositions) {
            if (currentPositions.contains(pos)) {
                continue;
            }
            if (hasSupport(level, pos, null) || BondedFallingBlocks.isBonded(level, pos)) {
                trackedPositions.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FallingBlock) {
                FallingBlockEntity.fall(level, pos, state);
            }
        }
        for (BlockPos pos : currentPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FallingBlock fallingBlock) {
                level.scheduleTick(pos, fallingBlock, 2);
            }
        }
        return trackedPositions;
    }

    private static Set<BlockPos> fallingBlockPositions(ServerLevel level, AABB supportBox) {
        int supportY = Mth.floor(supportBox.maxY + ALIGNMENT_EPSILON);
        if (Math.abs(supportBox.maxY - supportY) > ALIGNMENT_EPSILON) return Set.of();
        int minX = Mth.floor(supportBox.minX + ALIGNMENT_EPSILON);
        int maxX = Mth.floor(supportBox.maxX - ALIGNMENT_EPSILON);
        int minZ = Mth.floor(supportBox.minZ + ALIGNMENT_EPSILON);
        int maxZ = Mth.floor(supportBox.maxZ - ALIGNMENT_EPSILON);
        Set<BlockPos> positions = new HashSet<>();
        for (BlockPos pos : BlockPos.betweenClosed(minX, supportY, minZ, maxX, supportY, maxZ)) {
            if (level.getBlockState(pos).getBlock() instanceof FallingBlock) positions.add(pos.immutable());
        }
        return positions;
    }

    private static boolean isAlignedFullBlockSupport(
        AbstractPlasticEntity candidate,
        BlockPos fallingBlockPos
    ) {
        if (!candidate.isAlive() || !isFullBlockSize(candidate)) {
            return false;
        }
        AABB box = candidate.getBoundingBox();
        if (Math.abs(box.maxY - fallingBlockPos.getY()) > ALIGNMENT_EPSILON) return false;
        double overlapX = Math.min(box.maxX, fallingBlockPos.getX() + 1.0D)
            - Math.max(box.minX, fallingBlockPos.getX());
        double overlapZ = Math.min(box.maxZ, fallingBlockPos.getZ() + 1.0D)
            - Math.max(box.minZ, fallingBlockPos.getZ());
        return overlapX > ALIGNMENT_EPSILON && overlapZ > ALIGNMENT_EPSILON;
    }

    private static boolean isFullBlockSize(AbstractPlasticEntity candidate) {
        return candidate.getBbWidth() >= 1.0D - ALIGNMENT_EPSILON
            && candidate.getBbHeight() >= 1.0D - ALIGNMENT_EPSILON;
    }
}
