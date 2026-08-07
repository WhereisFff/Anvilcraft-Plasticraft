package dev.anvilcraft.plasticraft.block;

import com.mojang.serialization.MapCodec;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 安装在塑料成型舱顶部并驱动高精度逐体素打印的组件。 */
public class Plastic3DPrintingComponentBlock extends BaseEntityBlock {
    public static final MapCodec<Plastic3DPrintingComponentBlock> CODEC = simpleCodec(
        Plastic3DPrintingComponentBlock::new
    );
    private static final VoxelShape SHAPE = Shapes.or(
        box(2.0D, 0.0D, 2.0D, 14.0D, 3.0D, 14.0D),
        box(4.0D, 3.0D, 4.0D, 12.0D, 11.0D, 12.0D),
        box(1.0D, 11.0D, 5.0D, 15.0D, 15.0D, 11.0D)
    );

    public Plastic3DPrintingComponentBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Plastic3DPrintingComponentBlock> codec() {
        return CODEC;
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
        BlockState state = this.defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(PlasticraftBlocks.PLASTIC_MOLDING_CHAMBER.get());
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
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    protected VoxelShape getShape(
        BlockState state,
        BlockGetter level,
        BlockPos pos,
        CollisionContext context
    ) {
        return SHAPE;
    }
}
