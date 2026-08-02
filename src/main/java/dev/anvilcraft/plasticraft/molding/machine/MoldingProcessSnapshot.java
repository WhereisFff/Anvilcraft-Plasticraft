package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import net.neoforged.neoforge.fluids.FluidStack;

/** TODO-05 提交加工前使用的不可变服务器快照。 */
public record MoldingProcessSnapshot(
    long modelRevision,
    EditableMoldingModel model,
    BakedMoldingModel bakedModel,
    FluidStack batchFluid,
    int moldedClayBalls,
    MoldingProductionMode cycleMode
) {
    public MoldingProcessSnapshot {
        batchFluid = batchFluid.copy();
    }

    @Override
    public FluidStack batchFluid() {
        return this.batchFluid.copy();
    }
}
