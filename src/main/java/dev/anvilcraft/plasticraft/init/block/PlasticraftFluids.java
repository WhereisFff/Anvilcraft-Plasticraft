package dev.anvilcraft.plasticraft.init.block;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.client.renderer.HighViscosityResinFluidExtension;
import dev.anvilcraft.plasticraft.client.renderer.UniversalPlasticMeltFluidExtension;
import dev.anvilcraft.plasticraft.fluid.UniversalPlasticMeltFluidType;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.fluid.StationaryPlasticMeltFluid;
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
public final class PlasticraftFluids {
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

    public static final DeferredHolder<FluidType, FluidType> HIGH_HEAT_FUEL_TYPE = registerFluidType(
        "high_heat_fuel",
        900,
        800
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> HIGH_HEAT_FUEL = FLUIDS.register(
        "high_heat_fuel",
        () -> new BaseFlowingFluid.Source(highHeatFuelProperties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> FLOWING_HIGH_HEAT_FUEL = FLUIDS.register(
        "flowing_high_heat_fuel",
        () -> new BaseFlowingFluid.Flowing(highHeatFuelProperties())
    );

    public static final DeferredHolder<FluidType, FluidType> PLASTIC_OIL_TYPE = registerFluidType(
        "plastic_oil",
        1100,
        1600
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> PLASTIC_OIL = FLUIDS.register(
        "plastic_oil",
        () -> new BaseFlowingFluid.Source(plasticOilProperties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> FLOWING_PLASTIC_OIL = FLUIDS.register(
        "flowing_plastic_oil",
        () -> new BaseFlowingFluid.Flowing(plasticOilProperties())
    );

    public static final DeferredHolder<FluidType, FluidType> CRUDE_OIL_ACID_TYPE = registerFluidType(
        "crude_oil_acid",
        1200,
        1200
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> CRUDE_OIL_ACID = FLUIDS.register(
        "crude_oil_acid",
        () -> new BaseFlowingFluid.Source(crudeOilAcidProperties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> FLOWING_CRUDE_OIL_ACID = FLUIDS.register(
        "flowing_crude_oil_acid",
        () -> new BaseFlowingFluid.Flowing(crudeOilAcidProperties())
    );

    public static final DeferredHolder<FluidType, FluidType> UNIVERSAL_PLASTIC_MELT_TYPE = FLUID_TYPES.register(
        "universal_plastic_melt",
        () -> new UniversalPlasticMeltFluidType(FluidType.Properties.create()
            .descriptionId("block.anvilcraftplasticraft.universal_plastic_melt")
            .density(1900)
            .viscosity(20000)
            .fallDistanceModifier(0.0F)
            .motionScale(0.01D)
            .canSwim(false)
            .canDrown(false)
            .supportsBoating(false)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY))
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> UNIVERSAL_PLASTIC_MELT = FLUIDS.register(
        "universal_plastic_melt",
        () -> new StationaryPlasticMeltFluid.Source(universalPlasticMeltProperties())
    );
    public static final DeferredHolder<Fluid, BaseFlowingFluid> FLOWING_UNIVERSAL_PLASTIC_MELT = FLUIDS.register(
        "flowing_universal_plastic_melt",
        () -> new StationaryPlasticMeltFluid.Flowing(universalPlasticMeltProperties())
    );

    public static final BaseFlowingFluid.Properties HIGH_VISCOSITY_RESIN_PROPERTIES = new BaseFlowingFluid.Properties(
        LIQUID_HIGH_VISCOSITY_RESIN_TYPE,
        LIQUID_HIGH_VISCOSITY_RESIN,
        FLOWING_LIQUID_HIGH_VISCOSITY_RESIN
    )
        .bucket(PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET)
        .block(PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN)
        .tickRate(40)
        .slopeFindDistance(2)
        .levelDecreasePerBlock(3)
        .explosionResistance(100.0F);

    public static final BaseFlowingFluid.Properties HIGH_HEAT_FUEL_PROPERTIES = new BaseFlowingFluid.Properties(
        HIGH_HEAT_FUEL_TYPE,
        HIGH_HEAT_FUEL,
        FLOWING_HIGH_HEAT_FUEL
    )
        .bucket(PlasticraftItems.HIGH_HEAT_FUEL_BUCKET)
        .block(PlasticraftBlocks.HIGH_HEAT_FUEL)
        .tickRate(10)
        .slopeFindDistance(3)
        .explosionResistance(100.0F);

    public static final BaseFlowingFluid.Properties PLASTIC_OIL_PROPERTIES = new BaseFlowingFluid.Properties(
        PLASTIC_OIL_TYPE,
        PLASTIC_OIL,
        FLOWING_PLASTIC_OIL
    )
        .bucket(PlasticraftItems.PLASTIC_OIL_BUCKET)
        .block(PlasticraftBlocks.PLASTIC_OIL)
        .tickRate(12)
        .slopeFindDistance(3)
        .explosionResistance(100.0F);

    public static final BaseFlowingFluid.Properties CRUDE_OIL_ACID_PROPERTIES = new BaseFlowingFluid.Properties(
        CRUDE_OIL_ACID_TYPE,
        CRUDE_OIL_ACID,
        FLOWING_CRUDE_OIL_ACID
    )
        .bucket(PlasticraftItems.CRUDE_OIL_ACID_BUCKET)
        .block(PlasticraftBlocks.CRUDE_OIL_ACID)
        .tickRate(12)
        .slopeFindDistance(3)
        .explosionResistance(100.0F);

    public static final BaseFlowingFluid.Properties UNIVERSAL_PLASTIC_MELT_PROPERTIES =
        new BaseFlowingFluid.Properties(
            UNIVERSAL_PLASTIC_MELT_TYPE,
            UNIVERSAL_PLASTIC_MELT,
            FLOWING_UNIVERSAL_PLASTIC_MELT
        )
            .bucket(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET)
            .block(PlasticraftBlocks.UNIVERSAL_PLASTIC_MELT)
            .tickRate(40)
            .slopeFindDistance(1)
            .levelDecreasePerBlock(8)
            .explosionResistance(100.0F);

    private PlasticraftFluids() {
    }

    private static DeferredHolder<FluidType, FluidType> registerFluidType(
        String name,
        int density,
        int viscosity
    ) {
        return FLUID_TYPES.register(name, () -> new FluidType(FluidType.Properties.create()
            .descriptionId("block.anvilcraftplasticraft." + name)
            .density(density)
            .viscosity(viscosity)
            .supportsBoating(true)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));
    }

    private static BaseFlowingFluid.Properties properties() {
        return HIGH_VISCOSITY_RESIN_PROPERTIES;
    }

    private static BaseFlowingFluid.Properties highHeatFuelProperties() {
        return HIGH_HEAT_FUEL_PROPERTIES;
    }

    private static BaseFlowingFluid.Properties plasticOilProperties() {
        return PLASTIC_OIL_PROPERTIES;
    }

    private static BaseFlowingFluid.Properties crudeOilAcidProperties() {
        return CRUDE_OIL_ACID_PROPERTIES;
    }

    private static BaseFlowingFluid.Properties universalPlasticMeltProperties() {
        return UNIVERSAL_PLASTIC_MELT_PROPERTIES;
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(
            new HighViscosityResinFluidExtension(),
            LIQUID_HIGH_VISCOSITY_RESIN_TYPE
        );
        event.registerFluidType(fluidExtension("high_heat_fuel"), HIGH_HEAT_FUEL_TYPE);
        event.registerFluidType(fluidExtension("plastic_oil"), PLASTIC_OIL_TYPE);
        event.registerFluidType(fluidExtension("oil_essence"), CRUDE_OIL_ACID_TYPE);
        event.registerFluidType(new UniversalPlasticMeltFluidExtension(), UNIVERSAL_PLASTIC_MELT_TYPE);
    }

    private static HighViscosityResinFluidExtension fluidExtension(String textureName) {
        ResourceLocation texture = AnvilcraftPlasticraft.of("block/" + textureName);
        return new HighViscosityResinFluidExtension(
            texture,
            0xFFFFFFFF,
            1.0F,
            0xFFFFFFFF,
            false
        );
    }

    public static ResourceLocation liquidHighViscosityResinId() {
        return LIQUID_HIGH_VISCOSITY_RESIN.getId();
    }
}
