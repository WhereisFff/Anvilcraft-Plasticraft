package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Items;

public class PlasticOilCauldronBlock extends Layered4LevelCauldronBlock {
    private static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap(
        "anvilcraftplasticraft_plastic_oil"
    );

    public PlasticOilCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
    }

    public static void registerInteractions() {
        INTERACTIONS.map().put(Items.BUCKET, (state, level, pos, player, hand, stack) ->
            CauldronInteraction.fillBucket(
                state, level, pos, player, hand, stack, ModItems.PLASTIC_OIL_BUCKET.asStack(),
                ModBlocks.PLASTIC_OIL_CAULDRON.get()::isFull, SoundEvents.BUCKET_FILL
            ));
        CauldronInteraction.EMPTY.map().put(ModItems.PLASTIC_OIL_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteraction.emptyBucket(
                level, pos, player, hand, stack,
                ModBlocks.PLASTIC_OIL_CAULDRON.get().fullFilled(), SoundEvents.BUCKET_EMPTY
            ));
    }
}
