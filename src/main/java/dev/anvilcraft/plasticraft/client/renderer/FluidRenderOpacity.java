package dev.anvilcraft.plasticraft.client.renderer;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.fluids.FluidStack;

/** 统一判定容器内流体是否应作为不透明内容写入深度。 */
public final class FluidRenderOpacity {
    private FluidRenderOpacity() {
    }

    public static boolean isOpaque(FluidStack fluid) {
        // 第三方流体的视觉透明度由其客户端注册的区块渲染层定义，不能从流体名称或某个模组扩展推断。
        return ItemBlockRenderTypes.getRenderLayer(fluid.getFluid().defaultFluidState()) != RenderType.translucent();
    }
}
