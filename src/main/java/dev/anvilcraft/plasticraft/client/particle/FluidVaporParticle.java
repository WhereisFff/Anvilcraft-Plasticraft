package dev.anvilcraft.plasticraft.client.particle;

import dev.anvilcraft.plasticraft.particle.DynamicFluidVaporParticleOptions;
import dev.anvilcraft.plasticraft.particle.FluidVaporParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

/** 使用源流体颜色，并按实际气化速率调整寿命与扩散距离的蒸气粒子。 */
public final class FluidVaporParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float baseAlpha;

    private FluidVaporParticle(
        FluidStack fluid,
        int vaporizationRate,
        boolean pressurized,
        boolean outlet,
        ClientLevel level,
        double x,
        double y,
        double z,
        double speedX,
        double speedY,
        double speedZ,
        SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.hasPhysics = false;
        float intensity = Math.clamp(vaporizationRate / 50.0F, 0.1F, 1.0F);
        this.friction = pressurized
            ? 0.86F
            : outlet ? 0.91F : 0.89F + intensity * 0.06F;
        this.xd = speedX + (this.random.nextDouble() - 0.5D) * (pressurized ? 0.002D : 0.012D);
        this.yd = speedY + this.random.nextDouble() * (pressurized ? 0.002D : 0.010D);
        this.zd = speedZ + (this.random.nextDouble() - 0.5D) * (pressurized ? 0.002D : 0.012D);
        this.quadSize = pressurized
            ? 0.30F + this.random.nextFloat() * 0.20F
            : 0.13F + this.random.nextFloat() * 0.13F + intensity * 0.07F;
        int freeVaporLifetime = 9 + this.random.nextInt(7) + Math.round(intensity * 25.0F);
        this.lifetime = pressurized
            ? 24 + this.random.nextInt(10)
            : outlet ? Math.max(5, Math.round(freeVaporLifetime * 0.45F)) : freeVaporLifetime;
        this.baseAlpha = pressurized
            ? 0.26F
            : (outlet ? 0.66F : 0.72F) + intensity * 0.10F;
        this.setFluidColor(fluid);
        this.setAlpha(this.baseAlpha);
        this.setSpriteFromAge(sprites);
    }

    private void setFluidColor(FluidStack fluid) {
        int tint = fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)
            ? 0xFFFFFFFF
            : IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
        this.setColor(
            (float) (tint >> 16 & 0xFF) / 255.0F,
            (float) (tint >> 8 & 0xFF) / 255.0F,
            (float) (tint & 0xFF) / 255.0F
        );
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        this.setSpriteFromAge(this.sprites);
        this.setAlpha(this.baseAlpha * (1.0F - (float) this.age / this.lifetime));
    }

    public static final class Provider implements ParticleProvider<FluidVaporParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
            FluidVaporParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z,
            double speedX,
            double speedY,
            double speedZ
        ) {
            return new FluidVaporParticle(
                options.fluid(),
                0,
                false,
                false,
                level,
                x,
                y,
                z,
                speedX,
                speedY,
                speedZ,
                this.sprites
            );
        }
    }

    public static final class DynamicProvider implements ParticleProvider<DynamicFluidVaporParticleOptions> {
        private final SpriteSet sprites;

        public DynamicProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
            DynamicFluidVaporParticleOptions options,
            ClientLevel level,
            double x,
            double y,
            double z,
            double speedX,
            double speedY,
            double speedZ
        ) {
            return new FluidVaporParticle(
                options.fluid(),
                options.vaporizationRate(),
                options.pressurized(),
                options.outlet(),
                level,
                x,
                y,
                z,
                speedX,
                speedY,
                speedZ,
                this.sprites
            );
        }
    }
}
