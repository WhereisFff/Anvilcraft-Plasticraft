package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.entity.MoldedPlasticCauldronState;
import dev.anvilcraft.plasticraft.item.DyeableMaterial;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/** 在同一物品 ID 上渲染旧方块外形和动态制造外形。 */
public final class UniversalPlasticItemRenderer implements SpecialModelRenderer<ItemStackTemplate> {
    public enum Unbaked implements SpecialModelRenderer.Unbaked<ItemStackTemplate> {
        INSTANCE;

        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public SpecialModelRenderer<ItemStackTemplate> bake(BakingContext context) {
            return new UniversalPlasticItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (int corner = 0; corner < 8; corner++) {
            output.accept(new Vector3f(corner & 1, corner >> 1 & 1, corner >> 2));
        }
    }

    @Override
    public ItemStackTemplate extractArgument(ItemStack stack) {
        return new ItemStackTemplate(stack.typeHolder(), stack.getComponentsPatch());
    }

    @Override
    public void submit(@Nullable ItemStackTemplate stack, PoseStack parent, SubmitNodeCollector collector,
                       int light, int overlay, boolean foil, int outlineColor) {
        if (stack == null) {
            return;
        }
        PlasticRenderGeometry geometry = new PlasticRenderGeometry();
        renderItem(stack.create(), new PoseStack(), geometry, light, overlay);
        geometry.submit(parent, collector, Minecraft.getInstance().gameRenderer.getGameRenderState().levelRenderState.cameraRenderState);
    }

    private static void renderItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MoldedPlasticData data = MoldedPlasticData.get(stack).orElse(null);
        if (data == null) {
            Block productBlock = Block.byItem(stack.getItem());
            BlockState state = productBlock.defaultBlockState();
            if (state.hasProperty(DyeableMaterial.COLOR)) {
                state = state.setValue(DyeableMaterial.COLOR, PlasticMeltColor.get(stack));
            }
            if (PlasticEntityRenderHelper.isTransparent(state)) {
                renderClearPlasticBlock(state, pose, buffers, packedLight, packedOverlay);
            } else {
                PlasticBlockModel.render(
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
        BlockStateModel model = PlasticBlockModel.of(state);
        PlasticBlockModel.render(
            pose.last(),
            buffers.getBuffer(Sheets.translucentBlockItemSheet()),
            model,
            1.0F,
            1.0F,
            1.0F,
            packedLight,
            packedOverlay
        );
    }
}
