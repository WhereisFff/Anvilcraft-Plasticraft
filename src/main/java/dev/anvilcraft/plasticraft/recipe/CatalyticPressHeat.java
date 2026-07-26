package dev.anvilcraft.plasticraft.recipe;

import dev.dubhe.anvilcraft.api.heat.collector.HeatCollectorManager;
import dev.dubhe.anvilcraft.block.BurningHeaterBlock;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 将集热器发电功率和两种加热器状态统一换算为催化速度。 */
public final class CatalyticPressHeat {
    private CatalyticPressHeat() {
    }

    public static int power(BlockState state) {
        if (state.getBlock() instanceof BurningHeaterBlock) {
            int level = state.getValue(BurningHeaterBlock.LEVEL);
            if (level == 2) return 8;
            if (level == 1) return 2;
            return 0;
        }
        if (state.getBlock() instanceof HeaterBlock heater && heater.isActive(state)) return 8;
        return HeatCollectorManager.getEntry(state).map(entry -> entry.accepts(state)).orElse(0);
    }

    public static int processingTime(int baseTime, BlockState state) {
        int power = power(state);
        return power <= 0 ? 0 : (baseTime + power - 1) / power;
    }
}
