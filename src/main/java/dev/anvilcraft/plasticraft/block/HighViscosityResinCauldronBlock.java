package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 原版炼药锅中的液态高粘性树脂内容物。 */
public class HighViscosityResinCauldronBlock extends Layered4LevelCauldronBlock {
    private static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap(
        "anvilcraftplasticraft_high_viscosity_resin"
    );

    public HighViscosityResinCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
    }

    public static void registerInteractions() {
        INTERACTIONS.map().put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> CauldronInteraction.fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack(),
                ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get()::isFull,
                SoundEvents.BUCKET_FILL
            )
        );
        CauldronInteraction.EMPTY.map().put(
            ModItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteraction.emptyBucket(
                level,
                pos,
                player,
                hand,
                stack,
                ModBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get().fullFilled(),
                SoundEvents.BUCKET_EMPTY
            )
        );
    }

    public boolean containsEntity(BlockState state, BlockPos pos, Entity entity) {
        return this.isEntityInsideContent(state, pos, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!this.isEntityInsideContent(state, pos, entity)) return;
        HighViscosityResinFluidBlock.stickEntity(state, entity);
    }
}
