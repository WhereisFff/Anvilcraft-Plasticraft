package dev.anvilcraft.plasticraft.client.molding.scene;

import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** 计算工作空间长方体在当前观察方向下的投影外轮廓。 */
public final class MoldingWorkspaceOutline {
    public static final double MIN = 0.0D;
    public static final double MAX = 48.0D;
    public static final MoldingModelBounds FULL_BOUNDS = new MoldingModelBounds(
        new MoldingVec3(MIN, MIN, MIN),
        new MoldingVec3(MAX, MAX, MAX)
    );
    private static final double VISIBILITY_EPSILON = 1.0E-9D;
    private static final EdgeDefinition[] EDGES = {
        new EdgeDefinition(0, 1, Face.Y_MIN, Face.Z_MIN),
        new EdgeDefinition(0, 2, Face.X_MIN, Face.Z_MIN),
        new EdgeDefinition(0, 4, Face.X_MIN, Face.Y_MIN),
        new EdgeDefinition(1, 3, Face.X_MAX, Face.Z_MIN),
        new EdgeDefinition(1, 5, Face.X_MAX, Face.Y_MIN),
        new EdgeDefinition(2, 3, Face.Y_MAX, Face.Z_MIN),
        new EdgeDefinition(2, 6, Face.X_MIN, Face.Y_MAX),
        new EdgeDefinition(3, 7, Face.X_MAX, Face.Y_MAX),
        new EdgeDefinition(4, 5, Face.Y_MIN, Face.Z_MAX),
        new EdgeDefinition(4, 6, Face.X_MIN, Face.Z_MAX),
        new EdgeDefinition(5, 7, Face.X_MAX, Face.Z_MAX),
        new EdgeDefinition(6, 7, Face.Y_MAX, Face.Z_MAX)
    };

    private MoldingWorkspaceOutline() {
    }

    public static List<Edge> silhouetteEdges(Vector3d directionToCamera) {
        return silhouetteEdges(directionToCamera, FULL_BOUNDS);
    }

    public static List<Edge> silhouetteEdges(
        Vector3d directionToCamera,
        double minimum,
        double maximum
    ) {
        return silhouetteEdges(directionToCamera, cubeBounds(minimum, maximum));
    }

    public static List<Edge> silhouetteEdges(
        Vector3d directionToCamera,
        MoldingModelBounds bounds
    ) {
        return silhouetteEdges(new Vector3d(), false, directionToCamera, bounds);
    }

    public static List<Edge> silhouetteEdges(
        Vector3d cameraPosition,
        boolean perspective,
        Vector3d directionToCamera
    ) {
        return silhouetteEdges(cameraPosition, perspective, directionToCamera, FULL_BOUNDS);
    }

    public static List<Edge> silhouetteEdges(
        Vector3d cameraPosition,
        boolean perspective,
        Vector3d directionToCamera,
        double minimum,
        double maximum
    ) {
        return silhouetteEdges(cameraPosition, perspective, directionToCamera, cubeBounds(minimum, maximum));
    }

    public static List<Edge> silhouetteEdges(
        Vector3d cameraPosition,
        boolean perspective,
        Vector3d directionToCamera,
        MoldingModelBounds bounds
    ) {
        if (!perspective && directionToCamera.lengthSquared() < VISIBILITY_EPSILON) return List.of();
        Vector3d[] corners = corners(bounds);
        List<Edge> result = new ArrayList<>(6);
        for (EdgeDefinition edge : EDGES) {
            boolean firstVisible = edge.firstFace.visibleFrom(
                cameraPosition, perspective, directionToCamera, bounds
            );
            boolean secondVisible = edge.secondFace.visibleFrom(
                cameraPosition, perspective, directionToCamera, bounds
            );
            if (firstVisible != secondVisible) {
                result.add(new Edge(corners[edge.firstCorner], corners[edge.secondCorner]));
            }
        }
        return List.copyOf(result);
    }

    private static MoldingModelBounds cubeBounds(double minimum, double maximum) {
        if (minimum >= maximum) throw new IllegalArgumentException("Workspace bounds are inverted");
        return new MoldingModelBounds(
            new MoldingVec3(minimum, minimum, minimum),
            new MoldingVec3(maximum, maximum, maximum)
        );
    }

    private static Vector3d[] corners(MoldingModelBounds bounds) {
        MoldingVec3 minimum = bounds.minimum();
        MoldingVec3 maximum = bounds.maximum();
        return new Vector3d[] {
            new Vector3d(minimum.x(), minimum.y(), minimum.z()),
            new Vector3d(maximum.x(), minimum.y(), minimum.z()),
            new Vector3d(minimum.x(), maximum.y(), minimum.z()),
            new Vector3d(maximum.x(), maximum.y(), minimum.z()),
            new Vector3d(minimum.x(), minimum.y(), maximum.z()),
            new Vector3d(maximum.x(), minimum.y(), maximum.z()),
            new Vector3d(minimum.x(), maximum.y(), maximum.z()),
            new Vector3d(maximum.x(), maximum.y(), maximum.z())
        };
    }

    public record Edge(Vector3d from, Vector3d to) {
        public Edge {
            from = new Vector3d(from);
            to = new Vector3d(to);
        }

        @Override
        public Vector3d from() {
            return new Vector3d(this.from);
        }

        @Override
        public Vector3d to() {
            return new Vector3d(this.to);
        }
    }

    private record EdgeDefinition(int firstCorner, int secondCorner, Face firstFace, Face secondFace) {
    }

    private enum Face {
        X_MIN(new Vector3d(-1.0D, 0.0D, 0.0D), 0, false),
        X_MAX(new Vector3d(1.0D, 0.0D, 0.0D), 0, true),
        Y_MIN(new Vector3d(0.0D, -1.0D, 0.0D), 1, false),
        Y_MAX(new Vector3d(0.0D, 1.0D, 0.0D), 1, true),
        Z_MIN(new Vector3d(0.0D, 0.0D, -1.0D), 2, false),
        Z_MAX(new Vector3d(0.0D, 0.0D, 1.0D), 2, true);

        private final Vector3d normal;
        private final int axis;
        private final boolean maximumFace;

        Face(Vector3d normal, int axis, boolean maximumFace) {
            this.normal = normal;
            this.axis = axis;
            this.maximumFace = maximumFace;
        }

        private boolean visibleFrom(
            Vector3d cameraPosition,
            boolean perspective,
            Vector3d directionToCamera,
            MoldingModelBounds bounds
        ) {
            MoldingVec3 minimum = bounds.minimum();
            MoldingVec3 maximum = bounds.maximum();
            double centerX = (minimum.x() + maximum.x()) * 0.5D;
            double centerY = (minimum.y() + maximum.y()) * 0.5D;
            double centerZ = (minimum.z() + maximum.z()) * 0.5D;
            Vector3d point = switch (this.axis) {
                case 0 -> new Vector3d(this.maximumFace ? maximum.x() : minimum.x(), centerY, centerZ);
                case 1 -> new Vector3d(centerX, this.maximumFace ? maximum.y() : minimum.y(), centerZ);
                case 2 -> new Vector3d(centerX, centerY, this.maximumFace ? maximum.z() : minimum.z());
                default -> throw new IllegalStateException("Unknown workspace face axis");
            };
            Vector3d direction = perspective
                ? new Vector3d(cameraPosition).sub(point)
                : directionToCamera;
            return this.normal.dot(direction) > VISIBILITY_EPSILON;
        }
    }
}
