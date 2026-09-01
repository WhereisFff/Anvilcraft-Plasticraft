package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import dev.dubhe.anvilcraft.item.DiskItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/** 安装在塑料成型舱顶部并驱动高精度逐体素打印的组件。 */
public class Plastic3DPrintingComponentBlock extends BaseEntityBlock {
    public static final MapCodec<Plastic3DPrintingComponentBlock> CODEC = simpleCodec(
        Plastic3DPrintingComponentBlock::new
    );
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape NORTH_SHAPE = Shapes.or(
        box(0.0D, 0.0D, 3.0D, 16.0D, 3.0D, 16.0D),
        box(0.0D, 3.0D, 11.0D, 16.0D, 9.0D, 16.0D),
        box(3.0D, 2.0D, 1.0D, 13.0D, 10.0D, 11.0D),
        box(0.0D, 9.0D, 13.0D, 16.0D, 16.0D, 16.0D)
    );
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, rotateHorizontal(NORTH_SHAPE, facing));
        }
    }

    public Plastic3DPrintingComponentBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends Plastic3DPrintingComponentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return PlasticraftBlockEntities.PLASTIC_3D_PRINTING_COMPONENT.create(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState aligned = alignedWithChamber(
            this.defaultBlockState(),
            context.getLevel().getBlockState(context.getClickedPos().below())
        );
        return aligned != null && aligned.canSurvive(context.getLevel(), context.getClickedPos())
            ? aligned
            : null;
    }

    @Override
    protected void onPlace(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState oldState,
        boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            syncAppearanceFromChamber(level, pos, state);
        }
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get());
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
            && level.getBlockEntity(pos.below()) instanceof PlasticMoldingChamberBlockEntity chamber) {
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
            && level.getBlockEntity(pos.below()) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.openMenu(serverPlayer);
        }
        return ItemInteractionResult.SUCCESS;
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
        if (neighborPos.equals(pos.below()) && !state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
            return;
        }
        if (neighborPos.equals(pos.below())) {
            syncAppearanceFromChamber(level, pos, state);
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
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
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return SHAPES.getOrDefault(state.getValue(FACING), NORTH_SHAPE);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    private static void syncAppearanceFromChamber(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide) return;
        BlockState aligned = alignedWithChamber(state, level.getBlockState(pos.below()));
        if (aligned != null && aligned != state) {
            level.setBlock(pos, aligned, Block.UPDATE_ALL);
        }
    }

    @Nullable
    private static BlockState alignedWithChamber(BlockState state, BlockState chamberState) {
        if (!chamberState.is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get())) return null;
        BlockState aligned = state;
        if (chamberState.hasProperty(PlasticMoldingChamberBlock.FACING)) {
            aligned = aligned.setValue(FACING, chamberState.getValue(PlasticMoldingChamberBlock.FACING));
        }
        if (chamberState.hasProperty(PlasticMoldingChamberBlock.POWERED)) {
            aligned = aligned.setValue(POWERED, chamberState.getValue(PlasticMoldingChamberBlock.POWERED));
        }
        return aligned;
    }

    private static VoxelShape rotateHorizontal(VoxelShape shape, Direction facing) {
        int times = (facing.get2DDataValue() - Direction.NORTH.get2DDataValue() + 4) % 4;
        VoxelShape result = shape;
        for (int step = 0; step < times; step++) {
            VoxelShape rotated = Shapes.empty();
            for (AABB box : result.toAabbs()) {
                rotated = Shapes.or(
                    rotated,
                    Shapes.box(1.0D - box.maxZ, box.minY, box.minX, 1.0D - box.minZ, box.maxY, box.maxX)
                );
            }
            result = rotated;
        }
        return result.optimize();
    }
}
