package dev.anvilcraft.plasticraft.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge 活塞移动渲染会忽略 {@link RenderShape#INVISIBLE}，
 * 把默认 16x16x16 烘焙模型叠在方块化塑料的实体网格上。
 */
@Mixin(PistonHeadRenderer.class)
abstract class PistonHeadRendererMixin {
    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void plasticraft$skipInvisiblePlasticModel(
        BlockPos pos,
        BlockState state,
        PoseStack pose,
        MultiBufferSource buffers,
        Level level,
        boolean checkSides,
        int packedOverlay,
        CallbackInfo ci
    ) {
        if (state.getBlock() instanceof AbstractPlasticEntityBlock<?>
            && state.getRenderShape() == RenderShape.INVISIBLE) {
            ci.cancel();
        }
    }
}
