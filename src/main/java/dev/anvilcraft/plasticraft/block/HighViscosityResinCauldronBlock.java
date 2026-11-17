package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.dubhe.anvilcraft.block.cauldron.Layered4LevelCauldronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** 原版炼药锅中的液态高粘性树脂内容物。 */
public class HighViscosityResinCauldronBlock extends Layered4LevelCauldronBlock {
    private static final CauldronInteraction.Dispatcher INTERACTIONS = new CauldronInteraction.Dispatcher();

    public HighViscosityResinCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
    }

    public static void registerInteractions() {
        INTERACTIONS.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> CauldronInteractions.fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.asStack(),
                PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get()::isFull,
                SoundEvents.BUCKET_FILL
            )
        );
        CauldronInteractions.EMPTY.put(
            PlasticraftItems.LIQUID_HIGH_VISCOSITY_RESIN_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteractions.emptyBucket(
                level,
                pos,
                player,
                hand,
                stack,
                PlasticraftBlocks.LIQUID_HIGH_VISCOSITY_RESIN_CAULDRON.get().fullFilled(),
                SoundEvents.BUCKET_EMPTY
            )
        );
    }

    public boolean containsEntity(BlockState state, BlockPos pos, Entity entity) {
        return this.isEntityInsideContent(state, pos, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!this.isEntityInsideContent(state, pos, entity)) return;
        HighViscosityResinFluidBlock.stickEntity(state, entity);
    }
}
