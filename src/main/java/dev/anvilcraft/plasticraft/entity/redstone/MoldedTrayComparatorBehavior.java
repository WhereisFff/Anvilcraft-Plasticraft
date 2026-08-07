package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.ticks.TickPriority;

/** 支架上原版红石比较器的模拟输入、模式切换与输出。 */
final class MoldedTrayComparatorBehavior implements MoldedTrayRedstoneBehavior {
    private static final int DIODE_DELAY_TICKS = 2;

    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.COMPARATOR);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        int output = comparatorOutput(runtime, state);
        int oldOutput = currentOutput(runtime);
        boolean powered = state.getValue(BlockStateProperties.POWERED);
        boolean nextPowered = output > 0;
        if (runtime.remainingTicks() != 0 || oldOutput == output && powered == nextPowered) return false;
        runtime.scheduleTick(DIODE_DELAY_TICKS, transitionPriority(runtime, state));
        return true;
    }

    @Override
    public void scheduledTick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        applyOutput(runtime, state, comparatorOutput(runtime, state));
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
            ComparatorMode mode = state.getValue(ComparatorBlock.MODE);
            runtime.setState(state.setValue(
                ComparatorBlock.MODE,
                mode == ComparatorMode.COMPARE ? ComparatorMode.SUBTRACT : ComparatorMode.COMPARE
            ));
            BlockState updated = runtime.state();
            applyOutput(runtime, updated, comparatorOutput(runtime, updated));
            runtime.persistAndPublish();
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
        if (state.getValue(BlockStateProperties.HORIZONTAL_FACING) != queryDirection
            || !state.getValue(BlockStateProperties.POWERED)) {
            return 0;
        }
        return Math.clamp(component.blockEntityData().getInt("OutputSignal"), 0, 15);
    }

    @Override
    public boolean isDiode() {
        return true;
    }

    private static int comparatorOutput(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int rear = runtime.comparatorInput(facing);
        int side = Math.max(
            runtime.sideSignal(facing.getClockWise(), false),
            runtime.sideSignal(facing.getCounterClockWise(), false)
        );
        return state.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT
            ? Math.max(rear - side, 0)
            : rear >= side ? rear : 0;
    }

    private static int currentOutput(MoldedTrayRedstoneRuntime runtime) {
        return runtime.cachedBlockEntity() instanceof ComparatorBlockEntity comparator
            ? comparator.getOutputSignal()
            : runtime.loadedComponent().blockEntityData().getInt("OutputSignal");
    }

    private static boolean applyOutput(
        MoldedTrayRedstoneRuntime runtime,
        BlockState state,
        int output
    ) {
        int oldOutput = currentOutput(runtime);
        boolean powered = state.getValue(BlockStateProperties.POWERED);
        boolean nextPowered = output > 0;
        if (runtime.cachedBlockEntity() instanceof ComparatorBlockEntity comparator) {
            comparator.setOutputSignal(output);
        }
        if (powered != nextPowered) {
            runtime.setState(runtime.state().setValue(BlockStateProperties.POWERED, nextPowered));
        }
        return oldOutput != output || powered != nextPowered;
    }

    private static TickPriority transitionPriority(
        MoldedTrayRedstoneRuntime runtime,
        BlockState state
    ) {
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        return runtime.shouldPrioritizeDiode(facing) ? TickPriority.HIGH : TickPriority.NORMAL;
    }
}
