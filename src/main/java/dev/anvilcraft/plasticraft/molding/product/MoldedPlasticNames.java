package dev.anvilcraft.plasticraft.molding.product;

import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductType;
import dev.anvilcraft.plasticraft.molding.type.MoldingProductTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** 根据塑料材料和最终功能类型生成成品名称，蓝图名称不参与成品命名。 */
public final class MoldedPlasticNames {
    private MoldedPlasticNames() {
    }

    public static Component create(ItemStack stack, MoldedPlasticData data) {
        Component material = Component.translatable(
            "material.anvilcraftplasticraft." + PlasticItemData.getMaterial(stack)
        );
        Component suffix = MoldingProductTypes.get(data.finalType())
            .map(MoldingProductType::productNameSuffixKey)
            .map(Component::translatable)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown molded plastic product type " + data.finalType()
            ));
        return Component.translatable("item.anvilcraftplasticraft.molded_product_name", material, suffix);
    }
}
