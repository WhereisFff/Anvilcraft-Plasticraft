package dev.anvilcraft.plasticraft.entity.redstone;

import dev.anvilcraft.plasticraft.molding.product.MoldedTrayComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** 支架上原版阳光探测器的昼夜采样、反转交互与弱输出。 */
final class MoldedTrayDaylightDetectorBehavior implements MoldedTrayRedstoneBehavior {
    @Override
    public boolean supports(BlockState state) {
        return state.is(Blocks.DAYLIGHT_DETECTOR);
    }

    @Override
    public boolean tick(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        if (!runtime.host().level().dimensionType().hasSkyLight()
            || runtime.host().level().getGameTime() % 20L != 0L) return false;
        return updateSignal(runtime, state);
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
            runtime.setState(state.cycle(BlockStateProperties.INVERTED));
            updateSignal(runtime, runtime.state());
            runtime.persistAndPublish();
        }
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public int weakSignal(MoldedTrayComponent component, Direction queryDirection) {
        return component.state().getValue(BlockStateProperties.POWER);
    }

    @Override
    public int directSignal(
        MoldedTrayComponent component,
        Direction queryDirection,
        int weakSignal
    ) {
        return 0;
    }

    private static boolean updateSignal(MoldedTrayRedstoneRuntime runtime, BlockState state) {
        BlockPos position = MoldedTrayRedstoneNetwork.componentCell(runtime.host(), Direction.UP);
        int target = runtime.host().level().getBrightness(LightLayer.SKY, position)
            - runtime.host().level().getSkyDarken();
        float sunAngle = runtime.host().level().getSunAngle(1.0F);
        if (state.getValue(BlockStateProperties.INVERTED)) {
            target = 15 - target;
        } else if (target > 0) {
            float offset = sunAngle < (float) Math.PI ? 0.0F : (float) (Math.PI * 2.0D);
            sunAngle += (offset - sunAngle) * 0.2F;
            target = Math.round(target * (float) Math.cos(sunAngle));
        }
        target = Math.clamp(target, 0, 15);
        if (state.getValue(BlockStateProperties.POWER) == target) return false;
        runtime.setState(state.setValue(BlockStateProperties.POWER, target));
        return true;
    }
}
