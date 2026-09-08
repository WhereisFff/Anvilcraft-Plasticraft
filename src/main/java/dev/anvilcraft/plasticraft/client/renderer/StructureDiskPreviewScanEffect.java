package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.dubhe.anvilcraft.client.init.ModShaders;
import dev.dubhe.anvilcraft.client.renderer.RenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** 保持本体扫描后处理的外观，帧缓冲随预览缓存一起释放。 */
final class StructureDiskPreviewScanEffect {
    private static @Nullable RenderTarget previewFbo;
    private static int lastFboGuiScale;

    private StructureDiskPreviewScanEffect() {
    }

    static void render(int previewX, int previewY, int previewSize) {
        Minecraft minecraft = Minecraft.getInstance();
        if (RenderState.isScanPreviewEffectEnabled()) {
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            int guiScale = (int) minecraft.getWindow().getGuiScale();
            int fbWidth = previewSize * guiScale;
            int fbHeight = previewSize * guiScale;

            if (lastFboGuiScale != guiScale) {
                if (previewFbo != null) previewFbo.destroyBuffers();
                previewFbo = null;
                lastFboGuiScale = guiScale;
            }
            if (previewFbo == null) {
                previewFbo = new TextureTarget(fbWidth, fbHeight, true, Minecraft.ON_OSX);
            }

            final RenderTarget mainTarget = minecraft.getMainRenderTarget();
            int srcX = previewX * guiScale;
            int srcY = (minecraft.getWindow().getGuiScaledHeight() - previewY - previewSize) * guiScale;

            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainTarget.frameBufferId);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previewFbo.frameBufferId);
            GL30.glBlitFramebuffer(
                srcX,
                srcY,
                srcX + fbWidth,
                srcY + fbHeight,
                0,
                0,
                fbWidth,
                fbHeight,
                GL11.GL_COLOR_BUFFER_BIT,
                GL11.GL_NEAREST
            );
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, 0);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, 0);

            mainTarget.bindWrite(false);

            ShaderInstance shader = ModShaders.getScanPreviewShader();
            final float fbW = previewFbo.width;
            final float fbH = previewFbo.height;
            final float screenX = previewX * guiScale;
            final float screenY = (minecraft.getWindow().getGuiScaledHeight() - previewY - previewSize) * guiScale;

            RenderSystem.defaultBlendFunc();
            RenderSystem.viewport(0, 0, minecraft.getWindow().getWidth(), minecraft.getWindow().getHeight());

            shader.setSampler("DiffuseSampler", previewFbo);
            shader.safeGetUniform("ProjMat").set(ModShaders.getOrthoMatrix());
            shader.safeGetUniform("InSize").set(fbW, fbH);
            shader.safeGetUniform("OutPos").set(screenX, screenY);
            shader.safeGetUniform("OutSize").set(fbW, fbH);
            shader.safeGetUniform("GameTime").set((float) (System.currentTimeMillis() % 100000) / 1000.0f);

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            shader.apply();

            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
            builder.addVertex(0.0F, 0.0F, 0.0F);
            builder.addVertex(fbW, 0.0F, 0.0F);
            builder.addVertex(fbW, fbH, 0.0F);
            builder.addVertex(0.0F, fbH, 0.0F);
            BufferUploader.draw(builder.buildOrThrow());

            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            ProgramManager.glUseProgram(0);

            previewFbo.unbindRead();
        }
        RenderSystem.disableDepthTest();
        RenderSystem.disableBlend();
    }

    static void clear() {
        if (previewFbo != null) previewFbo.destroyBuffers();
        previewFbo = null;
        lastFboGuiScale = 0;
    }
}
