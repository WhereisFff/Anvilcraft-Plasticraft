package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.AbstractPlasticEntityBlock;
import net.minecraft.client.renderer.blockentity.PistonHeadRenderer;
import net.minecraft.client.renderer.blockentity.state.PistonHeadRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
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
    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/piston/PistonMovingBlockEntity;"
        + "Lnet/minecraft/client/renderer/blockentity/state/PistonHeadRenderState;FLnet/minecraft/world/phys/Vec3;"
        + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V", at = @At("RETURN"))
    private void plasticraft$skipInvisiblePlasticModel(
        PistonMovingBlockEntity piston, PistonHeadRenderState state, float partialTick, Vec3 camera,
        ModelFeatureRenderer.CrumblingOverlay breaking, CallbackInfo callback
    ) {
        BlockState moved = piston.getMovedState();
        if (moved.getBlock() instanceof AbstractPlasticEntityBlock<?> && moved.getRenderShape() == RenderShape.INVISIBLE) {
            state.block = null;
        }
    }
}
