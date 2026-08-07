package dev.anvilcraft.plasticraft.molding.bake;

/** 箱子与储罐共用的密封外壳分析结果。 */
public record MoldingFunctionalAnalysis(
    MoldingVolumeMask cavityMask,
    int cavityCount,
    int cavityVolume,
    int shellVolume,
    boolean internalDecoration,
    MoldingAnvilShapeAnalysis anvilShape,
    MoldingTrayShapeAnalysis trayShape
) {
    public MoldingFunctionalAnalysis(
        MoldingVolumeMask cavityMask,
        int cavityCount,
        int cavityVolume,
        int shellVolume,
        boolean internalDecoration
    ) {
        this(
            cavityMask,
            cavityCount,
            cavityVolume,
            shellVolume,
            internalDecoration,
            MoldingAnvilShapeAnalysis.invalid("anvil_shape_unavailable"),
            MoldingTrayShapeAnalysis.invalid("tray_shape_unavailable")
        );
    }

    public MoldingFunctionalAnalysis(
        MoldingVolumeMask cavityMask,
        int cavityCount,
        int cavityVolume,
        int shellVolume,
        boolean internalDecoration,
        MoldingAnvilShapeAnalysis anvilShape
    ) {
        this(
            cavityMask,
            cavityCount,
            cavityVolume,
            shellVolume,
            internalDecoration,
            anvilShape,
            MoldingTrayShapeAnalysis.invalid("tray_shape_unavailable")
        );
    }

    public MoldingFunctionalAnalysis {
        cavityMask = cavityMask.copy();
        anvilShape = anvilShape == null
            ? MoldingAnvilShapeAnalysis.invalid("anvil_shape_unavailable")
            : anvilShape;
        trayShape = trayShape == null
            ? MoldingTrayShapeAnalysis.invalid("tray_shape_unavailable")
            : trayShape;
        if (cavityCount < 0 || cavityVolume < 0 || shellVolume < 0) {
            throw new IllegalArgumentException("Invalid molding functional analysis");
        }
    }

    @Override
    public MoldingVolumeMask cavityMask() {
        return this.cavityMask.copy();
    }

    public MoldingFunctionalAnalysis withTrayShape(MoldingTrayShapeAnalysis replacement) {
        return new MoldingFunctionalAnalysis(
            this.cavityMask,
            this.cavityCount,
            this.cavityVolume,
            this.shellVolume,
            this.internalDecoration,
            this.anvilShape,
            replacement
        );
    }

    public MoldingFunctionalAnalysis withAnvilShape(MoldingAnvilShapeAnalysis replacement) {
        return new MoldingFunctionalAnalysis(
            this.cavityMask,
            this.cavityCount,
            this.cavityVolume,
            this.shellVolume,
            this.internalDecoration,
            replacement,
            this.trayShape
        );
    }
}
