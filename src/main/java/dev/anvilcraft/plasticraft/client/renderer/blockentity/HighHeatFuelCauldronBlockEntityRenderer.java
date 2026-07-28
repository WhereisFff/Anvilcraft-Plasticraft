package dev.anvilcraft.plasticraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.HighHeatFuelCauldronBlock;
import dev.anvilcraft.plasticraft.block.entity.HighHeatFuelCauldronBlockEntity;
import dev.anvilcraft.plasticraft.client.renderer.IgnitedFluidFlameRenderer;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** 在高热燃料液面上渲染动态蓝白色火焰。 */
public final class HighHeatFuelCauldronBlockEntityRenderer
    implements BlockEntityRenderer<HighHeatFuelCauldronBlockEntity> {
    public HighHeatFuelCauldronBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(
        HighHeatFuelCauldronBlockEntity blockEntity,
        float partialTick,
        PoseStack pose,
        MultiBufferSource buffers,
        int packedLight,
        int packedOverlay
    ) {
        BlockState state = blockEntity.getBlockState();
        if (!state.getValue(HighHeatFuelCauldronBlock.IGNITED) || blockEntity.isSpent()) return;
        int level = state.getValue(Layered4LevelCauldronBlock.LEVEL);
        float surfaceY = (6.0F + level * 2.0F) / 16.0F + 0.01F;
        IgnitedFluidFlameRenderer.renderSoul(
            pose,
            buffers,
            surfaceY,
            1.0F,
            packedOverlay
        );
    }

    @Override
    public AABB getRenderBoundingBox(HighHeatFuelCauldronBlockEntity blockEntity) {
        return new AABB(blockEntity.getBlockPos()).expandTowards(0.0D, 2.0D, 0.0D);
    }
}
