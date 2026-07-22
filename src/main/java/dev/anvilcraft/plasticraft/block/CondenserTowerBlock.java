package dev.anvilcraft.plasticraft.block;

import dev.dubhe.anvilcraft.block.LargeCauldronBlock;
import dev.dubhe.anvilcraft.block.multipart.MultiPartBlockEntity;
import dev.dubhe.anvilcraft.block.multipart.SimpleMultiPartBlock;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import dev.dubhe.anvilcraft.api.fluid.network.FluidNetworkManager;
import dev.anvilcraft.plasticraft.block.entity.CondenserTowerBlockEntity;
import dev.anvilcraft.plasticraft.init.block.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** 3x3x3 冷凝塔模块；中心部件持有该模块的流体缓存。 */
public class CondenserTowerBlock extends SimpleMultiPartBlock<Cube3x3PartHalf>
    implements MultiPartBlockEntity<Cube3x3PartHalf, CondenserTowerBlock> {
    public static final EnumProperty<Cube3x3PartHalf> HALF = EnumProperty.create("half", Cube3x3PartHalf.class);
    public static final BooleanProperty SEALED = BooleanProperty.create("sealed");

    public CondenserTowerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(HALF, Cube3x3PartHalf.BOTTOM_CENTER)
            .setValue(SEALED, false));
    }

    @Override
    public Vec3i getMainPartOffset() {
        return new Vec3i(0, 1, 0);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, SEALED);
    }

    @Override
    public Property<Cube3x3PartHalf> getPart() {
        return HALF;
    }

    @Override
    public Cube3x3PartHalf[] getParts() {
        return Cube3x3PartHalf.values();
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(HALF, state.getValue(HALF).rotate(rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(HALF, state.getValue(HALF).mirror(mirror));
    }

    @Override
    public BlockState getPlacementState(BlockPlaceContext context) {
        BlockState state = super.getPlacementState(context);
        if (state == null) return null;
        return state.setValue(SEALED, isAlignedWithLargeCauldron(context.getLevel(), context.getClickedPos()));
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
        refreshSeal(level, pos, state);
    }

    @Override
    public void onPlace(
        BlockState state,
        Level level,
        BlockPos pos,
        BlockState oldState,
        boolean movedByPiston
    ) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            level.invalidateCapabilities(pos);
            if (!level.isClientSide()) refreshSeal(level, pos, state);
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
        if (!level.isClientSide()) refreshSeal(level, pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            level.invalidateCapabilities(pos);
            if (!level.isClientSide()) {
                FluidNetworkManager.INSTANCE.markDirty(level);
                if (isMainPart(state)) {
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity instanceof CondenserTowerBlockEntity tower) tower.dropContents();
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 每个部件都提供完整方块碰撞，手持放置预览与实际占位保持一致。
        return Shapes.block();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public CondenserTowerBlock getMultiBlock() {
        return this;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.CONDENSER_TOWER.create(pos, state);
    }

    /** 判断模块底部是否正好贴着大型炼药锅顶部。 */
    public static boolean isAlignedWithLargeCauldron(LevelReader level, BlockPos moduleBase) {
        BlockPos below = moduleBase.below();
        BlockState state = level.getBlockState(below);
        return state.getBlock() instanceof LargeCauldronBlock
            && state.hasProperty(LargeCauldronBlock.HALF)
            && state.getValue(LargeCauldronBlock.HALF) == Cube3x3PartHalf.TOP_CENTER;
    }

    /** 返回某个部件是否为顶层四向流体接口。 */
    public static @Nullable Direction outputDirection(BlockState state) {
        if (!(state.getBlock() instanceof CondenserTowerBlock) || !state.hasProperty(HALF)) return null;
        return switch (state.getValue(HALF)) {
            case TOP_N -> Direction.NORTH;
            case TOP_S -> Direction.SOUTH;
            case TOP_W -> Direction.WEST;
            case TOP_E -> Direction.EAST;
            default -> null;
        };
    }

    private void refreshSeal(Level level, BlockPos pos, BlockState state) {
        if (!state.is(this) || !state.hasProperty(HALF)) return;
        BlockPos main = this.getMainPartPos(pos, state);
        BlockState mainState = level.getBlockState(main);
        if (!mainState.is(this)) return;
        BlockPos base = main.below();
        boolean sealed = isAlignedWithLargeCauldron(level, base);
        for (Cube3x3PartHalf part : this.getParts()) {
            BlockPos partPos = base.offset(part.getOffset());
            BlockState partState = level.getBlockState(partPos);
            if (partState.is(this) && partState.getValue(SEALED) != sealed) {
                level.setBlock(partPos, partState.setValue(SEALED, sealed), Block.UPDATE_CLIENTS);
            }
        }
    }
}
