package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.piston.PistonAdhesionController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

/** 使用活塞本次真实解析出的移动列表同步迁移胶粘状态。 */
@Mixin(PistonBaseBlock.class)
abstract class PistonBaseBlockMixin {
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
        PistonAdhesionController.beginMovement(level, List.copyOf(resolver.getToPush()), movementDirection);
        return true;
    }
}
