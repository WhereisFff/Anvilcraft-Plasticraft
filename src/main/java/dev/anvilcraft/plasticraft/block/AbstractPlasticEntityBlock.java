package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.lib.v2.piston.IMoveableEntityBlock;
import dev.anvilcraft.plasticraft.block.entity.BondedEntityBlockEntity;
import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.entity.collision.BuiltInPlasticEntityModels;
import dev.anvilcraft.plasticraft.entity.collision.PlasticEntityCollisionShapes;
import dev.anvilcraft.plasticraft.init.block.PlasticraftBlockEntities;
import dev.anvilcraft.plasticraft.item.PlasticItemData;
import dev.dubhe.anvilcraft.block.RoyalAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体化塑料制品的兼容方块。
 *
 * <p>物品通常直接创建持久化实体。命令、结构和世界生成仍可能放置已注册的方块，
 * 因此其计划刻总会把该临时状态转换为对应的塑料实体，
 * 而不是让 {@code FallingBlockMixin} 创建原版落方块实体。</p>
 */
public abstract class AbstractPlasticEntityBlock<E extends AbstractPlasticEntity> extends RoyalAnvilBlock
    implements IMoveableEntityBlock {
    /** 磁化状态与材料相互独立，并在实体转换后保留。 */
    public static final BooleanProperty MAGNETIZED = BooleanProperty.create("magnetized");
    public static final BooleanProperty BONDED = BooleanProperty.create("bonded");
    /** 皇家铁砧在方块局部坐标中的组合碰撞形状，供方块态与实体态共同使用。 */
    public static final VoxelShape ROYAL_ANVIL_COLLISION_SHAPE = BuiltInPlasticEntityModels
        .ROYAL_ANVIL_COMPATIBILITY;
    private static final Map<PlasticEntityOrientation, VoxelShape> BONDED_ANVIL_SHAPES =
        new ConcurrentHashMap<>();

    protected AbstractPlasticEntityBlock(Properties properties) {
        // Bonded six-axis orientations live in the block entity and must not be
        // collapsed into BlockStateBase's empty-level shape cache.
        super(properties.dynamicShape());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MAGNETIZED, BONDED);
    }

    @Override
    protected final void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(BONDED)) {
            if (level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded) {
                if (!bonded.isInitialized() || !bonded.hasSupport()) {
                    bonded.release();
                } else {
                    bonded.tickFunctionalEntity();
                    level.scheduleTick(pos, this, 1);
                }
            } else {
                level.setBlock(pos, state.setValue(BONDED, false), UPDATE_ALL);
                level.scheduleTick(pos, this, this.getDelayAfterPlace());
            }
            return;
        }
        // 不调用 super：AnvilCraft 会向 FallingBlock#tick 注入原版实体转换逻辑。
        Direction longAxis = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.SOUTH;
        PlasticEntityOrientation orientation = PlasticEntityOrientation.fromLongAxis(Direction.UP, longAxis);
        EntityType<? extends E> entityType = this.getPlasticEntityType();
        if (entityType == null) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }

        E entity = this.createPlasticEntity(
            entityType,
            level,
            Vec3.atBottomCenterOf(pos),
            state,
            this.createDropStack(state),
            orientation
        );
        if (entity == null) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }
        entity.setPos(entity.plasticraft$placementPosition(pos, orientation));
        entity.setStartPos(entity.blockPosition());

        BlockState replacement = state.getFluidState().createLegacyBlock();
        if (!level.setBlock(pos, replacement, UPDATE_ALL)) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }
        if (!level.addFreshEntity(entity)) {
            level.setBlock(pos, state, UPDATE_ALL);
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (state.getValue(BONDED)) {
            level.scheduleTick(pos, this, 2);
            return;
        }
        super.onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    public void neighborChanged(
        BlockState state,
        Level level,
        BlockPos pos,
        Block neighborBlock,
        BlockPos neighborPos,
        boolean movedByPiston
    ) {
        if (state.getValue(BONDED)) {
            level.scheduleTick(pos, this, 1);
            return;
        }
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return state.getValue(BONDED) ? RenderShape.INVISIBLE : super.getRenderShape(state);
    }

    @Override
    public InteractionResult use(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        if (state.getValue(BONDED)
            && level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()) {
            return bonded.interact(player, hand, hit);
        }
        return InteractionResult.PASS;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(BONDED)) return super.getShape(state, level, pos, context);
        PlasticEntityOrientation orientation = level.getBlockEntity(pos) instanceof BondedEntityBlockEntity bonded
            && bonded.isInitialized()
            && bonded.isPlastic()
            ? bonded.getPlasticOrientation()
            : PlasticEntityOrientation.fromLegacyState(state);
        return BONDED_ANVIL_SHAPES.computeIfAbsent(
            orientation,
            ignored -> rotateShape(ROYAL_ANVIL_COLLISION_SHAPE, orientation)
        );
    }

    protected static VoxelShape rotateShape(VoxelShape shape, PlasticEntityOrientation orientation) {
        return PlasticEntityCollisionShapes.rotate(shape, orientation);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return PlasticraftBlockEntities.BONDED_ENTITY.create(pos, state);
    }

    @Override
    public void notifyMoved(Level level, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (blockEntity instanceof BondedEntityBlockEntity bonded) bonded.moved();
    }

    protected abstract EntityType<? extends E> getPlasticEntityType();

    /** 构造由方块创建的实体所携带且保留变体信息的物品堆。 */
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (state.hasProperty(MAGNETIZED) && state.getValue(MAGNETIZED)) {
            PlasticItemData.setMagnetized(stack, true);
        }
        return stack;
    }

    protected abstract E createPlasticEntity(
        EntityType<? extends E> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticEntityOrientation orientation
    );
}
