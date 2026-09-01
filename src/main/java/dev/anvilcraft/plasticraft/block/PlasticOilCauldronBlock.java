package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class PlasticOilCauldronBlock extends Layered4LevelCauldronBlock {
    private static final double INNER_MIN = 2.0D / 16.0D;
    private static final double INNER_MAX = 14.0D / 16.0D;
    private static final double CONTENT_BOTTOM = 4.0D / 16.0D;
    private static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap(
        "anvilcraftplasticraft_plastic_oil"
    );

    public PlasticOilCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
    }

    public boolean containsEntity(BlockState state, BlockPos pos, Entity entity) {
        AABB bounds = entity.getBoundingBox();
        double minX = pos.getX() + INNER_MIN;
        double maxX = pos.getX() + INNER_MAX;
        double minY = pos.getY() + CONTENT_BOTTOM;
        double maxY = pos.getY() + this.getContentHeight(state);
        double minZ = pos.getZ() + INNER_MIN;
        double maxZ = pos.getZ() + INNER_MAX;
        // 漂浮物会停在液面上，接触液面也视为仍在液体中。
        return bounds.maxX > minX && bounds.minX < maxX
            && bounds.maxY > minY && bounds.minY <= maxY
            && bounds.maxZ > minZ && bounds.minZ < maxZ;
    }

    public static void registerInteractions() {
        INTERACTIONS.map().put(Items.BUCKET, (state, level, pos, player, hand, stack) ->
            CauldronInteraction.fillBucket(
                state, level, pos, player, hand, stack, PlasticraftItems.PLASTIC_OIL_BUCKET.asStack(),
                PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get()::isFull, SoundEvents.BUCKET_FILL
            ));
        CauldronInteraction.EMPTY.map().put(
            PlasticraftItems.PLASTIC_OIL_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteraction.emptyBucket(
                level, pos, player, hand, stack,
                PlasticraftBlocks.PLASTIC_OIL_CAULDRON.get().fullFilled(), SoundEvents.BUCKET_EMPTY
            ));
    }
}
