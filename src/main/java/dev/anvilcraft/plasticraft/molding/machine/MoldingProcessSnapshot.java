package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import net.neoforged.neoforge.fluids.FluidStack;

/** 巨型铁砧提交加工前使用的不可变服务器快照。 */
public record MoldingProcessSnapshot(
    long modelRevision,
    EditableMoldingModel model,
    BakedMoldingModel bakedModel,
    FluidStack batchFluid,
    int moldedClayBalls,
    MoldingProductionMode cycleMode,
    MoldingFormingMode formingMode,
    boolean typeOverride,
    boolean creativeOverride,
    int printingProgress,
    int printingTotal
) {
    public MoldingProcessSnapshot(
        long modelRevision,
        EditableMoldingModel model,
        BakedMoldingModel bakedModel,
        FluidStack batchFluid,
        int moldedClayBalls,
        MoldingProductionMode cycleMode
    ) {
        this(
            modelRevision,
            model,
            bakedModel,
            batchFluid,
            moldedClayBalls,
            cycleMode,
            MoldingFormingMode.CASTING,
            false,
            false,
            0,
            0
        );
    }

    public MoldingProcessSnapshot(
        long modelRevision,
        EditableMoldingModel model,
        BakedMoldingModel bakedModel,
        FluidStack batchFluid,
        int moldedClayBalls,
        MoldingProductionMode cycleMode,
        boolean typeOverride,
        boolean creativeOverride
    ) {
        this(
            modelRevision,
            model,
            bakedModel,
            batchFluid,
            moldedClayBalls,
            cycleMode,
            MoldingFormingMode.CASTING,
            typeOverride,
            creativeOverride,
            0,
            0
        );
    }

    public MoldingProcessSnapshot {
        batchFluid = batchFluid.copy();
    }

    @Override
    public FluidStack batchFluid() {
        return this.batchFluid.copy();
    }
}
