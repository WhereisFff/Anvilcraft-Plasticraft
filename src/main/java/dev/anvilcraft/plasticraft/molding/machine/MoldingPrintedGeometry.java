package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.molding.bake.ManufacturedMoldingGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingPreparedHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 打印施工显示：源凸体的连续外表面贴在已完成像素上；
 * 未完成的面只铺格子与源面的真实相交，相邻碎片略微重叠以免接缝漏光；
 * 轴对齐封口只留在尚未打印的邻格边界。
 */
public final class MoldingPrintedGeometry {
    private static final double EPSILON = 1.0E-7D;
    private static final double AREA_EPSILON = 1.0E-4D;
    private static final double SNAP_DISTANCE = 1.0E-4D;
    private static final double ORIGINAL_FACE_DOT = 0.999D;
    private static final double FRAGMENT_OVERLAP = 0.02D;

    private final MoldingPrintingPlan plan;
    private final MoldingVolumeMask mask;
    private final List<HullSource> hulls;
    private final List<FaceAssembler> sheets;
    private final List<PrintedTriangle> surface = new ArrayList<>();
    private final Map<CapKey, List<PrintedTriangle>> caps = new LinkedHashMap<>();
    private MoldingVolumeMask printed;
    private int completed;

    private MoldingPrintedGeometry(
        MoldingPrintingPlan plan,
        List<HullSource> hulls,
        List<FaceAssembler> sheets
    ) {
        this.plan = plan;
        this.mask = plan.voxelMask();
        this.hulls = List.copyOf(hulls);
        this.sheets = List.copyOf(sheets);
        this.printed = new MoldingVolumeMask(this.mask.sizeX(), this.mask.sizeY(), this.mask.sizeZ());
    }

    public static MoldingPrintedGeometry empty() {
        return new MoldingPrintedGeometry(
            new MoldingPrintingPlan("", new MoldingVolumeMask(), new int[0]),
            List.of(),
            List.of()
        );
    }

    public static MoldingPrintedGeometry prepare(EditableMoldingModel model, MoldingPrintingPlan plan) {
        ManufacturedMoldingGeometry geometry = MoldingModelBaker.createManufacturedGeometry(model, 1.0D);
        List<HullSource> hulls = new ArrayList<>(geometry.collisionHulls().size());
        for (MoldingConvexHull hull : geometry.collisionHulls()) {
            hulls.add(HullSource.of(hull));
        }
        List<FaceAssembler> sheets = new ArrayList<>();
        for (MoldingQuad quad : geometry.surfaceMesh()) {
            if (!quad.doubleSided()) continue;
            sheets.add(FaceAssembler.sheet(List.of(
                quad.first(),
                quad.second(),
                quad.third(),
                quad.fourth()
            ), quad.normal()));
        }
        return new MoldingPrintedGeometry(plan, hulls, sheets);
    }

    public void advanceTo(int requestedCompleted) {
        int target = Math.clamp(requestedCompleted, 0, this.plan.size());
        if (target == this.completed) return;
        if (target < this.completed) {
            this.completed = 0;
            this.caps.clear();
            this.printed = new MoldingVolumeMask(this.mask.sizeX(), this.mask.sizeY(), this.mask.sizeZ());
            for (HullSource hull : this.hulls) hull.reset();
            for (FaceAssembler sheet : this.sheets) sheet.reset();
        }
        if (target == this.plan.size() && target > 0) {
            this.completed = target;
            this.caps.clear();
            this.rebuildSurface();
            return;
        }
        while (this.completed < target) {
            int cell = this.plan.cellAt(this.completed++);
            int x = this.mask.xOf(cell);
            int y = this.mask.yOf(cell);
            int z = this.mask.zOf(cell);
            this.addCell(x, y, z);
        }
        this.rebuildSurface();
    }

    public List<PrintedTriangle> surfaceTriangles() {
        return this.surface;
    }

    public List<PrintedTriangle> capTriangles() {
        List<PrintedTriangle> result = new ArrayList<>();
        for (List<PrintedTriangle> cap : this.caps.values()) result.addAll(cap);
        return result;
    }

    public boolean isEmpty() {
        return this.surface.isEmpty() && this.caps.isEmpty();
    }

    private void addCell(int x, int y, int z) {
        for (int hullIndex = 0; hullIndex < this.hulls.size(); hullIndex++) {
            HullSource source = this.hulls.get(hullIndex);
            if (!source.prepared.mayOverlapCell(x, y, z)) continue;
            for (FaceAssembler face : source.faces) {
                if (!face.overlapsCell(x, y, z)) continue;
                List<MoldingVec3> clipped = face.projectToPlane(
                    MoldingModelBaker.clipPolygonToCell(face.source, x, y, z)
                );
                if (clipped.size() >= 3) face.addPolygon(clipped);
            }
            this.updateCaps(hullIndex, source, x, y, z);
        }
        for (FaceAssembler sheet : this.sheets) {
            if (!sheet.overlapsCell(x, y, z)) continue;
            List<MoldingVec3> clipped = sheet.projectToPlane(
                MoldingModelBaker.clipPolygonToCell(sheet.source, x, y, z)
            );
            if (clipped.size() >= 3) sheet.addPolygon(clipped);
        }
        this.printed.set(x, y, z);
    }

    private void rebuildSurface() {
        this.surface.clear();
        boolean finished = this.completed == this.plan.size() && this.completed > 0;
        if (finished) this.caps.clear();
        for (HullSource hull : this.hulls) hull.appendTo(this.surface, finished);
        for (FaceAssembler sheet : this.sheets) sheet.appendTo(this.surface, finished);
    }

    private void updateCaps(int hullIndex, HullSource source, int x, int y, int z) {
        this.updateCap(hullIndex, source, x, y, z, 0, false);
        this.updateCap(hullIndex, source, x, y, z, 0, true);
        this.updateCap(hullIndex, source, x, y, z, 1, false);
        this.updateCap(hullIndex, source, x, y, z, 1, true);
        this.updateCap(hullIndex, source, x, y, z, 2, false);
        this.updateCap(hullIndex, source, x, y, z, 2, true);
    }

    private void updateCap(
        int hullIndex,
        HullSource source,
        int x,
        int y,
        int z,
        int axis,
        boolean maxSide
    ) {
        int neighborX = x;
        int neighborY = y;
        int neighborZ = z;
        int plane;
        int first;
        int second;
        switch (axis) {
            case 0 -> {
                neighborX = maxSide ? x + 1 : x - 1;
                plane = maxSide ? x + 1 : x;
                first = y;
                second = z;
            }
            case 1 -> {
                neighborY = maxSide ? y + 1 : y - 1;
                plane = maxSide ? y + 1 : y;
                first = x;
                second = z;
            }
            case 2 -> {
                neighborZ = maxSide ? z + 1 : z - 1;
                plane = maxSide ? z + 1 : z;
                first = x;
                second = y;
            }
            default -> {
                return;
            }
        }
        CapKey key = new CapKey(hullIndex, axis, plane, first, second);
        if (!this.mask.get(neighborX, neighborY, neighborZ)) return;
        if (this.printed.get(neighborX, neighborY, neighborZ)) {
            this.caps.remove(key);
            return;
        }
        List<MoldingVec3> clipped = MoldingModelBaker.clipPolygonToConvexHull(
            cellFaceSquare(x, y, z, axis, maxSide),
            source.prepared.hull()
        );
        if (clipped.size() < 3 || source.onOriginalFace(clipped, axisNormal(axis, maxSide))) return;
        List<PrintedTriangle> triangles = new ArrayList<>();
        triangulate(clipped, axisNormal(axis, maxSide), false, triangles);
        if (!triangles.isEmpty()) this.caps.put(key, List.copyOf(triangles));
    }

    private static MoldingVec3 axisNormal(int axis, boolean maxSide) {
        double direction = maxSide ? 1.0D : -1.0D;
        return switch (axis) {
            case 0 -> new MoldingVec3(direction, 0.0D, 0.0D);
            case 1 -> new MoldingVec3(0.0D, direction, 0.0D);
            case 2 -> new MoldingVec3(0.0D, 0.0D, direction);
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static List<MoldingVec3> cellFaceSquare(int x, int y, int z, int axis, boolean maxSide) {
        double minX = x;
        double minY = y;
        double minZ = z;
        double maxX = x + 1.0D;
        double maxY = y + 1.0D;
        double maxZ = z + 1.0D;
        return switch (axis) {
            case 0 -> {
                double plane = maxSide ? maxX : minX;
                yield List.of(
                    new MoldingVec3(plane, minY, minZ),
                    new MoldingVec3(plane, minY, maxZ),
                    new MoldingVec3(plane, maxY, maxZ),
                    new MoldingVec3(plane, maxY, minZ)
                );
            }
            case 1 -> {
                double plane = maxSide ? maxY : minY;
                yield List.of(
                    new MoldingVec3(minX, plane, minZ),
                    new MoldingVec3(maxX, plane, minZ),
                    new MoldingVec3(maxX, plane, maxZ),
                    new MoldingVec3(minX, plane, maxZ)
                );
            }
            case 2 -> {
                double plane = maxSide ? maxZ : minZ;
                yield List.of(
                    new MoldingVec3(minX, minY, plane),
                    new MoldingVec3(minX, maxY, plane),
                    new MoldingVec3(maxX, maxY, plane),
                    new MoldingVec3(maxX, minY, plane)
                );
            }
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static void addTriangle(
        List<PrintedTriangle> output,
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third,
        MoldingVec3 normal,
        boolean doubleSided
    ) {
        MoldingVec3 cross = second.subtract(first).cross(third.subtract(first));
        if (cross.lengthSquared() <= EPSILON * EPSILON) return;
        if (cross.dot(normal) < 0.0D) {
            MoldingVec3 swap = second;
            second = third;
            third = swap;
        }
        output.add(new PrintedTriangle(first, second, third, normal, doubleSided));
    }

    public static double triangleListArea(List<PrintedTriangle> triangles) {
        double area = 0.0D;
        for (PrintedTriangle triangle : triangles) {
            area += 0.5D * Math.sqrt(triangle.second().subtract(triangle.first())
                .cross(triangle.third().subtract(triangle.first()))
                .lengthSquared());
        }
        return area;
    }

    public record PrintedTriangle(
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third,
        MoldingVec3 normal,
        boolean doubleSided
    ) {
    }

    private record CapKey(int hull, int axis, int plane, int first, int second) {
    }

    private static final class HullSource {
        private final MoldingPreparedHull prepared;
        private final List<FaceAssembler> faces;

        private HullSource(MoldingPreparedHull prepared, List<FaceAssembler> faces) {
            this.prepared = prepared;
            this.faces = faces;
        }

        private static HullSource of(MoldingConvexHull hull) {
            List<MoldingVec3> vertices = hull.vertices();
            List<FaceAssembler> faces = new ArrayList<>(hull.faces().size());
            for (MoldingConvexFace face : hull.faces()) {
                List<MoldingVec3> polygon = new ArrayList<>(face.vertices().size());
                for (int index : face.vertices()) polygon.add(vertices.get(index));
                faces.add(FaceAssembler.source(polygon, face.normal()));
            }
            return new HullSource(MoldingPreparedHull.prepare(hull), faces);
        }

        private void reset() {
            for (FaceAssembler face : this.faces) face.reset();
        }

        private boolean onOriginalFace(List<MoldingVec3> polygon, MoldingVec3 capNormal) {
            if (polygon.isEmpty()) return false;
            for (FaceAssembler face : this.faces) {
                if (Math.abs(capNormal.dot(face.normal)) < ORIGINAL_FACE_DOT) continue;
                boolean onPlane = true;
                for (MoldingVec3 vertex : polygon) {
                    if (!face.onSourcePlane(vertex)) {
                        onPlane = false;
                        break;
                    }
                }
                if (onPlane) return true;
            }
            return false;
        }

        private void appendTo(List<PrintedTriangle> output, boolean finished) {
            for (FaceAssembler face : this.faces) face.appendTo(output, finished);
        }
    }

    private static final class FaceAssembler {
        private final List<MoldingVec3> source;
        private final MoldingVec3 normal;
        private final boolean doubleSided;
        private final double minX;
        private final double minY;
        private final double minZ;
        private final double maxX;
        private final double maxY;
        private final double maxZ;
        private final List<List<MoldingVec3>> fragments = new ArrayList<>();

        private FaceAssembler(List<MoldingVec3> source, MoldingVec3 normal, boolean doubleSided) {
            this.source = List.copyOf(source);
            this.normal = normal;
            this.doubleSided = doubleSided;
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;
            for (MoldingVec3 vertex : this.source) {
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }

        private static FaceAssembler source(List<MoldingVec3> polygon, MoldingVec3 normal) {
            return new FaceAssembler(polygon, normal, false);
        }

        private static FaceAssembler sheet(List<MoldingVec3> polygon, MoldingVec3 normal) {
            return new FaceAssembler(polygon, normal, true);
        }

        private boolean overlapsCell(int x, int y, int z) {
            return this.maxX >= x - EPSILON && this.minX <= x + 1.0D + EPSILON
                && this.maxY >= y - EPSILON && this.minY <= y + 1.0D + EPSILON
                && this.maxZ >= z - EPSILON && this.minZ <= z + 1.0D + EPSILON;
        }

        private void reset() {
            this.fragments.clear();
        }

        private void addPolygon(List<MoldingVec3> polygon) {
            if (polygon.size() < 3) return;
            this.fragments.add(List.copyOf(polygon));
        }

        private boolean isComplete() {
            double sourceArea = polygonArea(this.source);
            if (sourceArea <= AREA_EPSILON) return false;
            return this.fragmentArea() >= sourceArea - Math.max(AREA_EPSILON, 0.02D * sourceArea);
        }

        private double fragmentArea() {
            double area = 0.0D;
            for (List<MoldingVec3> fragment : this.fragments) area += polygonArea(fragment);
            return area;
        }

        private boolean onSourcePlane(MoldingVec3 point) {
            return Math.abs(point.subtract(this.source.getFirst()).dot(this.normal)) <= SNAP_DISTANCE;
        }

        private List<MoldingVec3> projectToPlane(List<MoldingVec3> polygon) {
            if (polygon.size() < 3) return List.of();
            List<MoldingVec3> projected = new ArrayList<>(polygon.size());
            MoldingVec3 origin = this.source.getFirst();
            for (MoldingVec3 vertex : polygon) {
                projected.add(vertex.subtract(this.normal.scale(vertex.subtract(origin).dot(this.normal))));
            }
            return projected;
        }

        private void appendTo(List<PrintedTriangle> output, boolean finished) {
            if (finished || this.isComplete()) {
                triangulate(this.source, this.normal, this.doubleSided, output);
                return;
            }
            // 只铺已打印格子与源面的相交。
            for (List<MoldingVec3> fragment : this.fragments) {
                triangulate(this.overlapNeighbors(fragment), this.normal, true, output);
            }
        }

        private List<MoldingVec3> overlapNeighbors(List<MoldingVec3> polygon) {
            if (polygon.size() < 3) return polygon;
            MoldingVec3 centroid = MoldingVec3.ZERO;
            for (MoldingVec3 vertex : polygon) centroid = centroid.add(vertex);
            centroid = centroid.scale(1.0D / polygon.size());
            List<MoldingVec3> expanded = new ArrayList<>(polygon.size());
            for (MoldingVec3 vertex : polygon) {
                if (this.onSourceBoundary(vertex)) {
                    expanded.add(vertex);
                    continue;
                }
                MoldingVec3 offset = vertex.subtract(centroid);
                double length = Math.sqrt(offset.lengthSquared());
                if (length <= EPSILON) {
                    expanded.add(vertex);
                    continue;
                }
                expanded.add(vertex.add(offset.scale(FRAGMENT_OVERLAP / length)));
            }
            return expanded;
        }

        private boolean onSourceBoundary(MoldingVec3 point) {
            int count = this.source.size();
            for (int index = 0; index < count; index++) {
                MoldingVec3 from = this.source.get(index);
                MoldingVec3 to = this.source.get((index + 1) % count);
                MoldingVec3 span = to.subtract(from);
                double lengthSquared = span.lengthSquared();
                if (lengthSquared <= EPSILON * EPSILON) continue;
                if (point.subtract(from).cross(span).lengthSquared() > SNAP_DISTANCE * SNAP_DISTANCE * lengthSquared) {
                    continue;
                }
                double amount = point.subtract(from).dot(span) / lengthSquared;
                if (amount >= -EPSILON && amount <= 1.0D + EPSILON) return true;
            }
            return false;
        }
    }

    private static void triangulate(
        List<MoldingVec3> loop,
        MoldingVec3 normal,
        boolean doubleSided,
        List<PrintedTriangle> output
    ) {
        List<MoldingVec3> vertices = new ArrayList<>(loop);
        removeCollinear(vertices);
        if (vertices.size() < 3) return;
        if (signedArea(vertices, normal) < 0.0D) Collections.reverse(vertices);
        for (int index = 1; index + 1 < vertices.size(); index++) {
            addTriangle(
                output,
                vertices.get(0),
                vertices.get(index),
                vertices.get(index + 1),
                normal,
                doubleSided
            );
        }
    }

    private static void removeCollinear(List<MoldingVec3> vertices) {
        int index = 0;
        while (vertices.size() > 3 && index < vertices.size()) {
            MoldingVec3 previous = vertices.get(Math.floorMod(index - 1, vertices.size()));
            MoldingVec3 current = vertices.get(index);
            MoldingVec3 next = vertices.get((index + 1) % vertices.size());
            if (current.subtract(previous).cross(next.subtract(current)).lengthSquared() <= EPSILON * EPSILON) {
                vertices.remove(index);
                continue;
            }
            index++;
        }
    }

    private static MoldingVec3 newell(List<MoldingVec3> polygon) {
        MoldingVec3 area = MoldingVec3.ZERO;
        for (int index = 0; index < polygon.size(); index++) {
            MoldingVec3 current = polygon.get(index);
            MoldingVec3 next = polygon.get((index + 1) % polygon.size());
            area = area.add(current.cross(next));
        }
        return area;
    }

    private static double polygonArea(List<MoldingVec3> polygon) {
        if (polygon.size() < 3) return 0.0D;
        return 0.5D * Math.sqrt(newell(polygon).lengthSquared());
    }

    private static double signedArea(List<MoldingVec3> polygon, MoldingVec3 normal) {
        return 0.5D * newell(polygon).dot(normal);
    }
}
