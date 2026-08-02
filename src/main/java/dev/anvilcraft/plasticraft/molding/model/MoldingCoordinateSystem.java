package dev.anvilcraft.plasticraft.molding.model;

/** 固定世界 X/Y/Z 模型、参考网格和数值属性共用的坐标边界与原点。 */
public final class MoldingCoordinateSystem {
    public static final double WORKSPACE_MIN = 0.0D;
    public static final double WORKSPACE_MAX = 48.0D;
    public static final double GRID_MIN = 16.0D;
    public static final double GRID_MAX = 32.0D;
    public static final double DISPLAY_MIN = WORKSPACE_MIN - GRID_MIN;
    public static final double DISPLAY_MAX = WORKSPACE_MAX - GRID_MIN;
    public static final MoldingVec3 ORIGIN = new MoldingVec3(GRID_MIN, GRID_MIN, GRID_MIN);

    private MoldingCoordinateSystem() {
    }

    public static MoldingVec3 toDisplay(MoldingVec3 modelPoint) {
        return modelPoint.subtract(ORIGIN);
    }

    public static MoldingVec3 toModel(MoldingVec3 displayPoint) {
        return displayPoint.add(ORIGIN);
    }
}
