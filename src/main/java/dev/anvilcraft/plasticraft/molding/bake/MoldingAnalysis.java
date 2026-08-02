package dev.anvilcraft.plasticraft.molding.bake;

/** TODO-01 需要实时显示的制造派生统计。 */
public record MoldingAnalysis(
    int volume,
    int barrierFaceCount,
    int cavityCount,
    int cavityVolume,
    int surfaceQuadCount,
    int collisionBoxCount,
    boolean collisionComplexityExceeded,
    int minimumMeltMillibuckets,
    int clayBallRequirement
) {
    public boolean empty() {
        return this.volume == 0 && this.barrierFaceCount == 0;
    }
}
