package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.fluid.UniversalPlasticMeltBucketWrapper;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.api.fluid.LargeCauldronFluidHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** 通用塑料熔体颜色组件及流体合并的回归测试。 */
public final class PlasticMeltColorGameTests {
    private PlasticMeltColorGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate("7x6x7")
    @TestHolder(description = "Default-white plastic melt from buckets merges with component-free melt")
    static void defaultWhiteMeltMergesInLargeCauldron(ExtendedGameTestHelper helper) {
        FluidStack componentFree = new FluidStack(
            ModFluids.UNIVERSAL_PLASTIC_MELT.get(),
            2 * FluidType.BUCKET_VOLUME
        );
        FluidStack fromBucket = new UniversalPlasticMeltBucketWrapper(
            ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack()
        ).getFluid();

        check(!fromBucket.has(DataComponents.CUSTOM_DATA), "default-white melt retained empty custom data");
        check(
            FluidStack.isSameFluidSameComponents(componentFree, fromBucket),
            "default-white melt did not match component-free melt"
        );

        LargeCauldronFluidHandler handler = new LargeCauldronFluidHandler(() -> {
        });
        check(
            handler.fill(componentFree, IFluidHandler.FluidAction.EXECUTE) == componentFree.getAmount(),
            "large cauldron rejected component-free melt"
        );
        check(
            handler.fill(fromBucket, IFluidHandler.FluidAction.EXECUTE) == fromBucket.getAmount(),
            "large cauldron rejected default-white bucket melt"
        );
        check(handler.getTotalAmount() == 3 * FluidType.BUCKET_VOLUME, "large cauldron lost melt while merging");
        check(nonEmptyTanks(handler) == 1, "large cauldron split default-white melt into multiple layers");

        FluidStack withOtherData = new FluidStack(ModFluids.UNIVERSAL_PLASTIC_MELT.get(), FluidType.BUCKET_VOLUME);
        CompoundTag customData = new CompoundTag();
        customData.putBoolean("Preserved", true);
        withOtherData.set(DataComponents.CUSTOM_DATA, CustomData.of(customData));
        PlasticMeltColor.set(withOtherData, DyeColor.WHITE);
        check(
            withOtherData.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("Preserved"),
            "normalizing white melt removed unrelated custom data"
        );

        helper.succeed();
    }

    private static int nonEmptyTanks(IFluidHandler handler) {
        int count = 0;
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            if (!handler.getFluidInTank(tank).isEmpty()) count++;
        }
        return count;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
