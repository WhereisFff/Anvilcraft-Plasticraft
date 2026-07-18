package dev.anvilcraft.plasticraft.block;

import dev.anvilcraft.plasticraft.entity.AbstractPlasticAnvilEntity;
import dev.anvilcraft.plasticraft.entity.PlasticAnvilOrientation;
import dev.dubhe.anvilcraft.block.RoyalAnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Compatibility block for entity-backed plastic anvils.
 *
 * <p>Items normally create the persistent entity directly. Commands, structures,
 * and world generation may still place the registered block, so its scheduled
 * tick always converts that transient state to the matching plastic-anvil entity
 * instead of letting {@code FallingBlockMixin} create a vanilla falling block.</p>
 */
public abstract class AbstractPlasticAnvilBlock<E extends AbstractPlasticAnvilEntity> extends RoyalAnvilBlock {
    protected AbstractPlasticAnvilBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected final void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Do not call super: AnvilCraft injects its vanilla-entity conversion into FallingBlock#tick.
        Direction longAxis = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.SOUTH;
        PlasticAnvilOrientation orientation = PlasticAnvilOrientation.fromLongAxis(Direction.UP, longAxis);
        EntityType<? extends E> entityType = this.getPlasticAnvilEntityType();
        if (entityType == null) {
            level.scheduleTick(pos, this, this.getDelayAfterPlace());
            return;
        }

        E entity = this.createPlasticAnvilEntity(
            entityType,
            level,
            orientation.entityPosition(pos),
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

    protected abstract EntityType<? extends E> getPlasticAnvilEntityType();

    /** Builds the variant-preserving item stack carried by a block-created entity. */
    protected ItemStack createDropStack(BlockState state) {
        return new ItemStack(this);
    }

    protected abstract E createPlasticAnvilEntity(
        EntityType<? extends E> entityType,
        ServerLevel level,
        Vec3 position,
        BlockState displayState,
        ItemStack dropStack,
        PlasticAnvilOrientation orientation
    );
}
