package dev.anvilcraft.plasticraft.molding.bake;

/** 塑料铁砧形状扫描的有界结果。 */
public record MoldingAnvilShapeAnalysis(
    boolean valid,
    boolean giant,
    String reason,
    int minimumY,
    int maximumYExclusive,
    int totalThickness,
    int bottomThickness,
    int middleThickness,
    int topThickness,
    int bottomArea,
    int middleArea,
    int topArea,
    int bottomWidth,
    int bottomDepth,
    int middleWidth,
    int middleDepth
) {
    public MoldingAnvilShapeAnalysis {
        reason = reason == null ? "" : reason;
        if (minimumY < 0 || maximumYExclusive < minimumY
            || totalThickness < 0 || bottomThickness < 0
            || middleThickness < 0 || topThickness < 0
            || bottomArea < 0 || middleArea < 0 || topArea < 0
            || bottomWidth < 0 || bottomDepth < 0
            || middleWidth < 0 || middleDepth < 0) {
            throw new IllegalArgumentException("Invalid anvil shape analysis");
        }
    }

    public static MoldingAnvilShapeAnalysis invalid(String reason) {
        return new MoldingAnvilShapeAnalysis(
            false, false, reason, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
        );
    }
}
