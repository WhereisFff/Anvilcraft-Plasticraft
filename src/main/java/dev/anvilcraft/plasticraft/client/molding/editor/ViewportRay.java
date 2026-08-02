package dev.anvilcraft.plasticraft.client.molding.editor;

import org.joml.Vector3d;

public record ViewportRay(Vector3d origin, Vector3d direction) {
    public ViewportRay {
        origin = new Vector3d(origin);
        direction = new Vector3d(direction).normalize();
    }

    @Override
    public Vector3d origin() {
        return new Vector3d(this.origin);
    }

    @Override
    public Vector3d direction() {
        return new Vector3d(this.direction);
    }

    public Vector3d pointAt(double distance) {
        return new Vector3d(this.direction).mul(distance).add(this.origin);
    }
}
