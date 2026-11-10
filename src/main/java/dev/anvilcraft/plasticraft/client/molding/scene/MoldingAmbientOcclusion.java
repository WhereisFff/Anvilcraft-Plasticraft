package dev.anvilcraft.plasticraft.client.molding.scene;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 在实际模型面上采样局部半球遮蔽，避免旋转和零厚度模型受体素轮廓限制。 */
final class MoldingAmbientOcclusion {
    private static final int SAMPLES = 12;
    private static final double RADIUS = 6;
    private static final double SURFACE_OFFSET = 0.02;
    private static final Vector3d KEY_LIGHT = new Vector3d(-0.4, 0.85, -0.35).normalize();
    private final Node root;
    private final int maxSteps;

    MoldingAmbientOcclusion(List<List<Vector3d>> faces) {
        List<Face> occluders = new ArrayList<>();
        Set<List<Vector3d>> unique = new HashSet<>();
        for (List<Vector3d> face : faces) {
            List<Vector3d> key = face.stream().map(Vector3d::new)
                .sorted(Comparator.comparingDouble((Vector3d vertex) -> vertex.x)
                    .thenComparingDouble(vertex -> vertex.y).thenComparingDouble(vertex -> vertex.z))
                .toList();
            if (!unique.add(key)) {
                continue;
            }
            Face occluder = new Face(face);
            if (occluder.determinant > 1e-12) {
                occluders.add(occluder);
            }
        }
        this.root = new Node(occluders);
        this.maxSteps = Math.clamp((int) Math.sqrt(4096d / Math.max(1, faces.size())), 1, 8);
    }

    int subdivisions(double length) {
        return Math.clamp((int) Math.ceil(length / 4), 1, this.maxSteps);
    }

    int color(int color, Vector3d point, Vector3d normal) {
        double brightness = illumination(normal) * this.visibility(point, normal);
        int red = (int) Math.round(((color >> 16) & 255) * brightness);
        int green = (int) Math.round(((color >> 8) & 255) * brightness);
        int blue = (int) Math.round((color & 255) * brightness);
        return color & 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static double illumination(Vector3d normal) {
        // 有方向差异的环境光保留背光面的形状，主光仍按连续法线产生明暗。
        double ambient = 0.46 + 0.12 * normal.y + 0.08 * normal.x + 0.025 * normal.z;
        return Math.clamp(ambient + 0.36 * Math.max(0, normal.dot(KEY_LIGHT)), 0, 1);
    }

    private double visibility(Vector3d point, Vector3d normal) {
        Vector3d tangent = Math.abs(normal.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        tangent.cross(normal).normalize();
        Vector3d bitangent = new Vector3d(normal).cross(tangent);
        Vector3d origin = new Vector3d(point).fma(SURFACE_OFFSET, normal);
        double occlusion = 0;
        for (int sample = 0; sample < SAMPLES; sample++) {
            double radius = Math.sqrt((sample + 0.5) / SAMPLES);
            double angle = sample * Math.PI * (3 - Math.sqrt(5));
            Vector3d direction = new Vector3d(normal).mul(Math.sqrt(1 - radius * radius))
                .fma(radius * Math.cos(angle), tangent)
                .fma(radius * Math.sin(angle), bitangent);
            double distance = this.root.intersection(origin, direction, RADIUS);
            if (distance < RADIUS) {
                double attenuation = 1 - distance / RADIUS;
                occlusion += attenuation * attenuation;
            }
        }
        return 1 - 0.65 * occlusion / SAMPLES;
    }

    private static final class Face {
        private final Vector3d origin;
        private final Vector3d u;
        private final Vector3d v;
        private final Vector3d normal;
        private final Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        private final Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        private final double uu;
        private final double uv;
        private final double vv;
        private final double determinant;

        private Face(List<Vector3d> vertices) {
            this.origin = new Vector3d(vertices.get(0));
            this.u = new Vector3d(vertices.get(1)).sub(this.origin);
            this.v = new Vector3d(vertices.get(3)).sub(this.origin);
            this.normal = new Vector3d(this.u).cross(this.v);
            this.uu = this.u.lengthSquared();
            this.uv = this.u.dot(this.v);
            this.vv = this.v.lengthSquared();
            this.determinant = this.uu * this.vv - this.uv * this.uv;
            for (Vector3d vertex : vertices) {
                this.min.min(vertex);
                this.max.max(vertex);
            }
        }

        private double intersection(Vector3d rayOrigin, Vector3d direction, double limit) {
            double denominator = this.normal.dot(direction);
            if (Math.abs(denominator) < 1e-10) {
                return limit;
            }
            double dx = this.origin.x - rayOrigin.x;
            double dy = this.origin.y - rayOrigin.y;
            double dz = this.origin.z - rayOrigin.z;
            double distance = (dx * this.normal.x + dy * this.normal.y + dz * this.normal.z) / denominator;
            if (distance <= SURFACE_OFFSET * 0.1 || distance >= limit) {
                return limit;
            }
            dx = direction.x * distance - dx;
            dy = direction.y * distance - dy;
            dz = direction.z * distance - dz;
            double du = dx * this.u.x + dy * this.u.y + dz * this.u.z;
            double dv = dx * this.v.x + dy * this.v.y + dz * this.v.z;
            double u = (du * this.vv - dv * this.uv) / this.determinant;
            double v = (dv * this.uu - du * this.uv) / this.determinant;
            return u >= -1e-6 && u <= 1 + 1e-6 && v >= -1e-6 && v <= 1 + 1e-6 ? distance : limit;
        }
    }

    private static final class Node {
        private final Vector3d min = new Vector3d(Double.POSITIVE_INFINITY);
        private final Vector3d max = new Vector3d(Double.NEGATIVE_INFINITY);
        private final List<Face> faces;
        private final List<Node> children;

        private Node(List<Face> faces) {
            for (Face face : faces) {
                this.min.min(face.min);
                this.max.max(face.max);
            }
            if (faces.size() <= 8) {
                this.faces = List.copyOf(faces);
                this.children = List.of();
            } else {
                Vector3d size = new Vector3d(this.max).sub(this.min);
                int axis = size.x >= size.y && size.x >= size.z ? 0 : size.y >= size.z ? 1 : 2;
                faces.sort(Comparator.comparingDouble(face -> face.min.get(axis) + face.max.get(axis)));
                int middle = faces.size() / 2;
                this.faces = List.of();
                this.children = List.of(new Node(faces.subList(0, middle)), new Node(faces.subList(middle, faces.size())));
            }
        }

        private double intersection(Vector3d origin, Vector3d direction, double limit) {
            double near = 0;
            double far = limit;
            for (int axis = 0; axis < 3; axis++) {
                double component = direction.get(axis);
                double position = origin.get(axis);
                if (Math.abs(component) < 1e-10) {
                    if (position < this.min.get(axis) || position > this.max.get(axis)) {
                        return limit;
                    }
                } else {
                    double first = (this.min.get(axis) - position) / component;
                    double second = (this.max.get(axis) - position) / component;
                    near = Math.max(near, Math.min(first, second));
                    far = Math.min(far, Math.max(first, second));
                    if (near > far) {
                        return limit;
                    }
                }
            }
            if (near >= limit) {
                return limit;
            }
            double closest = limit;
            for (Face face : this.faces) {
                closest = face.intersection(origin, direction, closest);
            }
            for (Node child : this.children) {
                closest = child.intersection(origin, direction, closest);
            }
            return closest;
        }
    }
}
