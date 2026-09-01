package dev.anvilcraft.plasticraft.molding.bake;

/** 制造网格中的六个面方向。 */
public enum MoldingFaceDirection {
    NEGATIVE_X(Axis.X, -1, 0, 0),
    POSITIVE_X(Axis.X, 1, 0, 0),
    NEGATIVE_Y(Axis.Y, 0, -1, 0),
    POSITIVE_Y(Axis.Y, 0, 1, 0),
    NEGATIVE_Z(Axis.Z, 0, 0, -1),
    POSITIVE_Z(Axis.Z, 0, 0, 1);

    private final Axis axis;
    private final int stepX;
    private final int stepY;
    private final int stepZ;

    MoldingFaceDirection(Axis axis, int stepX, int stepY, int stepZ) {
        this.axis = axis;
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
    }

    public Axis axis() {
        return this.axis;
    }

    public int stepX() {
        return this.stepX;
    }

    public int stepY() {
        return this.stepY;
    }

    public int stepZ() {
        return this.stepZ;
    }

    public MoldingFaceDirection opposite() {
        return values()[this.ordinal() ^ 1];
    }

    public enum Axis {
        X,
        Y,
        Z
    }
}
