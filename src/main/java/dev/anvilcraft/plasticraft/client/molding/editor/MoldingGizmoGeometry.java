package dev.anvilcraft.plasticraft.client.molding.editor;

import org.joml.Vector3d;

public final class MoldingGizmoGeometry {
    public static final int RING_SEGMENTS = 128;
    private static final double AXIS_LENGTH_PIXELS = 28.0D;
    private static final double SHAFT_WIDTH_PIXELS = 0.75D;
    private static final double ARROW_LENGTH_PIXELS = 6.0D;
    private static final double ARROW_RADIUS_PIXELS = 3.2D;
    private static final double HANDLE_HALF_SIZE_PIXELS = 2.4D;
    private static final double RING_RADIUS_PIXELS = 26.0D;
    private static final double RING_WIDTH_PIXELS = 0.9D;
    private static final double OUTER_RING_RADIUS_PIXELS = 27.5D;
    private static final double OUTER_RING_WIDTH_PIXELS = 1.1D;
    private static final double PIVOT_RADIUS_PIXELS = 2.8D;
    private static final double MIRROR_RADIUS_PIXELS = 4.5D;
    private static final double HIT_RADIUS_PIXELS = 4.5D;

    private MoldingGizmoGeometry() {
    }

    public static double axisLength(double worldUnitsPerPixel) {
        return AXIS_LENGTH_PIXELS * worldUnitsPerPixel;
    }

    public static double shaftWidth(double worldUnitsPerPixel) {
        return SHAFT_WIDTH_PIXELS * worldUnitsPerPixel;
    }

    public static double arrowLength(double worldUnitsPerPixel) {
        return ARROW_LENGTH_PIXELS * worldUnitsPerPixel;
    }

    public static double arrowRadius(double worldUnitsPerPixel) {
        return ARROW_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static double handleHalfSize(double worldUnitsPerPixel) {
        return HANDLE_HALF_SIZE_PIXELS * worldUnitsPerPixel;
    }

    public static double ringRadius(double worldUnitsPerPixel) {
        return RING_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static double ringWidth(double worldUnitsPerPixel) {
        return RING_WIDTH_PIXELS * worldUnitsPerPixel;
    }

    public static double outerRingRadius(double worldUnitsPerPixel) {
        return OUTER_RING_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static double outerRingWidth(double worldUnitsPerPixel) {
        return OUTER_RING_WIDTH_PIXELS * worldUnitsPerPixel;
    }

    public static double pivotRadius(double worldUnitsPerPixel) {
        return PIVOT_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static double mirrorRadius(double worldUnitsPerPixel) {
        return MIRROR_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static double hitRadius(double worldUnitsPerPixel) {
        return HIT_RADIUS_PIXELS * worldUnitsPerPixel;
    }

    public static Vector3d ringPoint(Vector3d origin, MoldingAxis axis, double radius, double angle) {
        double first = Math.cos(angle) * radius;
        double second = Math.sin(angle) * radius;
        return switch (axis) {
            case X -> new Vector3d(origin).add(0.0D, first, second);
            case Y -> new Vector3d(origin).add(first, 0.0D, second);
            case Z -> new Vector3d(origin).add(first, second, 0.0D);
        };
    }

    public static Vector3d viewRingPoint(
        Vector3d origin,
        Vector3d cameraDirection,
        double radius,
        double angle
    ) {
        Vector3d normal = new Vector3d(cameraDirection).normalize();
        Vector3d reference = Math.abs(normal.y) < 0.9D
            ? new Vector3d(0.0D, 1.0D, 0.0D)
            : new Vector3d(1.0D, 0.0D, 0.0D);
        Vector3d firstAxis = new Vector3d(normal).cross(reference).normalize();
        Vector3d secondAxis = new Vector3d(normal).cross(firstAxis).normalize();
        return new Vector3d(origin)
            .add(firstAxis.mul(radius * Math.cos(angle)))
            .add(secondAxis.mul(radius * Math.sin(angle)));
    }

    public static boolean isFrontFacing(Vector3d point, Vector3d origin, Vector3d cameraDirection) {
        return new Vector3d(point).sub(origin).dot(cameraDirection) >= -1.0E-8D;
    }
}
