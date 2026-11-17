package dev.anvilcraft.plasticraft.client;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.api.texture.PlasticTextureCache;
import dev.anvilcraft.plasticraft.block.UniversalPlasticMeltCauldronBlock;
import dev.anvilcraft.plasticraft.client.event.PlasticraftPlacementPreviewRenderer;
import dev.anvilcraft.plasticraft.client.hud.AdhesiveBondHud;
import dev.anvilcraft.plasticraft.client.hud.BondedBlockTooltipProvider;
import dev.anvilcraft.plasticraft.client.particle.EnhancedPlasmaJetsParticle;
import dev.anvilcraft.plasticraft.client.particle.ExperienceVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.FluidVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.GaseousOilFlameParticle;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.DynamicPlasticTextureManager;
import dev.anvilcraft.plasticraft.client.renderer.HighViscosityResinFluidExtension;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticGeometryPicture;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPaletteItemTint;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPaletteTintManager;
import dev.anvilcraft.plasticraft.client.renderer.PlasticPreviewRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.PlasticTextureSpriteSource;
import dev.anvilcraft.plasticraft.client.renderer.UniversalPlasticItemRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.Plastic3DPrintingComponentRenderer;
import dev.anvilcraft.plasticraft.client.renderer.blockentity.PlasticMoldingChamberRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinCauldronRenderer;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingProjectionRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportRenderer;
import dev.anvilcraft.plasticraft.client.renderer.molding.MoldingViewportResources;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionGeometry;
import dev.anvilcraft.plasticraft.client.selection.PlasticSelectionIntegration;
import dev.anvilcraft.plasticraft.init.PlasticraftParticles;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.item.PlasticraftCreativeTabState;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** 注册 Plasticraft 的客户端渲染、色板和界面入口。 */
@Mod(value = AnvilcraftPlasticraft.MOD_ID, dist = Dist.CLIENT)
public final class AnvilcraftPlasticraftClient {
    public AnvilcraftPlasticraftClient(IEventBus modEventBus, ModContainer ignoredContainer) {
        modEventBus.addListener(PlasticraftFluids::registerClientExtensions);
        modEventBus.addListener(AnvilcraftPlasticraftClient::clientSetup);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerAdditionalModels);
        modEventBus.addListener(PlasticSelectionIntegration::reload);
        modEventBus.addListener(PlasticraftPlacementPreviewRenderer::reload);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerParticleProviders);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerGuiLayers);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerReloadListeners);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerBlockColors);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerItemColors);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerSpecialModels);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerFluidModels);
        modEventBus.addListener(ClearPlasticRenderTypes::register);
        modEventBus.addListener(PlasticPreviewRenderTypes::register);
        modEventBus.addListener(MoldingProjectionRenderTypes::register);
        modEventBus.addListener(MoldingViewportRenderer::register);
        modEventBus.addListener(PlasticGeometryPicture::register);
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
        PlasticraftCreativeTabState.reset();
        PlasticSelectionGeometry.clear();
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            PlasticSelectionIntegration.register();
            HudTooltipManager.INSTANCE.registerBlockTooltip(new BondedBlockTooltipProvider());
        });
    }

    private static void registerBlockColors(RegisterColorHandlersEvent.BlockTintSources event) {
        for (PlasticMaterial material : PlasticMaterial.values()) {
            event.register(List.of(state -> PlasticPaletteItemTint.color(material,
                state.getValue(UniversalPlasticMeltCauldronBlock.COLOR))), material.meltCauldron());
        }
    }

    private static void registerItemColors(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(AnvilcraftPlasticraft.of("palette"), PlasticPaletteItemTint.CODEC);
    }

    private static void registerSpecialModels(RegisterSpecialModelRendererEvent event) {
        event.register(AnvilcraftPlasticraft.of("plastic"), UniversalPlasticItemRenderer.Unbaked.CODEC);
    }

    private static void registerFluidModels(RegisterFluidModelsEvent event) {
        for (var entry : PlasticraftFluids.FLUIDS.getEntries()) {
            var fluid = entry.get();
            var extension = (HighViscosityResinFluidExtension) IClientFluidTypeExtensions.of(fluid);
            event.register(extension.model(), fluid);
        }
    }

    private static void registerAdditionalModels(ModelEvent.RegisterStandalone event) {
        PlasticMoldingChamberRenderer.PRINTER_FRAME_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_GLASS_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_GUI_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_X_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_Y_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_Z_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_Z_EYE_MODEL.register(event);
        Plastic3DPrintingComponentRenderer.EYE_MODEL.register(event);
        Plastic3DPrintingComponentRenderer.SCREEN_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_DOOR_LEFT_MODEL.register(event);
        PlasticMoldingChamberRenderer.PRINTER_DOOR_RIGHT_MODEL.register(event);
        HardenedResinCauldronRenderer.OUTLET_MODEL.register(event);
        AdhesivePatchRenderer.MODEL.register(event);
        IgnitedFluidFlameRenderer.BLUE_FLAME_MODEL.register(event);
        IgnitedFluidFlameRenderer.ORDINARY_FLAME_MODEL.register(event);
        PlasticEntityRenderHelper.HAMMER_AXIS_MODEL.register(event);
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

    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(AnvilcraftPlasticraft.of("molding_viewport"), MoldingViewportResources.INSTANCE);
        event.addListener(AnvilcraftPlasticraft.of("plastic_palette"), PlasticPaletteTintManager.INSTANCE);
        event.addListener(AnvilcraftPlasticraft.of("dynamic_plastic"), DynamicPlasticTextureManager.INSTANCE);
    }
}
