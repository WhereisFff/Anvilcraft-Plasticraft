package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.block.PlasticPotBlock;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
import dev.anvilcraft.plasticraft.item.PlasticPotItem;
import dev.dubhe.anvilcraft.init.block.ModBlockTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Blocks;

import static dev.anvilcraft.plasticraft.AnvilcraftPlasticraft.REGISTRUM;

/** Registry entries for the display block used by the plastic anvil entity. */
public final class PlasticBlocks {
    public static final BlockEntry<PlasticAnvilBlock> PLASTIC_ANVIL = REGISTRUM
        .block("plastic_anvil", PlasticAnvilBlock::new)
        .initialProperties(() -> Blocks.ANVIL)
        .properties(properties -> properties.noOcclusion().strength(5.0F, 1200.0F))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.ANVIL, ModBlockTags.NON_MAGNETIC)
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new PlasticAnvilItem(
            block,
            properties,
            PlasticEntities.PLASTIC_ANVIL,
            block::defaultBlockState
        ))
        .tag(PlasticItemTags.PLASTIC_ANVILS, PlasticItemTags.BUOYANT_PLASTIC_ITEMS, ItemTags.ANVIL)
        .model((context, provider) -> {
        })
        .build()
        .register();

    public static final BlockEntry<PlasticPotBlock> PLASTIC_POT = REGISTRUM
        .block("plastic_pot", PlasticPotBlock::new)
        .initialProperties(() -> Blocks.CAULDRON)
        .properties(properties -> properties.noOcclusion().strength(2.0F, 20.0F))
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new PlasticPotItem(
            block,
            properties,
            PlasticEntities.PLASTIC_POT,
            block::defaultBlockState
        ))
        .tag(PlasticItemTags.PLASTIC_POTS, PlasticItemTags.BUOYANT_PLASTIC_ITEMS)
        .model((context, provider) -> {
        })
        .build()
        .register();

    private PlasticBlocks() {
    }

    public static void register() {
        // Static entries are attached to the Registrum event bus during class loading.
    }
}
