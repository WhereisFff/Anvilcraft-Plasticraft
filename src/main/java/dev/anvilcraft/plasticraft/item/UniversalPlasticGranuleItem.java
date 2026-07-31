package dev.anvilcraft.plasticraft.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;

/**
 * 通过自定义数据从单一物品 ID 中选择十六种染料颜色。
 *
 * <p>默认栈显式记录白色，使村民交易可以用组件谓词准确要求白色塑料粒。</p>
 */
public class UniversalPlasticGranuleItem extends Item {
    public UniversalPlasticGranuleItem(Properties properties) {
        super(properties.component(
            DataComponents.CUSTOM_DATA,
            PlasticMeltColor.explicitColorData(DyeColor.WHITE)
        ));
    }
}
