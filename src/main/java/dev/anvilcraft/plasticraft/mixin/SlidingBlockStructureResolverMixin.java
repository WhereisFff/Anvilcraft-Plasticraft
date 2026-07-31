package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import dev.dubhe.anvilcraft.api.sliding.SlidingBlockStructureResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 让滑轨结构解析器递归收集树脂胶连接的所有真实方块。 */
@Mixin(value = SlidingBlockStructureResolver.class, priority = 1100)
abstract class SlidingBlockStructureResolverMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    protected abstract boolean addBlockLine(BlockPos start, Direction direction);

    @Shadow
    protected abstract boolean addBranchingBlocks(BlockPos fromPos);

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void plasticraft$resolveAdhesiveGroup(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) return;
        int previousSize;
        do {
            previousSize = this.toPush.size();
            for (BlockPos movedPos : List.copyOf(this.toPush)) {
                for (Direction face : BondedFallingBlocks.blockBondFaces(this.level, movedPos)) {
                    BlockPos partner = movedPos.relative(face);
                    if (this.toPush.contains(partner)) continue;
                    if (!this.addBlockLine(partner, face)
                        || !this.level.getBlockState(partner).isAir() && !this.toPush.contains(partner)) {
                        callback.setReturnValue(false);
                        return;
                    }
                }
                BlockState state = this.level.getBlockState(movedPos);
                if (state.isStickyBlock() && !this.addBranchingBlocks(movedPos)) {
                    callback.setReturnValue(false);
                    return;
                }
            }
        } while (this.toPush.size() != previousSize);
    }
}
