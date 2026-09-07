package dev.anvilcraft.plasticraft.entity.collision;

import dev.anvilcraft.plasticraft.entity.PlasticEntityOrientation;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** 动态塑料实体使用的不可变凸碰撞体，保留源 Cube 的连续斜面和棱边。 */
public final class PlasticConvexShape {
    private static final double AXIS_EPSILON = 1.0E-10D;
    private static final double PARALLEL_EPSILON = 1.0E-8D;
    private static final int[][] BOX_FACES = {
        {0, 2, 6, 4}, {1, 5, 7, 3},
        {0, 4, 5, 1}, {2, 3, 7, 6},
        {0, 1, 3, 2}, {4, 6, 7, 5}
    };

    private final Geometry geometry;
    private final Vec3 translation;
    private final AABB bounds;
    private volatile List<Vec3> translatedVertices;
    private volatile List<Face> translatedFaces;
    private volatile List<Edge> translatedEdges;

    private PlasticConvexShape(
        List<Vec3> vertices,
        List<Face> faces,
        List<Edge> edges
    ) {
        this(new Geometry(vertices, faces, edges), Vec3.ZERO);
    }

    private PlasticConvexShape(Geometry geometry, Vec3 translation) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.translation = Objects.requireNonNull(translation, "translation");
        this.bounds = geometry.bounds.move(translation);
    }

    public static PlasticConvexShape fromMolding(MoldingConvexHull hull, double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Convex collision scale must be positive");
        }
        List<Vec3> vertices = hull.vertices().stream()
            .map(vertex -> toVec3(vertex, scale))
            .toList();
        List<Face> faces = indexedFaces(vertices, hull.faces());
        List<Edge> edges = indexedEdges(vertices, hull.faces());
        return new PlasticConvexShape(vertices, faces, edges);
    }

    public static PlasticConvexShape box(AABB box) {
        Objects.requireNonNull(box, "box");
        List<Vec3> vertices = new ArrayList<>(8);
        for (int corner = 0; corner < 8; corner++) {
            vertices.add(new Vec3(
                (corner & 1) == 0 ? box.minX : box.maxX,
                (corner & 2) == 0 ? box.minY : box.maxY,
                (corner & 4) == 0 ? box.minZ : box.maxZ
            ));
        }
        List<Edge> edges = new ArrayList<>(12);
        List<Face> faces = new ArrayList<>(6);
        Vec3[] normals = {
            new Vec3(-1.0D, 0.0D, 0.0D), new Vec3(1.0D, 0.0D, 0.0D),
            new Vec3(0.0D, -1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D),
            new Vec3(0.0D, 0.0D, -1.0D), new Vec3(0.0D, 0.0D, 1.0D)
        };
        Set<Long> seen = new HashSet<>();
        for (int faceIndex = 0; faceIndex < BOX_FACES.length; faceIndex++) {
            int[] face = BOX_FACES[faceIndex];
            List<Vec3> faceVertices = new ArrayList<>(face.length);
            for (int index = 0; index < face.length; index++) {
                faceVertices.add(vertices.get(face[index]));
                addIndexedEdge(vertices, face[index], face[(index + 1) % face.length], seen, edges);
            }
            faces.add(new Face(faceVertices, normals[faceIndex]));
        }
        return new PlasticConvexShape(vertices, faces, edges);
    }

    public List<Vec3> vertices() {
        if (this.translation.equals(Vec3.ZERO)) return this.geometry.vertices;
        List<Vec3> cached = this.translatedVertices;
        if (cached != null) return cached;
        cached = this.geometry.vertices.stream().map(vertex -> vertex.add(this.translation)).toList();
        this.translatedVertices = cached;
        return cached;
    }

    public List<Vec3> faceNormals() {
        return this.geometry.faceNormals;
    }

    /** 返回按外法线描述的真实凸体面。 */
    public List<Face> faces() {
        if (this.translation.equals(Vec3.ZERO)) return this.geometry.faces;
        List<Face> cached = this.translatedFaces;
        if (cached != null) return cached;
        cached = this.geometry.faces.stream().map(face -> new Face(
            face.vertices().stream().map(vertex -> vertex.add(this.translation)).toList(),
            face.normal()
        )).toList();
        this.translatedFaces = cached;
        return cached;
    }

    public List<Vec3> edgeDirections() {
        return this.geometry.edgeDirections;
    }

    public List<Edge> edges() {
        if (this.translation.equals(Vec3.ZERO)) return this.geometry.edges;
        List<Edge> cached = this.translatedEdges;
        if (cached != null) return cached;
        cached = this.geometry.edges.stream().map(edge -> new Edge(
            edge.start().add(this.translation),
            edge.end().add(this.translation)
        )).toList();
        this.translatedEdges = cached;
        return cached;
    }

    public AABB bounds() {
        return this.bounds;
    }

    public Projection project(Vec3 axis) {
        Objects.requireNonNull(axis, "axis");
        Projection local = this.geometry.projections.computeIfAbsent(axis, this.geometry::project);
        double offset = this.translation.dot(axis);
        return new Projection(local.minimum + offset, local.maximum + offset);
    }

    public boolean isAxisAlignedBox() {
        return this.geometry.axisAlignedBox;
    }

    /** 返回点是否位于该闭凸体内。 */
    public boolean contains(Vec3 point, double epsilon) {
        Objects.requireNonNull(point, "point");
        if (!Double.isFinite(epsilon) || epsilon < 0.0D) {
            throw new IllegalArgumentException("Point containment epsilon must be finite and non-negative");
        }
        for (Face face : this.faces()) {
            if (face.signedDistance(point) > epsilon) return false;
        }
        return true;
    }

    /** 按各面的半空间裁剪线段，起点已在实体内部时立即命中。 */
    public Optional<Vec3> clip(Vec3 start, Vec3 end) {
        Vec3 movement = end.subtract(start);
        double enter = 0.0D;
        double exit = 1.0D;
        for (Face face : this.faces()) {
            double distance = face.signedDistance(start);
            double speed = face.normal().dot(movement);
            if (Math.abs(speed) <= AXIS_EPSILON) {
                if (distance > AXIS_EPSILON) return Optional.empty();
                continue;
            }
            double fraction = -distance / speed;
            if (speed < 0.0D) {
                enter = Math.max(enter, fraction);
            } else {
                exit = Math.min(exit, fraction);
            }
            if (enter > exit) return Optional.empty();
        }
        return Optional.of(start.add(movement.scale(enter)));
    }

    List<Vec3> separatingAxes(PlasticConvexShape other) {
        Objects.requireNonNull(other, "other");
        return this.geometry.separatingAxes.computeIfAbsent(
            other.geometry.axisBasis,
            this.geometry.axisBasis::combine
        );
    }

    /** 仅在当前凸体完整包含目标凸体时返回 true。 */
    public boolean contains(PlasticConvexShape other, double epsilon) {
        Objects.requireNonNull(other, "other");
        if (!Double.isFinite(epsilon) || epsilon < 0.0D) {
            throw new IllegalArgumentException("Containment epsilon must be finite and non-negative");
        }
        if (other.bounds.minX < this.bounds.minX - epsilon
            || other.bounds.minY < this.bounds.minY - epsilon
            || other.bounds.minZ < this.bounds.minZ - epsilon
            || other.bounds.maxX > this.bounds.maxX + epsilon
            || other.bounds.maxY > this.bounds.maxY + epsilon
            || other.bounds.maxZ > this.bounds.maxZ + epsilon) {
            return false;
        }
        for (Vec3 normal : this.faceNormals()) {
            Projection container = this.project(normal);
            Projection candidate = other.project(normal);
            if (candidate.minimum < container.minimum - epsilon
                || candidate.maximum > container.maximum + epsilon) {
                return false;
            }
        }
        return true;
    }

    public PlasticConvexShape rotate(PlasticEntityOrientation orientation, Vec3 pivot) {
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(pivot, "pivot");
        List<Vec3> transformedVertices = this.vertices().stream()
            .map(vertex -> pivot.add(rotateVector(vertex.subtract(pivot), orientation)))
            .toList();
        List<Face> transformedFaces = this.faces().stream().map(face -> new Face(
            face.vertices().stream()
                .map(vertex -> pivot.add(rotateVector(vertex.subtract(pivot), orientation)))
                .toList(),
            rotateVector(face.normal(), orientation)
        )).toList();
        List<Edge> transformedEdges = this.edges().stream()
            .map(edge -> new Edge(
                pivot.add(rotateVector(edge.start().subtract(pivot), orientation)),
                pivot.add(rotateVector(edge.end().subtract(pivot), orientation))
            ))
            .toList();
        return new PlasticConvexShape(transformedVertices, transformedFaces, transformedEdges);
    }

    public PlasticConvexShape rotateAroundAxis(Direction.Axis axis, double radians, Vec3 pivot) {
        Objects.requireNonNull(axis, "axis");
        Objects.requireNonNull(pivot, "pivot");
        if (!Double.isFinite(radians)) {
            throw new IllegalArgumentException("Convex collision rotation must be finite");
        }
        if (radians == 0.0D) return this;
        List<Vec3> transformedVertices = this.vertices().stream()
            .map(vertex -> pivot.add(rotateVectorAroundAxis(vertex.subtract(pivot), axis, radians)))
            .toList();
        List<Face> transformedFaces = this.faces().stream().map(face -> new Face(
            face.vertices().stream()
                .map(vertex -> pivot.add(rotateVectorAroundAxis(vertex.subtract(pivot), axis, radians)))
                .toList(),
            rotateVectorAroundAxis(face.normal(), axis, radians)
        )).toList();
        List<Edge> transformedEdges = this.edges().stream()
            .map(edge -> new Edge(
                pivot.add(rotateVectorAroundAxis(edge.start().subtract(pivot), axis, radians)),
                pivot.add(rotateVectorAroundAxis(edge.end().subtract(pivot), axis, radians))
            ))
            .toList();
        return new PlasticConvexShape(transformedVertices, transformedFaces, transformedEdges);
    }

    public PlasticConvexShape move(Vec3 movement) {
        Objects.requireNonNull(movement, "movement");
        if (movement.equals(Vec3.ZERO)) return this;
        return new PlasticConvexShape(this.geometry, this.translation.add(movement));
    }

    private static List<Face> indexedFaces(List<Vec3> vertices, List<MoldingConvexFace> faces) {
        return faces.stream().map(face -> new Face(
            face.vertices().stream().map(vertices::get).toList(),
            toVec3(face.normal(), 1.0D)
        )).toList();
    }

    private static List<Edge> indexedEdges(List<Vec3> vertices, List<MoldingConvexFace> faces) {
        List<Edge> edges = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (MoldingConvexFace face : faces) {
            List<Integer> indices = face.vertices();
            for (int index = 0; index < indices.size(); index++) {
                addIndexedEdge(
                    vertices,
                    indices.get(index),
                    indices.get((index + 1) % indices.size()),
                    seen,
                    edges
                );
            }
        }
        return edges;
    }

    private static void addIndexedEdge(
        List<Vec3> vertices,
        int first,
        int second,
        Set<Long> seen,
        List<Edge> output
    ) {
        int minimum = Math.min(first, second);
        int maximum = Math.max(first, second);
        long key = (long) minimum << 32 | Integer.toUnsignedLong(maximum);
        if (seen.add(key)) output.add(new Edge(vertices.get(first), vertices.get(second)));
    }

    private static List<Vec3> distinctAxes(List<Vec3> candidates) {
        List<Vec3> result = new ArrayList<>();
        for (Vec3 candidate : candidates) {
            double lengthSquared = candidate.lengthSqr();
            if (lengthSquared <= AXIS_EPSILON) continue;
            Vec3 normalized = candidate.scale(1.0D / Math.sqrt(lengthSquared));
            if (result.stream().noneMatch(axis -> Math.abs(axis.dot(normalized)) >= 1.0D - PARALLEL_EPSILON)) {
                result.add(normalized);
            }
        }
        return List.copyOf(result);
    }

    private static void addDistinctAxis(List<Vec3> axes, Vec3 candidate) {
        double lengthSquared = candidate.lengthSqr();
        if (lengthSquared <= AXIS_EPSILON) return;
        Vec3 normalized = candidate.scale(1.0D / Math.sqrt(lengthSquared));
        for (Vec3 axis : axes) {
            if (Math.abs(axis.dot(normalized)) >= 1.0D - PARALLEL_EPSILON) return;
        }
        axes.add(normalized);
    }

    private static Vec3 rotateVector(Vec3 vector, PlasticEntityOrientation orientation) {
        Direction xAxis = orientation.orthogonalAxis();
        Direction yAxis = orientation.attachmentFace();
        Direction zAxis = orientation.longAxis();
        return new Vec3(
            vector.x * xAxis.getStepX() + vector.y * yAxis.getStepX() + vector.z * zAxis.getStepX(),
            vector.x * xAxis.getStepY() + vector.y * yAxis.getStepY() + vector.z * zAxis.getStepY(),
            vector.x * xAxis.getStepZ() + vector.y * yAxis.getStepZ() + vector.z * zAxis.getStepZ()
        );
    }

    private static Vec3 rotateVectorAroundAxis(Vec3 vector, Direction.Axis axis, double radians) {
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        return switch (axis) {
            case X -> new Vec3(
                vector.x,
                vector.y * cos - vector.z * sin,
                vector.y * sin + vector.z * cos
            );
            case Y -> new Vec3(
                vector.x * cos + vector.z * sin,
                vector.y,
                -vector.x * sin + vector.z * cos
            );
            case Z -> new Vec3(
                vector.x * cos - vector.y * sin,
                vector.x * sin + vector.y * cos,
                vector.z
            );
        };
    }

    private static Vec3 toVec3(MoldingVec3 vector, double scale) {
        return new Vec3(vector.x() * scale, vector.y() * scale, vector.z() * scale);
    }

    private static AABB bounds(List<Vec3> vertices) {
        Vec3 first = vertices.getFirst();
        double minX = first.x;
        double minY = first.y;
        double minZ = first.z;
        double maxX = first.x;
        double maxY = first.y;
        double maxZ = first.z;
        for (Vec3 vertex : vertices) {
            minX = Math.min(minX, vertex.x);
            minY = Math.min(minY, vertex.y);
            minZ = Math.min(minZ, vertex.z);
            maxX = Math.max(maxX, vertex.x);
            maxY = Math.max(maxY, vertex.y);
            maxZ = Math.max(maxZ, vertex.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean isAxisAlignedBox(List<Vec3> vertices, AABB bounds) {
        if (vertices.size() != 8) return false;
        boolean[] corners = new boolean[8];
        for (Vec3 vertex : vertices) {
            int x = endpoint(vertex.x, bounds.minX, bounds.maxX);
            int y = endpoint(vertex.y, bounds.minY, bounds.maxY);
            int z = endpoint(vertex.z, bounds.minZ, bounds.maxZ);
            if (x < 0 || y < 0 || z < 0) return false;
            corners[x | y << 1 | z << 2] = true;
        }
        for (boolean corner : corners) {
            if (!corner) return false;
        }
        return true;
    }

    private static int endpoint(double value, double minimum, double maximum) {
        if (Math.abs(value - minimum) <= AXIS_EPSILON) return 0;
        if (Math.abs(value - maximum) <= AXIS_EPSILON) return 1;
        return -1;
    }

    private static final class Geometry {
        private final List<Vec3> vertices;
        private final List<Face> faces;
        private final List<Vec3> faceNormals;
        private final List<Vec3> edgeDirections;
        private final List<Edge> edges;
        private final AABB bounds;
        private final boolean axisAlignedBox;
        private final AxisBasis axisBasis;
        private final Map<Vec3, Projection> projections = new ConcurrentHashMap<>();
        private final Map<AxisBasis, List<Vec3>> separatingAxes = new ConcurrentHashMap<>();

        private Geometry(List<Vec3> vertices, List<Face> faces, List<Edge> edges) {
            this.vertices = List.copyOf(vertices);
            this.faces = orientFaces(this.vertices, faces);
            this.faceNormals = distinctAxes(this.faces.stream().map(Face::normal).toList());
            this.edges = List.copyOf(edges);
            this.edgeDirections = distinctAxes(this.edges.stream()
                .map(edge -> edge.end().subtract(edge.start()))
                .toList());
            if (this.vertices.size() < 4 || this.faceNormals.size() < 3 || this.edgeDirections.size() < 3) {
                throw new IllegalArgumentException("Convex collision shape is degenerate");
            }
            this.bounds = bounds(this.vertices);
            this.axisAlignedBox = isAxisAlignedBox(this.vertices, this.bounds);
            this.axisBasis = new AxisBasis(this.faceNormals, this.edgeDirections);
        }

        private static List<Face> orientFaces(List<Vec3> vertices, List<Face> faces) {
            Vec3 center = vertices.stream().reduce(Vec3.ZERO, Vec3::add)
                .scale(1.0D / vertices.size());
            List<Face> oriented = new ArrayList<>(faces.size());
            for (Face face : faces) {
                Vec3 faceCenter = face.vertices().stream().reduce(Vec3.ZERO, Vec3::add)
                    .scale(1.0D / face.vertices().size());
                Vec3 normal = face.normal();
                if (normal.dot(faceCenter.subtract(center)) < 0.0D) normal = normal.scale(-1.0D);
                oriented.add(new Face(face.vertices(), normal));
            }
            return List.copyOf(oriented);
        }

        private Projection project(Vec3 axis) {
            double minimum = this.vertices.getFirst().dot(axis);
            double maximum = minimum;
            for (int index = 1; index < this.vertices.size(); index++) {
                double projection = this.vertices.get(index).dot(axis);
                minimum = Math.min(minimum, projection);
                maximum = Math.max(maximum, projection);
            }
            return new Projection(minimum, maximum);
        }
    }

    private record AxisBasis(List<Vec3> faceNormals, List<Vec3> edgeDirections) {
        private AxisBasis {
            faceNormals = List.copyOf(faceNormals);
            edgeDirections = List.copyOf(edgeDirections);
        }

        private List<Vec3> combine(AxisBasis other) {
            List<Vec3> axes = new ArrayList<>(
                this.faceNormals.size() + other.faceNormals.size()
                    + this.edgeDirections.size() * other.edgeDirections.size()
            );
            for (Vec3 normal : this.faceNormals) addDistinctAxis(axes, normal);
            for (Vec3 normal : other.faceNormals) addDistinctAxis(axes, normal);
            for (Vec3 firstEdge : this.edgeDirections) {
                for (Vec3 secondEdge : other.edgeDirections) {
                    addDistinctAxis(axes, firstEdge.cross(secondEdge));
                }
            }
            return List.copyOf(axes);
        }
    }

    public record Edge(Vec3 start, Vec3 end) {
    }

    public record Face(List<Vec3> vertices, Vec3 normal) {
        public Face {
            vertices = List.copyOf(vertices);
            normal = Objects.requireNonNull(normal, "normal");
            if (vertices.size() < 3 || normal.lengthSqr() <= AXIS_EPSILON) {
                throw new IllegalArgumentException("Convex collision face is degenerate");
            }
            normal = normal.normalize();
        }

        public double planeOffset() {
            return this.normal.dot(this.vertices.getFirst());
        }

        public double signedDistance(Vec3 point) {
            return this.normal.dot(point) - this.planeOffset();
        }
    }

    public record Projection(double minimum, double maximum) {
    }
}
