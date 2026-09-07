package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.Plastic3DPrintingComponentBlock;
import dev.anvilcraft.plasticraft.block.entity.Plastic3DPrintingComponentBlockEntity;
import dev.dubhe.anvilcraft.client.support.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;

/** 在 cg 体积内绘制储存的塑料熔体；有电时再用全亮类型绘制拆出的屏幕和眼睛。 */
public final class Plastic3DPrintingComponentRenderer
    implements BlockEntityRenderer<Plastic3DPrintingComponentBlockEntity> {
    public static final ModelResourceLocation EYE_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_component_eye")
    );
    public static final ModelResourceLocation SCREEN_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/print_component_screen")
    );
    private static final float TANK_INSET = 0.02F / 16.0F;
    private static final float TANK_MIN_X = 4.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_X = 12.0F / 16.0F - TANK_INSET;
    private static final float TANK_MIN_Y = 4.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_Y = 9.0F / 16.0F - TANK_INSET;
    private static final float TANK_MIN_Z = 2.0F / 16.0F + TANK_INSET;
    private static final float TANK_MAX_Z = 10.0F / 16.0F - TANK_INSET;
    private final BlockRenderDispatcher dispatcher;

    public Plastic3DPrintingComponentRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockRenderDispatcher();
    }

    @Override
    public void render(
        Plastic3DPrintingComponentBlockEntity component,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        renderDynamicParts(
            component.getBlockState(),
            component.fluid(),
            poseStack,
            bufferSource,
            packedLight,
            this.dispatcher
        );
    }

    public static void renderDynamicParts(
        BlockState state,
        FluidStack fluid,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        BlockRenderDispatcher dispatcher
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
                poseStack, bufferSource, dispatcher, SCREEN_MODEL, Sheets.translucentCullBlockSheet()
            );
            renderStandaloneModel(poseStack, bufferSource, dispatcher, EYE_MODEL, RenderType.cutout());
        }
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(Plastic3DPrintingComponentBlockEntity component) {
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
            fluid,
            TANK_MIN_X,
            TANK_MIN_Y,
            TANK_MIN_Z,
            TANK_MAX_X,
            fluidTop,
            TANK_MAX_Z,
            bufferSource,
            poseStack,
            packedLight,
            true,
            false
        );
    }

    private static void renderStandaloneModel(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        BlockRenderDispatcher dispatcher,
        ModelResourceLocation location,
        RenderType renderType
    ) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(location);
        dispatcher.getModelRenderer().renderModel(
            poseStack.last(),
            bufferSource.getBuffer(renderType),
            null,
            model,
            0.0F,
            0.0F,
            0.0F,
            LightTexture.FULL_BLOCK,
            OverlayTexture.NO_OVERLAY
        );
    }
}
