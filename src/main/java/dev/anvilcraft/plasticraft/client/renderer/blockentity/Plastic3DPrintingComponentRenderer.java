package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.Plastic3DPrintingComponentBlock;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.FluidRenderOpacity;
import dev.anvilcraft.plasticraft.client.renderer.PlasticBlockModel;
import dev.anvilcraft.plasticraft.client.renderer.PlasticRenderGeometry;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** 在 cg 体积内绘制储存的塑料熔体；有电时再用全亮类型绘制拆出的屏幕和眼睛。 */
public final class Plastic3DPrintingComponentRenderer
    extends PlasticBlockEntityRenderer<Plastic3DPrintingComponentBlockEntity> {
    public static final PlasticBlockModel EYE_MODEL = new PlasticBlockModel(
        AnvilcraftPlasticraft.of("block/print_component_eye")
    );
    public static final PlasticBlockModel SCREEN_MODEL = new PlasticBlockModel(
        AnvilcraftPlasticraft.of("block/print_component_screen")
    );
    private static final float TANK_INSET = 0.02F / 16.0F;
    private static final float TANK_MIN_X = 4.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_X = 12.0F / 16.0F - TANK_INSET;
    private static final float TANK_MIN_Y = 4.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_Y = 9.0F / 16.0F - TANK_INSET;
    private static final float TANK_MIN_Z = 2.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_Z = 10.0F / 16.0F - TANK_INSET;


    public Plastic3DPrintingComponentRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void extractGeometry(
        Plastic3DPrintingComponentBlockEntity component,
        float partialTick,
        PoseStack poseStack,
        PlasticRenderGeometry bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        renderDynamicParts(
            component.getBlockState(),
            component.fluid(),
            poseStack,
            bufferSource,
            packedLight
        );
    }

    public static void renderDynamicParts(
        BlockState state,
        FluidStack fluid,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        Direction facing = state.hasProperty(Plastic3DPrintingComponentBlock.FACING)
            ? state.getValue(Plastic3DPrintingComponentBlock.FACING)
            : Direction.NORTH;
        boolean powered = state.hasProperty(Plastic3DPrintingComponentBlock.POWERED)
            && state.getValue(Plastic3DPrintingComponentBlock.POWERED);
        poseStack.pushPose();
        poseStack.mulPose(MachineModelTransforms.facing(facing));
        renderMelt(fluid, poseStack, bufferSource, packedLight);
        if (powered) {
            renderStandaloneModel(
                poseStack, bufferSource, SCREEN_MODEL, Sheets.translucentBlockSheet()
            );
            renderStandaloneModel(poseStack, bufferSource, EYE_MODEL, Sheets.cutoutBlockSheet());
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(Plastic3DPrintingComponentBlockEntity component) {
        return new AABB(component.getBlockPos()).inflate(0.25D).expandTowards(0.0D, 0.25D, 0.0D);
    }

    private static void renderMelt(
        FluidStack fluid,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        if (fluid.isEmpty()) return;
        float fill = Mth.clamp(
            (float) fluid.getAmount() / Plastic3DPrintingComponentBlockEntity.CAPACITY,
            0.0F,
            1.0F
        );
        float fluidTop = Mth.lerp(fill, TANK_MIN_Y, TANK_MAX_Y);
        FluidRenderHelper.INSTANCE.renderFluidBox(
                FluidRenderOpacity.model(fluid).stillMaterial().sprite(), FluidResource.of(fluid),
                TANK_MIN_X,
            TANK_MIN_Y,
            TANK_MIN_Z,
            TANK_MAX_X,
            fluidTop,
            TANK_MAX_Z,
                FluidRenderOpacity.tint(fluid), bufferSource.getBuffer(FluidRenderOpacity.isOpaque(fluid) ? Sheets.cutoutBlockSheet() : Sheets.translucentBlockSheet()),
                poseStack.last(), packedLight, true, false
            );
    }

    private static void renderStandaloneModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        PlasticBlockModel location,
        RenderType renderType
    ) {
        BlockStateModel model = location.baked();
        PlasticBlockModel.render(
            poseStack.last(),
            bufferSource.getBuffer(renderType),
            model,
            0.0F,
            0.0F,
            0.0F,
            LightCoordsUtil.pack(15, 0),
            OverlayTexture.NO_OVERLAY
        );
    }
}
