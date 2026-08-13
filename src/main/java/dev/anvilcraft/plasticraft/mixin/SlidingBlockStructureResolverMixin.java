package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.block.piston.BondedPistonReactions;
import dev.dubhe.anvilcraft.api.sliding.SlidingBlockStructureResolver;
import dev.dubhe.anvilcraft.block.sliding.ISlidingRail;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 让滑轨结构解析器递归收集树脂胶连接的所有真实方块。
 * 粘在滑轨上的方块以胶粘为准，不能把下方滑轨并入载荷，否则本体会先拆掉可移动方块实体再因“起点下方是滑轨”提前返回。
 */
@Mixin(value = SlidingBlockStructureResolver.class, priority = 1100)
abstract class SlidingBlockStructureResolverMixin {
    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    @Final
    private List<BlockPos> toDestroy;

    @Shadow
    @Final
    private Direction pushDirection;

    @Shadow
    protected abstract boolean addBlockLine(BlockPos start, Direction direction);

    @Shadow
    protected abstract boolean addBranchingBlocks(BlockPos fromPos);

    @Unique
    private @Nullable BlockPos plasticraft$pushReactionPos;

    @Redirect(
        method = {"resolve", "addBlockLine", "addBranchingBlocks"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState("
                     + "Lnet/minecraft/core/BlockPos;"
                     + ")Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState plasticraft$trackPushReactionPos(Level level, BlockPos pos) {
        this.plasticraft$pushReactionPos = pos.immutable();
        return level.getBlockState(pos);
    }

    @WrapOperation(
        method = {"resolve", "addBlockLine"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getPistonPushReaction()"
                     + "Lnet/minecraft/world/level/material/PushReaction;"
        )
    )
    private PushReaction plasticraft$treatBondedDestroyAsNormal(
        BlockState state,
        Operation<PushReaction> original
    ) {
        PushReaction reaction = original.call(state);
        BlockPos pos = this.plasticraft$pushReactionPos;
        return pos == null
            ? reaction
            : BondedPistonReactions.pushReaction(this.level, pos, state, reaction, this.pushDirection);
    }

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void plasticraft$resolveAdhesiveGroup(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValue()) return;
        BondedPistonReactions.claimDestroyBlocks(this.level, this.toPush, this.toDestroy, this.pushDirection);
        int previousSize;
        do {
            previousSize = this.toPush.size();
            for (BlockPos movedPos : List.copyOf(this.toPush)) {
                for (Direction face : BondedPistonReactions.blockBondFaces(
                    this.level,
                    movedPos,
                    this.pushDirection
                )) {
                    BlockPos partner = movedPos.relative(face);
                    if (this.toPush.contains(partner)) continue;
                    // 胶粘优先：粘在滑轨上的方块组整组都不作为滑轨载荷移动。
                    if (this.level.getBlockState(partner).getBlock() instanceof ISlidingRail) {
                        callback.setReturnValue(false);
                        return;
                    }
                    if (!this.addBlockLine(partner, face)
                        || !this.level.getBlockState(partner).isAir()
                            && !this.toPush.contains(partner)
                            && !BondedPistonReactions.claimDestroyBlock(
                                this.level,
                                this.toPush,
                                this.toDestroy,
                                partner,
                                this.pushDirection
                            )) {
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
            BondedPistonReactions.claimDestroyBlocks(this.level, this.toPush, this.toDestroy, this.pushDirection);
        } while (this.toPush.size() != previousSize);
    }
}
