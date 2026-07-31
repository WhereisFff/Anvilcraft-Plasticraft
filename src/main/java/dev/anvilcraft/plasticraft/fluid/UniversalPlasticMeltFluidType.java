package dev.anvilcraft.plasticraft.fluid;

import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

/** 为单一流体 ID 创建能够保留调色数据的熔体桶。 */
public final class UniversalPlasticMeltFluidType extends FluidType {
    public UniversalPlasticMeltFluidType(Properties properties) {
        super(properties);
    }

    @Override
    public ItemStack getBucket(FluidStack stack) {
        ItemStack bucket = ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        PlasticMeltColor.set(bucket, PlasticMeltColor.get(stack));
        return bucket;
    }
}
