package dev.anvilcraft.plasticraft.allay;

import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.world.item.ItemStack;

/** 悦灵安全帽物品判定:通用塑料且成型类型为 allay_hard_hat。 */
public final class AllayHardHats {
    private AllayHardHats() {
    }

    public static boolean isHardHat(ItemStack stack) {
        return !stack.isEmpty()
            && stack.is(PlasticraftItemTags.PLASTIC_PRODUCTS)
            && MoldedPlasticData.get(stack)
                .map(data -> MoldingProductTypes.ALLAY_HARD_HAT_ID.equals(data.finalType()))
                .orElse(false);
    }
}
