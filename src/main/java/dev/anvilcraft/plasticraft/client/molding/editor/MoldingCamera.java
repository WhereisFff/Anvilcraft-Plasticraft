package dev.anvilcraft.plasticraft.client.molding.editor;

import org.joml.Matrix4f;
import org.joml.Vector3d;

public final class MoldingCamera {
    private static final double MIN_DISTANCE = 8.0D;
    private static final double MAX_DISTANCE = 320.0D;
    private static final double MIN_ORTHOGRAPHIC_SPAN = 8.0D;
    private static final double MAX_ORTHOGRAPHIC_SPAN = 192.0D;
    private static final double NEAR_PLANE = 0.1D;
    private static final double FAR_PLANE = 512.0D;
    private static final double FIELD_OF_VIEW = Math.toRadians(45.0D);

    private final Vector3d target = new Vector3d(24.0D, 24.0D, 24.0D);
    private MoldingViewPreset preset = MoldingViewPreset.PERSPECTIVE;
    private double yaw = Math.toRadians(-145.0D);
    private double pitch = Math.toRadians(28.0D);
    private double distance = 92.0D;
    private double orthographicSpan = 62.0D;

    public MoldingViewPreset preset() {
        return this.preset;
    }

    public Vector3d target() {
        return new Vector3d(this.target);
    }

    public void setPreset(MoldingViewPreset preset) {
        this.preset = preset;
    }

    public void reset() {
        this.target.set(24.0D, 24.0D, 24.0D);
        this.yaw = Math.toRadians(-145.0D);
        this.pitch = Math.toRadians(28.0D);
        this.distance = 92.0D;
        this.orthographicSpan = 62.0D;
        this.preset = MoldingViewPreset.PERSPECTIVE;
    }

    public void orbit(double deltaYaw, double deltaPitch) {
        this.preset = MoldingViewPreset.PERSPECTIVE;
        this.yaw += deltaYaw;
        this.pitch = Math.clamp(this.pitch + deltaPitch, Math.toRadians(-89.0D), Math.toRadians(89.0D));
    }

    public void pan(double horizontal, double vertical, int width, int height) {
        ViewportTransform transform = this.transform(width, height);
        ViewportRay center = transform.ray(width * 0.5D, height * 0.5D);
        Vector3d forward = center.direction();
        Vector3d up = cameraUp(this.preset, forward);
        Vector3d right = new Vector3d(forward).cross(up).normalize();
        up = new Vector3d(right).cross(forward).normalize();
        double scale = transform.worldUnitsPerPixel();
        this.target.add(right.mul(-horizontal * scale)).add(up.mul(vertical * scale));
    }

    public void zoom(double amount) {
        if (this.preset == MoldingViewPreset.PERSPECTIVE) {
            this.distance = Math.clamp(this.distance * Math.pow(1.1D, amount), MIN_DISTANCE, MAX_DISTANCE);
        } else {
            this.orthographicSpan = Math.clamp(
                this.orthographicSpan * Math.pow(1.1D, amount),
                MIN_ORTHOGRAPHIC_SPAN,
                MAX_ORTHOGRAPHIC_SPAN
            );
        }
    }

    public void focus(Vector3d center, double extent) {
        this.target.set(center);
        double boundedExtent = Math.max(extent, 2.0D);
        this.distance = Math.clamp(boundedExtent * 2.4D, MIN_DISTANCE, MAX_DISTANCE);
        this.orthographicSpan = Math.clamp(boundedExtent * 1.35D, MIN_ORTHOGRAPHIC_SPAN, MAX_ORTHOGRAPHIC_SPAN);
    }

    public ViewportTransform transform(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("Viewport dimensions must be positive");
        double aspect = (double) width / height;
        Vector3d position = position();
        Vector3d forward = new Vector3d(this.target).sub(position).normalize();
        Vector3d preferredUp = cameraUp(this.preset, forward);
        Vector3d right = new Vector3d(forward).cross(preferredUp).normalize();
        Vector3d up = new Vector3d(right).cross(forward).normalize();
        Matrix4f view = new Matrix4f().lookAt(
            (float) position.x,
            (float) position.y,
            (float) position.z,
            (float) this.target.x,
            (float) this.target.y,
            (float) this.target.z,
            (float) up.x,
            (float) up.y,
            (float) up.z
        );
        boolean perspective = this.preset == MoldingViewPreset.PERSPECTIVE;
        Matrix4f projection = perspective
            ? new Matrix4f().perspective((float) FIELD_OF_VIEW, (float) aspect, (float) NEAR_PLANE, (float) FAR_PLANE)
            : new Matrix4f().ortho(
                (float) (-this.orthographicSpan * aspect * 0.5D),
                (float) (this.orthographicSpan * aspect * 0.5D),
                (float) (-this.orthographicSpan * 0.5D),
                (float) (this.orthographicSpan * 0.5D),
                (float) NEAR_PLANE,
                (float) FAR_PLANE
            );
        return new ViewportTransform(
            position,
            forward,
            right,
            up,
            view,
            projection,
            perspective,
            FIELD_OF_VIEW,
            this.orthographicSpan,
            this.distance,
            aspect,
            width,
            height
        );
    }

    private Vector3d position() {
        return switch (this.preset) {
            case PERSPECTIVE -> new Vector3d(
                Math.cos(this.pitch) * Math.sin(this.yaw),
                Math.sin(this.pitch),
                Math.cos(this.pitch) * Math.cos(this.yaw)
            ).mul(this.distance).add(this.target);
            case FRONT -> new Vector3d(this.target).add(0.0D, 0.0D, -this.distance);
            case BACK -> new Vector3d(this.target).add(0.0D, 0.0D, this.distance);
            case LEFT -> new Vector3d(this.target).add(-this.distance, 0.0D, 0.0D);
            case RIGHT -> new Vector3d(this.target).add(this.distance, 0.0D, 0.0D);
            case TOP -> new Vector3d(this.target).add(0.0D, this.distance, 0.0D);
            case BOTTOM -> new Vector3d(this.target).add(0.0D, -this.distance, 0.0D);
        };
    }

    private static Vector3d cameraUp(MoldingViewPreset preset, Vector3d forward) {
        if (preset == MoldingViewPreset.TOP) return new Vector3d(0.0D, 0.0D, 1.0D);
        if (preset == MoldingViewPreset.BOTTOM) return new Vector3d(0.0D, 0.0D, -1.0D);
        if (Math.abs(forward.y) > 0.999D) return new Vector3d(0.0D, 0.0D, 1.0D);
        return new Vector3d(0.0D, 1.0D, 0.0D);
    }
}
