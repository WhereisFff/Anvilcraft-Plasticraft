package dev.anvilcraft.plasticraft.fluid;

import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

import java.util.Objects;
import java.util.function.Supplier;

/** 在桶物品和流体能力之间双向复制熔体颜色。 */
public final class UniversalPlasticMeltBucketWrapper extends FluidBucketWrapper {
    private final Supplier<? extends Item> bucket;
    private final Supplier<? extends Fluid> fluid;
    private final boolean carriesColor;

    public UniversalPlasticMeltBucketWrapper(
        ItemStack container,
        Supplier<? extends Item> bucket,
        Supplier<? extends Fluid> fluid
    ) {
        this(container, bucket, fluid, true);
    }

    public UniversalPlasticMeltBucketWrapper(
        ItemStack container,
        Supplier<? extends Item> bucket,
        Supplier<? extends Fluid> fluid,
        boolean carriesColor
    ) {
        super(container);
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.carriesColor = carriesColor;
    }

    @Override
    public FluidStack getFluid() {
        if (!this.container.is(this.bucket.get())) return FluidStack.EMPTY;
        FluidStack fluid = new FluidStack(this.fluid.get(), FluidType.BUCKET_VOLUME);
        if (this.carriesColor) PlasticMeltColor.set(fluid, PlasticMeltColor.get(this.container));
        return fluid;
    }

    @Override
    protected void setFluid(FluidStack fluid) {
        super.setFluid(fluid);
        if (!fluid.isEmpty() && this.container.is(this.bucket.get())) {
            if (this.carriesColor) PlasticMeltColor.set(this.container, PlasticMeltColor.get(fluid));
        }
    }
}
