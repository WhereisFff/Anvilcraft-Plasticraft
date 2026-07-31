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

/** 让碰撞顶面与世界网格对齐的塑料实体支撑普通下落方块。 */
public final class PlasticFallingBlockSupport {
    private static final double ALIGNMENT_EPSILON = 1.0E-3D;
    private static final double PROBE_DEPTH = 0.05D;
    /** 实体注册尺寸只用于宽阶段检索，实际支撑始终由独立碰撞子箱决定。 */
    private static final double COLLISION_QUERY_MARGIN = 1.0D;

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
        AABB fallingBlockBox = new AABB(fallingBlockPos);
        return level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            probe.inflate(COLLISION_QUERY_MARGIN),
            candidate -> candidate != fallingEntity
                && hasAlignedCollisionTop(candidate, fallingBlockBox, supportY)
        );
    }

    /** 方块态下落方块只按下方逻辑格是否被塑料实体占用判断，不要求其顶面对齐网格。 */
    public static boolean occupiesCellBelow(Level level, BlockPos fallingBlockPos) {
        AABB supportCell = new AABB(fallingBlockPos.below());
        return !level.getEntitiesOfClass(
            AbstractPlasticEntity.class,
            supportCell.inflate(COLLISION_QUERY_MARGIN),
            candidate -> candidate.isAlive()
                && candidate.plasticraft$getCollisionBox().components().stream().anyMatch(supportCell::intersects)
        ).isEmpty();
    }

    /** 区分整格顶面着陆、弹性反弹和非整格顶面碎裂，避免继续进入 AnvilCraft 的实体碎裂判定。 */
    public static LandingAction resolveLanding(FallingBlockEntity fallingEntity) {
        double velocityY = fallingEntity.getDeltaMovement().y;
        AABB fallingBox = fallingEntity.getBoundingBox();
        double currentBottom = fallingBox.minY;
        double previousBottom = currentBottom - Math.min(velocityY, 0.0D);
        double supportTop = findHighestSupportTop(
            fallingEntity,
            fallingBox,
            currentBottom - PROBE_DEPTH,
            previousBottom + ALIGNMENT_EPSILON
        );
        BlockPos supportCellPos = fallingEntity.blockPosition().below();
        if (!Double.isFinite(supportTop)
            && fallingEntity.verticalCollisionBelow
            && FallingBlock.isFree(fallingEntity.level().getBlockState(supportCellPos))) {
            AABB supportCell = new AABB(supportCellPos);
            supportTop = findHighestSupportTop(
                fallingEntity,
                fallingBox,
                supportCell.minY,
                Math.min(currentBottom, supportCell.maxY) + ALIGNMENT_EPSILON
            );
        }
        if (!Double.isFinite(supportTop)) return LandingAction.NONE;

        double landingY = Math.rint(supportTop);
        if (Math.abs(supportTop - landingY) > ALIGNMENT_EPSILON) {
            fallingEntity.setPos(fallingEntity.getX(), supportTop, fallingEntity.getZ());
            return LandingAction.BREAK;
        }
        if (velocityY > 0.0D) {
            fallingEntity.setOnGround(false);
            return LandingAction.BOUNCE;
        }

        fallingEntity.setPos(fallingEntity.getX(), landingY, fallingEntity.getZ());
        fallingEntity.setOnGround(true);
        return LandingAction.LAND;
    }

    private static double findHighestSupportTop(
        FallingBlockEntity fallingEntity,
        AABB fallingBox,
        double minimumTop,
        double maximumTop
    ) {
        if (maximumTop < minimumTop) return Double.NEGATIVE_INFINITY;
        AABB probe = new AABB(
            fallingBox.minX,
            minimumTop,
            fallingBox.minZ,
            fallingBox.maxX,
            maximumTop,
            fallingBox.maxZ
        );
        double supportTop = Double.NEGATIVE_INFINITY;
        for (AbstractPlasticEntity candidate : fallingEntity.level().getEntitiesOfClass(
            AbstractPlasticEntity.class,
            probe.inflate(COLLISION_QUERY_MARGIN),
            candidate -> candidate != fallingEntity && candidate.isAlive()
        )) {
            for (AABB component : candidate.plasticraft$getCollisionBox().components()) {
                double componentTop = component.maxY;
                if (componentTop < minimumTop
                    || componentTop > maximumTop
                    || !hasHorizontalOverlap(fallingBox, component)
                    || componentTop <= supportTop) {
                    continue;
                }
                supportTop = componentTop;
            }
        }
        return supportTop;
    }

    public static Set<BlockPos> updateSupportChecks(
        AbstractPlasticEntity support,
        Set<BlockPos> previousPositions
    ) {
        if (!(support.level() instanceof ServerLevel level)) return Set.of();
        Set<BlockPos> currentPositions = fallingBlockPositions(level, support);
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

    private static Set<BlockPos> fallingBlockPositions(ServerLevel level, AbstractPlasticEntity support) {
        Set<BlockPos> positions = new HashSet<>();
        for (AABB component : support.plasticraft$getCollisionBox().components()) {
            int fallingY = Mth.floor(component.maxY - ALIGNMENT_EPSILON) + 1;
            int minX = Mth.floor(component.minX + ALIGNMENT_EPSILON);
            int maxX = Mth.floor(component.maxX - ALIGNMENT_EPSILON);
            int minZ = Mth.floor(component.minZ + ALIGNMENT_EPSILON);
            int maxZ = Mth.floor(component.maxZ - ALIGNMENT_EPSILON);
            for (BlockPos pos : BlockPos.betweenClosed(minX, fallingY, minZ, maxX, fallingY, maxZ)) {
                if (level.getBlockState(pos).getBlock() instanceof FallingBlock) positions.add(pos.immutable());
            }
        }
        return positions;
    }

    private static boolean hasAlignedCollisionTop(
        AbstractPlasticEntity candidate,
        AABB fallingBlockBox,
        double supportY
    ) {
        if (!candidate.isAlive()) return false;
        return candidate.plasticraft$getCollisionBox().components().stream().anyMatch(component ->
            Math.abs(component.maxY - supportY) <= ALIGNMENT_EPSILON
                && hasHorizontalOverlap(component, fallingBlockBox)
        );
    }

    private static boolean hasHorizontalOverlap(AABB first, AABB second) {
        double overlapX = Math.min(first.maxX, second.maxX) - Math.max(first.minX, second.minX);
        double overlapZ = Math.min(first.maxZ, second.maxZ) - Math.max(first.minZ, second.minZ);
        return overlapX > ALIGNMENT_EPSILON && overlapZ > ALIGNMENT_EPSILON;
    }

    public enum LandingAction {
        NONE,
        BOUNCE,
        LAND,
        BREAK
    }
}
