package dev.anvilcraft.plasticraft.client;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.block.PlasticPotBlock;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@Mod(value = AnvilcraftPlasticraft.MOD_ID, dist = Dist.CLIENT)
public final class AnvilcraftPlasticraftClient {
    public AnvilcraftPlasticraftClient(IEventBus ignoredModBus, ModContainer ignoredContainer) {
        ignoredModBus.addListener(AnvilcraftPlasticraftClient::registerBlockColors);
        ignoredModBus.addListener(AnvilcraftPlasticraftClient::registerItemColors);
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tintIndex) -> PlasticAnvilBlock.tint(state), PlasticBlocks.PLASTIC_ANVIL.get());
        event.register((state, level, pos, tintIndex) -> PlasticPotBlock.tint(state), PlasticBlocks.PLASTIC_POT.get());
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
            (stack, tintIndex) -> PlasticAnvilBlock.tint(PlasticAnvilItem.getColor(stack)),
            PlasticBlocks.PLASTIC_ANVIL.asItem()
        );
        event.register(
            (stack, tintIndex) -> PlasticAnvilBlock.tint(PlasticItemData.getColor(stack)),
            PlasticBlocks.PLASTIC_POT.asItem()
        );
    }
}
