package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.adhesive.AdhesiveFallingBlockBehavior;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 粘附期间由树脂系统接管普通下落方块的位置和生命周期。 */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityAdhesionMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$holdAdhesiveControlledFallingBlock(CallbackInfo callback) {
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        if (AdhesiveFallingBlockBehavior.beforeTick(entity)) callback.cancel();
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/item/FallingBlockEntity;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            shift = At.Shift.AFTER
        )
    )
    private void plasticraft$detectBondedLanding(CallbackInfo callback) {
        AdhesiveFallingBlockBehavior.afterMovement((FallingBlockEntity) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void plasticraft$resetAdhesiveLifetime(CallbackInfo callback) {
        AdhesiveFallingBlockBehavior.afterTick((FallingBlockEntity) (Object) this);
    }

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        )
    )
    private boolean plasticraft$retainBondAfterLanding(
        Level level,
        BlockPos pos,
        BlockState state,
        int flags
    ) {
        boolean placed = level.setBlock(pos, state, flags);
        FallingBlockEntity entity = (FallingBlockEntity) (Object) this;
        if (placed && level instanceof ServerLevel serverLevel) {
            AdhesiveFallingBlockBehavior.afterLanding(serverLevel, entity, pos);
        }
        return placed;
    }
}
