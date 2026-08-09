package dev.anvilcraft.plasticraft.molding.bake;

/** 支架形状扫描的有界结果。 */
public record MoldingTrayShapeAnalysis(
    boolean valid,
    String reason,
    double bottomY,
    double topY,
    double height,
    int footprintArea,
    int supportedCellMask
) {
    public MoldingTrayShapeAnalysis {
        reason = reason == null ? "" : reason;
        if (!Double.isFinite(bottomY)
            || !Double.isFinite(topY)
            || !Double.isFinite(height)
            || bottomY < 0.0D
            || topY < bottomY
            || height < 0.0D
            || footprintArea < 0
            || (supportedCellMask & ~0x1FF) != 0) {
            throw new IllegalArgumentException("Invalid tray shape analysis");
        }
    }

    public boolean supportsCell(int x, int z) {
        if (x < 0 || x >= 3 || z < 0 || z >= 3) return false;
        return (this.supportedCellMask & 1 << (z * 3 + x)) != 0;
    }

    public static MoldingTrayShapeAnalysis invalid(String reason) {
        return new MoldingTrayShapeAnalysis(false, reason, 0, 0, 0, 0, 0);
    }
}
