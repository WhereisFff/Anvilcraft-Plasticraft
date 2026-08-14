package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.block.entity.AllayLoungeBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 悦灵休息室。本体始终使用稳定的完整方块碰撞;有电与无电经 POWERED 在两套烘焙模型间切换。
 */
public class AllayLoungeBlock extends BaseEntityBlock {
    public static final MapCodec<AllayLoungeBlock> CODEC = simpleCodec(AllayLoungeBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public AllayLoungeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends AllayLoungeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        if (player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.openMenu(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock()) && !level.isClientSide
            && level.getBlockEntity(pos) instanceof AllayLoungeBlockEntity lounge) {
            lounge.releaseAllToWorld();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AllayLoungeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> blockEntityType
    ) {
        if (level.isClientSide) return null;
        return createTickerHelper(
            blockEntityType,
            PlasticraftBlockEntities.ALLAY_LOUNGE.get(),
            AllayLoungeBlockEntity::serverTick
        );
    }
}
