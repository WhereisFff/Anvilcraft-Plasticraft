package dev.anvilcraft.plasticraft.drone;

import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.item.CapacitorItem;

/**
 * 无人机统一能耗模型:
 * 总消耗 = 256 FE x 空中 gt + 256 FE x 实际飞行格数 + 2,560 FE x 瞬时操作次数。
 * 容量与充电速率引用 AnvilCraft 本体常量和功率转换效率,不复制容易漂移的魔法数。
 */
public final class DroneEnergyModel {
    /** 未着地或入库时每 gt 扣除的悬浮 FE。 */
    public static final int HOVER_COST_PER_AIR_TICK = 256;
    /** 按服务端实际轨迹长度累计的每格飞行 FE。 */
    public static final int MOVE_COST_PER_BLOCK = 256;
    /** 电网内每架未满电无人机请求的充电功率(kW)。 */
    public static final int CHARGE_POWER_KW = 8;
    /**
     * 安全降落储备:四格悬停高度触地约需 30 gt,预留约 80 gt 悬浮机动余量;
     * 世界观察无人机达到该储备前必须停止领取新覆盖并降落。
     */
    public static final long SAFE_LANDING_RESERVE = HOVER_COST_PER_AIR_TICK * 80L;
    /**
     * 无任务观察无人机的起飞门槛:高于两倍降落储备才起飞,
     * 避免电量在门槛附近来回穿越造成起飞降落抖动。
     */
    public static final long IDLE_TAKEOFF_MINIMUM = SAFE_LANDING_RESERVE * 2L;

    private DroneEnergyModel() {
    }

    /** 每架无人机的 FE 容量,直接跟随本体电容器当前容量。 */
    public static int capacity() {
        return CapacitorItem.ENERGY;
    }

    /** 每 gt 充入的 FE:8 功率按本体当前功率转换效率换算。 */
    public static int chargePerTick() {
        return Math.multiplyExact(CHARGE_POWER_KW, AnvilCraft.CONFIG.powerConverter.powerConverterEfficiency);
    }

    /**
     * 任务能量报价:预计空中时间、实际路径长度与瞬时操作数。
     * 分配前还需叠加安全降落储备,报价本身不含储备。
     *
     * @param airTicks       预计空中 gt,含前进、转弯、排队与悬停
     * @param flightDistance 预计实际轨迹长度(格),不按起终点直线计算
     * @param instantActions 预计瞬时操作次数
     */
    public record Quote(int airTicks, double flightDistance, int instantActions) {
        public long totalCost(long costPerInstantAction) {
            return (long) this.airTicks * HOVER_COST_PER_AIR_TICK
                + (long) Math.ceil(this.flightDistance) * MOVE_COST_PER_BLOCK
                + this.instantActions * costPerInstantAction;
        }
    }
}
