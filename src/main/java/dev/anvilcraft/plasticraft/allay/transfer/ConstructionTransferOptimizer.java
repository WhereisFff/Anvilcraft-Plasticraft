package dev.anvilcraft.plasticraft.allay.transfer;

import dev.anvilcraft.plasticraft.allay.AllayFlightPlanner;

/**
 * 预计完工时间优化器(纯函数,便于测试)。
 * 只在“借调一只同能力悦灵能缩短完工时间”时判定有收益,否则拒绝无收益的远程借调。
 */
public final class ConstructionTransferOptimizer {
    /** 单操作周期估算:往返取料 + 交付 + 调度等待(游戏刻),只用于相对比较,取保守中间值。 */
    public static final long C_OP_TICKS = 200L;
    /** 每跳固定开销:20 gt 出库通道 + 20 gt 入栈通道。 */
    public static final long HOP_OVERHEAD_TICKS = 40L;

    private ConstructionTransferOptimizer() {
    }

    /**
     * 借调单只同能力悦灵抵达任务现场的预计成本(游戏刻):沿链累计飞行距离按 0.25 格/gt 估算,
     * 再加每跳固定通道开销。
     */
    public static double transferTicks(int hops, double totalDistance) {
        return hops * HOP_OVERHEAD_TICKS + totalDistance / AllayFlightPlanner.SPEED;
    }

    /**
     * 是否存在收益。设现有工人数 W(本地已到场 + 在途)、剩余未租操作数 R、单操作周期 C、转运耗时 T:
     * 不借调的预计完工时间为 C·R/W,借调后为 (T + C·R)/(W+1),两者相减可知
     * 只要 W·T &lt; C·R 就能缩短完工时间。W 为 0 时任务根本无法推进,只要还有剩余操作就必须借调。
     */
    public static boolean isBeneficial(int loadedWorkers, int inTransit, int remainingOps, double transferTicks) {
        if (remainingOps <= 0) return false;
        long workers = (long) Math.max(loadedWorkers, 0) + Math.max(inTransit, 0);
        if (workers <= 0) return true;
        return transferTicks * workers < (double) C_OP_TICKS * remainingOps;
    }
}
