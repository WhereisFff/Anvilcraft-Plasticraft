package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.ticks.TickPriority;

/** 支架上原版红石中继器的延迟、锁定、交互与输出。 */
final class MoldedTrayRepeaterBehavior implements MoldedTrayRedstoneBehavior {
    private static final int DIODE_DELAY_TICKS = 2;

    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.REPEATER);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean locked = isLocked(runtime, facing);
        boolean powered = state.getValue(BlockStateProperties.POWERED);
        boolean changed = false;
        if (state.hasProperty(RepeaterBlock.LOCKED) && state.getValue(RepeaterBlock.LOCKED) != locked) {
            runtime.setState(state.setValue(RepeaterBlock.LOCKED, locked));
            changed = true;
        }
        if (!locked && runtime.remainingTicks() == 0 && powered != (runtime.inputSignal(facing) > 0)) {
            runtime.scheduleTick(delay(state), transitionPriority(runtime, state));
            changed = true;
        }
        return changed;
    }

    @Override
    public void scheduledTick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean locked = isLocked(runtime, facing);
        if (state.hasProperty(RepeaterBlock.LOCKED) && state.getValue(RepeaterBlock.LOCKED) != locked) {
            state = state.setValue(RepeaterBlock.LOCKED, locked);
            runtime.setState(state);
        }
        if (locked) return;

        boolean powered = state.getValue(BlockStateProperties.POWERED);
        boolean shouldPower = runtime.inputSignal(facing) > 0;
        if (powered && !shouldPower) {
            runtime.setState(state.setValue(BlockStateProperties.POWERED, false));
        } else if (!powered) {
            runtime.setState(state.setValue(BlockStateProperties.POWERED, true));
            if (!shouldPower) runtime.scheduleTick(delay(state), TickPriority.VERY_HIGH);
        }
    }

    @Override
    public void resumeScheduledAction(
        MoldedTrayRedstoneRuntime runtime,
        MoldedTrayComponent component
    ) {
        if (component.scheduledTicks() > 0) {
            runtime.scheduleTick(component.scheduledTicks(), transitionPriority(runtime, component.state()));
        }
    }

    @Override
    public InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        if (!player.getAbilities().mayBuild) return InteractionResult.PASS;
        if (!player.level().isClientSide) {
            int delay = state.getValue(RepeaterBlock.DELAY);
            runtime.setState(state.setValue(RepeaterBlock.DELAY, delay == 4 ? 1 : delay + 1));
            runtime.persistIfChanged();
            runtime.host().level().playSound(
                null,
                runtime.host().blockPosition(),
                SoundEvents.COMPARATOR_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                1.0F
            );
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        BlockState state = component.state();
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == queryDirection
            && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    public boolean isDiode() {
        return true;
    }

    private static boolean isLocked(MoldedTrayRedstoneRuntime runtime, Direction facing) {
        return runtime.sideSignal(facing.getClockWise(), true) > 0
            || runtime.sideSignal(facing.getCounterClockWise(), true) > 0;
    }

    private static int delay(BlockState state) {
        return state.getValue(RepeaterBlock.DELAY) * DIODE_DELAY_TICKS;
    }

    private static TickPriority transitionPriority(
        MoldedTrayRedstoneRuntime runtime,
        BlockState state
    ) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (runtime.shouldPrioritizeDiode(facing)) return TickPriority.EXTREMELY_HIGH;
        return state.getValue(BlockStateProperties.POWERED)
            ? TickPriority.VERY_HIGH
            : TickPriority.HIGH;
    }
}
