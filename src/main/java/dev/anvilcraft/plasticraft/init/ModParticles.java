package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Plasticraft 的客户端视觉粒子类型。 */
public final class ModParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(
        Registries.PARTICLE_TYPE,
        AnvilcraftPlasticraft.MOD_ID
    );

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> OIL_VAPOR = PARTICLES.register(
        "oil_vapor",
        () -> new SimpleParticleType(false)
    );

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLES.register(modEventBus);
    }
}
