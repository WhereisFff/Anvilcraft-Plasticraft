package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.entity.PlasticFluidPhysics;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class PlasticEntityEvents {
    private PlasticEntityEvents() {
    }

    @SubscribeEvent
    public static void afterEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof ItemEntity item) {
            PlasticFluidPhysics.floatPlasticItem(item);
        }
    }
}
