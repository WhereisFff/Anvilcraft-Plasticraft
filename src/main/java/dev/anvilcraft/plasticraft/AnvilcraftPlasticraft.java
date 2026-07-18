package dev.anvilcraft.plasticraft;

import com.mojang.logging.LogUtils;
import dev.anvilcraft.plasticraft.data.PlasticDatagen;
import dev.anvilcraft.plasticraft.init.PlasticBlocks;
import dev.anvilcraft.plasticraft.init.PlasticEntities;
import dev.anvilcraft.plasticraft.init.PlasticItemGroups;
import dev.anvilcraft.plasticraft.init.PlasticMenuTypes;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AnvilcraftPlasticraft.MOD_ID)
public final class AnvilcraftPlasticraft {
    public static final String MOD_ID = "anvilcraftplasticraft";
    public static final String MOD_NAME = "Anvilcraft: Plasticraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Registrum REGISTRUM = Registrum.create(MOD_ID)
        .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);

    public AnvilcraftPlasticraft(IEventBus modEventBus, ModContainer ignored) {
        PlasticItemGroups.register(modEventBus);
        PlasticBlocks.register();
        PlasticEntities.register();
        PlasticMenuTypes.register();
        PlasticDatagen.init();
        LOGGER.info("Loading {}", MOD_NAME);
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
