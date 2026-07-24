package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.lib.v2.recipe.cache.BlockCache;
import dev.anvilcraft.plasticraft.block.entity.HighHeatFuelCauldronBlockEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlocks;
import dev.anvilcraft.plasticraft.init.block.ModFluids;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.dubhe.anvilcraft.api.block.IIgnitableCauldron;
import dev.dubhe.anvilcraft.block.HeaterBlock;
import dev.dubhe.anvilcraft.block.Layered4LevelCauldronBlock;
import dev.dubhe.anvilcraft.block.PlasmaJetsBlock;
import dev.dubhe.anvilcraft.init.item.ModItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;

/** 以每层高热燃料分段维持强化喷流的四级炼药锅。 */
public class HighHeatFuelCauldronBlock extends Layered4LevelCauldronBlock implements IIgnitableCauldron, EntityBlock {
    public static final BooleanProperty IGNITED = BooleanProperty.create("ignited");
    private static final CauldronInteraction.InteractionMap INTERACTIONS = CauldronInteraction.newInteractionMap(
        "anvilcraftplasticraft_high_heat_fuel"
    );

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
        INTERACTIONS.map().put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> CauldronInteraction.fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                ModItems.HIGH_HEAT_FUEL_BUCKET.asStack(),
                candidate -> candidate.is(ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
                    && candidate.getValue(LEVEL) == MAX_LEVEL
                    && !candidate.getValue(IGNITED),
                SoundEvents.BUCKET_FILL
            )
        );
        CauldronInteraction.EMPTY.map().put(
            ModItems.HIGH_HEAT_FUEL_BUCKET.get(),
            (state, level, pos, player, hand, stack) -> CauldronInteraction.emptyBucket(
                level,
                pos,
                player,
                hand,
                stack,
                ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get().fullFilled(),
                SoundEvents.BUCKET_EMPTY
            )
        );
        registerIgniter(Items.FLINT_AND_STEEL, true);
        registerIgniter(Items.FIRE_CHARGE, false);
    }

    private static void registerIgniter(net.minecraft.world.item.Item item, boolean damages) {
        INTERACTIONS.map().put(item, (state, level, pos, player, hand, stack) -> {
            if (!canIgnite(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            ignite(level, pos, state);
            if (damages) {
                stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
            } else if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(
                null,
                pos,
                damages ? SoundEvents.FLINTANDSTEEL_USE : SoundEvents.FIRECHARGE_USE,
                SoundSource.BLOCKS
            );
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        });
    }

    private static boolean canIgnite(BlockState state) {
        return state.is(ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
            && state.getValue(LEVEL) == MAX_LEVEL
            && !state.getValue(IGNITED);
    }

    private static void ignite(LevelAccessor level, BlockPos pos, BlockState state) {
        if (!canIgnite(state)) return;
        level.setBlock(pos, state.setValue(IGNITED, true), Block.UPDATE_ALL);
        if (level instanceof Level world && world.getBlockState(pos.below()).is(dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER)) {
            world.scheduleTick(pos, state.getBlock(), 2);
        }
    }

    public static boolean consumeLayer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
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
        return level.getBlockState(pos).is(ModBlocks.HIGH_HEAT_FUEL_CAULDRON.get())
            && level.getBlockEntity(pos) instanceof HighHeatFuelCauldronBlockEntity blockEntity
            && blockEntity.isSpent();
    }

    public static void clearSpent(Level level, BlockPos pos) {
        if (isSpent(level, pos)) level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
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
    protected ItemInteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hitResult
    ) {
        CauldronInteraction interaction = this.interactions.map().get(stack.getItem());
        return interaction == null
            ? ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
            : interaction.interact(state, level, pos, player, hand, stack);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(IGNITED) && level.getBlockState(pos.below()).is(dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighborBlock,
        BlockPos neighborPos,
        boolean movedByPiston
    ) {
        if (state.getValue(IGNITED) && level.getBlockState(pos.below()).is(dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER)) {
            level.scheduleTick(pos, this, 2);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState heater = level.getBlockState(pos.below());
        if (state.getValue(IGNITED)
            && state.getValue(LEVEL) == MAX_LEVEL
            && heater.is(dev.dubhe.anvilcraft.init.block.ModBlocks.HEATER)
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
        return ModFluids.HIGH_HEAT_FUEL.get();
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
