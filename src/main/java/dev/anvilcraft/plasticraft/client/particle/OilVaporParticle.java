package dev.anvilcraft.plasticraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** 使用喷流粒子轮廓、但以原油色缓慢上升的短程蒸气。 */
public final class OilVaporParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    private OilVaporParticle(
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
        this.quadSize = 0.07F + this.random.nextFloat() * 0.08F;
        this.lifetime = 14 + this.random.nextInt(9);
        this.setColor(0.025F, 0.003F, 0.045F);
        this.setAlpha(0.82F);
        this.setSpriteFromAge(sprites);
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

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double speedX,
            double speedY,
            double speedZ
        ) {
            return new OilVaporParticle(level, x, y, z, speedX, speedY, speedZ, this.sprites);
        }
    }
}
