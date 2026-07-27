package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.HardenedResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

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

    /** 方块态下落方块只检查下方逻辑格是否被塑料实体占用，不要求其碰撞箱具有完整顶面。 */
    public static boolean occupiesCellBelow(Level level, BlockPos fallingBlockPos) {
        BlockPos supportPos = fallingBlockPos.below();
        return !level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            new AABB(supportPos),
            candidate -> candidate.isAlive()
                && BlockPos.containing(candidate.getBoundingBox().getCenter()).equals(supportPos)
        ).isEmpty();
    }

    /** 仅在本刻扫过顶面，或已进入本体的实体碰撞余量时完成着陆。 */
    public static boolean landOnSupport(FallingBlockEntity fallingEntity) {
        double velocityY = fallingEntity.getDeltaMovement().y;
        if (velocityY > 0.0D) return false;

        AABB fallingBox = fallingEntity.getBoundingBox();
        double currentBottom = fallingBox.minY;
        double previousBottom = currentBottom - velocityY;
        AABB probe = new AABB(
            fallingBox.minX,
            currentBottom - PROBE_DEPTH,
            fallingBox.minZ,
            fallingBox.maxX,
            previousBottom + ALIGNMENT_EPSILON,
            fallingBox.maxZ
        );
        AbstractPlasticEntity landingSupport = null;
        double landingY = Double.NEGATIVE_INFINITY;
        for (AbstractPlasticEntity candidate : fallingEntity.level().getEntitiesOfClass(
            AbstractPlasticEntity.class,
            probe,
            candidate -> candidate != fallingEntity && candidate.isAlive() && isFullBlockSize(candidate)
        )) {
            AABB supportBox = candidate.getBoundingBox();
            double supportTop = supportBox.maxY;
            double alignedTop = Math.rint(supportTop);
            boolean crossedSurface = supportTop >= currentBottom - ALIGNMENT_EPSILON;
            boolean reachedCollisionMargin = supportTop >= currentBottom - PROBE_DEPTH;
            if (Math.abs(supportTop - alignedTop) > ALIGNMENT_EPSILON
                || (!crossedSurface && !reachedCollisionMargin)
                || supportTop > previousBottom + ALIGNMENT_EPSILON
                || !hasHorizontalOverlap(fallingBox, supportBox)
                || supportTop <= landingY) {
                continue;
            }
            landingSupport = candidate;
            landingY = alignedTop;
        }
        if (landingSupport == null) return false;

        fallingEntity.setPos(fallingEntity.getX(), landingY, fallingEntity.getZ());
        fallingEntity.setOnGround(true);
        return true;
    }

    /** 树脂铁砧使用不完整方块碰撞，普通下落方块接触后应碎裂而不是与其重叠。 */
    public static boolean touchesIncompletePlasticAnvil(FallingBlockEntity fallingEntity) {
        AABB fallingBox = fallingEntity.getBoundingBox();
        double currentBottom = fallingBox.minY;
        AABB probe = new AABB(
            fallingBox.minX,
            currentBottom - PROBE_DEPTH,
            fallingBox.minZ,
            fallingBox.maxX,
            currentBottom + ALIGNMENT_EPSILON,
            fallingBox.maxZ
        );
        boolean entityContact = !fallingEntity.level().getEntitiesOfClass(
            AbstractPlasticEntity.class,
            probe,
            candidate -> candidate != fallingEntity
                && candidate.isAlive()
                && (candidate instanceof ResinAnvilEntity || candidate instanceof HardenedResinAnvilEntity)
                && hasHorizontalOverlap(fallingBox, candidate.getBoundingBox())
        ).isEmpty();
        if (entityContact) return true;

        int minX = Mth.floor(probe.minX + ALIGNMENT_EPSILON);
        int maxX = Mth.floor(probe.maxX - ALIGNMENT_EPSILON);
        int minY = Mth.floor(probe.minY);
        int maxY = Mth.floor(probe.maxY);
        int minZ = Mth.floor(probe.minZ + ALIGNMENT_EPSILON);
        int maxZ = Mth.floor(probe.maxZ - ALIGNMENT_EPSILON);
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            BlockState state = fallingEntity.level().getBlockState(pos);
            if (!state.is(ModBlocks.RESIN_ANVIL.get()) && !state.is(ModBlocks.HARDEND_RESIN_ANVIL.get())) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(fallingEntity.level(), pos);
            if (shape.toAabbs().stream().map(box -> box.move(pos)).anyMatch(probe::intersects)) return true;
        }
        return false;
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
        return hasHorizontalOverlap(
            box,
            new AABB(
                fallingBlockPos.getX(),
                box.minY,
                fallingBlockPos.getZ(),
                fallingBlockPos.getX() + 1.0D,
                box.maxY,
                fallingBlockPos.getZ() + 1.0D
            )
        );
    }

    private static boolean hasHorizontalOverlap(AABB first, AABB second) {
        double overlapX = Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX);
        double overlapZ = Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ);
        return overlapX > ALIGNMENT_EPSILON && overlapZ > ALIGNMENT_EPSILON;
    }

    private static boolean isFullBlockSize(AbstractPlasticEntity candidate) {
        return candidate.getBbWidth() >= 1.0D - ALIGNMENT_EPSILON
            && candidate.getBbHeight() >= 1.0D - ALIGNMENT_EPSILON;
    }
}
