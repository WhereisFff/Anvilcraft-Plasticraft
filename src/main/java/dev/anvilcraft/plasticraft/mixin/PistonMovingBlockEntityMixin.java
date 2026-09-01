package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.block.piston.BondedPistonReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** 胶粘 DESTROY 方块落地时跳过邻接形状破坏，避免支撑尚未放回时被打碎。 */
@Mixin(PistonMovingBlockEntity.class)
abstract class PistonMovingBlockEntityMixin {
    @WrapOperation(
        method = {"tick", "finalTick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;updateFromNeighbourShapes("
                     + "Lnet/minecraft/world/level/block/state/BlockState;"
                     + "Lnet/minecraft/world/level/LevelAccessor;"
                     + "Lnet/minecraft/core/BlockPos;"
                     + ")Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private static BlockState plasticraft$keepBondedDestroyBlock(
        BlockState movedState,
        LevelAccessor level,
        BlockPos pos,
        Operation<BlockState> original
    ) {
        return BondedPistonReactions.settleState(
            level,
            pos,
            movedState,
            original.call(movedState, level, pos)
        );
    }

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/Block;updateOrDestroy("
                     + "Lnet/minecraft/world/level/block/state/BlockState;"
                     + "Lnet/minecraft/world/level/block/state/BlockState;"
                     + "Lnet/minecraft/world/level/LevelAccessor;"
                     + "Lnet/minecraft/core/BlockPos;"
                     + "I)V"
        )
    )
    private static void plasticraft$skipBondedDestroyDrop(
        BlockState from,
        BlockState to,
        LevelAccessor level,
        BlockPos pos,
        int flags,
        Operation<Void> original
    ) {
        if (level instanceof Level world && BondedPistonReactions.isAdhesivelyGrouped(world, pos)) {
            return;
        }
        original.call(from, to, level, pos, flags);
    }

    @WrapOperation(
        method = {"tick", "finalTick"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;neighborChanged("
                     + "Lnet/minecraft/core/BlockPos;"
                     + "Lnet/minecraft/world/level/block/Block;"
                     + "Lnet/minecraft/core/BlockPos;"
                     + ")V"
        )
    )
    private static void plasticraft$skipFragileBondedNeighborUpdate(
        Level level,
        BlockPos pos,
        Block block,
        BlockPos fromPos,
        Operation<Void> original
    ) {
        if (BondedPistonReactions.shouldSkipSettleNeighborUpdate(level, pos)) {
            return;
        }
        original.call(level, pos, block, fromPos);
    }
}
