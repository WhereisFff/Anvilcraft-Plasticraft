package dev.anvilcraft.plasticraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import net.minecraft.server.level.ChunkMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Queue;

/**
 * 卸载回调新增的重试留到下一轮；关服时无限预算会让这些重试占满主线程，
 * 导致释放区块生成引用的任务永远得不到执行机会。
 */
@Mixin(ChunkMap.class)
abstract class ChunkMapUnloadMixin {
    @WrapOperation(
        method = "processUnloads",
        at = @At(value = "INVOKE", target = "Ljava/util/Queue;size()I")
    )
    private int plasticraft$captureUnloadBatch(
        Queue<Runnable> queue,
        Operation<Integer> original,
        @Share("plasticraft$unloadBudget") LocalIntRef budget
    ) {
        int size = original.call(queue);
        budget.set(size);
        return size;
    }

    @WrapOperation(
        method = "processUnloads",
        at = @At(value = "INVOKE", target = "Ljava/util/Queue;poll()Ljava/lang/Object;")
    )
    @Nullable
    private Object plasticraft$pollUnloadBatch(
        Queue<Runnable> queue,
        Operation<Object> original,
        @Share("plasticraft$unloadBudget") LocalIntRef budget
    ) {
        if (budget.get() <= 0) return null;
        budget.set(budget.get() - 1);
        return original.call(queue);
    }
}
