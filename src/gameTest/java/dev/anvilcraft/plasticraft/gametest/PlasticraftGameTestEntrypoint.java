package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.testframework.conf.FrameworkConfiguration;
import net.neoforged.testframework.impl.MutableTestFramework;

/** Test-only second entry point, absent from the published main source set. */
@Mod(PlasticraftGameTestEntrypoint.MOD_ID)
public final class PlasticraftGameTestEntrypoint {
    public static final String MOD_ID = AnvilcraftPlasticraft.MOD_ID + "_tests";

    public PlasticraftGameTestEntrypoint(IEventBus modEventBus, ModContainer container) {
        MutableTestFramework framework = FrameworkConfiguration.builder(AnvilcraftPlasticraft.of("tests"))
            .build()
            .create();
        framework.init(modEventBus, container);
    }
}
