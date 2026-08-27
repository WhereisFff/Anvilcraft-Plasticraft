package dev.anvilcraft.plasticraft.entity.physics;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 按方块格索引休眠中的塑料实体，使方块写入和邻域事件能在常数时间内定位需要唤醒的实体。
 *
 * <p>登记的是碰撞子盒外扩一格后覆盖的全部方块格，因此「支撑格」和「侧向接触格」都在索引里；
 * 只要事件发生在实体一格范围内就必定命中，不需要在唤醒侧再做外扩。</p>
 */
public final class PlasticRestIndex {
    private static final Map<Level, LevelIndex> LEVELS = new WeakHashMap<>();

    private PlasticRestIndex() {
    }

    public static void register(Level level, AbstractPlasticEntity entity, Set<BlockPos> cells) {
        if (cells.isEmpty()) return;
        synchronized (LEVELS) {
            LEVELS.computeIfAbsent(level, ignored -> new LevelIndex()).put(entity, cells);
        }
    }

    public static void unregister(Level level, AbstractPlasticEntity entity, Set<BlockPos> cells) {
        if (cells.isEmpty()) return;
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return;
            index.remove(entity, cells);
            if (index.isEmpty()) LEVELS.remove(level);
        }
    }

    /** 单点方块写入或方块事件的唤醒入口。 */
    public static void wakeAt(Level level, BlockPos pos) {
        wake(collectAt(level, pos));
    }

    /** 实体移动、生成、移除时唤醒其覆盖范围内的休眠实体。 */
    public static void wakeInBounds(Level level, AABB bounds) {
        wake(collectInBounds(level, bounds));
    }

    private static List<AbstractPlasticEntity> collectAt(Level level, BlockPos pos) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null) return List.of();
            Set<AbstractPlasticEntity> entities = index.entitiesByCell.get(pos.asLong());
            return entities == null ? List.of() : new ArrayList<>(entities);
        }
    }

    private static List<AbstractPlasticEntity> collectInBounds(Level level, AABB bounds) {
        synchronized (LEVELS) {
            LevelIndex index = LEVELS.get(level);
            if (index == null || index.entitiesByCell.isEmpty()) return List.of();
            Set<AbstractPlasticEntity> collected = null;
            for (BlockPos pos : cellsOf(bounds)) {
                Set<AbstractPlasticEntity> entities = index.entitiesByCell.get(pos.asLong());
                if (entities == null) continue;
                if (collected == null) collected = new HashSet<>();
                collected.addAll(entities);
            }
            return collected == null ? List.of() : new ArrayList<>(collected);
        }
    }

    /**
     * 唤醒必须在释放索引锁之后执行：{@code plasticraft$wakeFromRest} 会回头调用
     * {@link #unregister} 注销自身，在锁内调用会形成重入并在遍历中修改桶。
     */
    private static void wake(List<AbstractPlasticEntity> entities) {
        for (AbstractPlasticEntity entity : entities) entity.plasticraft$wakeFromRest();
    }

    public static Iterable<BlockPos> cellsOf(AABB bounds) {
        return BlockPos.betweenClosed(
            Mth.floor(bounds.minX),
            Mth.floor(bounds.minY),
            Mth.floor(bounds.minZ),
            Mth.floor(bounds.maxX),
            Mth.floor(bounds.maxY),
            Mth.floor(bounds.maxZ)
        );
    }

    private static final class LevelIndex {
        private final Map<Long, Set<AbstractPlasticEntity>> entitiesByCell = new HashMap<>();

        private void put(AbstractPlasticEntity entity, Set<BlockPos> cells) {
            for (BlockPos cell : cells) {
                this.entitiesByCell.computeIfAbsent(cell.asLong(), ignored -> new HashSet<>()).add(entity);
            }
        }

        private void remove(AbstractPlasticEntity entity, Set<BlockPos> cells) {
            for (BlockPos cell : cells) {
                long key = cell.asLong();
                Set<AbstractPlasticEntity> entities = this.entitiesByCell.get(key);
                if (entities == null) continue;
                entities.remove(entity);
                if (entities.isEmpty()) this.entitiesByCell.remove(key);
            }
        }

        private boolean isEmpty() {
            return this.entitiesByCell.isEmpty();
        }
    }
}
