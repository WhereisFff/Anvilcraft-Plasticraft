package dev.anvilcraft.plasticraft.molding.machine;

import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.item.SuperCapacitorItem;

/** 隔离本体电力配置与容量常量，后续版本迁移只需替换此边界。 */
public final class MoldingPowerBridge {
    public static final int RATED_POWER_KW = 256;

    private MoldingPowerBridge() {
    }

    public static int capacity() {
        return SuperCapacitorItem.ENERGY;
    }

    public static int energyPerWorkingTick() {
        return Math.multiplyExact(RATED_POWER_KW, AnvilCraft.CONFIG.powerConverter.powerConverterEfficiency);
    }
}
