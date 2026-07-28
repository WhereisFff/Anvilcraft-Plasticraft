package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.anvilcraft.plasticraft.recipe.PlasticOilCatalysis;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 扩展鱼缸内的高热燃料伤害与塑料油催化。 */
@Mixin(FishTankBlockEntity.class)
abstract class FishTankBlockEntityMixin {
    @Inject(method = "serverTick", at = @At("HEAD"))
    private static void plasticraft$tickPlasticOilCatalysis(
        Level level,
        BlockPos pos,
        BlockState state,
        FishTankBlockEntity tank,
        CallbackInfo ci
    ) {
        if (level instanceof ServerLevel serverLevel) {
            PlasticOilCatalysis.tickFishTank(serverLevel, tank);
        }
    }

    @ModifyArg(
        method = "entityInsideFluidContent",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        ),
        index = 1
    )
    private float plasticraft$doubleHighHeatFuelDamage(float original) {
        FishTankBlockEntity tank = (FishTankBlockEntity) (Object) this;
        return IgnitedFluidEffects.damageFor(tank.getFluidHandler().getFluid(), original);
    }
}
