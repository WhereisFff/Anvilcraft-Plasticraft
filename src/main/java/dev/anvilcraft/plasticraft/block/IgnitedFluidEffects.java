package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/** 统一可燃流体容器的着火伤害，高热燃料造成普通燃料的两倍伤害。 */
public final class IgnitedFluidEffects {
    public static final float ORDINARY_DAMAGE = 4.0F;
    public static final float HIGH_HEAT_DAMAGE = ORDINARY_DAMAGE * 2.0F;

    private IgnitedFluidEffects() {
    }

    public static float damageFor(FluidStack fluid, float ordinaryDamage) {
        return isHighHeatFuel(fluid) ? ordinaryDamage * 2.0F : ordinaryDamage;
    }

    public static void hurt(Entity entity, Level level, FluidStack fluid) {
        hurt(entity, level, damageFor(fluid, ORDINARY_DAMAGE));
    }

    public static void hurtWithHighHeatFuel(Entity entity, Level level) {
        hurt(entity, level, HIGH_HEAT_DAMAGE);
    }

    private static void hurt(Entity entity, Level level, float damage) {
        if (level.isClientSide()) return;
        if (!entity.fireImmune()) {
            entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 1);
            if (entity.getRemainingFireTicks() == 0) entity.igniteForSeconds(8.0F);
        }
        entity.hurt(level.damageSources().inFire(), damage);
    }

    public static boolean isHighHeatFuel(FluidStack fluid) {
        return fluid.is(PlasticraftFluids.HIGH_HEAT_FUEL.get())
            || fluid.is(PlasticraftFluids.FLOWING_HIGH_HEAT_FUEL.get());
    }
}
