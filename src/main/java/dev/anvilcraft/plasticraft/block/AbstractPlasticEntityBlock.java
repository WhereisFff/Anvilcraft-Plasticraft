package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticEntity;
import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.dubhe.anvilcraft.block.RoyalAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * 实体化塑料制品的兼容方块。
 *
 * <p>物品通常直接创建持久化实体。命令、结构和世界生成仍可能放置已注册的方块，
 * 因此其计划刻总会把该临时状态转换为对应的塑料实体，
 * 而不是让 {@code FallingBlockMixin} 创建原版落方块实体。</p>
 */
public abstract class AbstractPlasticEntityBlock<E extends AbstractPlasticEntity> extends RoyalAnvilBlock {
    /** 磁化状态与材料相互独立，并在实体转换后保留。 */
    public static final BooleanProperty MAGNETIZED = BooleanProperty.create("magnetized");

    protected AbstractPlasticEntityBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MAGNETIZED);
    }

    @Override
    protected final void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
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
            orientation.entityPosition(pos, entityType.getWidth(), entityType.getHeight()),
            state,
            this.createDropStack(state),
            orientation
        );
        if (entity == null) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }

        BlockState replacement = state.getFluidState().createLegacyBlock();
        if (!level.setBlock(pos, replacement, UPDATE_ALL)) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }
        if (!level.addFreshEntity(entity)) {
            level.setBlock(pos, state, UPDATE_ALL);
        }
    }

    protected abstract EntityType<? extends E> getPlasticEntityType();

    /** 构造由方块创建的实体所携带且保留变体信息的物品堆。 */
    protected ItemStack createDropStack(BlockState state) {
        ItemStack stack = new ItemStack(this);
        if (state.hasProperty(MAGNETIZED) && state.getValue(MAGNETIZED)) {
            dev.anvilcraft.plasticraft.item.PlasticItemData.setMagnetized(stack, true);
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
