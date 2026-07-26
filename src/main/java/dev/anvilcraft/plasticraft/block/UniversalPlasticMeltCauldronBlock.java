package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.DyeColor;

public class UniversalPlasticMeltCauldronBlock extends Layered4LevelCauldronBlock {
    private static final net.minecraft.world.phys.Vec3 STICK_SPEED = new net.minecraft.world.phys.Vec3(
        0.25D,
        0.05D,
        0.25D
    );
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    private static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap(
        "anvilcraftplasticraft_universal_plastic_melt"
    );

    public UniversalPlasticMeltCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1).setValue(COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, COLOR);
    }

    public boolean containsEntity(BlockState state, BlockPos pos, Entity entity) {
        return this.isEntityInsideContent(state, pos, entity);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (this.containsEntity(state, pos, entity)) entity.makeStuckInBlock(state, STICK_SPEED);
    }

    public static void registerInteractions() {
        INTERACTIONS.map().put(Items.BUCKET, (state, level, pos, player, hand, stack) ->
            CauldronInteraction.fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                coloredBucket(state.getValue(COLOR)),
                candidate -> candidate.is(ModBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get())
                    && candidate.getValue(LEVEL) == MAX_LEVEL,
                SoundEvents.BUCKET_FILL
            )
        );
        CauldronInteraction.EMPTY.map().put(ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteraction.emptyBucket(
                level,
                pos,
                player,
                hand,
                stack,
                ModBlocks.UNIVERSAL_PLASTIC_MELT_CAULDRON.get().fullFilled()
                    .setValue(COLOR, PlasticMeltColor.get(stack)),
                SoundEvents.BUCKET_EMPTY
            ));
    }

    private static net.minecraft.world.item.ItemStack coloredBucket(DyeColor color) {
        net.minecraft.world.item.ItemStack bucket = ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        PlasticMeltColor.set(bucket, color);
        return bucket;
    }
}
