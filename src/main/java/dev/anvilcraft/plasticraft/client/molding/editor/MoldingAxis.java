package dev.anvilcraft.plasticraft.client.molding.editor;

import org.joml.Vector3d;

public enum MoldingAxis {
    X(new Vector3d(1.0D, 0.0D, 0.0D)),
    Y(new Vector3d(0.0D, 1.0D, 0.0D)),
    Z(new Vector3d(0.0D, 0.0D, 1.0D));

    private final Vector3d vector;

    MoldingAxis(Vector3d vector) {
        this.vector = vector;
    }

    public Vector3d vector() {
        return new Vector3d(this.vector);
    }
}
