package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.dubhe.anvilcraft.block.AdvancedComparatorBlock;
import dev.dubhe.anvilcraft.block.entity.AdvancedComparatorBlockEntity;
import dev.dubhe.anvilcraft.init.block.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 支架上 AnvilCraft 高级比较器的阈值、模式与红石控制。 */
final class MoldedTrayAdvancedComparatorBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return state.is(ModBlocks.ADVANCED_COMPARATOR.get());
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        if (!(runtime.cachedBlockEntity() instanceof AdvancedComparatorBlockEntity comparator)) return false;
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int input = runtime.comparatorInput(facing);
        boolean changed = comparator.getInputtingSignal() != input;
        comparator.setInputtingSignal(input);
        if (comparator.isRedstoneControl()) {
            int clockwise = runtime.inputSignal(facing.getClockWise());
            int counterClockwise = runtime.inputSignal(facing.getCounterClockWise());
            int high = Math.max(clockwise, counterClockwise);
            int low = Math.min(clockwise, counterClockwise);
            changed |= comparator.getHighLimit() != high || comparator.getLowLimit() != low;
            comparator.setHighLimit(high);
            comparator.setLowLimit(low);
        }

        AdvancedComparatorBlockEntity.State previousState = comparator.getState();
        AdvancedComparatorBlockEntity.Mode mode = comparator.getCompareMode();
        int high = comparator.getHighLimit();
        int low = comparator.getLowLimit();
        if (mode == AdvancedComparatorBlockEntity.Mode.WINDOW && high == low && input == high) {
            comparator.setState(AdvancedComparatorBlockEntity.State.OUTPUT_HIGH);
        }
        switch (comparator.getState()) {
            case OUTPUT_LOW -> {
                if (mode == AdvancedComparatorBlockEntity.Mode.HYSTERESIS && input >= high
                    || mode == AdvancedComparatorBlockEntity.Mode.WINDOW && input >= low && input <= high) {
                    comparator.setState(AdvancedComparatorBlockEntity.State.OUTPUT_HIGH);
                }
            }
            case OUTPUT_HIGH -> {
                if (mode == AdvancedComparatorBlockEntity.Mode.HYSTERESIS && input < low
                    || mode == AdvancedComparatorBlockEntity.Mode.WINDOW && (input < low || input > high)) {
                    comparator.setState(AdvancedComparatorBlockEntity.State.OUTPUT_LOW);
                }
            }
        }
        changed |= comparator.getState() != previousState;
        boolean powered = comparator.isOutputting();
        BlockState updated = state
            .setValue(AdvancedComparatorBlock.POWERED, powered)
            .setValue(AdvancedComparatorBlock.INPUT, input > 0)
            .setValue(AdvancedComparatorBlock.POWER, Math.clamp(input, 0, 15))
            .setValue(AdvancedComparatorBlock.MODE, mode);
        if (!updated.equals(state)) {
            runtime.setState(updated);
            changed = true;
        }
        if (changed) comparator.setChanged();
        return changed;
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
}
