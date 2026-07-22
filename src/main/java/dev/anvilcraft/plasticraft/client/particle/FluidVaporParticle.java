package dev.anvilcraft.plasticraft.client.particle;

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

/** A short-lived vapor particle tinted from the source fluid's client rendering extension. */
public final class FluidVaporParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private FluidVaporParticle(
        FluidVaporParticleOptions options,
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
        this.friction = 0.91F;
        this.xd = speedX + (this.random.nextDouble() - 0.5D) * 0.012D;
        this.yd = speedY + this.random.nextDouble() * 0.012D;
        this.zd = speedZ + (this.random.nextDouble() - 0.5D) * 0.012D;
        this.quadSize = 0.16F + this.random.nextFloat() * 0.16F;
        this.lifetime = 14 + this.random.nextInt(9);
        this.setFluidColor(options.fluid());
        this.setAlpha(0.82F);
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
        this.setAlpha(0.82F * (1.0F - (float) this.age / this.lifetime));
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
                options,
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
