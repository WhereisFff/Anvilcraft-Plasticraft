package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/** 轮盘缩略图及物品预览共用带深度附件的独立 GUI 绘制。 */
public record PlasticGeometryPicture(PlasticRenderGeometry geometry, Matrix3x2f pose, int x0, int y0, int x1, int y1,
                                     float scale, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
    implements PictureInPictureRenderState {
    public static void add(GuiGraphicsExtractor graphics, PlasticRenderGeometry geometry, int x, int y,
                           int width, int height, float scale) {
        var matrix = new Matrix3x2f(graphics.pose());
        var clip = graphics.peekScissorStack();
        var bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(matrix);
        graphics.submitPictureInPictureRenderState(new PlasticGeometryPicture(geometry, matrix, x, y, x + width, y + height,
            scale, clip, clip == null ? bounds : bounds.intersection(clip)));
    }

    public static void register(RegisterPictureInPictureRenderersEvent event) {
        event.register(PlasticGeometryPicture.class, Renderer::new);
    }

    private static final class Renderer extends PictureInPictureRenderer<PlasticGeometryPicture> {
        Renderer(MultiBufferSource.BufferSource buffers) {
            super(buffers);
        }

        @Override
        public Class<PlasticGeometryPicture> getRenderStateClass() {
            return PlasticGeometryPicture.class;
        }

        @Override
        protected void renderToTexture(PlasticGeometryPicture picture, PoseStack pose) {
            Minecraft.getInstance().gameRenderer.getLighting().setupFor(Lighting.Entry.ITEMS_3D);
            picture.geometry.draw(pose, this.bufferSource, Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState);
        }

        @Override
        protected float getTranslateY(int height, int guiScale) {
            return height * 0.5f;
        }

        @Override
        protected String getTextureLabel() {
            return "Plastic model preview";
        }
    }
}
