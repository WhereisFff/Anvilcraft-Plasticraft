package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 创建不对应独立注册 ID 的标准通用塑料物品栈。 */
public final class UniversalPlasticItemStacks {
    private static final int FULL_BLOCK_MELT_MILLIBUCKETS = 1024;
    private static final UUID FULL_BLOCK_ELEMENT_ID = UUID.fromString("33b07632-aede-4e56-ad65-b7264eb12bea");
    private static final MoldedPlasticData FULL_BLOCK_DATA = createFullBlockData();

    private UniversalPlasticItemStacks() {
    }

    public static ItemStack fullBlock() {
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, FULL_BLOCK_DATA);
        PlasticItemData.setMaterial(stack, "universal_plastic");
        PlasticMeltColor.set(stack, DyeColor.WHITE);
        return stack;
    }

    private static MoldedPlasticData createFullBlockData() {
        double minimum = MoldingCoordinateSystem.GRID_MIN;
        double maximum = MoldingCoordinateSystem.GRID_MAX;
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "Universal Plastic",
            EditableMoldingModel.NORMAL_TYPE,
            List.of(new MoldingElement(
                FULL_BLOCK_ELEMENT_ID,
                "Block",
                Optional.empty(),
                new MoldingVec3(minimum, minimum, minimum),
                new MoldingVec3(maximum, maximum, maximum),
                MoldingTransform.IDENTITY,
                true,
                false
            )),
            List.of()
        );
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        FluidStack material = new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), 1);
        return MoldedPlasticData.manufacture(
            model,
            baked,
            material,
            FULL_BLOCK_MELT_MILLIBUCKETS
        );
    }
}
