package dev.anvilcraft.plasticraft.item;

import net.minecraft.world.level.material.Fluid;

import java.util.function.Supplier;

/** 透明塑料熔体桶，复用通用熔体桶的颜色组件与十六色变体。 */
public final class ClearPlasticMeltBucketItem extends UniversalPlasticMeltBucketItem {
    public ClearPlasticMeltBucketItem(Supplier<? extends Fluid> fluid, Properties properties) {
        super(fluid, properties);
    }
}
