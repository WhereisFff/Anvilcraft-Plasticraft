package dev.anvilcraft.plasticraft.init;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.particle.FluidVaporParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Plasticraft 的客户端视觉粒子类型。 */
public final class ModParticles {
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

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
