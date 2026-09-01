package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.piston.BondedPistonReactions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;

/** 胶粘 DESTROY 方块随活塞移动时，禁止邻接更新打碎它或播放破坏粒子。 */
@Mixin(Level.class)
abstract class LevelBondedPistonDropMixin {
    @Inject(
        method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
        at = @At("HEAD"),
        cancellable = true
    )
    private void plasticraft$suppressBondedDestroyDrop(
        BlockPos pos,
        boolean dropBlock,
        @Nullable Entity entity,
        int recursionLeft,
        CallbackInfoReturnable<Boolean> callback
    ) {
        Level level = (Level) (Object) this;
        if (!BondedPistonReactions.shouldSuppressMovementBreakDrop(level, pos)) return;
        callback.setReturnValue(false);
    }
}
