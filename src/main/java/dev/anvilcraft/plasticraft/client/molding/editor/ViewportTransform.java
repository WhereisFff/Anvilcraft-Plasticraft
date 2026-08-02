package dev.anvilcraft.plasticraft.client.molding.editor;

import org.joml.Matrix4f;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.joml.Vector4d;

import java.util.Optional;

/** 相机的不可变语义快照，同时驱动矩阵、投影和 CPU 射线。 */
public final class ViewportTransform {
    private final Vector3d position;
    private final Vector3d forward;
    private final Vector3d right;
    private final Vector3d up;
    private final Matrix4f viewMatrix;
    private final Matrix4f projectionMatrix;
    private final boolean perspective;
    private final double verticalFieldOfView;
    private final double orthographicSpan;
    private final double targetDistance;
    private final double aspect;
    private final int width;
    private final int height;

    ViewportTransform(
        Vector3d position,
        Vector3d forward,
        Vector3d right,
        Vector3d up,
        Matrix4f viewMatrix,
        Matrix4f projectionMatrix,
        boolean perspective,
        double verticalFieldOfView,
        double orthographicSpan,
        double targetDistance,
        double aspect,
        int width,
        int height
    ) {
        this.position = new Vector3d(position);
        this.forward = new Vector3d(forward);
        this.right = new Vector3d(right);
        this.up = new Vector3d(up);
        this.viewMatrix = new Matrix4f(viewMatrix);
        this.projectionMatrix = new Matrix4f(projectionMatrix);
        this.perspective = perspective;
        this.verticalFieldOfView = verticalFieldOfView;
        this.orthographicSpan = orthographicSpan;
        this.targetDistance = targetDistance;
        this.aspect = aspect;
        this.width = width;
        this.height = height;
    }

    public Matrix4f viewMatrix() {
        return new Matrix4f(this.viewMatrix);
    }

    public Matrix4f projectionMatrix() {
        return new Matrix4f(this.projectionMatrix);
    }

    public Vector3d position() {
        return new Vector3d(this.position);
    }

    public boolean perspective() {
        return this.perspective;
    }

    public ViewportRay ray(double screenX, double screenY) {
        double normalizedX = 2.0D * screenX / this.width - 1.0D;
        double normalizedY = 1.0D - 2.0D * screenY / this.height;
        if (this.perspective) {
            double halfHeight = Math.tan(this.verticalFieldOfView * 0.5D);
            Vector3d direction = new Vector3d(this.forward)
                .add(new Vector3d(this.right).mul(normalizedX * this.aspect * halfHeight))
                .add(new Vector3d(this.up).mul(normalizedY * halfHeight))
                .normalize();
            return new ViewportRay(this.position, direction);
        }
        double halfHeight = this.orthographicSpan * 0.5D;
        Vector3d origin = new Vector3d(this.position)
            .add(new Vector3d(this.right).mul(normalizedX * this.aspect * halfHeight))
            .add(new Vector3d(this.up).mul(normalizedY * halfHeight));
        return new ViewportRay(origin, this.forward);
    }

    public Optional<Vector2d> project(Vector3d point) {
        Matrix4f viewProjection = new Matrix4f(this.projectionMatrix).mul(this.viewMatrix);
        Vector4d clip = new Vector4d(point, 1.0D).mul(viewProjection);
        if (Math.abs(clip.w) < 1.0E-9D || this.perspective && clip.w <= 0.0D) return Optional.empty();
        double normalizedX = clip.x / clip.w;
        double normalizedY = clip.y / clip.w;
        return Optional.of(new Vector2d(
            (normalizedX + 1.0D) * 0.5D * this.width,
            (1.0D - normalizedY) * 0.5D * this.height
        ));
    }

    public Vector3d directionInView(Vector3d direction) {
        return new Vector3d(
            direction.dot(this.right),
            -direction.dot(this.up),
            direction.dot(this.forward)
        );
    }

    public double worldUnitsPerPixel() {
        if (!this.perspective) return this.orthographicSpan / this.height;
        return 2.0D * Math.tan(this.verticalFieldOfView * 0.5D)
            * this.targetDistance / this.height;
    }

    public double worldUnitsPerPixel(Vector3d point) {
        if (!this.perspective) return this.orthographicSpan / this.height;
        double depth = Math.max(1.0E-6D, new Vector3d(point).sub(this.position).dot(this.forward));
        return 2.0D * Math.tan(this.verticalFieldOfView * 0.5D) * depth / this.height;
    }

    public Vector3d directionToCamera(Vector3d point) {
        if (!this.perspective) return new Vector3d(this.forward).negate();
        Vector3d direction = new Vector3d(this.position).sub(point);
        return direction.lengthSquared() < 1.0E-12D ? new Vector3d(this.forward).negate() : direction.normalize();
    }

}
