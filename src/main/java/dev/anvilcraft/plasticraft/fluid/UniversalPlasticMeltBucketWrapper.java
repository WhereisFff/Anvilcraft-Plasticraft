package dev.anvilcraft.plasticraft.fluid;

import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

/** 在桶物品和流体能力之间双向复制熔体颜色。 */
public final class UniversalPlasticMeltBucketWrapper extends FluidBucketWrapper {
    public UniversalPlasticMeltBucketWrapper(ItemStack container) {
        super(container);
    }

    @Override
    public FluidStack getFluid() {
        if (!this.container.is(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get())) return FluidStack.EMPTY;
        FluidStack fluid = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), FluidType.BUCKET_VOLUME);
        PlasticMeltColor.set(fluid, PlasticMeltColor.get(this.container));
        return fluid;
    }

    @Override
    protected void setFluid(FluidStack fluid) {
        super.setFluid(fluid);
        if (!fluid.isEmpty() && this.container.is(PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get())) {
            PlasticMeltColor.set(this.container, PlasticMeltColor.get(fluid));
        }
    }
}
