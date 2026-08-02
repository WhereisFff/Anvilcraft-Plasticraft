package dev.anvilcraft.plasticraft.client.molding.scene;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** 计算工作空间立方体在当前观察方向下的投影外轮廓。 */
public final class MoldingWorkspaceOutline {
    public static final double MIN = 0.0D;
    public static final double MAX = 48.0D;
    private static final double VISIBILITY_EPSILON = 1.0E-9D;
    private static final Vector3d[] CORNERS = {
        new Vector3d(MIN, MIN, MIN), new Vector3d(MAX, MIN, MIN),
        new Vector3d(MIN, MAX, MIN), new Vector3d(MAX, MAX, MIN),
        new Vector3d(MIN, MIN, MAX), new Vector3d(MAX, MIN, MAX),
        new Vector3d(MIN, MAX, MAX), new Vector3d(MAX, MAX, MAX)
    };
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
        return silhouetteEdges(new Vector3d(), false, directionToCamera);
    }

    public static List<Edge> silhouetteEdges(
        Vector3d cameraPosition,
        boolean perspective,
        Vector3d directionToCamera
    ) {
        if (!perspective && directionToCamera.lengthSquared() < VISIBILITY_EPSILON) return List.of();
        List<Edge> result = new ArrayList<>(6);
        for (EdgeDefinition edge : EDGES) {
            boolean firstVisible = edge.firstFace.visibleFrom(cameraPosition, perspective, directionToCamera);
            boolean secondVisible = edge.secondFace.visibleFrom(cameraPosition, perspective, directionToCamera);
            if (firstVisible != secondVisible) {
                result.add(new Edge(CORNERS[edge.firstCorner], CORNERS[edge.secondCorner]));
            }
        }
        return List.copyOf(result);
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
        X_MIN(new Vector3d(-1.0D, 0.0D, 0.0D), new Vector3d(MIN, 24.0D, 24.0D)),
        X_MAX(new Vector3d(1.0D, 0.0D, 0.0D), new Vector3d(MAX, 24.0D, 24.0D)),
        Y_MIN(new Vector3d(0.0D, -1.0D, 0.0D), new Vector3d(24.0D, MIN, 24.0D)),
        Y_MAX(new Vector3d(0.0D, 1.0D, 0.0D), new Vector3d(24.0D, MAX, 24.0D)),
        Z_MIN(new Vector3d(0.0D, 0.0D, -1.0D), new Vector3d(24.0D, 24.0D, MIN)),
        Z_MAX(new Vector3d(0.0D, 0.0D, 1.0D), new Vector3d(24.0D, 24.0D, MAX));

        private final Vector3d normal;
        private final Vector3d point;

        Face(Vector3d normal, Vector3d point) {
            this.normal = normal;
            this.point = point;
        }

        private boolean visibleFrom(
            Vector3d cameraPosition,
            boolean perspective,
            Vector3d directionToCamera
        ) {
            Vector3d direction = perspective
                ? new Vector3d(cameraPosition).sub(this.point)
                : directionToCamera;
            return this.normal.dot(direction) > VISIBILITY_EPSILON;
        }
    }
}
