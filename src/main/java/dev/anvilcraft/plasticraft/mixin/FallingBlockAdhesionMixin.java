package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.block.BondedFallingBlockInfo;
import dev.anvilcraft.plasticraft.block.BondedFallingBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 让恢复为原方块状态的下落方块在支撑仍存在时跳过下落。 */
@Mixin(FallingBlock.class)
abstract class FallingBlockAdhesionMixin {
    private static final int SUPPORT_CHECK_INTERVAL = 2;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void plasticraft$keepBondedBlockFixed(
        BlockState state,
        ServerLevel level,
        BlockPos pos,
        RandomSource random,
        CallbackInfo callback
    ) {
        BondedFallingBlockInfo info = BondedFallingBlocks.get(level, pos);
        if (info != null && !state.equals(info.blockState())) {
            if (!state.is(info.blockState().getBlock())) {
                BondedFallingBlocks.remove(level, pos);
                info = null;
            } else {
                info = info.withBlockState(state);
                BondedFallingBlocks.put(level, pos, info);
            }
        }
        if (info != null && !info.hasSupport(level)) {
            BondedFallingBlocks.remove(level, pos);
        }
        BondedFallingBlocks.validate(level, pos);
        if (!BondedFallingBlocks.isBonded(level, pos)) return;
        level.scheduleTick(pos, state.getBlock(), SUPPORT_CHECK_INTERVAL);
        callback.cancel();
    }
}
