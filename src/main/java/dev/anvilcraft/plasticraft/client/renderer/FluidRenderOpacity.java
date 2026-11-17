package dev.anvilcraft.plasticraft.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.neoforged.neoforge.fluids.FluidStack;

/** 统一判定容器内流体是否应作为不透明内容写入深度。 */
public final class FluidRenderOpacity {
    private FluidRenderOpacity() {
    }

    public static boolean isOpaque(FluidStack fluid) {
        // 第三方流体的视觉透明度由其客户端注册的区块渲染层定义，不能从流体名称或某个模组扩展推断。
        return !model(fluid).layer().translucent();
    }

    public static FluidModel model(FluidStack fluid) {
        return Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.getFluid().defaultFluidState());
    }

    public static int tint(FluidStack fluid) {
        var source = model(fluid).fluidTintSource();
        return source == null ? -1 : source.colorAsStack(fluid);
    }
}
