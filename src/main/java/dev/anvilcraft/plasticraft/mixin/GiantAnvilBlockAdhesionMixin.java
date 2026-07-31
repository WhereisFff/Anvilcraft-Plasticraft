package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.dubhe.anvilcraft.block.GiantAnvilBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让任一组成块仍有胶连接的巨型铁砧保持完整方块结构。 */
@Mixin(GiantAnvilBlock.class)
abstract class GiantAnvilBlockAdhesionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$keepBondedGiantAnvilFixed(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo callback
    ) {
        if (!state.hasProperty(GiantAnvilBlock.HALF)) return;
        GiantAnvilBlock block = (GiantAnvilBlock) (Object) this;
        Cube3x3PartHalf currentPart = state.getValue(GiantAnvilBlock.HALF);
        BlockPos bottomCenter = pos.subtract(currentPart.getOffset());
        for (Cube3x3PartHalf part : block.getParts()) {
            if (!BondedFallingBlocks.isBonded(level, bottomCenter.offset(part.getOffset()))) continue;
            level.scheduleTick(bottomCenter, block, 2);
            callback.cancel();
            return;
        }
    }
}
