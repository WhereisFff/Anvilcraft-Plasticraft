package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.IgnitedFluidEffects;
import dev.dubhe.anvilcraft.block.entity.FishTankBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** 将鱼缸内高热燃料的燃烧伤害提高到普通燃料的两倍。 */
@Mixin(FishTankBlockEntity.class)
abstract class FishTankBlockEntityMixin {
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
