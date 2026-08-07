package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.dubhe.anvilcraft.block.plate.TimeCountedPressurePlateBlock;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/** 支架上原版及 AnvilCraft 压力板的实体检测、延迟与输出。 */
final class MoldedTrayPressurePlateBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return MoldedTrayPressurePlateSupport.isPressurePlate(state);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        int current = MoldedTrayPressurePlateSupport.currentSignal(state);
        boolean timeCounted = runtime.cachedBlockEntity() != null
            && state.getBlock() instanceof TimeCountedPressurePlateBlock;
        if (!timeCounted && current > 0 && runtime.remainingTicks() > 0) {
            runtime.remainingTicks(runtime.remainingTicks() - 1);
            if (runtime.remainingTicks() > 0) return false;
        }
        int expected = MoldedTrayPressurePlateSupport.expectedSignal(
            runtime.host(),
            state,
            runtime.cachedBlockEntity()
        );
        if (!timeCounted) {
            runtime.remainingTicks(expected > 0 ? MoldedTrayPressurePlateSupport.pressedTime(state) : 0);
        }
        if (expected == current) return false;
        runtime.setState(MoldedTrayPressurePlateSupport.withSignal(state, expected));
        if (current == 0 || expected == 0) {
            boolean activated = expected > 0;
            playSound(runtime, activated);
            runtime.host().gameEvent(
                activated ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE
            );
        }
        return true;
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        return MoldedTrayPressurePlateSupport.currentSignal(component.state());
    }

    @Override
    public int directSignal(
        MoldedTrayComponent component,
        Direction queryDirection,
        int weakSignal
    ) {
        return queryDirection == Direction.UP ? weakSignal : 0;
    }

    private static void playSound(MoldedTrayRedstoneRuntime runtime, boolean powered) {
        BlockState state = runtime.state();
        boolean wooden = state.is(BlockTags.WOODEN_PRESSURE_PLATES);
        boolean stone = state.is(Blocks.STONE_PRESSURE_PLATE)
            || state.is(Blocks.POLISHED_BLACKSTONE_PRESSURE_PLATE);
        runtime.host().level().playSound(
            null,
            runtime.host().blockPosition(),
            wooden
                ? powered ? SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON : SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF
                : stone
                    ? powered ? SoundEvents.STONE_PRESSURE_PLATE_CLICK_ON : SoundEvents.STONE_PRESSURE_PLATE_CLICK_OFF
                    : powered ? SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON : SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
            SoundSource.BLOCKS,
            0.3F,
            1.0F
        );
    }
}
