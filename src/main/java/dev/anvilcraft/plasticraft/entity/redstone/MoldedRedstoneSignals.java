package dev.anvilcraft.plasticraft.entity.redstone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.SignalGetter;

final class MoldedRedstoneSignals {
    private MoldedRedstoneSignals() {
    }

    static int activationSignal(SignalGetter getter, BlockPos source, Direction direction) {
        return Math.clamp(Math.max(
            getter.getSignal(source, direction),
            getter.getControlInputSignal(source, direction, false)
        ), 0, 15);
    }

    static int directSignal(SignalGetter getter, BlockPos source, Direction direction) {
        return Math.clamp(getter.getDirectSignal(source, direction), 0, 15);
    }
}
