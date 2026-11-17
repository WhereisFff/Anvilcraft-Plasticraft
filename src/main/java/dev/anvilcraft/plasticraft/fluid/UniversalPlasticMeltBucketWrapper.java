package dev.anvilcraft.plasticraft.fluid;

import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.transfer.ItemAccessResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

import java.util.Objects;
import java.util.function.Supplier;

/** 在桶物品和流体能力之间双向复制熔体颜色。 */
public final class UniversalPlasticMeltBucketWrapper extends ItemAccessResourceHandler<FluidResource> {
    private final Supplier<? extends Item> bucket;
    private final Supplier<? extends Fluid> fluid;
    private final boolean carriesColor;

    public UniversalPlasticMeltBucketWrapper(
        ItemAccess container,
        Supplier<? extends Item> bucket,
        Supplier<? extends Fluid> fluid
    ) {
        this(container, bucket, fluid, true);
    }

    public UniversalPlasticMeltBucketWrapper(
        ItemAccess container,
        Supplier<? extends Item> bucket,
        Supplier<? extends Fluid> fluid,
        boolean carriesColor
    ) {
        super(container, 1);
        this.bucket = Objects.requireNonNull(bucket, "bucket");
        this.fluid = Objects.requireNonNull(fluid, "fluid");
        this.carriesColor = carriesColor;
    }

    @Override
    protected FluidResource getResourceFrom(ItemResource container, int index) {
        if (!container.is(this.bucket.get())) {
            return FluidResource.EMPTY;
        }
        FluidStack stack = new FluidStack(this.fluid.get(), FluidType.BUCKET_VOLUME);
        if (this.carriesColor) {
            PlasticMeltColor.set(stack, PlasticMeltColor.get(container.toStack()));
        }
        return FluidResource.of(stack);
    }

    @Override
    protected int getAmountFrom(ItemResource container, int index) {
        return container.is(this.bucket.get()) ? FluidType.BUCKET_VOLUME : 0;
    }

    @Override
    protected int getCapacity(int index, FluidResource resource) {
        return FluidType.BUCKET_VOLUME;
    }

    @Override
    protected ItemResource update(ItemResource container, int index, FluidResource resource, int amount) {
        if (amount == 0) {
            return ItemResource.of(Items.BUCKET);
        }
        if (amount != FluidType.BUCKET_VOLUME || resource.getFluid() != this.fluid.get()) {
            return ItemResource.EMPTY;
        }
        ItemStack filled = new ItemStack(this.bucket.get());
        if (this.carriesColor) {
            PlasticMeltColor.set(filled, PlasticMeltColor.get(resource.toStack(amount)));
        }
        return ItemResource.of(filled);
    }
}
