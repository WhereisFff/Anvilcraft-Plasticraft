package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.blueprint.ConstructionJobController;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 观察服务端真实方块写入,把施工期间与蓝图冲突的外部方块交给拆除调度。 */
@Mixin(Level.class)
abstract class ConstructionWorldChangeMixin {
    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"))
    private void plasticraft$observeConstructionChange(
        BlockPos pos,
        LevelChunk chunk,
        BlockState previous,
        BlockState current,
        int flags,
        int recursionLeft,
        CallbackInfo callback
    ) {
        if (!((Object) this instanceof ServerLevel level)) return;
        if (ConstructionJobController.isWorldChangeObservationSuppressed()) return;
        ConstructionJobController.onRealBlockStateChanged(level, pos, previous, current);
    }
}
