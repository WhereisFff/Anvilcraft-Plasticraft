package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.dubhe.anvilcraft.init.item.ModItemGroups;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PlasticItemGroups {
    public static final String TITLE_KEY = "itemGroup.anvilcraftplasticraft.main";
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(
        Registries.CREATIVE_MODE_TAB,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register(
        "main",
        () -> CreativeModeTab.builder()
            .icon(PlasticBlocks.PLASTIC_ANVIL::asStack)
            .displayItems((parameters, output) -> {
                output.accept(PlasticBlocks.PLASTIC_ANVIL.asItem());
                output.accept(PlasticBlocks.PLASTIC_POT.asItem());
            })
            .title(Component.translatable(TITLE_KEY))
            .withTabsAfter(ModItemGroups.ANVILCRAFT_INGREDIENTS.getId())
            .build()
    );

    private PlasticItemGroups() {
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
