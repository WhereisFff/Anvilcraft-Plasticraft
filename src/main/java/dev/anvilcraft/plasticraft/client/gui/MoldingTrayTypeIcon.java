package dev.anvilcraft.plasticraft.client.gui;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import dev.anvilcraft.plasticraft.molding.type.MoldingTrayIconModel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/** 通过通用塑料物品渲染器显示内置支架模型。 */
public final class MoldingTrayTypeIcon {
    private MoldingTrayTypeIcon() {
    }

    public static ItemStack stack() {
        return Holder.STACK;
    }

    private static ItemStack createStack() {
        EditableMoldingModel model = MoldingTrayIconModel.model();
        BakedMoldingModel baked = MoldingModelBaker.bake(model);
        int melt = baked.analysis().minimumMeltMillibuckets();
        MoldedPlasticData data = MoldedPlasticData.manufacture(
            model,
            baked,
            new FluidStack(PlasticraftFluids.UNIVERSAL_PLASTIC_MELT.get(), melt),
            melt
        );
        if (!MoldingProductTypes.TRAY_ID.equals(data.finalType())) {
            throw new IllegalStateException("Bundled tray icon model is not a valid tray");
        }
        ItemStack stack = PlasticraftBlocks.UNIVERSAL_PLASTIC.asStack();
        MoldedPlasticData.set(stack, data);
        return stack;
    }

    private static final class Holder {
        private static final ItemStack STACK = createStack();
    }
}
