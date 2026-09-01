package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** 单种支架红石元件的独立运行逻辑。 */
interface MoldedTrayRedstoneBehavior {
    boolean supports(BlockState state);

    default boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        return false;
    }

    default void scheduledTick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
    }

    default void blockEvent(MoldedTrayRedstoneRuntime runtime, BlockState state, int eventId) {
    }

    default void resumeScheduledAction(
        MoldedTrayRedstoneRuntime runtime,
        MoldedTrayComponent component
    ) {
    }

    default InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        return InteractionResult.PASS;
    }

    default int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        return 0;
    }

    default int directSignal(
        MoldedTrayComponent component,
        Direction queryDirection,
        int weakSignal
    ) {
        return weakSignal;
    }

    default boolean isDiode() {
        return false;
    }

    default boolean supportsStructureDisk() {
        return false;
    }

    default void loadRuntime(
        MoldedTrayRedstoneRuntime runtime,
        MoldedTrayComponent component
    ) {
    }

    default void writeSnapshotData(
        MoldedTrayRedstoneRuntime runtime,
        CompoundTag data
    ) {
    }
}
