package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.anvilcraft.plasticraft.block.piston.BondedPistonReactions;
import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import dev.anvilcraft.plasticraft.block.piston.PlasticPistonOccupancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

/** 使用活塞本次真实解析出的移动列表同步迁移胶粘状态。 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
    @Redirect(
        method = "checkIfExtend",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;blockEvent("
                     + "Lnet/minecraft/core/BlockPos;"
                     + "Lnet/minecraft/world/level/block/Block;"
                     + "II)V"
        ),
        require = 2
    )
    private void plasticraft$preserveShortPulseDrop(
        Level level,
        BlockPos pistonPos,
        Block piston,
        int eventId,
        int eventParam
    ) {
        Direction facing = Direction.from3DDataValue(eventParam & 7);
        int adjustedEventId = eventId == PistonBaseBlock.TRIGGER_CONTRACT
            && PlasticPistonOccupancy.shouldDropOnRetraction(level, pistonPos, facing)
            ? PistonBaseBlock.TRIGGER_DROP
            : eventId;
        level.blockEvent(pistonPos, piston, adjustedEventId, eventParam);
    }

    @Redirect(
        method = "triggerEvent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getBlockState("
                     + "Lnet/minecraft/core/BlockPos;"
                     + ")Lnet/minecraft/world/level/block/state/BlockState;"
        )
    )
    private BlockState plasticraft$includePlasticEntityDuringRetraction(Level level, BlockPos pos) {
        return PlasticPistonOccupancy.blockState(level, pos, level.getBlockState(pos));
    }

    @Inject(method = "isPushable", at = @At("RETURN"), cancellable = true)
    private static void plasticraft$allowBondedDestroyBlocks(
        BlockState state,
        Level level,
        BlockPos pos,
        Direction movementDirection,
        boolean allowDestroy,
        Direction pistonFacing,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (!callback.getReturnValue()
            && BondedPistonReactions.shouldTreatAsPushable(level, pos, state, movementDirection)) {
            callback.setReturnValue(true);
        }
    }

    @Redirect(
        method = "moveBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/piston/PistonStructureResolver;resolve()Z"
        )
    )
    private boolean plasticraft$prepareAdhesiveMovement(
        PistonStructureResolver resolver,
        Level level,
        BlockPos pistonPos,
        Direction facing,
        boolean extending
    ) {
        if (!resolver.resolve()) return false;
        Direction movementDirection = extending ? facing : facing.getOpposite();
        List<BlockPos> resolvedPositions = List.copyOf(resolver.getToPush());
        Set<BlockPos> virtualPositions = PlasticPistonOccupancy.beginMovement(
            level,
            resolvedPositions,
            pistonPos,
            facing,
            extending
        );
        resolver.getToPush().removeIf(virtualPositions::contains);
        PistonAdhesionController.beginMovement(
            level,
            List.copyOf(resolver.getToPush()),
            movementDirection
        );
        return true;
    }

    @WrapOperation(
        method = "moveBlocks",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;addDestroyBlockEffect("
                     + "Lnet/minecraft/core/BlockPos;"
                     + "Lnet/minecraft/world/level/block/state/BlockState;"
                     + ")V"
        )
    )
    private void plasticraft$skipBondedDestroyEffect(
        Level world,
        BlockPos pos,
        BlockState state,
        Operation<Void> original,
        Level level,
        BlockPos pistonPos,
        Direction facing,
        boolean extending
    ) {
        Direction movementDirection = extending ? facing : facing.getOpposite();
        if (BondedPistonReactions.shouldTreatAsPushable(world, pos, state, movementDirection)) {
            return;
        }
        original.call(world, pos, state);
    }
}
