package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneScheduler;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** 清理维度卸载后不再有效的支架红石调度状态。 */
@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class MoldedTrayRedstoneEvents {
    private MoldedTrayRedstoneEvents() {
    }

    @SubscribeEvent
    public static void levelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) MoldedTrayRedstoneScheduler.clear(level);
    }
}
