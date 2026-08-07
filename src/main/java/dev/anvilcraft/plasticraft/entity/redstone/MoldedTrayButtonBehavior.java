package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedPlasticData;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponentGeometry;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;

/** 支架上全部按钮的按压、箭矢检测、释放与方向输出。 */
final class MoldedTrayButtonBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return state.getBlock() instanceof ButtonBlock || state.is(BlockTags.BUTTONS);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        boolean powered = state.getValue(BlockStateProperties.POWERED);
        boolean arrowSensitive = state.is(BlockTags.WOODEN_BUTTONS);
        if (!powered) {
            if (!arrowSensitive || !hasArrow(runtime, state)) return false;
            runtime.setState(state.setValue(BlockStateProperties.POWERED, true));
            runtime.remainingTicks(pressedTime(state));
            playSound(runtime, true);
            runtime.host().gameEvent(GameEvent.BLOCK_ACTIVATE);
            return true;
        }
        if (runtime.remainingTicks() > 0) {
            runtime.remainingTicks(runtime.remainingTicks() - 1);
            if (runtime.remainingTicks() > 0) return false;
        }
        if (arrowSensitive && hasArrow(runtime, state)) {
            runtime.remainingTicks(pressedTime(state));
            return false;
        }
        runtime.setState(state.setValue(BlockStateProperties.POWERED, false));
        playSound(runtime, false);
        runtime.host().gameEvent(GameEvent.BLOCK_DEACTIVATE);
        return true;
    }

    @Override
    public InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        if (state.getValue(BlockStateProperties.POWERED)) return InteractionResult.CONSUME;
        if (!player.level().isClientSide) {
            runtime.setState(state.setValue(BlockStateProperties.POWERED, true));
            runtime.remainingTicks(pressedTime(state));
            playSound(runtime, true);
            runtime.host().gameEvent(GameEvent.BLOCK_ACTIVATE, player);
            runtime.persistAndPublish();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        return component.state().getValue(BlockStateProperties.POWERED) ? 15 : 0;
    }

    @Override
    public int directSignal(
        MoldedTrayComponent component,
        Direction queryDirection,
        int weakSignal
    ) {
        return connectedDirection(component.state()) == queryDirection ? weakSignal : 0;
    }

    private static boolean hasArrow(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        MoldedPlasticData data = runtime.host().getMoldedData().orElseThrow();
        AABB local = MoldedTrayComponentGeometry.localInteractionShape(
            data,
            state,
            runtime.host().level(),
            runtime.host().blockPosition()
        ).bounds();
        AABB world = MoldedTrayPressurePlateSupport.worldBounds(runtime.host(), local);
        return !runtime.host().level().getEntitiesOfClass(AbstractArrow.class, world).isEmpty();
    }

    private static int pressedTime(BlockState state) {
        return state.is(BlockTags.WOODEN_BUTTONS) ? 30 : 20;
    }

    private static Direction connectedDirection(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.ATTACH_FACE)) return Direction.UP;
        AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
        return switch (face) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        };
    }

    private static void playSound(MoldedTrayRedstoneRuntime runtime, boolean powered) {
        boolean wooden = runtime.state().is(BlockTags.WOODEN_BUTTONS);
        runtime.host().level().playSound(
            null,
            runtime.host().blockPosition(),
            wooden
                ? powered ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvents.WOODEN_BUTTON_CLICK_OFF
                : powered ? SoundEvents.STONE_BUTTON_CLICK_ON : SoundEvents.STONE_BUTTON_CLICK_OFF,
            SoundSource.BLOCKS,
            0.3F,
            powered ? 0.6F : 0.5F
        );
    }
}
