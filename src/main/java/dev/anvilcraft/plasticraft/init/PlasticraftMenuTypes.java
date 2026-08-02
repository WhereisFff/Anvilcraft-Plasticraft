package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.MenuEntry;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.gui.screen.HardenedResinAnvilScreen;
import dev.anvilcraft.plasticraft.client.gui.screen.PlasticMoldingChamberScreen;
import dev.anvilcraft.plasticraft.inventory.HardenedResinAnvilMenu;
import dev.anvilcraft.plasticraft.inventory.PlasticMoldingChamberMenu;

public final class PlasticraftMenuTypes {
    public static final MenuEntry<PlasticMoldingChamberMenu> PLASTIC_MOLDING_CHAMBER = AnvilcraftPlasticraft.REGISTRUM
        .menu(
            "plastic_molding_chamber",
            (type, id, inventory, buffer) -> new PlasticMoldingChamberMenu(type, id, inventory, buffer),
            () -> PlasticMoldingChamberScreen::new
        )
        .register();

    public static final MenuEntry<HardenedResinAnvilMenu> HARDEND_RESIN_ANVIL = AnvilcraftPlasticraft.REGISTRUM
        .menu(
            "hardend_resin_anvil",
            (type, id, inventory, buffer) -> new HardenedResinAnvilMenu(type, id, inventory, buffer),
            () -> HardenedResinAnvilScreen::new
        )
        .register();

    private PlasticraftMenuTypes() {
    }

    public static void register() {
        HardenedResinAnvilMenu.configureMenuType(HARDEND_RESIN_ANVIL::get);
    }

}
