package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.dubhe.anvilcraft.util.ModClientFluidTypeExtensionImpl;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Plasticraft 的可储存与可放置流体注册。 */
public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(
        NeoForgeRegistries.FLUID_TYPES,
        AnvilcraftPlasticraft.MOD_ID
    );
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
        Registries.FLUID,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<FluidType, FluidType> LIQUID_HIGH_VISCOSITY_RESIN_TYPE = FLUID_TYPES.register(
        "liquid_high_viscosity_resin",
        () -> new FluidType(FluidType.Properties.create()
            .descriptionId("block.anvilcraftplasticraft.liquid_high_viscosity_resin")
            .density(1800)
            .viscosity(12000)
            .fallDistanceModifier(0.0F)
            .motionScale(0.01D)
            .canSwim(false)
            .canDrown(false)
            .supportsBoating(false)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
    );

    public static final DeferredHolder<Fluid, BaseFlowingFluid> LIQUID_HIGH_VISCOSITY_RESIN = FLUIDS.register(
        "liquid_high_viscosity_resin",
        () -> new BaseFlowingFluid.Source(properties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> FLOWING_LIQUID_HIGH_VISCOSITY_RESIN = FLUIDS.register(
        "flowing_liquid_high_viscosity_resin",
        () -> new BaseFlowingFluid.Flowing(properties())
    );

    public static final BaseFlowingFluid.Properties HIGH_VISCOSITY_RESIN_PROPERTIES = new BaseFlowingFluid.Properties(
        LIQUID_HIGH_VISCOSITY_RESIN_TYPE,
        LIQUID_HIGH_VISCOSITY_RESIN,
        FLOWING_LIQUID_HIGH_VISCOSITY_RESIN
    )
        .bucket(() -> ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get())
        .block(() -> ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN.get())
        .tickRate(40)
        .slopeFindDistance(2)
        .levelDecreasePerBlock(3)
        .explosionResistance(100.0F);

    private ModFluids() {
    }

    private static BaseFlowingFluid.Properties properties() {
        return HIGH_VISCOSITY_RESIN_PROPERTIES;
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(
            new ModClientFluidTypeExtensionImpl(
                AnvilcraftPlasticraft.of("block/liquid_high_viscosity_resin_still"),
                AnvilcraftPlasticraft.of("block/liquid_high_viscosity_resin_flow"),
                0x6B481D,
                1.5F,
                0xFFFFFFFF,
                false
            ),
            LIQUID_HIGH_VISCOSITY_RESIN_TYPE
        );
    }

    public static ResourceLocation liquidHighViscosityResinId() {
        return LIQUID_HIGH_VISCOSITY_RESIN.getId();
    }
}
