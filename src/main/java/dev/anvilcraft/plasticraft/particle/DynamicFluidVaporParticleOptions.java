package dev.anvilcraft.plasticraft.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.init.PlasticraftParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** 携带实际气化速率与渲染位置状态的动态流体蒸气粒子参数。 */
public record DynamicFluidVaporParticleOptions(
    FluidStack fluid,
    int vaporizationRate,
    boolean pressurized,
    boolean outlet
) implements ParticleOptions {
    public static final MapCodec<DynamicFluidVaporParticleOptions> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(DynamicFluidVaporParticleOptions::fluid),
            Codec.INT.fieldOf("vaporization_rate").forGetter(DynamicFluidVaporParticleOptions::vaporizationRate),
            Codec.BOOL.fieldOf("pressurized").forGetter(DynamicFluidVaporParticleOptions::pressurized),
            Codec.BOOL.fieldOf("outlet").forGetter(DynamicFluidVaporParticleOptions::outlet)
        ).apply(instance, DynamicFluidVaporParticleOptions::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicFluidVaporParticleOptions> STREAM_CODEC =
        StreamCodec.composite(
            FluidStack.STREAM_CODEC,
            DynamicFluidVaporParticleOptions::fluid,
            ByteBufCodecs.VAR_INT,
            DynamicFluidVaporParticleOptions::vaporizationRate,
            ByteBufCodecs.BOOL,
            DynamicFluidVaporParticleOptions::pressurized,
            ByteBufCodecs.BOOL,
            DynamicFluidVaporParticleOptions::outlet,
            DynamicFluidVaporParticleOptions::new
        );

    public DynamicFluidVaporParticleOptions(FluidStack fluid, int vaporizationRate) {
        this(fluid, vaporizationRate, false, false);
    }

    public DynamicFluidVaporParticleOptions(FluidStack fluid, boolean pressurized) {
        this(fluid, 0, pressurized, false);
    }

    public static DynamicFluidVaporParticleOptions atOutlet(FluidStack fluid, int vaporizationRate) {
        return new DynamicFluidVaporParticleOptions(fluid, vaporizationRate, false, true);
    }

    public DynamicFluidVaporParticleOptions {
        Objects.requireNonNull(fluid, "fluid");
        if (fluid.isEmpty()) throw new IllegalArgumentException("Vapor particle fluid must not be empty");
        if (vaporizationRate < 0) throw new IllegalArgumentException("Vaporization rate must not be negative");
        if (pressurized && outlet) throw new IllegalArgumentException("Vapor particle mode is ambiguous");
        fluid = fluid.copyWithAmount(1);
    }

    @Override
    public FluidStack fluid() {
        return this.fluid.copy();
    }

    @Override
    public ParticleType<?> getType() {
        return PlasticraftParticles.DYNAMIC_FLUID_VAPOR.get();
    }
}
