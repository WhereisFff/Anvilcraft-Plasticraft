package dev.anvilcraft.plasticraft.client;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.particle.FluidVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.ExperienceVaporParticle;
import dev.anvilcraft.plasticraft.client.particle.EnhancedPlasmaJetsParticle;
import dev.anvilcraft.plasticraft.client.particle.GaseousOilFlameParticle;
import dev.anvilcraft.plasticraft.client.hud.AdhesiveBondHud;
import dev.anvilcraft.plasticraft.client.hud.BondedBlockTooltipProvider;
import dev.dubhe.anvilcraft.api.tooltip.HudTooltipManager;
import dev.anvilcraft.plasticraft.client.renderer.entity.HardenedResinCauldronRenderer;
import dev.anvilcraft.plasticraft.client.renderer.AdhesivePatchRenderer;
import dev.anvilcraft.plasticraft.init.ModParticles;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** 为今后注册支持调色板的材料而保留的客户端入口点。 */
@Mod(value = AnvilcraftPlasticraft.MOD_ID, dist = Dist.CLIENT)
public final class AnvilcraftPlasticraftClient {
    public AnvilcraftPlasticraftClient(IEventBus modEventBus, ModContainer ignoredContainer) {
        modEventBus.addListener(ModFluids::registerClientExtensions);
        modEventBus.addListener(AnvilcraftPlasticraftClient::clientSetup);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerAdditionalModels);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerParticleProviders);
        modEventBus.addListener(AnvilcraftPlasticraftClient::registerGuiLayers);
        // 硬化树脂和树脂均为固定颜色材料，不注册方块或物品着色处理器。
        // 后续支持调色板的材料在此按需注册。
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            HudTooltipManager.INSTANCE.registerBlockTooltip(new BondedBlockTooltipProvider());
            ItemBlockRenderTypes.setRenderLayer(
                ModFluids.LIQUID_HIGH_VISCOSITY_RESIN.get(),
                RenderType.translucent()
            );
            ItemBlockRenderTypes.setRenderLayer(
                ModFluids.FLOWING_LIQUID_HIGH_VISCOSITY_RESIN.get(),
                RenderType.translucent()
            );
            ItemBlockRenderTypes.setRenderLayer(ModFluids.HIGH_HEAT_FUEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_HIGH_HEAT_FUEL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.PLASTIC_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_PLASTIC_OIL.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.CRUDE_OIL_ACID.get(), RenderType.translucent());
            ItemBlockRenderTypes.setRenderLayer(ModFluids.FLOWING_CRUDE_OIL_ACID.get(), RenderType.translucent());
        });
    }

    private static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(HardenedResinCauldronRenderer.OUTLET_MODEL);
        event.register(AdhesivePatchRenderer.MODEL);
    }

    private static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FLUID_VAPOR.get(), FluidVaporParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DYNAMIC_FLUID_VAPOR.get(), FluidVaporParticle.DynamicProvider::new);
        event.registerSpriteSet(ModParticles.ENHANCED_PLASMA_JETS.get(), EnhancedPlasmaJetsParticle.Provider::new);
        event.registerSpecial(ModParticles.EXPERIENCE_VAPOR.get(), new ExperienceVaporParticle.Provider(false));
        event.registerSpecial(ModParticles.EXPERIENCE_VAPOR_OUTLET.get(), new ExperienceVaporParticle.Provider(true));
        event.registerSpecial(ModParticles.GASEOUS_OIL_FLAME.get(), new GaseousOilFlameParticle.Provider());
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(AnvilcraftPlasticraft.of("adhesive_bond_hud"), AdhesiveBondHud::render);
    }
}
