package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlockTags;
import dev.anvilcraft.plasticraft.init.item.ModItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** 只保留源方块、携带颜色并能够冷却成塑料粒的熔体。 */
public class UniversalPlasticMeltFluidBlock extends LiquidBlock implements EntityBlock {
    public UniversalPlasticMeltFluidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties) {
        super(fluid.get(), properties.randomTicks());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UniversalPlasticMeltBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide) level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (shouldCool(level, pos)) {
            cool(level, pos);
            return;
        }
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (shouldCool(level, pos)) cool(level, pos);
    }

    @Override
    protected void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block block,
        BlockPos neighborPos,
        boolean movedByPiston
    ) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 1);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        entity.makeStuckInBlock(state, new net.minecraft.world.phys.Vec3(0.25D, 0.05D, 0.25D));
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) != 0) return ItemStack.EMPTY;
        ItemStack bucket = ModItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            PlasticMeltColor.set(bucket, melt.getColor());
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return bucket;
    }

    private static boolean shouldCool(ServerLevel level, BlockPos pos) {
        if (level.isRainingAt(pos.above())) return true;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).is(ModBlockTags.PLASTIC_MELT_COOLANTS)
                || level.getFluidState(neighbor).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    private static void cool(ServerLevel level, BlockPos pos) {
        ItemStack result = new ItemStack(ModItems.UNIVERSAL_PLASTIC_GRANULE.get(), 16);
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            PlasticMeltColor.set(result, melt.getColor());
        }
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        ItemEntity item = new ItemEntity(level, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D, result);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
            net.minecraft.sounds.SoundSource.BLOCKS, 0.8F, 1.15F);
    }
}
