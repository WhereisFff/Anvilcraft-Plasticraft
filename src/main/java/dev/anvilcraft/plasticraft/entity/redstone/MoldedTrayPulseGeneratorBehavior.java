package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.dubhe.anvilcraft.block.entity.PulseGeneratorBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.ticks.TickPriority;

/** 支架上 AnvilCraft 脉冲发生器的沿触发、循环状态机与原生阶段时序。 */
final class MoldedTrayPulseGeneratorBehavior implements MoldedTrayRedstoneBehavior {
    private static final int END_OUTPUTTING_EVENT = 0;
    private static final int END_WAITING_EVENT = 1;

    @Override
    public boolean supports(BlockState state) {
        return state.is(ModBlocks.PULSE_GENERATOR.get());
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        if (!(runtime.cachedBlockEntity() instanceof PulseGeneratorBlockEntity pulse)) return false;
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        boolean inputting = runtime.inputSignal(facing) > 0;
        boolean previousInputting = pulse.isInputtingSignal();
        pulse.setInputtingSignal(inputting);
        boolean changed = previousInputting != inputting;
        if (pulse.getStartMode() == PulseGeneratorBlockEntity.Mode.LOOP) {
            if (inputting) {
                changed |= !pulse.isDeadlock() || pulse.isProcessing() || runtime.remainingTicks() != 0;
                pulse.setDeadlock(true);
                if (pulse.isProcessing()) pulse.setState(PulseGeneratorBlockEntity.State.DEFAULT);
                runtime.cancelScheduledAction();
            } else if (pulse.isDeadlock()) {
                pulse.setDeadlock(false);
                changed = true;
                if (pulse.getWaitingTime() == 0) {
                    pulse.setState(PulseGeneratorBlockEntity.State.WAITING);
                    runtime.scheduleTick(1, TickPriority.LOW);
                } else {
                    startWaiting(runtime, pulse);
                }
            } else if (!pulse.isProcessing()) {
                startWaiting(runtime, pulse);
                changed = true;
            }
        } else {
            if (pulse.isDeadlock()) {
                pulse.setDeadlock(false);
                changed = true;
            }
            boolean edge = switch (pulse.getStartMode()) {
                case RISING_EDGE -> !previousInputting && inputting;
                case FALLING_EDGE -> previousInputting && !inputting;
                case LOOP -> false;
            };
            if (edge && !pulse.isProcessing()) {
                startWaiting(runtime, pulse);
                changed = true;
            }
        }
        return updatePoweredState(runtime, pulse) || changed;
    }

    @Override
    public void scheduledTick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        if (!(runtime.cachedBlockEntity() instanceof PulseGeneratorBlockEntity pulse)
            || pulse.isDeadlock()) return;
        switch (pulse.getState()) {
            case WAITING -> startOutputting(runtime, pulse);
            case OUTPUTTING -> endOutputting(runtime, pulse);
            case DEFAULT -> updatePoweredState(runtime, pulse);
        }
    }

    @Override
    public void blockEvent(MoldedTrayRedstoneRuntime runtime, BlockState state, int eventId) {
        if (!(runtime.cachedBlockEntity() instanceof PulseGeneratorBlockEntity pulse)) return;
        if (eventId == END_WAITING_EVENT && pulse.getState() == PulseGeneratorBlockEntity.State.WAITING) {
            startOutputting(runtime, pulse);
        } else if (eventId == END_OUTPUTTING_EVENT
            && pulse.getState() == PulseGeneratorBlockEntity.State.OUTPUTTING) {
            endOutputting(runtime, pulse);
        }
    }

    @Override
    public void resumeScheduledAction(
        MoldedTrayRedstoneRuntime runtime,
        MoldedTrayComponent component
    ) {
        if (!(runtime.cachedBlockEntity() instanceof PulseGeneratorBlockEntity pulse)
            || pulse.isDeadlock()) return;
        int remaining = component.scheduledTicks();
        if (pulse.getState() == PulseGeneratorBlockEntity.State.WAITING) {
            if (remaining > 0) runtime.scheduleTick(remaining, TickPriority.LOW);
            else runtime.scheduleBlockEvent(END_WAITING_EVENT);
        } else if (pulse.getState() == PulseGeneratorBlockEntity.State.OUTPUTTING) {
            if (remaining > 0) runtime.scheduleTick(remaining, TickPriority.VERY_LOW);
            else runtime.scheduleBlockEvent(END_OUTPUTTING_EVENT);
        }
    }

    @Override
    public InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        return runtime.openMenu(player);
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        BlockState state = component.state();
        return state.getValue(BlockStateProperties.HORIZONTAL_FACING) == queryDirection
            && state.getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    public boolean supportsStructureDisk() {
        return true;
    }

    private static void startWaiting(
        MoldedTrayRedstoneRuntime runtime,
        PulseGeneratorBlockEntity pulse
    ) {
        pulse.setState(PulseGeneratorBlockEntity.State.WAITING);
        if (pulse.getWaitingTime() > 0) {
            runtime.scheduleTick(pulse.getWaitingTime(), TickPriority.LOW);
        } else {
            runtime.scheduleBlockEvent(END_WAITING_EVENT);
        }
    }

    private static void startOutputting(
        MoldedTrayRedstoneRuntime runtime,
        PulseGeneratorBlockEntity pulse
    ) {
        pulse.setState(PulseGeneratorBlockEntity.State.OUTPUTTING);
        if (pulse.getSignalDuration() > 0) {
            runtime.scheduleTick(pulse.getSignalDuration(), TickPriority.VERY_LOW);
        }
        publishOutput(runtime, pulse);
        if (pulse.getSignalDuration() == 0) runtime.scheduleBlockEvent(END_OUTPUTTING_EVENT);
    }

    private static void endOutputting(
        MoldedTrayRedstoneRuntime runtime,
        PulseGeneratorBlockEntity pulse
    ) {
        pulse.setState(PulseGeneratorBlockEntity.State.DEFAULT);
        updatePoweredState(runtime, pulse);
        runtime.publishNetwork();
        if (pulse.getStartMode() == PulseGeneratorBlockEntity.Mode.LOOP && !pulse.isDeadlock()) {
            startWaiting(runtime, pulse);
        }
    }

    private static void publishOutput(
        MoldedTrayRedstoneRuntime runtime,
        PulseGeneratorBlockEntity pulse
    ) {
        updatePoweredState(runtime, pulse);
        runtime.publishNetwork();
    }

    private static boolean updatePoweredState(
        MoldedTrayRedstoneRuntime runtime,
        PulseGeneratorBlockEntity pulse
    ) {
        BlockState currentState = runtime.state();
        boolean shouldPower = pulse.isOutputting();
        if (currentState.getValue(BlockStateProperties.POWERED) == shouldPower) return false;
        runtime.setState(currentState.setValue(BlockStateProperties.POWERED, shouldPower));
        return true;
    }
}
