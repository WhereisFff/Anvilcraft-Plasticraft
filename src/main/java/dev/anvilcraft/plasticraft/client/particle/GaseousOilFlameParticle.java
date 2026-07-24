package dev.anvilcraft.plasticraft.client.particle;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.anvilcraft.plasticraft.client.renderer.HighHeatFuelFlameRenderer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 在逸散原油的出口短暂绘制与高热燃料一致的蓝白火焰。 */
public final class GaseousOilFlameParticle extends Particle {
    private static final ParticleRenderType RENDER_TYPE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public String toString() {
            return "GASEOUS_OIL_FLAME";
        }
    };

    private final float halfWidth;
    private final float heightScale;
    private final long seed;

    private GaseousOilFlameParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        double halfWidth,
        double heightScale,
        double seedOffset
    ) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.lifetime = 2;
        this.halfWidth = Mth.clamp((float) halfWidth, 0.16F, 0.34F);
        this.heightScale = Mth.clamp((float) heightScale, 0.55F, 1.0F);
        this.seed = BlockSeed.at(x, y, z) ^ Double.doubleToLongBits(seedOffset);
        this.setSize(this.halfWidth * 2.0F, this.heightScale * 2.0F);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cameraPosition = camera.getPosition();
        float renderX = (float) (this.x - cameraPosition.x);
        float renderY = (float) (this.y - cameraPosition.y);
        float renderZ = (float) (this.z - cameraPosition.z);
        PoseStack poseStack = new PoseStack();
        poseStack.translate(renderX - 0.5F, renderY, renderZ - 0.5F);
        HighHeatFuelFlameRenderer.render(
            poseStack,
            buffer,
            0.0F,
            this.halfWidth,
            this.level.getGameTime() + partialTick,
            this.seed,
            this.heightScale,
            true
        );
    }

    @Override
    public ParticleRenderType getRenderType() {
        return RENDER_TYPE;
    }

    @Override
    public AABB getRenderBoundingBox(float partialTick) {
        return this.getBoundingBox().inflate(0.5D, 2.0D, 0.5D);
    }

    private static final class BlockSeed {
        private BlockSeed() {
        }

        private static long at(double x, double y, double z) {
            long blockX = Mth.floor(x);
            long blockY = Mth.floor(y);
            long blockZ = Mth.floor(z);
            return blockX * 3_129_871L ^ blockZ * 116_129_781L ^ blockY;
        }
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(
            SimpleParticleType type,
            ClientLevel level,
            double x,
            double y,
            double z,
            double halfWidth,
            double heightScale,
            double seedOffset
        ) {
            return new GaseousOilFlameParticle(level, x, y, z, halfWidth, heightScale, seedOffset);
        }
    }
}
