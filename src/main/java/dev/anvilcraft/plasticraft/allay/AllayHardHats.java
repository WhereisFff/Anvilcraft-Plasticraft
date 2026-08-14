package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;

/** 悦灵安全帽物品判定:通用塑料且成型类型为 allay_hard_hat。 */
public final class AllayHardHats {
    private AllayHardHats() {
    }

    public static boolean isHardHat(ItemStack stack) {
        return !stack.isEmpty()
            && stack.is(PlasticraftBlocks.UNIVERSAL_PLASTIC.asItem())
            && MoldedPlasticData.get(stack)
                .map(data -> MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(data.finalType()))
                .orElse(false);
    }
}
