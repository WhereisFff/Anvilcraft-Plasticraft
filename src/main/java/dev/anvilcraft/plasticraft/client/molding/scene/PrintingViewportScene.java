package dev.anvilcraft.plasticraft.client.molding.scene;

import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.joml.Vector3d;

/** 建模器打印场景只读取客户端已同步的组件、门与三轴显示状态。 */
public record PrintingViewportScene(
    BlockState chamberState,
    BlockState componentState,
    Vector3d controllerOrigin,
    MoldingVec3 headPosition,
    float doorProgress,
    int packedLight,
    FluidStack componentFluid
) {
    public PrintingViewportScene {
        controllerOrigin = new Vector3d(controllerOrigin);
        doorProgress = Math.clamp(doorProgress, 0.0F, 1.0F);
        componentFluid = componentFluid.copy();
    }

    @Override
    public Vector3d controllerOrigin() {
        return new Vector3d(this.controllerOrigin);
    }

    @Override
    public FluidStack componentFluid() {
        return this.componentFluid.copy();
    }
}
