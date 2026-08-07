package dev.anvilcraft.plasticraft.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.molding.bake.MoldingTrayShapeAnalysis;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.phys.AABB;

import java.util.Optional;

/** 在塑料网格局部中心绘制支架承载的方块元件。 */
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
        render(data, dispatcher, null, 0.0F, pose, buffers, packedLight, packedOverlay);
    }

    public static void render(
        MoldedPlasticData data,
        BlockRenderDispatcher dispatcher,
        @Nullable BlockEntity blockEntity,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        Optional<MoldedTrayComponent> component = trayComponent(data);
        if (component.isEmpty()) return;
        MoldingTrayShapeAnalysis shape = data.trayShapeAnalysis();
        if (!shape.valid()) return;
        MoldedTrayComponent tray = component.orElseThrow();
        AABB componentBounds = MoldedTrayComponentGeometry.localBounds(shape);
        pose.pushPose();
        pose.translate(componentBounds.minX, componentBounds.minY, componentBounds.minZ);
        dispatcher.renderSingleBlock(
            tray.state(),
            pose,
            buffers,
            packedLight,
            packedOverlay
        );
        if (blockEntity != null
            && blockEntity.getBlockState().getBlock() == tray.state().getBlock()) {
            Minecraft.getInstance().getBlockEntityRenderDispatcher().render(
                blockEntity,
                partialTick,
                pose,
                buffers
            );
        }
        pose.popPose();
    }

    public static AABB renderBounds(MoldedPlasticData data) {
        AABB bounds = data.surfaceBounds();
        if (trayComponent(data).isEmpty()) return bounds;
        MoldingTrayShapeAnalysis shape = data.trayShapeAnalysis();
        if (!shape.valid()) return bounds;
        return bounds.minmax(MoldedTrayComponentGeometry.localBounds(shape));
    }

    private static Optional<MoldedTrayComponent> trayComponent(MoldedPlasticData data) {
        return MoldingProductTypes.isTray(data.finalType())
            ? data.contents().trayComponent()
            : Optional.empty();
    }
}
