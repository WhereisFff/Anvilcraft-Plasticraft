package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.anvilcraft.plasticraft.entity.LaserPlasticInteraction;
import dev.dubhe.anvilcraft.AnvilCraft;
import dev.dubhe.anvilcraft.block.entity.BaseLaserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让激光识别已替换方块占位的塑料实体。 */
@Mixin(BaseLaserBlockEntity.class)
abstract class BaseLaserBlockEntityMixin {
    @ModifyReturnValue(
        method = "getIrradiateBlockPos(ILnet/minecraft/core/Direction;Lnet/minecraft/core/BlockPos;)"
            + "Lnet/minecraft/core/BlockPos;",
        at = @At("RETURN")
    )
    private BlockPos plasticraft$stopAtPlastic(
        BlockPos original,
        int ignoredExpectedLength,
        Direction direction,
        BlockPos origin
    ) {
        BaseLaserBlockEntity laser = (BaseLaserBlockEntity) (Object) this;
        Level level = laser.getLevel();
        if (level == null) return original;
        return LaserPlasticInteraction.findFirstBlockingPlastic(
            level,
            direction,
            origin,
            original,
            AnvilCraft.CONFIG.isLaserDoImpactChecking
        );
    }

    @Inject(
        method = "emitLaser(Lnet/minecraft/core/Direction;)V",
        at = @At("TAIL")
    )
    private void plasticraft$hurtPlasticTarget(Direction direction, CallbackInfo callback) {
        BaseLaserBlockEntity laser = (BaseLaserBlockEntity) (Object) this;
        if (!(laser.getLevel() instanceof ServerLevel level)) return;
        BlockPos target = laser.getIrradiateBlockPos();
        int damage = Math.min(16, laser.getLaserLevel() - 4);
        if (target == null || damage <= 0) return;
        LaserPlasticInteraction.hurtBlockingPlastic(
            level,
            direction,
            target,
            AnvilCraft.CONFIG.isLaserDoImpactChecking,
            damage
        );
    }
}
