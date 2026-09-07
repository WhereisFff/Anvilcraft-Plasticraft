package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.AnvilcraftPlasticraft;
import dev.anvilcraft.plasticraft.block.AllayLoungeBlock;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

public final class AllayLoungeRenderer implements BlockEntityRenderer<AllayLoungeBlockEntity> {
    public static final ModelResourceLocation HATCH_LEFT_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/allay_lounge_hatch_left")
    );
    public static final ModelResourceLocation HATCH_RIGHT_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/allay_lounge_hatch_right")
    );
    public static final ModelResourceLocation INDICATOR_BLUE_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/allay_lounge_indicator_blue")
    );
    public static final ModelResourceLocation INDICATOR_GREEN_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/allay_lounge_indicator_green")
    );
    public static final ModelResourceLocation INDICATOR_RED_MODEL = ModelResourceLocation.standalone(
        AnvilcraftPlasticraft.of("block/allay_lounge_indicator_red")
    );

    private final BlockRenderDispatcher dispatcher;
    private final AllayLoungeItemRenderer items;

    public AllayLoungeRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockRenderDispatcher();
        this.items = new AllayLoungeItemRenderer(Minecraft.getInstance().getItemRenderer());
    }

    @Override
    public void render(
        AllayLoungeBlockEntity lounge,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight,
        int packedOverlay
    ) {
        Direction facing = lounge.getBlockState().getValue(AllayLoungeBlock.FACING);
        float openness = lounge.hatchOpenness(partialTick);
        poseStack.pushPose();
        poseStack.mulPose(MachineModelTransforms.facing(facing));
        this.renderHatch(lounge, HATCH_LEFT_MODEL, true, openness,
            poseStack, bufferSource, packedLight, packedOverlay);
        this.renderHatch(lounge, HATCH_RIGHT_MODEL, false, openness,
            poseStack, bufferSource, packedLight, packedOverlay);
        ModelResourceLocation indicator = switch (lounge.indicatorStatus()) {
            case RUNNING -> INDICATOR_GREEN_MODEL;
            case INTERRUPTED -> INDICATOR_RED_MODEL;
            case IDLE -> INDICATOR_BLUE_MODEL;
        };
        this.renderPart(lounge, indicator, poseStack, bufferSource, LightTexture.FULL_BRIGHT, packedOverlay);
        poseStack.popPose();

        // 预约面是世界方向；四面斜槽对称，不能再叠加一次方块朝向。
        for (Direction side : Direction.Plane.HORIZONTAL) {
            ItemStack stack = lounge.pickupDisplay(side);
            if (stack.isEmpty()) continue;
            this.items.render(lounge, side, stack, lounge.pickupSlideProgress(side, partialTick),
                poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void renderHatch(
        AllayLoungeBlockEntity lounge,
        ModelResourceLocation model,
        boolean left,
        float openness,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay
    ) {
        pose.pushPose();
        pose.mulPose(MachineModelTransforms.loungeHatch(left, openness));
        this.renderPart(lounge, model, pose, buffers, light, overlay);
        pose.popPose();
    }

    private void renderPart(
        AllayLoungeBlockEntity lounge,
        ModelResourceLocation model,
        PoseStack pose,
        MultiBufferSource buffers,
        int light,
        int overlay
    ) {
        this.dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(Sheets.cutoutBlockSheet()),
            lounge.getBlockState(),
            Minecraft.getInstance().getModelManager().getModel(model),
            1.0F, 1.0F, 1.0F, light, overlay
        );
    }

    @Override
    public AABB getRenderBoundingBox(AllayLoungeBlockEntity lounge) {
        return new AABB(lounge.getBlockPos()).inflate(0.25D, 0.0D, 0.25D)
            .expandTowards(0.0D, 0.5D, 0.0D);
    }
}
