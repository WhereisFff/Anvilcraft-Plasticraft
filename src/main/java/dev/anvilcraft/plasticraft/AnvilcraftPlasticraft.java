package dev.anvilcraft.plasticraft;

import com.mojang.logging.LogUtils;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.data.PlasticraftDatagen;
import dev.anvilcraft.plasticraft.event.HighViscosityResinEvents;
import dev.anvilcraft.plasticraft.init.ModParticles;
import dev.anvilcraft.plasticraft.init.ModRecipeTypes;
import dev.anvilcraft.plasticraft.init.ModAttachments;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModBlockEntities;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.entity.ModEntities;
import dev.anvilcraft.plasticraft.init.item.ModItemGroups;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.init.ModMenuTypes;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EscapingVaporEffects;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetVaporizationSource;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.anvilcraft.lib.v2.network.register.NetworkRegistrar;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.VaporizationSources;
import dev.anvilcraft.lib.v2.yukkuri.api.vapor.YukkuriCapabilities;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
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
        ModAttachments.register(modEventBus);
        ModItemGroups.register(modEventBus);
        ModFluids.register(modEventBus);
        ModBlocks.register();
        ModBlockEntities.register();
        ModItems.register();
        PlasticItemTooltipManager.init();
        ModEntities.register();
        ModMenuTypes.register();
        ModParticles.register(modEventBus);
        ModRecipeTypes.register(modEventBus);
        PlasticraftDatagen.init();
        VaporizationSources.register(PlasmaJetVaporizationSource.INSTANCE);
        NeoForge.EVENT_BUS.addListener(AnvilcraftPlasticraft::addItemTooltips);
        NeoForge.EVENT_BUS.addListener(HighViscosityResinEvents::useEntity);
        NeoForge.EVENT_BUS.addListener(CondenserTowerProcess::onLargeCauldronProcess);
        NeoForge.EVENT_BUS.addListener(EscapingVaporEffects::rightClickBlock);
        modEventBus.addListener(HighViscosityResinEvents::registerCauldronFluidContent);
        modEventBus.addListener(ModBlocks::registerDispenserBehavior);
        modEventBus.addListener(AnvilcraftPlasticraft::registerCapabilities);
        modEventBus.addListener(AnvilcraftPlasticraft::registerPayloads);
        LOGGER.info("Loading {}", MOD_NAME);
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addItemTooltips(ItemTooltipEvent event) {
        PlasticItemTooltipManager.addTooltip(event.getItemStack(), event.getToolTip());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY,
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            (cauldron, side) -> cauldron.getItemHandler()
        );
        event.registerEntity(
            Capabilities.FluidHandler.ENTITY,
            ModEntities.HARDEND_RESIN_CAULDRON.get(),
            (cauldron, side) -> cauldron.getFluidHandler()
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            ModBlockEntities.BONDED_ENTITY.get(),
            (bonded, side) -> bonded.getCapabilityFluidHandler()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            ModBlockEntities.BONDED_ENTITY.get(),
            (bonded, side) -> bonded.getItemHandler()
        );
        event.registerBlock(
            Capabilities.FluidHandler.BLOCK,
            dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity::capability,
            ModBlocks.CONDENSER_TOWER.get()
        );
        event.registerBlock(
            YukkuriCapabilities.VAPOR_CONSUMER,
            dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity::vaporCapability,
            ModBlocks.CONDENSER_TOWER.get()
        );
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        NetworkRegistrar.register(event.registrar("1"), MOD_ID);
    }
}
