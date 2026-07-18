package dev.anvilcraft.plasticraft.init;

import dev.anvilcraft.lib.v2.registrum.util.entry.BlockEntry;
import dev.anvilcraft.plasticraft.block.PlasticAnvilBlock;
import dev.anvilcraft.plasticraft.item.PlasticAnvilItem;
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
        .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.ANVIL)
        .blockstate((context, provider) -> {
        })
        .item((block, properties) -> new PlasticAnvilItem(
            block,
            properties,
            () -> PlasticEntities.PLASTIC_ANVIL.get(),
            () -> block.defaultBlockState()
        ))
        .tag(PlasticItemTags.PLASTIC_ANVILS, ItemTags.ANVIL)
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
