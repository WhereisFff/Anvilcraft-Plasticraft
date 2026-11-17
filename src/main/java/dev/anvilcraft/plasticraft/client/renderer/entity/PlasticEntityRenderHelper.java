package dev.anvilcraft.plasticraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anvilcraft.plasticraft.client.renderer.ClearPlasticRenderTypes;
import dev.anvilcraft.plasticraft.client.renderer.MoldedPlasticMeshRenderer;
import dev.anvilcraft.plasticraft.client.renderer.PlasticBlockModel;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.ResinAnvilEntity;
import dev.anvilcraft.plasticraft.entity.UniversalPlasticEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.client.init.ModRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** 实体化塑料制品共用的方块模型渲染逻辑。 */
public final class PlasticEntityRenderHelper {
    public static final PlasticBlockModel HAMMER_AXIS_MODEL = new PlasticBlockModel(
        AnvilCraft.of("block/axis")
    );

    private PlasticEntityRenderHelper() {
    }

    public static void renderBlock(
        AbstractPlasticEntity entity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        BlockStateModel model = PlasticBlockModel.of(state);
        RenderType renderType = isTransparent(state)
            ? ClearPlasticRenderTypes.block()
            : entity instanceof ResinAnvilEntity
                ? Sheets.translucentBlockItemSheet()
                : Sheets.cutoutBlockSheet();
        VertexConsumer consumer = buffers.getBuffer(renderType);
        if (entity instanceof UniversalPlasticEntity universal) {
            consumer = TransparentPlasticFaceCullingVertexConsumer.wrapWithBlockFaceShading(universal, consumer);
        }
        int tint = entity.getDisplayTint();
        float red = (float) (tint >> 16 & 0xFF) / 255.0F;
        float green = (float) (tint >> 8 & 0xFF) / 255.0F;
        float blue = (float) (tint & 0xFF) / 255.0F;
        PlasticBlockModel.render(
            pose.last(),
            consumer,
            model,
            red,
            green,
            blue,
            packedLight,
            OverlayTexture.NO_OVERLAY
        );
        TransparentPlasticFaceCullingVertexConsumer.finish(consumer);
    }

    /** 实体、轮盘缩略图和终点预览必须从同一份同步模型数据选择外观。 */
    public static void renderModel(
        AbstractPlasticEntity entity,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight
    ) {
        if (entity instanceof UniversalPlasticEntity universal
            && universal.getMoldedData().isPresent()) {
            MoldedPlasticMeshRenderer.render(
                universal.getMoldedData().orElseThrow(),
                pose,
                buffers,
                packedLight,
                0xFFFFFFFF,
                isTransparent(universal),
                universal
            );
            return;
        }
        renderBlock(entity, pose, buffers, packedLight);
    }

    /** 逐帧多次问询，因此避开熔体栈复制和 Optional 链。 */
    public static boolean isTransparent(UniversalPlasticEntity entity) {
        if (isTransparent(entity.getDisplayState())) return true;
        MoldedPlasticData data = entity.getMoldedData().orElse(null);
        if (data == null) return false;
        PlasticMaterial material = PlasticMaterial.fromMeltOrNull(data.materialView());
        return material != null && material.isTransparent();
    }

    public static boolean isTransparent(BlockState state) {
        return state.is(PlasticraftBlocks.CLEAR_PLASTIC.get()) || state.is(PlasticraftBlocks.ELASTIC_PLASTIC.get());
    }

    /** 使用本体铁砧锤的蓝色半透明材质渲染方向预览。 */
    public static void renderHammerPreviewModel(
        AbstractPlasticEntity entity,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        renderHammerPreviewModel(entity, pose, buffers, true);
    }

    public static void renderHammerPreviewModel(
        AbstractPlasticEntity entity,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        if (entity instanceof UniversalPlasticEntity universal
            && universal.getMoldedData().isPresent()) {
            MoldedPlasticMeshRenderer.renderPreview(
                universal.getMoldedData().orElseThrow(),
                pose,
                buffers,
                valid
            );
            return;
        }
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        BlockStateModel model = PlasticBlockModel.of(state);
        renderHammerPreviewModel(state, model, pose, buffers, valid);
    }

    /** 合法姿态沿用本体淡蓝覆盖，碰撞姿态用红色顶点色将其转为淡红覆盖。 */
    public static void renderHammerPreviewModel(
        BlockState state,
        BlockStateModel model,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        int greenBlue = valid ? 255 : 26;
        VertexConsumer consumer = new PreviewColorVertexConsumer(
            buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY),
            255,
            greenBlue,
            greenBlue
        );
        PlasticBlockModel.render(
            pose.last(),
            consumer,
            model,
            1.0F,
            1.0F,
            1.0F,
            LightCoordsUtil.pack(15, 0),
            OverlayTexture.NO_OVERLAY
        );
    }

    /** 使用同一蓝色半透明材质渲染普通下落方块的终点虚影。 */
    public static void renderFallingPreviewModel(
        FallingBlockEntity entity,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        renderFallingPreviewModel(entity, pose, buffers, true);
    }

    public static void renderFallingPreviewModel(
        FallingBlockEntity entity,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        BlockState state = entity.getBlockState();
        BlockStateModel model = PlasticBlockModel.of(state);
        VertexConsumer source = buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY);
        VertexConsumer consumer = valid ? source : new PreviewColorVertexConsumer(source, 255, 26, 26);
        PlasticBlockModel.render(
            pose.last(),
            consumer,
            model,
            1.0F,
            1.0F,
            1.0F,
            LightCoordsUtil.pack(15, 0),
            OverlayTexture.NO_OVERLAY
        );
    }

    /** 渲染固定在世界坐标轴上的蓝色方向轴。 */
    public static void renderHammerAxis(
        AbstractPlasticEntity entity,
        PoseStack pose,
        MultiBufferSource buffers
    ) {
        BlockState state = PlasticEntityRenderTransforms.canonicalize(entity.getDisplayState());
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.TRANSLUCENT_COLORED_OVERLAY);
        BlockStateModel axis = HAMMER_AXIS_MODEL.baked();
        PlasticBlockModel.render(
            pose.last(),
            consumer,
            axis,
            1.0F,
            1.0F,
            1.0F,
            LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY
        );
    }

    private static final class PreviewColorVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int red;
        private final int green;
        private final int blue;

        private PreviewColorVertexConsumer(VertexConsumer delegate, int red, int green, int blue) {
            this.delegate = delegate;
            this.red = red;
            this.green = green;
            this.blue = blue;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            this.delegate.setColor(this.red, this.green, this.blue, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this.setColor(this.red, this.green, this.blue, color >>> 24);
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            this.delegate.setLineWidth(width);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            this.delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            this.delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            this.delegate.setNormal(x, y, z);
            return this;
        }
    }
}
