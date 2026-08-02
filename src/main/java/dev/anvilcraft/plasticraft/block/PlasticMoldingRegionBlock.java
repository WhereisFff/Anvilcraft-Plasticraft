package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.block.entity.PlasticMoldingChamberBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** 无物理碰撞但保持选取、交互和区域占用的轻量部件。 */
public class PlasticMoldingRegionBlock extends Block {
    public static final MapCodec<PlasticMoldingRegionBlock> CODEC = simpleCodec(PlasticMoldingRegionBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<MoldingRegionPart> PART = EnumProperty.create("part", MoldingRegionPart.class);
    private static final int VALIDATION_DELAY = 40;

    public PlasticMoldingRegionBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(PART, MoldingRegionPart.D1_R0_U0));
    }

    @Override
    protected MapCodec<? extends PlasticMoldingRegionBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        BlockHitResult hitResult
    ) {
        BlockPos controller = PlasticMoldingChamberStructure.controllerPos(
            pos,
            state.getValue(FACING),
            state.getValue(PART)
        );
        if (player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(controller) instanceof PlasticMoldingChamberBlockEntity chamber) {
            chamber.openMenu(serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && PlasticMoldingChamberStructure.isValidPart(level, pos, state)) {
            BlockPos controller = PlasticMoldingChamberStructure.controllerPos(
                pos,
                state.getValue(FACING),
                state.getValue(PART)
            );
            level.destroyBlock(controller, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
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
        if (!level.isClientSide) level.scheduleTick(pos, this, VALIDATION_DELAY);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!PlasticMoldingChamberStructure.isValidPart(level, pos, state)) {
            level.removeBlock(pos, false);
            return;
        }
        level.scheduleTick(pos, this, VALIDATION_DELAY);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return 0.0F;
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        BlockPos controller = PlasticMoldingChamberStructure.controllerPos(
            pos,
            state.getValue(FACING),
            state.getValue(PART)
        );
        return level.getBlockEntity(controller) instanceof PlasticMoldingChamberBlockEntity chamber
            && chamber.hasMoldCollision()
            ? Shapes.block()
            : Shapes.empty();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getVisualShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return Shapes.empty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }
}
