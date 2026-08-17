package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.material.PlasticMaterial;

/** 透明塑料熔体炼药锅，保留染色玻璃式颜色方块状态。 */
public final class ClearPlasticMeltCauldronBlock extends UniversalPlasticMeltCauldronBlock {
    public ClearPlasticMeltCauldronBlock(Properties properties) {
        super(properties, PlasticMaterial.CLEAR);
    }
}
