package dev.anvilcraft.plasticraft.mixin;

import dev.anvilcraft.plasticraft.entity.physics.PlasticBlockEpoch;
import dev.anvilcraft.plasticraft.entity.physics.PlasticRestIndex;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 方块写入让支撑与遮挡记忆化缓存整体失效，两端都需要推进纪元。
 *
 * <p>同一处还承担休眠唤醒：休眠实体按「碰撞子盒外扩一格」登记方块格，因此这里按写入坐标
 * 精确命中即可。注入在 HEAD 只负责清除休眠标记，真正的支撑判定发生在下一刻，那时方块已落地。</p>
 */
@Mixin(Level.class)
abstract class PlasticWorldChangeMixin {
    @Inject(method = "markAndNotifyBlock", at = @At("HEAD"))
    private void plasticraft$bumpBlockEpoch(
        BlockPos pos,
        LevelChunk chunk,
        BlockState previous,
        BlockState current,
        int flags,
        int recursionLeft,
        CallbackInfo callback
    ) {
        PlasticBlockEpoch.bump();
        PlasticRestIndex.wakeAt((Level) (Object) this, pos);
    }
}
