package dev.anvilcraft.plasticraft.client;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.blueprint.ConstructionProjectionIndex;
import dev.anvilcraft.plasticraft.client.blueprint.ClientConstructionOverlayLookup;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.client.hud.AdhesiveBondHud;
import dev.anvilcraft.plasticraft.client.hud.BondedBlockTooltipProvider;
import dev.anvilcraft.plasticraft.client.particle.EnhancedPlasmaJetsParticle;
import dev.anvilcraft.plasticraft.client.particle.ExperienceVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.FluidVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.GaseousOilFlameParticle;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPreviewRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.PlasticTextureSpriteSource;
import dev.anvilcraft.plasticraft.client.renderer.UniversalPlasticItemRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.Plastic3DPrintingComponentRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.PlasticMoldingChamberRenderer;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportResources;
import dev.anvilcraft.plasticraft.client.renderer.entity.CatalyticPressLidRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinCauldronRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.drone.DroneItemRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.drone.DroneModel;
import dev.anvilcraft.plasticraft.client.renderer.entity.drone.DroneToolAttachmentModel;
import dev.anvilcraft.plasticraft.init.PlasticraftParticles;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** 为今后注册支持调色板的材料而保留的客户端入口点。 */
@Mod(value = AnvilcraftPlasticraft.MOD_ID, dist = Dist.CLIENT)
public final class AnvilcraftPlasticraftClient {
    public AnvilcraftPlasticraftClient(IEventBus modEventBus, ModContainer ignoredContainer) {
        modEventBus.addListener(PlasticraftFluids::registerClientExtensions);
        modEventBus.addListener(AnvilcraftPlasticraftClient::clientSetup);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerAdditionalModels);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerParticleProviders);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerGuiLayers);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerReloadListeners);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerBlockColors);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerItemColors);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerClientExtensions);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerLayerDefinitions);
        modEventBus.addListener(PlasticPreviewRenderTypes::registerShader);
        modEventBus.addListener(PlasticTextureSpriteSource::registerType);
        NeoForge.EVENT_BUS.addListener(AnvilcraftPlasticraftClient::clearPlasticTextureCache);
        AnvilCraftClientApiBootstrap.register();
        // 硬化树脂和树脂均为固定颜色材料，不注册方块或物品着色处理器。
        // 后续支持调色板的材料在此按需注册。
    }

    private static void clearPlasticTextureCache(ClientPlayerNetworkEvent.LoggingOut event) {
        PlasticTextureCache.clear();
        DynamicPlasticTextureManager.INSTANCE.clear();
        MoldingViewportResources.INSTANCE.closeAll();
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            HudTooltipManager.INSTANCE.registerBlockTooltip(new BondedBlockTooltipProvider());
            ItemBlockRenderTypes.setRenderLayer(
                PlasticraftFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
                RenderType.translucent()
            );
            ItemBlockRenderTypes.setRenderLayer(
                PlasticraftFluids.FLOWING_LIQUID_HIGH_VISCOSITY_RESIN.get(),
                RenderType.translucent()
            );
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.HIGH_HEAT_FUEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.FLOWING_HIGH_HEAT_FUEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.PLASTIC_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.FLOWING_PLASTIC_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.CRUDE_OIL_ACID.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.FLOWING_CRUDE_OIL_ACID.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(
                PlasticraftFluids.FLOWING_UNIVERSAL_PLASTIC_MELT.get(),
                RenderType.cutout()
            );
            ItemProperties.register(
                PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem(),
                AnvilcraftPlasticraft.of("plastic_color"),
                (stack, level, entity, seed) -> PlasticMeltColor.get(stack).getId()
            );
            ConstructionProjectionIndex.setOverlayLookup(ClientConstructionOverlayLookup::plannedOverlay);
        });
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
            (state, level, pos, tintIndex) -> tintIndex == 0
                ? PlasticMeltColor.tint(state.getValue(UniversalPlasticMeltCauldronBlock.COLOR))
                : 0xFFFFFFFF,
            PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get()
        );
    }

    private static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
            (stack, tintIndex) -> tintIndex == 0 ? PlasticMeltColor.tint(stack) : 0xFFFFFFFF,
            PlasticraftItems.UNIVERSAL_PLASTIC_GRANULE.get()
        );
        event.register(
            (stack, tintIndex) -> tintIndex == 1 ? PlasticMeltColor.tint(stack) : 0xFFFFFFFF,
            PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get()
        );
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return UniversalPlasticItemRenderer.getInstance();
            }
        }, PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem());
        event.registerItem(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return DroneItemRenderer.getInstance();
            }
        },
            PlasticraftItems.DRONE.get(),
            PlasticraftItems.CONSTRUCTION_DRONE.get(),
            PlasticraftItems.DEMOLITION_DRONE.get(),
            PlasticraftItems.COLLECTION_DRONE.get(),
            PlasticraftItems.OBSERVATION_DRONE.get()
        );
    }

    private static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(DroneModel.LAYER, DroneModel::createBodyLayer);
        event.registerLayerDefinition(
            DroneToolAttachmentModel.CONSTRUCTION_CLAW_LAYER,
            DroneToolAttachmentModel::createConstructionClawLayer
        );
        event.registerLayerDefinition(
            DroneToolAttachmentModel.DEMOLITION_STONECUTTER_LAYER,
            DroneToolAttachmentModel::createDemolitionStonecutterLayer
        );
        event.registerLayerDefinition(
            DroneToolAttachmentModel.COLLECTION_MAGNET_LAYER,
            DroneToolAttachmentModel::createCollectionMagnetLayer
        );
        event.registerLayerDefinition(
            DroneToolAttachmentModel.OBSERVATION_SPYGLASS_LAYER,
            DroneToolAttachmentModel::createObservationSpyglassLayer
        );
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(PlasticMoldingChamberRenderer.PRINTER_FRAME_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_GLASS_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_GUI_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_X_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_Y_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_Z_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_Z_EYE_MODEL);
        event.register(Plastic3DPrintingComponentRenderer.EYE_MODEL);
        event.register(Plastic3DPrintingComponentRenderer.SCREEN_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_DOOR_LEFT_MODEL);
        event.register(PlasticMoldingChamberRenderer.PRINTER_DOOR_RIGHT_MODEL);
        event.register(HardenedResinCauldronRenderer.OUTLET_MODEL);
        event.register(CatalyticPressLidRenderer.ARM_MODEL);
        event.register(AdhesivePatchRenderer.MODEL);
        event.register(IgnitedFluidFlameRenderer.BLUE_FLAME_MODEL);
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(PlasticraftParticles.FLUID_VAPOR.get(), FluidVaporParticle.Provider::new);
        event.registerSpriteSet(PlasticraftParticles.DYNAMIC_FLUID_VAPOR.get(), FluidVaporParticle.DynamicProvider::new);
        event.registerSpriteSet(PlasticraftParticles.ENHANCED_PLASMA_JETS.get(), EnhancedPlasmaJetsParticle.Provider::new);
        event.registerSpecial(PlasticraftParticles.EXPERIENCE_VAPOR.get(), new ExperienceVaporParticle.Provider(false));
        event.registerSpecial(PlasticraftParticles.EXPERIENCE_VAPOR_OUTLET.get(), new ExperienceVaporParticle.Provider(true));
        event.registerSpriteSet(PlasticraftParticles.GASEOUS_OIL_FLAME.get(), GaseousOilFlameParticle.Provider::new);
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(AnvilcraftPlasticraft.of("adhesive_bond_hud"), AdhesiveBondHud::render);
    }

    private static void registerReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(MoldingViewportResources.INSTANCE);
        event.registerReloadListener(DynamicPlasticTextureManager.INSTANCE);
    }
}
