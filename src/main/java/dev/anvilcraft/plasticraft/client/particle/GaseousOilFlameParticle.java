package dev.anvilcraft.plasticraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** 在逸散原油的出口短暂绘制原版灵魂火贴图。 */
public final class GaseousOilFlameParticle extends SingleQuadParticle {
    private GaseousOilFlameParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double halfWidth,
        double heightScale,
        SpriteSet sprites
    ) {
        super(level, x, y, z, sprites.first());
        this.hasPhysics = false;
        this.lifetime = 2;
        float clampedHalfWidth = Mth.clamp((float) halfWidth, 0.16F, 0.34F);
        float clampedHeightScale = Mth.clamp((float) heightScale, 0.55F, 1.0F);
        this.quadSize = Math.max(clampedHalfWidth, clampedHeightScale * 0.5F);
        this.setSize(this.quadSize * 2.0F, clampedHeightScale * 2.0F);
        this.setPos(x, y + this.quadSize, z);
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.setSprite(sprites.get(this.random));
        this.setBoundingBox(this.getBoundingBox().inflate(0.5, 2, 0.5));
    }

    @Override
    protected Layer getLayer() {
        return Layer.OPAQUE;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return LightCoordsUtil.FULL_BRIGHT;
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
            double halfWidth,
            double heightScale,
            double ignoredSeedOffset,
            RandomSource random
        ) {
            return new GaseousOilFlameParticle(level, x, y, z, halfWidth, heightScale, this.sprites);
        }
    }
}
