package dev.anvilcraft.plasticraft.entity.physics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 全局方块写入计数器，供方块支撑判定与遮挡剔除的记忆化缓存判断「方块是否可能已经变化」。
 *
 * <p>只保证发生写入时数值一定变化，不区分维度也不区分位置。无关维度或远处的写入只会让缓存
 * 多算一次，永远不会让缓存读到过期结果，因此不需要按 {@code Level} 分桶带来的额外同步成本。</p>
 */
public final class PlasticBlockEpoch {
    private static final AtomicLong COUNTER = new AtomicLong();

    private PlasticBlockEpoch() {
    }

    public static void bump() {
        COUNTER.incrementAndGet();
    }

    public static long current() {
        return COUNTER.get();
    }
}
