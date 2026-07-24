package dev.anvilcraft.plasticraft.client.particle;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** 使用原版经验球图集、缓慢上浮并轻微摆动的气态经验粒子。 */
public final class ExperienceVaporParticle extends SingleQuadParticle {
    private static final int SMALL_ICON_COUNT = 3;
    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace(
        "textures/entity/experience_orb.png"
    );
    private static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(true);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TEXTURE);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() {
            return "EXPERIENCE_VAPOR";
        }
    };

    private final float u0;
    private final float u1;
    private final float v0;
    private final float v1;
    private final double driftPhase;
    private final boolean pressurized;
    private final boolean outlet;

    private ExperienceVaporParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double speedX,
        double speedY,
        double speedZ,
        boolean outlet
    ) {
        super(level, x, y, z);
        this.outlet = outlet;
        this.pressurized = !outlet && speedY < 0.01D;
        this.hasPhysics = false;
        this.friction = this.pressurized ? 0.985F : this.outlet ? 0.94F : 0.97F;
        this.xd = speedX + (this.random.nextDouble() - 0.5D) * 0.006D;
        this.yd = speedY + this.random.nextDouble() * (this.pressurized ? 0.002D : 0.008D);
        this.zd = speedZ + (this.random.nextDouble() - 0.5D) * 0.006D;
        this.quadSize = this.pressurized
            ? 0.12F + this.random.nextFloat() * 0.08F
            : 0.10F + this.random.nextFloat() * 0.07F;
        this.lifetime = this.pressurized
            ? 44 + this.random.nextInt(25)
            : this.outlet ? 12 + this.random.nextInt(9) : 32 + this.random.nextInt(25);
        int icon = this.random.nextInt(SMALL_ICON_COUNT);
        this.u0 = (icon % 4) * 0.25F;
        this.u1 = this.u0 + 0.25F;
        this.v0 = (icon / 4) * 0.25F;
        this.v1 = this.v0 + 0.25F;
        this.driftPhase = this.random.nextDouble() * Math.PI * 2.0D;
        this.setAlpha(this.pressurized ? 0.72F : this.outlet ? 0.78F : 0.86F);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) return;
        double wave = (this.age * 0.16D) + this.driftPhase;
        double drift = this.pressurized ? 0.0007D : 0.0012D;
        this.xd += Math.sin(wave) * drift;
        this.zd += Math.cos(wave * 0.83D) * drift;
        if (this.pressurized) this.yd = Math.max(this.yd, 0.0025D);

        float colorTime = this.age * 0.5F;
        float red = (Mth.sin(colorTime) + 1.0F) * 0.5F;
        float blue = (Mth.sin(colorTime + (float) (Math.PI * 4.0D / 3.0D)) + 1.0F) * 0.10F;
        this.setColor(red, 1.0F, blue);
        float fade = Math.min(1.0F, (this.lifetime - this.age) / 10.0F);
        this.setAlpha((this.pressurized ? 0.72F : this.outlet ? 0.78F : 0.86F) * fade);
    }

    @Override
    protected int getLightColor(float partialTick) {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    @Override
    protected float getU0() {
        return this.u0;
    }

    @Override
    protected float getU1() {
        return this.u1;
    }

    @Override
    protected float getV0() {
        return this.v0;
    }

    @Override
    protected float getV1() {
        return this.v1;
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final boolean outlet;

        public Provider(boolean outlet) {
            this.outlet = outlet;
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
            return new ExperienceVaporParticle(level, x, y, z, speedX, speedY, speedZ, this.outlet);
        }
    }
}
