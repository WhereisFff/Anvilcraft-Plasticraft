package dev.anvilcraft.plasticraft;

import com.mojang.logging.LogUtils;
import dev.anvilcraft.lib.v2.config.ConfigManager;
import dev.anvilcraft.lib.v2.network.register.NetworkRegistrar;
import dev.anvilcraft.lib.v2.registrum.Registrum;
import dev.anvilcraft.plasticraft.api.tooltip.PlasticItemTooltipManager;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.config.PlasticraftClientConfig;
import dev.anvilcraft.plasticraft.data.PlasticraftDatagen;
import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveInvisibilityService;
import dev.anvilcraft.plasticraft.event.AnvilCraftApiBootstrap;
import dev.anvilcraft.plasticraft.event.HighViscosityResinEvents;
import dev.anvilcraft.plasticraft.event.PlasticVillagerTrades;
import dev.anvilcraft.plasticraft.fluid.UniversalPlasticMeltBucketWrapper;
import dev.anvilcraft.plasticraft.init.PlasticraftAttachments;
import dev.anvilcraft.plasticraft.init.PlasticraftDataComponents;
import dev.anvilcraft.plasticraft.init.PlasticraftMenuTypes;
import dev.anvilcraft.plasticraft.init.PlasticraftParticles;
import dev.anvilcraft.plasticraft.init.PlasticraftRecipeTypes;
import dev.anvilcraft.plasticraft.init.PlasticraftStorageTypes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.entity.PlasticraftEntities;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemGroups;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticFluidHandler;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticItemHandler;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.recipe.CondenserTowerProcess;
import dev.anvilcraft.plasticraft.recipe.EscapingVaporEffects;
import dev.anvilcraft.plasticraft.recipe.PlasmaJetVaporizationSource;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.anvilcraft.plasticraft.vapor.VaporCapabilities;
import dev.anvilcraft.plasticraft.vapor.VaporizationSources;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

import java.util.Arrays;

@Mod(AnvilcraftPlasticraft.MOD_ID)
public final class AnvilcraftPlasticraft {
    public static final String MOD_ID = "anvilcraftplasticraft";
    public static final String MOD_NAME = "Anvilcraft: Plasticraft";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final Registrum REGISTRUM = Registrum.create(MOD_ID)
        .defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
    public static final PlasticraftClientConfig CLIENT_CONFIG = ConfigManager.register(
        MOD_ID,
        PlasticraftClientConfig::new
    );

    public AnvilcraftPlasticraft(IEventBus modEventBus, ModContainer ignored) {
        PlasticraftStorageTypes.register(modEventBus);
        PlasticraftAttachments.register(modEventBus);
        PlasticraftDataComponents.register(modEventBus);
        PlasticraftItemGroups.register(modEventBus);
        PlasticraftFluids.register(modEventBus);
        PlasticraftBlocks.register();
        PlasticraftBlockEntities.register();
        PlasticraftItems.register();
        PlasticItemTooltipManager.init();
        PlasticraftEntities.register();
        PlasticraftMenuTypes.register();
        PlasticraftParticles.register(modEventBus);
        PlasticraftRecipeTypes.register(modEventBus);
        PlasticraftDatagen.init();
        VaporizationSources.register(PlasmaJetVaporizationSource.INSTANCE);
        AnvilCraftApiBootstrap.register();
        NeoForge.EVENT_BUS.addListener(AnvilcraftPlasticraft::addItemTooltips);
        NeoForge.EVENT_BUS.addListener(HighViscosityResinEvents::useEntity);
        NeoForge.EVENT_BUS.addListener(HighViscosityResinEvents::useItem);
        NeoForge.EVENT_BUS.addListener(HighViscosityResinEvents::projectileImpact);
        NeoForge.EVENT_BUS.addListener(AdhesiveInvisibilityService::projectileImpact);
        NeoForge.EVENT_BUS.addListener(CondenserTowerProcess::onLargeCauldronProcess);
        NeoForge.EVENT_BUS.addListener(EscapingVaporEffects::rightClickBlock);
        NeoForge.EVENT_BUS.addListener(PlasticOilCatalysis::onChunkSent);
        NeoForge.EVENT_BUS.addListener(PlasticVillagerTrades::addTrades);
        modEventBus.addListener(HighViscosityResinEvents::registerCauldronFluidContent);
        modEventBus.addListener(PlasticraftBlocks::registerDispenserBehavior);
        modEventBus.addListener(AnvilcraftPlasticraft::registerCapabilities);
        modEventBus.addListener(AnvilcraftPlasticraft::registerPayloads);
        LOGGER.info("Loading {}", MOD_NAME);
    }

    public static ResourceLocation of(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void addItemTooltips(ItemTooltipEvent event) {
        PlasticItemTooltipManager.addTooltip(event.getItemStack(), event.getContext(), event.getToolTip());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY,
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            (cauldron, side) -> cauldron.getItemHandler()
        );
        event.registerEntity(
            Capabilities.ItemHandler.ENTITY_AUTOMATION,
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            (cauldron, side) -> cauldron.getItemHandler()
        );
        event.registerEntity(
            Capabilities.FluidHandler.ENTITY,
            PlasticraftEntities.HARDEND_RESIN_CAULDRON.get(),
            (cauldron, side) -> cauldron.getFluidHandler()
        );
        for (PlasticMaterial material : PlasticMaterial.values()) {
            event.registerEntity(
                Capabilities.ItemHandler.ENTITY,
                material.entityType(),
                (plastic, side) -> plastic.getMoldedItemHandler().getSlots() > 0
                    ? plastic.getMoldedItemHandler() : null
            );
            event.registerEntity(
                Capabilities.ItemHandler.ENTITY_AUTOMATION,
                material.entityType(),
                (plastic, side) -> plastic.getAutomationItemHandler(side)
            );
            event.registerEntity(
                Capabilities.FluidHandler.ENTITY,
                material.entityType(),
                (plastic, side) -> plastic.getAutomationFluidHandler(side)
            );
        }
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            PlasticraftBlockEntities.BONDED_ENTITY.get(),
            (bonded, side) -> bonded.getCapabilityFluidHandler(side)
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PlasticraftBlockEntities.BONDED_ENTITY.get(),
            (bonded, side) -> bonded.getAutomationItemHandler(side)
        );
        event.registerBlock(
            Capabilities.FluidHandler.BLOCK,
            CondenserTowerBlockEntity::capability,
            PlasticraftBlocks.CONDENSER_TOWER.get()
        );
        event.registerBlock(
            VaporCapabilities.VAPOR_CONSUMER,
            CondenserTowerBlockEntity::vaporCapability,
            PlasticraftBlocks.CONDENSER_TOWER.get()
        );
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
            (chamber, side) -> side == Direction.UP || side == Direction.DOWN ? null : chamber.clayItemHandler()
        );
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
            (chamber, side) -> side == null || side == Direction.UP || side == Direction.DOWN
                ? chamber.fluidHandler()
                : null
        );
        event.registerBlockEntity(
            Capabilities.EnergyStorage.BLOCK,
            PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
            (chamber, side) -> chamber.energyStorage()
        );
        for (PlasticMaterial material : PlasticMaterial.values()) {
            event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, ignored) -> new UniversalPlasticMeltBucketWrapper(stack, material::bucket, material::melt),
                material.bucket()
            );
        }
        Item[] plasticProducts = Arrays.stream(PlasticMaterial.values())
            .map(material -> material.productBlock().asItem())
            .toArray(Item[]::new);
        event.registerItem(
            Capabilities.ItemHandler.ITEM,
            (stack, ignored) -> MoldedPlasticData.get(stack)
                .filter(data -> stack.getCount() == 1
                    && (MoldingProductTypes.isCrate(data.finalType())
                        || MoldingProductTypes.isCauldron(data.finalType())))
                .map(data -> new MoldedPlasticItemHandler(
                    () -> MoldedPlasticData.get(stack),
                    replacement -> MoldedPlasticData.set(stack, replacement)
                ))
                .orElse(null),
            plasticProducts
        );
        event.registerItem(
            Capabilities.FluidHandler.ITEM,
            (stack, ignored) -> MoldedPlasticData.get(stack)
                .filter(data -> stack.getCount() == 1 && MoldingProductTypes.holdsFluids(data.finalType()))
                .map(data -> new MoldedPlasticFluidHandler(
                    () -> MoldedPlasticData.get(stack),
                    replacement -> MoldedPlasticData.set(stack, replacement),
                    () -> stack
                ))
                .orElse(null),
            plasticProducts
        );
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        NetworkRegistrar.register(event.registrar("1"), MOD_ID);
    }
}
