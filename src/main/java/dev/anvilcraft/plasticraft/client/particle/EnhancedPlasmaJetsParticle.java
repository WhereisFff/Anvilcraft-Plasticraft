package dev.anvilcraft.plasticraft.client.particle;

import dev.dubhe.anvilcraft.client.particle.PlasmaJetsParticle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/** 使用青蓝到蓝白色阶、速度更快的强化等离子喷流粒子。 */
public final class EnhancedPlasmaJetsParticle extends PlasmaJetsParticle {
    private EnhancedPlasmaJetsParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double speedX,
        double speedY,
        double speedZ,
        SpriteSet sprites
    ) {
        super(level, x, y, z, speedX, speedY, speedZ, sprites);
        this.yd *= 1.2D;
        this.gravity = 0.12F;
    }

    @Override
    protected void setColorFromAge(int age, int maxAge) {
        float progress = Math.clamp((float) age / Math.max(1, maxAge), 0.0F, 1.0F);
        this.setColor(
            0.24F + progress * 0.58F,
            0.82F + progress * 0.12F,
            1.0F
        );
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
            return new EnhancedPlasmaJetsParticle(level, x, y, z, speedX, speedY, speedZ, this.sprites);
        }
    }
}
