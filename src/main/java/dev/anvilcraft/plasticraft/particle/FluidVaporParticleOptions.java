package dev.anvilcraft.plasticraft.particle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** 保持原网络格式的基础流体蒸气粒子参数。 */
public record FluidVaporParticleOptions(FluidStack fluid) implements ParticleOptions {
    public static final MapCodec<FluidVaporParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(FluidVaporParticleOptions::fluid)
        ).apply(instance, FluidVaporParticleOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidVaporParticleOptions> STREAM_CODEC =
        FluidStack.STREAM_CODEC.map(FluidVaporParticleOptions::new, FluidVaporParticleOptions::fluid);

    public FluidVaporParticleOptions {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid.isEmpty()) throw new IllegalArgumentException("Vapor particle fluid must not be empty");
        fluid = fluid.copyWithAmount(1);
    }

    @Override
    public FluidStack fluid() {
        return this.fluid.copy();
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.FLUID_VAPOR.get();
    }
}
