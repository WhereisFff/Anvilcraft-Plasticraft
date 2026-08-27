package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 在同一物品 ID 上渲染旧方块外形和动态制造外形。 */
public final class UniversalPlasticItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static UniversalPlasticItemRenderer instance;

    private UniversalPlasticItemRenderer(Minecraft minecraft) {
        super(minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
    }

    public static UniversalPlasticItemRenderer getInstance() {
        if (instance == null) instance = new UniversalPlasticItemRenderer(Minecraft.getInstance());
        return instance;
    }

    @Override
    public void renderByItem(
        ItemStack stack,
        ItemDisplayContext displayContext,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElse(null);
        if (data == null) {
            Block productBlock = Block.byItem(stack.getItem());
            BlockState state = productBlock.defaultBlockState();
            if (state.hasProperty(DyeableMaterial.COLOR)) {
                state = state.setValue(DyeableMaterial.COLOR, PlasticMeltColor.get(stack));
            }
            if (state.is(PlasticraftBlocks.CLEAR_PLASTIC.get())) {
                renderClearPlasticBlock(state, pose, buffers, packedLight, packedOverlay);
            } else {
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    state,
                    pose,
                    buffers,
                    packedLight,
                    packedOverlay
                );
            }
            return;
        }
        AABB bounds = MoldedTrayComponentRenderer.renderBounds(data);
        Direction outlet = MoldingProductTypes.isCauldron(data.finalType())
            ? MoldedPlasticCauldronState.get(stack).flatMap(MoldedPlasticCauldronState::outletSide).orElse(null)
            : null;
        if (outlet != null) bounds = bounds.minmax(MoldedPlasticMeshRenderer.cauldronOutletBounds(data, outlet));
        boolean translucent = PlasticMaterial.fromMelt(data.material()).map(PlasticMaterial::isTransparent).orElse(false);
        double largestSize = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        if (!Double.isFinite(largestSize) || largestSize <= 0.0D) return;
        Vec3 center = bounds.getCenter();
        float scale = (float) (1.0D / largestSize);
        pose.pushPose();
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.scale(scale, scale, scale);
        pose.translate(-center.x, -center.y, -center.z);
        MoldedPlasticMeshRenderer.render(
            data,
            pose,
            buffers,
            packedLight,
            0xFFFFFFFF,
            translucent
        );
        if (outlet != null) {
            MoldedPlasticMeshRenderer.renderCauldronOutlet(
                data,
                outlet,
                pose,
                buffers,
                packedLight,
                translucent
            );
        }
        MoldedTrayComponentRenderer.render(
            data,
            Minecraft.getInstance().getBlockRenderer(),
            pose,
            buffers,
            packedLight,
            packedOverlay
        );
        pose.popPose();
    }

    private static void renderClearPlasticBlock(
        BlockState state,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BakedModel model = dispatcher.getBlockModel(state);
        dispatcher.getModelRenderer().renderModel(
            pose.last(),
            buffers.getBuffer(Sheets.translucentItemSheet()),
            state,
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            packedOverlay
        );
    }
}
