package dev.anvilcraft.plasticraft.recipe;

import dev.dubhe.anvilcraft.api.heat.collector.HeatCollectorManager;
import dev.dubhe.anvilcraft.block.power.consumer.HeaterBlock;
import dev.dubhe.anvilcraft.block.workstation.BurningHeaterBlock;
import net.minecraft.world.level.block.state.BlockState;

/** 将集热器发电功率和两种加热器状态统一换算为催化速度。 */
public final class PlasticCatalysisHeat {
    private PlasticCatalysisHeat() {
    }

    public static int power(BlockState state) {
        if (state.getBlock() instanceof BurningHeaterBlock) {
            int level = state.getValue(BurningHeaterBlock.LEVEL);
            if (level == 2) {
                return 8;
            }
            if (level == 1) {
                return 2;
            }
            return 0;
        }
        if (state.getBlock() instanceof HeaterBlock heater && heater.isActive(state)) {
            return 8;
        }
        return HeatCollectorManager.getEntry(state).map(entry -> entry.accepts(state)).orElse(0);
    }

}
