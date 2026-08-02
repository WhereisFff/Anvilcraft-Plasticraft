package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.block.entity.UniversalPlasticMeltBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockTags;
import dev.anvilcraft.plasticraft.init.item.PlasticraftItems;
import dev.anvilcraft.plasticraft.item.PlasticMeltColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** 只保留单格流体、携带颜色并统一固化为通用塑料制品的熔体。 */
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
        if (trySolidifyFromEnvironment(level, pos)) return;
        level.scheduleTick(pos, this, 10);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        trySolidifyFromEnvironment(level, pos);
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
        entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05D, 0.25D));
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(LEVEL) != 0) return ItemStack.EMPTY;
        ItemStack bucket = PlasticraftItems.UNIVERSAL_PLASTIC_MELT_BUCKET.asStack();
        if (level.getBlockEntity(pos) instanceof UniversalPlasticMeltBlockEntity melt) {
            PlasticMeltColor.set(bucket, melt.getColor());
        }
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return bucket;
    }

    /** 计划刻、随机刻和测试共用的环境判定入口，结算仍委托给唯一固化服务。 */
    public static boolean trySolidifyFromEnvironment(ServerLevel level, BlockPos pos) {
        if (!shouldCool(level, pos)) return false;
        return UniversalPlasticSolidification.solidify(level, pos);
    }

    private static boolean shouldCool(ServerLevel level, BlockPos pos) {
        if (level.isRainingAt(pos.above())) return true;
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (level.getBlockState(neighbor).is(PlasticraftBlockTags.PLASTIC_MELT_COOLANTS)
                || level.getFluidState(neighbor).is(FluidTags.WATER)) return true;
        }
        return false;
    }

}
