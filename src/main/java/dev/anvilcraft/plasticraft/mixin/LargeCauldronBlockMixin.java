package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 手持冷凝塔时把大锅顶面显示为完整方块，方便对齐放置。 */
@Mixin(LargeCauldronBlock.class)
abstract class LargeCauldronBlockMixin {
    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void plasticraft$showCondenserPlacementShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context,
        CallbackInfoReturnable<VoxelShape> cir
    ) {
        if (state.getValue(LargeCauldronBlock.HALF).getOffsetY() == 2
            && context.isHoldingItem(PlasticraftBlocks.CONDENSER_TOWER.asItem())) {
            cir.setReturnValue(Shapes.block());
        }
    }
}
