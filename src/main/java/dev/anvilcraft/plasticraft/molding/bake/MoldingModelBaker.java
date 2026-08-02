package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将精确源元素确定性地烘焙到 1 px 制造网格。 */
public final class MoldingModelBaker {
    public static final int BAKE_VERSION = 1;
    public static final int MAX_COLLISION_BOXES = 4096;
    private static final double EPSILON = 1.0E-7D;
    private static final int PADDED_SIZE = MoldingVolumeMask.SIZE + 2;
    private static final MoldingFaceDirection[] DIRECTIONS = MoldingFaceDirection.values();

    private MoldingModelBaker() {
    }

    public static BakedMoldingModel bake(EditableMoldingModel model) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        MoldingVolumeMask volume = new MoldingVolumeMask();
        Set<MoldingBarrierFace> barriers = new HashSet<>();
        List<MoldingQuad> zeroThicknessQuads = new ArrayList<>();

        for (MoldingElement element : model.elements()) {
            List<MoldingGroup> hierarchy = hierarchy(element, groups);
            if (!element.visible() || hierarchy.stream().anyMatch(group -> !group.visible())) continue;
            List<MoldingVec3> vertices = transformedVertices(element, hierarchy);
            validateWorkspace(vertices);
            if (element.hasVolume()) {
                rasterizeCube(volume, element, hierarchy, vertices);
            } else {
                rasterizeZeroThicknessCube(barriers, zeroThicknessQuads, vertices);
            }
        }

        int[] fillOrder = createFillOrder(volume);
        List<MoldingQuad> surface = createSurfaceMesh(volume);
        surface.addAll(zeroThicknessQuads);
        CollisionResult collision = createCollisionShape(volume);
        List<MoldingCavity> cavities = findCavities(volume, barriers);
        int cavityVolume = cavities.stream().mapToInt(MoldingCavity::volume).sum();
        boolean hasShape = !volume.isEmpty() || !barriers.isEmpty();
        int minimumMelt = hasShape ? Math.max((volume.volume() + 3) / 4, 250) : 0;
        int clayBalls = (MoldingVolumeMask.CELL_COUNT - volume.volume() + 1023) / 1024;
        MoldingAnalysis analysis = new MoldingAnalysis(
            volume.volume(),
            barriers.size(),
            cavities.size(),
            cavityVolume,
            surface.size(),
            collision.boxes().size(),
            collision.complexityExceeded(),
            minimumMelt,
            clayBalls
        );
        return new BakedMoldingModel(
            BAKE_VERSION,
            volume,
            barriers,
            fillOrder,
            surface,
            collision.boxes(),
            cavities,
            analysis,
            MoldingModelHasher.hash(model, BAKE_VERSION)
        );
    }

    public static List<MoldingVec3> transformedVertices(
        EditableMoldingModel model,
        MoldingElement element
    ) {
        return transformedVertices(element, hierarchy(element, model.groupMap()));
    }

    public static MoldingVec3 transformedPoint(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingVec3 point
    ) {
        return applyTransforms(point, element.transform(), hierarchy(element, model.groupMap()));
    }

    public static MoldingVec3 inverseTransformedPoint(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingVec3 point
    ) {
        return inverseTransforms(point, element.transform(), hierarchy(element, model.groupMap()));
    }

    private static List<MoldingGroup> hierarchy(
        MoldingElement element,
        Map<UUID, MoldingGroup> groups
    ) {
        List<MoldingGroup> result = new ArrayList<>();
        element.groupId().ifPresent(id -> {
            MoldingGroup current = groups.get(id);
            while (current != null) {
                result.add(current);
                current = current.parentId().map(groups::get).orElse(null);
            }
        });
        return result;
    }

    private static List<MoldingVec3> transformedVertices(
        MoldingElement element,
        List<MoldingGroup> hierarchy
    ) {
        List<MoldingVec3> vertices = new ArrayList<>();
        if (element.hasVolume()) {
            for (int corner = 0; corner < 8; corner++) {
                vertices.add(applyTransforms(
                    new MoldingVec3(
                        (corner & 1) == 0 ? element.from().x() : element.to().x(),
                        (corner & 2) == 0 ? element.from().y() : element.to().y(),
                        (corner & 4) == 0 ? element.from().z() : element.to().z()
                    ),
                    element.transform(),
                    hierarchy
                ));
            }
            return vertices;
        }

        MoldingVec3 from = element.from();
        MoldingVec3 to = element.to();
        if (from.x() == to.x()) {
            vertices.add(new MoldingVec3(from.x(), from.y(), from.z()));
            vertices.add(new MoldingVec3(from.x(), to.y(), from.z()));
            vertices.add(new MoldingVec3(from.x(), to.y(), to.z()));
            vertices.add(new MoldingVec3(from.x(), from.y(), to.z()));
        } else if (from.y() == to.y()) {
            vertices.add(new MoldingVec3(from.x(), from.y(), from.z()));
            vertices.add(new MoldingVec3(from.x(), from.y(), to.z()));
            vertices.add(new MoldingVec3(to.x(), from.y(), to.z()));
            vertices.add(new MoldingVec3(to.x(), from.y(), from.z()));
        } else {
            vertices.add(new MoldingVec3(from.x(), from.y(), from.z()));
            vertices.add(new MoldingVec3(to.x(), from.y(), from.z()));
            vertices.add(new MoldingVec3(to.x(), to.y(), from.z()));
            vertices.add(new MoldingVec3(from.x(), to.y(), from.z()));
        }
        return vertices.stream()
            .map(vertex -> applyTransforms(vertex, element.transform(), hierarchy))
            .toList();
    }

    private static MoldingVec3 applyTransforms(
        MoldingVec3 point,
        MoldingTransform elementTransform,
        List<MoldingGroup> hierarchy
    ) {
        MoldingVec3 result = elementTransform.apply(point);
        for (MoldingGroup group : hierarchy) result = group.transform().apply(result);
        return result;
    }

    private static MoldingVec3 inverseTransforms(
        MoldingVec3 point,
        MoldingTransform elementTransform,
        List<MoldingGroup> hierarchy
    ) {
        MoldingVec3 result = point;
        for (int index = hierarchy.size() - 1; index >= 0; index--) {
            result = hierarchy.get(index).transform().inverse(result);
        }
        return elementTransform.inverse(result);
    }

    private static void validateWorkspace(List<MoldingVec3> vertices) {
        for (MoldingVec3 vertex : vertices) {
            if (vertex.x() < -EPSILON || vertex.x() > MoldingVolumeMask.SIZE + EPSILON
                || vertex.y() < -EPSILON || vertex.y() > MoldingVolumeMask.SIZE + EPSILON
                || vertex.z() < -EPSILON || vertex.z() > MoldingVolumeMask.SIZE + EPSILON) {
                throw new IllegalArgumentException("Molding element extends outside the 48x48x48 workspace");
            }
        }
    }

    private static void rasterizeCube(
        MoldingVolumeMask volume,
        MoldingElement element,
        List<MoldingGroup> hierarchy,
        List<MoldingVec3> vertices
    ) {
        Bounds bounds = Bounds.of(vertices);
        int minX = cellMinimum(bounds.min().x());
        int minY = cellMinimum(bounds.min().y());
        int minZ = cellMinimum(bounds.min().z());
        int maxX = cellMaximum(bounds.max().x());
        int maxY = cellMaximum(bounds.max().y());
        int maxZ = cellMaximum(bounds.max().z());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    MoldingVec3 local = inverseTransforms(
                        new MoldingVec3(x + 0.5D, y + 0.5D, z + 0.5D),
                        element.transform(),
                        hierarchy
                    );
                    if (contains(element, local)) volume.set(x, y, z);
                }
            }
        }
    }

    private static boolean contains(MoldingElement element, MoldingVec3 point) {
        return between(point.x(), element.from().x(), element.to().x())
            && between(point.y(), element.from().y(), element.to().y())
            && between(point.z(), element.from().z(), element.to().z());
    }

    private static boolean between(double value, double first, double second) {
        return value >= Math.min(first, second) - EPSILON
            && value <= Math.max(first, second) + EPSILON;
    }

    private static int cellMinimum(double coordinate) {
        return Math.clamp((int) Math.floor(coordinate), 0, MoldingVolumeMask.SIZE - 1);
    }

    private static int cellMaximum(double coordinate) {
        return Math.clamp((int) Math.ceil(coordinate) - 1, 0, MoldingVolumeMask.SIZE - 1);
    }

    private static void rasterizeZeroThicknessCube(
        Set<MoldingBarrierFace> barriers,
        List<MoldingQuad> surface,
        List<MoldingVec3> vertices
    ) {
        MoldingVec3 normal = vertices.get(1).subtract(vertices.get(0))
            .cross(vertices.get(2).subtract(vertices.get(0)));
        double length = Math.sqrt(normal.lengthSquared());
        if (length <= EPSILON) throw new IllegalArgumentException("Degenerate zero-thickness cube");
        surface.add(new MoldingQuad(
            vertices.get(0),
            vertices.get(1),
            vertices.get(2),
            vertices.get(3),
            normal.scale(1.0D / length),
            true
        ));

        Bounds bounds = Bounds.of(vertices);
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.X);
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Y);
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Z);
    }

    private static void scanZeroThicknessAxis(
        Set<MoldingBarrierFace> barriers,
        List<MoldingVec3> vertices,
        Bounds bounds,
        MoldingFaceDirection.Axis axis
    ) {
        double axialMin = component(bounds.min(), axis);
        double axialMax = component(bounds.max(), axis);
        MoldingFaceDirection.Axis uAxis = axis == MoldingFaceDirection.Axis.X
            ? MoldingFaceDirection.Axis.Y
            : MoldingFaceDirection.Axis.X;
        MoldingFaceDirection.Axis vAxis = axis == MoldingFaceDirection.Axis.Z
            ? MoldingFaceDirection.Axis.Y
            : MoldingFaceDirection.Axis.Z;
        int minPlane = Math.clamp((int) Math.ceil(axialMin - 0.5D - EPSILON), 0, MoldingVolumeMask.SIZE);
        int maxPlane = Math.clamp((int) Math.floor(axialMax + 0.5D + EPSILON), 0, MoldingVolumeMask.SIZE);
        int minU = transverseMinimum(component(bounds.min(), uAxis));
        int maxU = transverseMaximum(component(bounds.max(), uAxis));
        int minV = transverseMinimum(component(bounds.min(), vAxis));
        int maxV = transverseMaximum(component(bounds.max(), vAxis));

        for (int plane = minPlane; plane <= maxPlane; plane++) {
            for (int u = minU; u <= maxU; u++) {
                for (int v = minV; v <= maxV; v++) {
                    MoldingVec3 first = transitionPoint(axis, plane - 0.5D, u + 0.5D, v + 0.5D);
                    MoldingVec3 second = transitionPoint(axis, plane + 0.5D, u + 0.5D, v + 0.5D);
                    if (intersectsQuad(first, second, vertices)) {
                        barriers.add(new MoldingBarrierFace(axis, plane, u, v));
                    }
                }
            }
        }
    }

    private static int transverseMinimum(double coordinate) {
        return Math.clamp((int) Math.ceil(coordinate - 0.5D - EPSILON) - 1, 0, MoldingVolumeMask.SIZE - 1);
    }

    private static int transverseMaximum(double coordinate) {
        return Math.clamp((int) Math.floor(coordinate - 0.5D + EPSILON) + 1, 0, MoldingVolumeMask.SIZE - 1);
    }

    private static double component(MoldingVec3 value, MoldingFaceDirection.Axis axis) {
        return switch (axis) {
            case X -> value.x();
            case Y -> value.y();
            case Z -> value.z();
        };
    }

    private static MoldingVec3 transitionPoint(
        MoldingFaceDirection.Axis axis,
        double axial,
        double u,
        double v
    ) {
        return switch (axis) {
            case X -> new MoldingVec3(axial, u, v);
            case Y -> new MoldingVec3(u, axial, v);
            case Z -> new MoldingVec3(u, v, axial);
        };
    }

    private static boolean intersectsQuad(
        MoldingVec3 start,
        MoldingVec3 end,
        List<MoldingVec3> vertices
    ) {
        return intersectsTriangle(start, end, vertices.get(0), vertices.get(1), vertices.get(2))
            || intersectsTriangle(start, end, vertices.get(0), vertices.get(2), vertices.get(3));
    }

    private static boolean intersectsTriangle(
        MoldingVec3 start,
        MoldingVec3 end,
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third
    ) {
        MoldingVec3 direction = end.subtract(start);
        MoldingVec3 edgeOne = second.subtract(first);
        MoldingVec3 edgeTwo = third.subtract(first);
        MoldingVec3 h = direction.cross(edgeTwo);
        double determinant = edgeOne.dot(h);
        if (Math.abs(determinant) < EPSILON) return false;
        double inverse = 1.0D / determinant;
        MoldingVec3 s = start.subtract(first);
        double u = inverse * s.dot(h);
        if (u < -EPSILON || u > 1.0D + EPSILON) return false;
        MoldingVec3 q = s.cross(edgeOne);
        double v = inverse * direction.dot(q);
        if (v < -EPSILON || u + v > 1.0D + EPSILON) return false;
        double distance = inverse * edgeTwo.dot(q);
        return distance >= -EPSILON && distance <= 1.0D + EPSILON;
    }

    private static int[] createFillOrder(MoldingVolumeMask volume) {
        int[] result = new int[volume.volume()];
        int output = 0;
        for (int y = 0; y < MoldingVolumeMask.SIZE; y++) {
            for (int x = 0; x < MoldingVolumeMask.SIZE; x++) {
                for (int z = 0; z < MoldingVolumeMask.SIZE; z++) {
                    if (volume.get(x, y, z)) result[output++] = MoldingVolumeMask.index(x, y, z);
                }
            }
        }
        return result;
    }

    private static List<MoldingQuad> createSurfaceMesh(MoldingVolumeMask volume) {
        List<MoldingQuad> result = new ArrayList<>();
        for (MoldingFaceDirection direction : DIRECTIONS) meshDirection(volume, direction, result);
        return result;
    }

    private static void meshDirection(
        MoldingVolumeMask volume,
        MoldingFaceDirection direction,
        List<MoldingQuad> output
    ) {
        for (int plane = 0; plane <= MoldingVolumeMask.SIZE; plane++) {
            boolean[][] active = new boolean[MoldingVolumeMask.SIZE][MoldingVolumeMask.SIZE];
            for (int u = 0; u < MoldingVolumeMask.SIZE; u++) {
                for (int v = 0; v < MoldingVolumeMask.SIZE; v++) {
                    active[u][v] = hasExposedFace(volume, direction, plane, u, v);
                }
            }
            mergeSurfacePlane(active, direction, plane, output);
        }
    }

    private static boolean hasExposedFace(
        MoldingVolumeMask volume,
        MoldingFaceDirection direction,
        int plane,
        int u,
        int v
    ) {
        return switch (direction) {
            case NEGATIVE_X -> volume.get(plane, u, v) && !volume.get(plane - 1, u, v);
            case POSITIVE_X -> volume.get(plane - 1, u, v) && !volume.get(plane, u, v);
            case NEGATIVE_Y -> volume.get(u, plane, v) && !volume.get(u, plane - 1, v);
            case POSITIVE_Y -> volume.get(u, plane - 1, v) && !volume.get(u, plane, v);
            case NEGATIVE_Z -> volume.get(u, v, plane) && !volume.get(u, v, plane - 1);
            case POSITIVE_Z -> volume.get(u, v, plane - 1) && !volume.get(u, v, plane);
        };
    }

    private static void mergeSurfacePlane(
        boolean[][] active,
        MoldingFaceDirection direction,
        int plane,
        List<MoldingQuad> output
    ) {
        for (int u = 0; u < MoldingVolumeMask.SIZE; u++) {
            for (int v = 0; v < MoldingVolumeMask.SIZE; v++) {
                if (!active[u][v]) continue;
                int vLength = 1;
                while (v + vLength < MoldingVolumeMask.SIZE && active[u][v + vLength]) vLength++;
                int uLength = 1;
                while (u + uLength < MoldingVolumeMask.SIZE
                    && rowActive(active, u + uLength, v, vLength)) {
                    uLength++;
                }
                for (int clearU = u; clearU < u + uLength; clearU++) {
                    for (int clearV = v; clearV < v + vLength; clearV++) active[clearU][clearV] = false;
                }
                output.add(surfaceQuad(direction, plane, u, v, uLength, vLength));
            }
        }
    }

    private static boolean rowActive(boolean[][] active, int u, int v, int length) {
        for (int offset = 0; offset < length; offset++) {
            if (!active[u][v + offset]) return false;
        }
        return true;
    }

    private static MoldingQuad surfaceQuad(
        MoldingFaceDirection direction,
        int plane,
        int u,
        int v,
        int uLength,
        int vLength
    ) {
        MoldingVec3 normal = new MoldingVec3(direction.stepX(), direction.stepY(), direction.stepZ());
        MoldingVec3 first;
        MoldingVec3 second;
        MoldingVec3 third;
        MoldingVec3 fourth;
        switch (direction.axis()) {
            case X -> {
                first = new MoldingVec3(plane, u, v);
                second = new MoldingVec3(plane, u + uLength, v);
                third = new MoldingVec3(plane, u + uLength, v + vLength);
                fourth = new MoldingVec3(plane, u, v + vLength);
            }
            case Y -> {
                first = new MoldingVec3(u, plane, v);
                second = new MoldingVec3(u, plane, v + vLength);
                third = new MoldingVec3(u + uLength, plane, v + vLength);
                fourth = new MoldingVec3(u + uLength, plane, v);
            }
            case Z -> {
                first = new MoldingVec3(u, v, plane);
                second = new MoldingVec3(u + uLength, v, plane);
                third = new MoldingVec3(u + uLength, v + vLength, plane);
                fourth = new MoldingVec3(u, v + vLength, plane);
            }
            default -> throw new IllegalStateException("Unexpected molding face axis");
        }
        if (direction == MoldingFaceDirection.POSITIVE_X
            || direction == MoldingFaceDirection.POSITIVE_Y
            || direction == MoldingFaceDirection.POSITIVE_Z) {
            MoldingVec3 swap = second;
            second = fourth;
            fourth = swap;
        }
        return new MoldingQuad(first, second, third, fourth, normal, false);
    }

    private static CollisionResult createCollisionShape(MoldingVolumeMask volume) {
        BitSet consumed = new BitSet(MoldingVolumeMask.CELL_COUNT);
        List<MoldingCollisionBox> boxes = new ArrayList<>();
        for (int y = 0; y < MoldingVolumeMask.SIZE; y++) {
            for (int x = 0; x < MoldingVolumeMask.SIZE; x++) {
                for (int z = 0; z < MoldingVolumeMask.SIZE; z++) {
                    int index = MoldingVolumeMask.index(x, y, z);
                    if (!volume.get(x, y, z) || consumed.get(index)) continue;
                    if (boxes.size() >= MAX_COLLISION_BOXES) {
                        return new CollisionResult(boxes, true);
                    }
                    int maxX = extendX(volume, consumed, x, y, z);
                    int maxZ = extendZ(volume, consumed, x, maxX, y, z);
                    int maxY = extendY(volume, consumed, x, maxX, y, z, maxZ);
                    markConsumed(consumed, x, maxX, y, maxY, z, maxZ);
                    boxes.add(new MoldingCollisionBox(x, y, z, maxX, maxY, maxZ));
                }
            }
        }
        return new CollisionResult(boxes, false);
    }

    private static int extendX(MoldingVolumeMask volume, BitSet consumed, int x, int y, int z) {
        int result = x + 1;
        while (result < MoldingVolumeMask.SIZE
            && available(volume, consumed, result, y, z)) {
            result++;
        }
        return result;
    }

    private static int extendZ(
        MoldingVolumeMask volume,
        BitSet consumed,
        int minX,
        int maxX,
        int y,
        int z
    ) {
        int result = z + 1;
        while (result < MoldingVolumeMask.SIZE
            && layerAvailable(volume, consumed, minX, maxX, y, result, result + 1)) {
            result++;
        }
        return result;
    }

    private static int extendY(
        MoldingVolumeMask volume,
        BitSet consumed,
        int minX,
        int maxX,
        int y,
        int minZ,
        int maxZ
    ) {
        int result = y + 1;
        while (result < MoldingVolumeMask.SIZE
            && layerAvailable(volume, consumed, minX, maxX, result, minZ, maxZ)) {
            result++;
        }
        return result;
    }

    private static boolean layerAvailable(
        MoldingVolumeMask volume,
        BitSet consumed,
        int minX,
        int maxX,
        int y,
        int minZ,
        int maxZ
    ) {
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                if (!available(volume, consumed, x, y, z)) return false;
            }
        }
        return true;
    }

    private static boolean available(
        MoldingVolumeMask volume,
        BitSet consumed,
        int x,
        int y,
        int z
    ) {
        return volume.get(x, y, z) && !consumed.get(MoldingVolumeMask.index(x, y, z));
    }

    private static void markConsumed(
        BitSet consumed,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    consumed.set(MoldingVolumeMask.index(x, y, z));
                }
            }
        }
    }

    private static List<MoldingCavity> findCavities(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers
    ) {
        BitSet exterior = new BitSet(PADDED_SIZE * PADDED_SIZE * PADDED_SIZE);
        flood(volume, barriers, -1, -1, -1, exterior, null);
        BitSet assigned = new BitSet(MoldingVolumeMask.CELL_COUNT);
        List<MoldingCavity> cavities = new ArrayList<>();
        for (int y = 0; y < MoldingVolumeMask.SIZE; y++) {
            for (int x = 0; x < MoldingVolumeMask.SIZE; x++) {
                for (int z = 0; z < MoldingVolumeMask.SIZE; z++) {
                    int volumeIndex = MoldingVolumeMask.index(x, y, z);
                    if (volume.get(x, y, z)
                        || exterior.get(paddedIndex(x, y, z))
                        || assigned.get(volumeIndex)) {
                        continue;
                    }
                    BitSet component = new BitSet(MoldingVolumeMask.CELL_COUNT);
                    BoundsAccumulator bounds = new BoundsAccumulator();
                    flood(volume, barriers, x, y, z, assigned, new CavityOutput(component, bounds));
                    cavities.add(bounds.toCavity(component));
                }
            }
        }
        return cavities;
    }

    private static void flood(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        int startX,
        int startY,
        int startZ,
        BitSet visited,
        CavityOutput cavity
    ) {
        Deque<GridCell> queue = new ArrayDeque<>();
        queue.add(new GridCell(startX, startY, startZ));
        while (!queue.isEmpty()) {
            GridCell cell = queue.removeFirst();
            if (!inPaddedBounds(cell.x(), cell.y(), cell.z()) || volume.get(cell.x(), cell.y(), cell.z())) continue;
            int visitIndex = cavity == null
                ? paddedIndex(cell.x(), cell.y(), cell.z())
                : MoldingVolumeMask.index(cell.x(), cell.y(), cell.z());
            if (visited.get(visitIndex)) continue;
            visited.set(visitIndex);
            if (cavity != null) {
                cavity.cells().set(visitIndex);
                cavity.bounds().include(cell.x(), cell.y(), cell.z());
            }
            for (MoldingFaceDirection direction : DIRECTIONS) {
                GridCell next = new GridCell(
                    cell.x() + direction.stepX(),
                    cell.y() + direction.stepY(),
                    cell.z() + direction.stepZ()
                );
                if (!inPaddedBounds(next.x(), next.y(), next.z())
                    || blocked(barriers, cell, direction)) {
                    continue;
                }
                queue.addLast(next);
            }
        }
    }

    private static boolean blocked(
        Set<MoldingBarrierFace> barriers,
        GridCell cell,
        MoldingFaceDirection direction
    ) {
        int x = cell.x();
        int y = cell.y();
        int z = cell.z();
        return switch (direction.axis()) {
            case X -> y >= 0 && y < MoldingVolumeMask.SIZE && z >= 0 && z < MoldingVolumeMask.SIZE
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Y -> x >= 0 && x < MoldingVolumeMask.SIZE && z >= 0 && z < MoldingVolumeMask.SIZE
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Z -> x >= 0 && x < MoldingVolumeMask.SIZE && y >= 0 && y < MoldingVolumeMask.SIZE
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
        };
    }

    private static boolean inPaddedBounds(int x, int y, int z) {
        return x >= -1 && x <= MoldingVolumeMask.SIZE
            && y >= -1 && y <= MoldingVolumeMask.SIZE
            && z >= -1 && z <= MoldingVolumeMask.SIZE;
    }

    private static int paddedIndex(int x, int y, int z) {
        return ((y + 1) * PADDED_SIZE + x + 1) * PADDED_SIZE + z + 1;
    }

    private record Bounds(MoldingVec3 min, MoldingVec3 max) {
        private static Bounds of(List<MoldingVec3> vertices) {
            MoldingVec3 min = vertices.getFirst();
            MoldingVec3 max = vertices.getFirst();
            for (MoldingVec3 vertex : vertices) {
                min = min.min(vertex);
                max = max.max(vertex);
            }
            return new Bounds(min, max);
        }
    }

    private record CollisionResult(List<MoldingCollisionBox> boxes, boolean complexityExceeded) {
    }

    private record GridCell(int x, int y, int z) {
    }

    private record CavityOutput(BitSet cells, BoundsAccumulator bounds) {
    }

    private static final class BoundsAccumulator {
        private int minX = MoldingVolumeMask.SIZE;
        private int minY = MoldingVolumeMask.SIZE;
        private int minZ = MoldingVolumeMask.SIZE;
        private int maxX;
        private int maxY;
        private int maxZ;

        private void include(int x, int y, int z) {
            this.minX = Math.min(this.minX, x);
            this.minY = Math.min(this.minY, y);
            this.minZ = Math.min(this.minZ, z);
            this.maxX = Math.max(this.maxX, x + 1);
            this.maxY = Math.max(this.maxY, y + 1);
            this.maxZ = Math.max(this.maxZ, z + 1);
        }

        private MoldingCavity toCavity(BitSet cells) {
            return new MoldingCavity(
                cells,
                this.minX,
                this.minY,
                this.minZ,
                this.maxX,
                this.maxY,
                this.maxZ
            );
        }
    }
}
