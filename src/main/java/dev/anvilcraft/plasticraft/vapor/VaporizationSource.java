package dev.anvilcraft.plasticraft.vapor;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** 发现一种气化方式并创建精确、可回滚的处理报价。 */
public interface VaporizationSource {
    ResourceLocation id();

    default int priority() {
        return 0;
    }

    /**
     * 创建输出不超过 {@code maxVapor} 的报价。可被多次调用，不得改变世界状态。
     * 返回 {@code null} 表示当前顶层流体无法处理。
     */
    @Nullable
    VaporizationOffer createOffer(VaporizationContext context, FluidStack availableInput, int maxVapor);

    /**
     * 在炼药锅输入已被抽走后消耗源侧资源并产生效果。
     * 效果应按 {@code offer.input().getAmount()} 推导密度与射程。
     */
    default void commit(VaporizationContext context, VaporizationOffer offer) {
    }
}
