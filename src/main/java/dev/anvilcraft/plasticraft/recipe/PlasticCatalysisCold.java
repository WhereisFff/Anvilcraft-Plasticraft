package dev.anvilcraft.plasticraft.recipe;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockTags;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItemTags;
import net.minecraft.world.level.block.state.BlockState;

/** 工程塑料催化共用的寒冷环境判定。 */
public final class PlasticCatalysisCold {
    private PlasticCatalysisCold() {
    }

    public static boolean isCold(BlockState state) {
        return state.is(PlasticraftBlockTags.PLASTIC_MELT_COOLANTS)
            || state.getBlock().asItem().builtInRegistryHolder().is(PlasticraftItemTags.COLD_ITEMS);
    }
}
