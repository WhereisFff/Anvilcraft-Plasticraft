package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.plasticraft.block.entity.HighHeatFuelCauldronBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.anvilcraft.plasticraft.init.block.PlasticraftFluids;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.block.cauldron.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.power.consumer.HeaterBlock;
import dev.dubhe.anvilcraft.block.special.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.cauldron.CauldronInteractions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** 以每层高热燃料分段维持强化喷流的四级炼药锅。 */
public class HighHeatFuelCauldronBlock extends Layered4LevelCauldronBlock implements IIgnitableCauldron, EntityBlock {
    public static final BooleanProperty IGNITED = BooleanProperty.create("ignited");
    private static final CauldronInteraction.Dispatcher INTERACTIONS = new CauldronInteraction.Dispatcher();

    public HighHeatFuelCauldronBlock(Properties properties) {
        super(properties, INTERACTIONS);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, 1).setValue(IGNITED, false));
    }

    @Override
    protected MapCodec<? extends HighHeatFuelCauldronBlock> codec() {
        return simpleCodec(HighHeatFuelCauldronBlock::new);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HighHeatFuelCauldronBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL, IGNITED);
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
                PlasticraftItems.HIGH_HEAT_FUEL_BUCKET.asStack(),
                candidate -> candidate.is(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
                    && candidate.getValue(LEVEL) == MAX_LEVEL
                    && !candidate.getValue(IGNITED),
                SoundEvents.BUCKET_FILL
            )
        );
        CauldronInteractions.EMPTY.put(
            PlasticraftItems.HIGH_HEAT_FUEL_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteractions.emptyBucket(
                level,
                pos,
                player,
                hand,
                stack,
                PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get().fullFilled(),
                SoundEvents.BUCKET_EMPTY
            )
        );
        registerIgniter(Items.FLINT_AND_STEEL, true);
        registerIgniter(Items.FIRE_CHARGE, false);
    }

    private static void registerIgniter(Item item, boolean damages) {
        INTERACTIONS.put(item, (state, level, pos, player, hand, stack) -> {
            if (!canIgnite(state)) return InteractionResult.TRY_WITH_EMPTY_HAND;
            ignite(level, pos, state);
            if (damages) {
                stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
            } else if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(
                null,
                pos,
                damages ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE,
                SoundSource.BLOCKS
            );
            return InteractionResult.SUCCESS;
        });
    }

    private static boolean canIgnite(BlockState state) {
        return state.is(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
            && state.getValue(LEVEL) == MAX_LEVEL
            && !state.getValue(IGNITED);
    }

    private static void ignite(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!canIgnite(state)) return;
        level.setBlock(pos, state.setValue(IGNITED, true), Block.UPDATE_ALL);
        if (level instanceof Level world && world.getBlockState(pos.below()).is(ModBlocks.HEATER)) {
            world.scheduleTick(pos, state.getBlock(), 2);
        }
    }

    public static boolean consumeLayer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
            || !state.getValue(IGNITED)
            || !(level.getBlockEntity(pos) instanceof HighHeatFuelCauldronBlockEntity blockEntity)
            || blockEntity.isSpent()) {
            return false;
        }
        int currentLevel = state.getValue(LEVEL);
        if (currentLevel > 1) {
            level.setBlockAndUpdate(pos, state.setValue(LEVEL, currentLevel - 1));
        } else {
            blockEntity.setSpent(true);
        }
        return true;
    }

    public static boolean isSpent(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(PlasticraftBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
            && level.getBlockEntity(pos) instanceof HighHeatFuelCauldronBlockEntity blockEntity
            && blockEntity.isSpent();
    }

    public static void clearSpent(Level level, BlockPos pos) {
        if (isSpent(level, pos)) level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        if (!this.isEntityInsideContent(state, pos, entity)) return;
        if (state.getValue(IGNITED)) {
            if (!isSpent(level, pos)) {
                IgnitedFluidEffects.hurtWithHighHeatFuel(entity, level);
            }
            return;
        }
        if (!canIgnite(state)) return;
        if (entity.getType() == EntityType.ARROW && entity.isOnFire()) {
            ignite(level, pos, state);
            return;
        }
        if (!(entity instanceof ItemEntity item)) return;
        if (item.getItem().is(ModItemTags.FIRE_STARTER)) {
            ignite(level, pos, state);
            item.getItem().shrink(1);
        } else if (item.getItem().is(ModItemTags.UNBROKEN_FIRE_STARTER)) {
            ignite(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        CauldronInteraction interaction = this.interactions.get(stack);
        return interaction == null
            ? InteractionResult.TRY_WITH_EMPTY_HAND
            : interaction.interact(state, level, pos, player, hand, stack);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(IGNITED) && level.getBlockState(pos.below()).is(ModBlocks.HEATER)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighborBlock,
        @Nullable Orientation orientation,
        boolean movedByPiston
    ) {
        if (state.getValue(IGNITED) && level.getBlockState(pos.below()).is(ModBlocks.HEATER)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState heater = level.getBlockState(pos.below());
        if (state.getValue(IGNITED)
            && state.getValue(LEVEL) == MAX_LEVEL
            && heater.is(ModBlocks.HEATER)
            && !heater.getValue(HeaterBlock.OVERLOAD)
            && !PlasmaJetsBlock.trySpawn(pos.above(), level)) {
            level.scheduleTick(pos, this, 10);
        }
    }

    @Override
    public boolean isEmpty(BlockCache cache, BlockPos pos) {
        return cache.getBlockEntity(pos) instanceof HighHeatFuelCauldronBlockEntity blockEntity
            && blockEntity.isSpent();
    }

    @Override
    public boolean isIgnited(BlockCache cache, BlockPos pos) {
        return cache.getBlockState(pos).getValue(IGNITED);
    }

    @Override
    public void setIgnited(BlockCache cache, BlockPos pos, boolean ignited) {
        BlockState state = cache.getBlockState(pos);
        if (ignited && canIgnite(state)) cache.setBlock(pos, state.setValue(IGNITED, true));
    }

    @Override
    public Fluid getFluid(BlockCache cache, BlockPos pos) {
        return PlasticraftFluids.HIGH_HEAT_FUEL.get();
    }

    @Override
    public int getFluidAmount(BlockCache cache, BlockPos pos) {
        BlockState state = cache.getBlockState(pos);
        if (cache.getBlockEntity(pos) instanceof HighHeatFuelCauldronBlockEntity blockEntity
            && blockEntity.isSpent()) {
            return 0;
        }
        return state.getValue(LEVEL) * 250;
    }
}
