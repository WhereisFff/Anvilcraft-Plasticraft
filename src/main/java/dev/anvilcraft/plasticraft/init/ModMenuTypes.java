package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.MenuEntry;
import dev.anvilcraft.plasticraft.client.gui.screen.HardenedResinAnvilScreen;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class ModMenuTypes {
    public static final MenuEntry<HardenedResinAnvilMenu> HARDEND_RESIN_ANVIL = REGISTRUM
        .menu(
            "hardend_resin_anvil",
            (type, id, inventory, buffer) -> new HardenedResinAnvilMenu(type, id, inventory, buffer),
            () -> HardenedResinAnvilScreen::new
        )
        .register();

    private ModMenuTypes() {
    }

    public static void register() {
        HardenedResinAnvilMenu.configureMenuType(HARDEND_RESIN_ANVIL::get);
    }

}
