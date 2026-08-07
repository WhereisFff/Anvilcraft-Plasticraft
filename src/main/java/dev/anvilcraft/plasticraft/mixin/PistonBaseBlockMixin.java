package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import dev.anvilcraft.plasticraft.block.piston.PlasticPistonOccupancy;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** 使用活塞本次真实解析出的移动列表同步迁移胶粘状态。 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
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
        List<BlockPos> toPush = List.copyOf(resolver.getToPush());
        PlasticPistonOccupancy.beginMovement(level, toPush, movementDirection);
        PistonAdhesionController.beginMovement(level, toPush, movementDirection);
        return true;
    }
}
