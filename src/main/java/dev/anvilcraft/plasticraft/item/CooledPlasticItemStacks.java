package dev.anvilcraft.plasticraft.item;

import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.material.PlasticMaterial;
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
import net.neoforged.neoforge.fluids.FluidType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 创建世界熔体冷却专用的 16x16x14 塑料制品。 */
public final class CooledPlasticItemStacks {
    private static final int COOLED_HEIGHT = 14;
    private static final UUID COOLED_BLOCK_ELEMENT_ID = UUID.fromString("be6639f3-90de-46a4-bd76-a6db45f70c16");
    private static final MoldedPlasticData COOLED_BLOCK_DATA = createCooledBlockData();

    private CooledPlasticItemStacks() {
    }

    public static ItemStack create(PlasticMaterial material, DyeColor color) {
        FluidStack moldedMaterial = new FluidStack(material.melt(), FluidType.BUCKET_VOLUME);
        if (material.supportsDyeing()) PlasticMeltColor.set(moldedMaterial, color);
        ItemStack stack = material.productStack(color);
        MoldedPlasticData.set(stack, COOLED_BLOCK_DATA.withMaterial(moldedMaterial));
        return stack;
    }

    private static MoldedPlasticData createCooledBlockData() {
        double minimum = MoldingCoordinateSystem.GRID_MIN;
        double maximum = MoldingCoordinateSystem.GRID_MAX;
        EditableMoldingModel model = new EditableMoldingModel(
            EditableMoldingModel.CURRENT_FORMAT_VERSION,
            "World-Cooled Plastic Block",
            EditableMoldingModel.NORMAL_TYPE,
            List.of(new MoldingElement(
                COOLED_BLOCK_ELEMENT_ID,
                "Block",
                Optional.empty(),
                new MoldingVec3(minimum, minimum, minimum),
                new MoldingVec3(maximum, minimum + COOLED_HEIGHT, maximum),
                MoldingTransform.IDENTITY,
                true,
                false
            )),
            List.of()
        );
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        FluidStack material = new FluidStack(
            PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(),
            FluidType.BUCKET_VOLUME
        );
        return MoldedPlasticData.manufacture(
            model,
            baked,
            material,
            FluidType.BUCKET_VOLUME
        );
    }
}
