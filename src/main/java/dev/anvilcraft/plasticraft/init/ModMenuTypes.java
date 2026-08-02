package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.MenuEntry;
import dev.anvilcraft.plasticraft.client.gui.screen.HardenedResinAnvilScreen;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticMoldingChamberScreen;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

public final class ModMenuTypes {
    public static final MenuEntry<PlasticMoldingChamberMenu> PLASTIC_MOLDING_CHAMBER = REGISTRUM
        .menu(
            "plastic_molding_chamber",
            (type, id, inventory, buffer) -> new PlasticMoldingChamberMenu(type, id, inventory, buffer),
            () -> PlasticMoldingChamberScreen::new
        )
        .register();

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
