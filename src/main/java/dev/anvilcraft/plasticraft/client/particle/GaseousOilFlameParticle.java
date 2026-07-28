package dev.anvilcraft.plasticraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** 在逸散原油的出口短暂绘制原版灵魂火贴图。 */
public final class GaseousOilFlameParticle extends TextureSheetParticle {
    private GaseousOilFlameParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double halfWidth,
        double heightScale,
        SpriteSet sprites
    ) {
        super(level, x, y, z);
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
        this.pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        return this.getBoundingBox().inflate(0.5D, 2.0D, 0.5D);
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
            double ignoredSeedOffset
        ) {
            return new GaseousOilFlameParticle(level, x, y, z, halfWidth, heightScale, this.sprites);
        }
    }
}
