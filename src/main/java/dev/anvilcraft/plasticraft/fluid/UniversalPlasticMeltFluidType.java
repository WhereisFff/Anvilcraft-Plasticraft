package dev.anvilcraft.plasticraft.fluid;

import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

import java.util.Objects;
import java.util.function.Supplier;

/** 为单一流体 ID 创建能够保留调色数据的熔体桶。 */
public final class UniversalPlasticMeltFluidType extends FluidType {
    private final Supplier<ItemStack> bucketSupplier;
    private final boolean carriesColor;

    public UniversalPlasticMeltFluidType(Properties properties, Supplier<ItemStack> bucketSupplier) {
        this(properties, bucketSupplier, true);
    }

    public UniversalPlasticMeltFluidType(
        Properties properties,
        Supplier<ItemStack> bucketSupplier,
        boolean carriesColor
    ) {
        super(properties);
        this.bucketSupplier = Objects.requireNonNull(bucketSupplier, "bucketSupplier");
        this.carriesColor = carriesColor;
    }

    @Override
    public ItemStack getBucket(FluidStack stack) {
        ItemStack bucket = this.bucketSupplier.get();
        if (this.carriesColor) PlasticMeltColor.set(bucket, PlasticMeltColor.get(stack));
        return bucket;
    }
}
