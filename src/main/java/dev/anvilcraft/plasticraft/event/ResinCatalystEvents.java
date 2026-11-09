package dev.anvilcraft.plasticraft.event;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.recipe.ResinCatalysts;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

@EventBusSubscriber(modid = AnvilcraftPlasticraft.MOD_ID)
public final class ResinCatalystEvents {
    private ResinCatalystEvents() {
    }

    @SubscribeEvent
    public static void onTagsUpdated(TagsUpdatedEvent event) {
        ResinCatalysts.clear();
    }
}
