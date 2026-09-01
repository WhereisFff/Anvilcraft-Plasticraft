package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** 支架上原版红石火把的延迟、熄灭与烧毁恢复。 */
final class MoldedTrayRedstoneTorchBehavior implements MoldedTrayRedstoneBehavior {
    private static final int TOGGLE_DELAY = 2;
    private static final int RESTART_DELAY = 160;
    private static final long BURNOUT_WINDOW = 60L;

    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.REDSTONE_TORCH);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        long now = runtime.host().level().getGameTime();
        List<Long> activeToggles = runtime.torchToggleTimes().stream()
            .filter(time -> now - time < BURNOUT_WINDOW)
            .toList();
        runtime.torchToggleTimes(activeToggles);
        boolean poweredBelow = runtime.inputSignal(Direction.DOWN) > 0;
        boolean lit = state.getValue(RedstoneTorchBlock.LIT);
        if (runtime.remainingTicks() > 0) {
            runtime.remainingTicks(runtime.remainingTicks() - 1);
            if (runtime.remainingTicks() == 0 && lit == poweredBelow) {
                boolean nextLit = !lit;
                runtime.setState(runtime.state().setValue(RedstoneTorchBlock.LIT, nextLit));
                if (!nextLit) {
                    ArrayList<Long> toggles = new ArrayList<>(runtime.torchToggleTimes());
                    toggles.add(now);
                    runtime.torchToggleTimes(toggles.stream().toList());
                    if (runtime.torchToggleTimes().size() >= 8) runtime.remainingTicks(RESTART_DELAY);
                }
                return true;
            }
            return false;
        }
        if (lit == poweredBelow) runtime.remainingTicks(TOGGLE_DELAY);
        return false;
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        return component.state().getValue(RedstoneTorchBlock.LIT)
            && queryDirection != Direction.UP ? 15 : 0;
    }

    @Override
    public int directSignal(
        MoldedTrayComponent component,
        Direction queryDirection,
        int weakSignal
    ) {
        return queryDirection == Direction.DOWN ? weakSignal : 0;
    }
}
