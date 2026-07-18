package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.MenuEntry;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticAnvilScreen;
import dev.anvilcraft.plasticraft.inventory.PlasticAnvilMenu;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class PlasticMenuTypes {
    public static final MenuEntry<PlasticAnvilMenu> PLASTIC_ANVIL = REGISTRUM
        .menu(
            "plastic_anvil",
            (type, id, inventory, buffer) -> new PlasticAnvilMenu(type, id, inventory, buffer),
            () -> PlasticAnvilScreen::new
        )
        .register();

    private PlasticMenuTypes() {
    }

    public static void register() {
        PlasticAnvilMenu.configureMenuType(PLASTIC_ANVIL::get);
    }
}
