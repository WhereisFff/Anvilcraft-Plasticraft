package dev.anvilcraft.plasticraft.init;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.particle.DynamicFluidVaporParticleOptions;
import dev.anvilcraft.plasticraft.particle.FluidVaporParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Plasticraft 的客户端视觉粒子类型。 */
public final class PlasticraftParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(
        Registries.PARTICLE_TYPE,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<ParticleType<?>, ParticleType<FluidVaporParticleOptions>> FLUID_VAPOR =
        PARTICLES.register(
            "fluid_vapor",
            () -> new ParticleType<>(false) {
                @Override
                public MapCodec<FluidVaporParticleOptions> codec() {
                    return FluidVaporParticleOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, FluidVaporParticleOptions> streamCodec() {
                    return FluidVaporParticleOptions.STREAM_CODEC;
                }
            }
    );
    public static final DeferredHolder<ParticleType<?>, ParticleType<DynamicFluidVaporParticleOptions>>
        DYNAMIC_FLUID_VAPOR = PARTICLES.register(
            "dynamic_fluid_vapor",
            () -> new ParticleType<>(false) {
                @Override
                public MapCodec<DynamicFluidVaporParticleOptions> codec() {
                    return DynamicFluidVaporParticleOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, DynamicFluidVaporParticleOptions> streamCodec() {
                    return DynamicFluidVaporParticleOptions.STREAM_CODEC;
                }
            }
    );

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENHANCED_PLASMA_JETS = PARTICLES.register(
        "enhanced_plasma_jets",
        () -> new SimpleParticleType(false)
    );
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPERIENCE_VAPOR = PARTICLES.register(
        "experience_vapor",
        () -> new SimpleParticleType(false)
    );
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EXPERIENCE_VAPOR_OUTLET = PARTICLES.register(
        "experience_vapor_outlet",
        () -> new SimpleParticleType(false)
    );
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> GASEOUS_OIL_FLAME = PARTICLES.register(
        "gaseous_oil_flame",
        () -> new SimpleParticleType(false)
    );

    private PlasticraftParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
