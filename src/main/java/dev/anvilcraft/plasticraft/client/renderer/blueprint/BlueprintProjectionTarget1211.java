package dev.anvilcraft.plasticraft.client.renderer.blueprint;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;

/** 1.21.1 投影离屏目标，未来版本只需替换这一后端而不改投影场景。 */
final class BlueprintProjectionTarget1211 {
    private static TextureTarget target;
    private static boolean active;

    private BlueprintProjectionTarget1211() {
    }

    static boolean begin() {
        RenderSystem.assertOnRenderThread();
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width <= 0 || main.height <= 0) return false;
        try {
            ensureSize(main.width, main.height);
            target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            target.clear(Minecraft.ON_OSX);
            target.copyDepthFrom(main);
            target.bindWrite(true);
            active = true;
            return true;
        } catch (RuntimeException exception) {
            active = false;
            restoreMainState(true);
            throw exception;
        }
    }

    static void bindWrite() {
        if (active && target != null) {
            target.bindWrite(false);
        }
    }

    static void finish() {
        if (!active || target == null) return;
        active = false;
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.getMainRenderTarget();
        try {
            main.bindWrite(true);
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
            );
            target.blitToScreen(main.width, main.height, false);
        } finally {
            restoreMainState(false);
        }
    }

    static void abort() {
        active = false;
        restoreMainState(true);
    }

    private static void restoreMainState(boolean viewport) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader != null) shader.clear();
        Minecraft.getInstance().getMainRenderTarget().bindWrite(viewport);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    static void release() {
        TextureTarget released = target;
        target = null;
        active = false;
        if (released == null) return;
        if (RenderSystem.isOnRenderThread()) {
            released.destroyBuffers();
        } else {
            RenderSystem.recordRenderCall(released::destroyBuffers);
        }
    }

    private static void ensureSize(int width, int height) {
        if (target == null) {
            target = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            return;
        }
        if (target.width != width || target.height != height) {
            target.resize(width, height, Minecraft.ON_OSX);
        }
    }
}
