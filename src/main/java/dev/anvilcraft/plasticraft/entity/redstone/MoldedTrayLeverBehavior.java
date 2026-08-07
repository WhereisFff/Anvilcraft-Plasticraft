package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;

/** 支架上原版拉杆的切换交互与方向输出。 */
final class MoldedTrayLeverBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.LEVER);
    }

    @Override
    public InteractionResult interact(
        MoldedTrayRedstoneRuntime runtime,
        Player player,
        InteractionHand hand,
        BlockState state
    ) {
        if (!player.level().isClientSide) {
            boolean powered = !state.getValue(BlockStateProperties.POWERED);
            runtime.setState(state.setValue(BlockStateProperties.POWERED, powered));
            runtime.host().level().playSound(
                null,
                runtime.host().blockPosition(),
                SoundEvents.LEVER_CLICK,
                SoundSource.BLOCKS,
                0.3F,
                powered ? 0.6F : 0.5F
            );
            runtime.host().gameEvent(
                powered ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE,
                player
            );
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

    private static Direction connectedDirection(BlockState state) {
        if (!state.hasProperty(BlockStateProperties.ATTACH_FACE)) return Direction.UP;
        AttachFace face = state.getValue(BlockStateProperties.ATTACH_FACE);
        return switch (face) {
            case CEILING -> Direction.DOWN;
            case FLOOR -> Direction.UP;
            case WALL -> state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        };
    }
}
