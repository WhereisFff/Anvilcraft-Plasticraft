package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.redstone.MoldedTrayRedstoneScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在原生阶段中执行没有实际方块坐标的支架元件任务。 */
@Mixin(ServerLevel.class)
abstract class ServerLevelTrayRedstoneMixin {
    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void plasticraft$runTrayScheduledTick(BlockPos pos, Block block, CallbackInfo callback) {
        if (block == Blocks.STRUCTURE_VOID
            && MoldedTrayRedstoneScheduler.runScheduledTick((ServerLevel) (Object) this, pos)) {
            callback.cancel();
        }
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"), cancellable = true)
    private void plasticraft$runTrayBlockEvent(
        BlockEventData event,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (event.block() == Blocks.STRUCTURE_VOID
            && MoldedTrayRedstoneScheduler.runBlockEvent((ServerLevel) (Object) this, event)) {
            callback.setReturnValue(false);
        }
    }
}
