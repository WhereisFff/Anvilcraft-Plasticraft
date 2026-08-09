package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.client.renderer.entity.PlasticEntityRenderHelper;
import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayLightSource;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayCell;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentPlacement;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;

/** 按建模格绘制支架承载的方块元件。 */
public final class MoldedTrayComponentRenderer {
    private MoldedTrayComponentRenderer() {
    }

    public static void render(
        MoldedPlasticData data,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        render(data, dispatcher, Map.of(), 0.0F, pose, buffers, packedLight, packedOverlay);
    }

    public static void render(
        MoldedPlasticData data,
        BlockRenderDispatcher dispatcher,
        Map<MoldedTrayCell, BlockEntity> blockEntities,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        for (MoldedTrayComponentPlacement placement : trayComponents(data)) {
            AABB componentBounds = MoldedTrayComponentGeometry.localBounds(data, placement.cell());
            BlockState state = placement.component().state();
            int componentLight = LightTexture.pack(
                Math.max(LightTexture.block(packedLight), MoldedTrayLightSource.emission(state)),
                LightTexture.sky(packedLight)
            );
            pose.pushPose();
            pose.translate(componentBounds.minX, componentBounds.minY, componentBounds.minZ);
            dispatcher.renderSingleBlock(
                state,
                pose,
                buffers,
                componentLight,
                packedOverlay
            );
            BlockEntity blockEntity = blockEntities.get(placement.cell());
            if (blockEntity != null
                && blockEntity.getBlockState().getBlock() == state.getBlock()) {
                Minecraft.getInstance().getBlockEntityRenderDispatcher().render(
                    blockEntity,
                    partialTick,
                    pose,
                    buffers
                );
            }
            pose.popPose();
        }
    }

    public static AABB renderBounds(MoldedPlasticData data) {
        AABB bounds = data.surfaceBounds();
        for (MoldedTrayComponentPlacement placement : trayComponents(data)) {
            bounds = bounds.minmax(MoldedTrayComponentGeometry.localBounds(data, placement.cell()));
        }
        return bounds;
    }

    /** 使用与塑料本体一致的颜色覆盖绘制牵引终点元件。 */
    public static void renderPreview(
        MoldedPlasticData data,
        BlockRenderDispatcher dispatcher,
        PoseStack pose,
        MultiBufferSource buffers,
        boolean valid
    ) {
        for (MoldedTrayComponentPlacement placement : trayComponents(data)) {
            AABB componentBounds = MoldedTrayComponentGeometry.localBounds(data, placement.cell());
            pose.pushPose();
            pose.translate(componentBounds.minX, componentBounds.minY, componentBounds.minZ);
            PlasticEntityRenderHelper.renderHammerPreviewModel(
                placement.component().state(),
                dispatcher.getBlockModel(placement.component().state()),
                dispatcher,
                pose,
                buffers,
                valid
            );
            pose.popPose();
        }
    }

    private static List<MoldedTrayComponentPlacement> trayComponents(MoldedPlasticData data) {
        return MoldingProductTypes.isTray(data.finalType())
            ? data.contents().trayComponents()
            : List.of();
    }
}
