package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.dubhe.anvilcraft.util.AccelerateManager;
import dev.dubhe.anvilcraft.util.GravityManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * 管理单个塑料实体的休眠生命周期：进入与退出休眠、空间索引的登记与注销、错峰复核。
 *
 * <p>进入休眠的判定依赖 {@link AbstractPlasticEntity} 的私有运动学状态，因此条件评估留在实体内；
 * 本类持有休眠标志、连续静止计数、已登记的方块格与复核基线，并负责复核这些没有显式唤醒钩子的外部影响。</p>
 */
public final class PlasticEntityRestState {
    /** 错峰复核周期。重力场与加速环的状态变化不经过方块写入，最坏 4 刻内被复核发现。 */
    public static final int AUDIT_INTERVAL = 4;
    /** 沉降保护：加速请求连续为零若干刻后才允许休眠，避免加速环边界抖动导致反复进出休眠。 */
    private static final int CALM_THRESHOLD = 2;

    private boolean resting;
    private int calmStreak;
    private Set<BlockPos> registeredCells = Set.of();
    private Direction restGravityDirection;

    public boolean isResting() {
        return this.resting;
    }

    /** 累计连续静止刻数，任一刻不满足即清零；返回是否已越过沉降阈值。 */
    public boolean updateCalmStreak(boolean calm) {
        this.calmStreak = calm ? this.calmStreak + 1 : 0;
        return this.calmStreak >= CALM_THRESHOLD;
    }

    public void enter(AbstractPlasticEntity entity, Direction gravityDirection) {
        Set<BlockPos> cells = restCells(entity);
        if (cells.isEmpty()) return;
        this.registeredCells = cells;
        this.restGravityDirection = gravityDirection;
        this.resting = true;
        PlasticRestIndex.register(entity.level(), entity, cells);
    }

    public void exit(AbstractPlasticEntity entity) {
        if (!this.resting) return;
        this.resting = false;
        this.calmStreak = 0;
        this.restGravityDirection = null;
        Set<BlockPos> cells = this.registeredCells;
        this.registeredCells = Set.of();
        PlasticRestIndex.unregister(entity.level(), entity, cells);
    }

    /** 按实体 id 错峰，使同一批休眠实体的复核成本摊到 {@link #AUDIT_INTERVAL} 刻里。 */
    public boolean shouldAudit(long gameTime, int entityId) {
        return Math.floorMod(gameTime + entityId, AUDIT_INTERVAL) == 0;
    }

    /**
     * 复核休眠前提是否仍然成立。
     *
     * <p>只检查没有显式唤醒钩子的来源：AnvilCraft 的重力场方向、加速环接管状态。
     * 方块支撑与流体本应由方块写入钩子覆盖，这里一并复核作为兜底。</p>
     */
    public boolean auditRequiresWake(AbstractPlasticEntity entity) {
        if (this.restGravityDirection == null) return true;
        if (AccelerateManager.isControlledByRing(entity)) return true;
        Vec3 gravity = entity.isNoGravity()
            ? Vec3.ZERO
            : GravityManager.getNetGravityVectorForFallingBlock(entity);
        if (PlasticEntityPhysics.directionOrNull(gravity) != this.restGravityDirection) return true;
        if (PlasticFluidPhysics.sample(entity).isPresent()) return true;
        return !PlasticEntityPhysics.hasBlockSupport(entity, this.restGravityDirection);
    }

    @Nullable
    public Direction restGravityDirection() {
        return this.restGravityDirection;
    }

    /**
     * 登记范围取碰撞子盒外扩一格覆盖的方块格，因此下方支撑格与四周接触格都在索引内；
     * 唤醒侧只需用事件自身的坐标或包围盒查询，不必再外扩。
     */
    private static Set<BlockPos> restCells(AbstractPlasticEntity entity) {
        Set<BlockPos> cells = new HashSet<>();
        for (AABB component : entity.plasticraft$getCollisionBox().components()) {
            for (BlockPos pos : PlasticRestIndex.cellsOf(component.inflate(1.0D))) {
                cells.add(pos.immutable());
            }
        }
        return cells;
    }
}
