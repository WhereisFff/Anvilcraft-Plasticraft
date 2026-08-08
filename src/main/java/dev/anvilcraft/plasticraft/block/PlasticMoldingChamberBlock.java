package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
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

/** 塑料成型舱控制器，正面方向由水平 FACING 保存。 */
public class PlasticMoldingChamberBlock extends BaseEntityBlock {
    public static final MapCodec<PlasticMoldingChamberBlock> CODEC = simpleCodec(PlasticMoldingChamberBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LOCKED = BlockStateProperties.LOCKED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PlasticMoldingChamberBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(LOCKED, false)
            .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends PlasticMoldingChamberBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction front = context.getHorizontalDirection().getOpposite();
        return PlasticMoldingChamberStructure.canPlace(context.getLevel(), context.getClickedPos(), front)
            ? this.defaultBlockState().setValue(FACING, front)
            : null;
    }

    @Override
    public void setPlacedBy(
        Level level,
        BlockPos pos,
        BlockState state,
        @Nullable LivingEntity placer,
        ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) PlasticMoldingChamberStructure.repair(level, pos, state.getValue(FACING));
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
            && level.getBlockEntity(pos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.openMenu(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
        if (!(stack.getItem() instanceof DiskItem)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.openMenu(serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected void onRemove(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState newState,
        boolean movedByPiston
    ) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                if (level.getBlockEntity(pos) instanceof PlasticMoldingChamberBlockEntity chamber) {
                    chamber.dropContents();
                }
                PlasticMoldingChamberStructure.removeParts(level, pos, state.getValue(FACING));
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCKED, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.create(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
        Level level,
        BlockState state,
        BlockEntityType<T> type
    ) {
        return level.isClientSide
            ? null
            : createTickerHelper(
                type,
                PlasticraftBlockEntities.PLASTIC_MOLDING_CHAMBER.get(),
                PlasticMoldingChamberBlockEntity::serverTick
            );
    }
}
