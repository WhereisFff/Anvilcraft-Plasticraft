package dev.anvilcraft.plasticraft.molding.bake;

/** 炼药锅开顶内腔扫描的有界结果。 */
public record MoldingCauldronShapeAnalysis(
    boolean valid,
    String reason,
    MoldingVolumeMask cavityMask,
    int cavityVolume,
    int openingArea,
    int depth,
    boolean large
) {
    /** 无效结果通常不携带几何；负尺寸空腔组合的中间结果会保留已扫描到的开顶内腔。 */
    private static final MoldingVolumeMask NO_CAVITY = new MoldingVolumeMask(1, 1, 1);

    public MoldingCauldronShapeAnalysis {
        reason = reason == null ? "" : reason;
        cavityMask = cavityMask.copy();
        if (cavityVolume < 0 || openingArea < 0 || depth < 0) {
            throw new IllegalArgumentException("Invalid cauldron shape analysis");
        }
    }

    @Override
    public MoldingVolumeMask cavityMask() {
        return this.cavityMask.copy();
    }

    public static MoldingCauldronShapeAnalysis invalid(String reason) {
        return new MoldingCauldronShapeAnalysis(false, reason, NO_CAVITY, 0, 0, 0, false);
    }

    static MoldingCauldronShapeAnalysis provisional(
        String reason,
        MoldingVolumeMask cavityMask,
        int cavityVolume,
        int openingArea,
        int depth
    ) {
        return new MoldingCauldronShapeAnalysis(
            false,
            reason,
            cavityMask,
            cavityVolume,
            openingArea,
            depth,
            false
        );
    }
}
