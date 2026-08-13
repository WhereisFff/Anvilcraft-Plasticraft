package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 将精确源元素确定性地烘焙到 1 px 制造网格。 */
public final class MoldingModelBaker {
    public static final int BAKE_VERSION = 1;
    public static final int MAX_COLLISION_BOXES = 4096;
    private static final int BAKED_CACHE_LIMIT = 128;
    private static final int MANUFACTURED_CACHE_LIMIT = 256;
    private static final double EPSILON = 1.0E-7D;
    private static final double VOLUME_EPSILON = 1.0E-10D;
    private static final int[][] CUBE_FACES = {
        {0, 4, 6, 2}, {1, 3, 7, 5},
        {0, 1, 5, 4}, {2, 6, 7, 3},
        {0, 2, 3, 1}, {4, 5, 7, 6}
    };
    private static final MoldingFaceDirection[] DIRECTIONS = MoldingFaceDirection.values();
    private static final Map<EditableMoldingModel, BakedMoldingModel> IDENTITY_BAKED_CACHE =
        new IdentityHashMap<>();
    private static final Deque<EditableMoldingModel> BAKED_IDENTITY_ORDER = new ArrayDeque<>();
    private static final Map<String, BakedMoldingModel> BAKED_CACHE = boundedCache(BAKED_CACHE_LIMIT);
    private static final Map<ManufacturedKey, ManufacturedMoldingGeometry> MANUFACTURED_CACHE =
        boundedCache(MANUFACTURED_CACHE_LIMIT);

    private MoldingModelBaker() {
    }

    public static BakedMoldingModel bake(EditableMoldingModel model) {
        synchronized (IDENTITY_BAKED_CACHE) {
            BakedMoldingModel cached = IDENTITY_BAKED_CACHE.get(model);
            if (cached != null) return cached;
        }
        String modelHash = MoldingModelHasher.hash(model, BAKE_VERSION);
        synchronized (BAKED_CACHE) {
            BakedMoldingModel cached = BAKED_CACHE.get(modelHash);
            if (cached != null) {
                cacheIdentity(model, cached);
                return cached;
            }
        }
        BakedMoldingModel baked = bakeUncached(model, modelHash);
        synchronized (BAKED_CACHE) {
            BakedMoldingModel cached = BAKED_CACHE.get(modelHash);
            if (cached != null) baked = cached;
            else BAKED_CACHE.put(modelHash, baked);
        }
        cacheIdentity(model, baked);
        return baked;
    }

    private static BakedMoldingModel bakeUncached(EditableMoldingModel model, String modelHash) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        BakeSpace space = spaceFor(model);
        MoldingVolumeMask volume = new MoldingVolumeMask(space.sizeX(), space.sizeY(), space.sizeZ());
        Set<MoldingBarrierFace> barriers = new HashSet<>();
        List<MoldingQuad> zeroThicknessQuads = new ArrayList<>();

        for (MoldingElement element : model.elements()) {
            List<MoldingGroup> hierarchy = hierarchy(element, groups);
            if (!element.visible() || hierarchy.stream().anyMatch(group -> !group.visible())) continue;
            List<MoldingVec3> vertices = translatedVertices(element, hierarchy, space.offset());
            validateWorkspace(vertices, space);
            if (element.hasVolume()) {
                rasterizeCube(volume, element, hierarchy, vertices, space);
            } else {
                rasterizeZeroThicknessCube(barriers, zeroThicknessQuads, vertices, space);
            }
        }

        int[] fillOrder = createFillOrder(volume);
        List<MoldingQuad> surface = createSurfaceMesh(volume);
        surface.addAll(zeroThicknessQuads);
        CollisionResult collision = createCollisionShape(volume);
        List<MoldingCavity> cavities = findCavities(volume, barriers);
        MoldingFunctionalAnalysis functionalAnalysis = MoldingShellAnalyzer.analyze(volume, barriers)
            .withAnvilShape(MoldingAnvilShapeAnalyzer.analyze(model, volume))
            .withTrayShape(MoldingTrayShapeAnalyzer.analyze(volume, surface));
        int cavityVolume = cavities.stream().mapToInt(MoldingCavity::volume).sum();
        boolean hasShape = !volume.isEmpty() || !barriers.isEmpty();
        int minimumMelt = hasShape ? Math.max((volume.volume() + 3) / 4, 250) : 0;
        int clayBalls = (volume.cellCount() - volume.volume() + 1023) / 1024;
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
            functionalAnalysis,
            analysis,
            modelHash
        );
    }

    /** 构造仅供计量和分析使用的已支付体素掩码，不得把它用作制品表面或动态碰撞。 */
    public static MoldingVolumeMask createPaidVolumeMask(BakedMoldingModel baked, int maximumCells) {
        return createPaidVolumeMask(baked.volumeMask(), baked.fillOrder(), maximumCells);
    }

    public static MoldingVolumeMask createPaidVolumeMask(
        MoldingVolumeMask source,
        int[] fillOrder,
        int maximumCells
    ) {
        if (maximumCells < 0) throw new IllegalArgumentException("Manufactured cell count must not be negative");
        int cellCount = Math.min(fillOrder.length, maximumCells);
        MoldingVolumeMask volume = new MoldingVolumeMask(source.sizeX(), source.sizeY(), source.sizeZ());
        for (int index = 0; index < cellCount; index++) {
            int cell = fillOrder[index];
            volume.set(source.xOf(cell), source.yOf(cell), source.zOf(cell));
        }
        return volume;
    }

    /** 生成旧式体素分析快照；实际制品改用按源 Cube 水平裁切的连续几何。 */
    public static ManufacturedMoldingShape manufacture(BakedMoldingModel baked, int maximumCells) {
        int[] fillOrder = baked.fillOrder();
        MoldingVolumeMask volume = createPaidVolumeMask(baked, maximumCells);
        int cellCount = volume.volume();

        List<MoldingQuad> allZeroThickness = baked.surfaceMesh().stream()
            .filter(MoldingQuad::doubleSided)
            .toList();
        List<MoldingQuad> selectedZeroThickness;
        if (fillOrder.length == 0) {
            selectedZeroThickness = allZeroThickness;
        } else if (cellCount == fillOrder.length) {
            selectedZeroThickness = allZeroThickness;
        } else {
            selectedZeroThickness = allZeroThickness.stream()
                .filter(quad -> touchesFormedVolume(quad, volume))
                .toList();
        }
        return manufacture(volume, selectedZeroThickness);
    }

    /** 从持久化的实际形状重新建立派生网格与碰撞。 */
    public static ManufacturedMoldingShape manufacture(
        MoldingVolumeMask volume,
        List<MoldingQuad> zeroThicknessQuads
    ) {
        MoldingVolumeMask copiedVolume = volume.copy();
        List<MoldingQuad> copiedQuads = List.copyOf(zeroThicknessQuads);
        if (copiedQuads.stream().anyMatch(quad -> !quad.doubleSided())) {
            throw new IllegalArgumentException("Manufactured zero-thickness surfaces must be double-sided");
        }
        List<MoldingQuad> surface = createSurfaceMesh(copiedVolume);
        surface.addAll(copiedQuads);
        CollisionResult collision = createCollisionShape(copiedVolume);
        return new ManufacturedMoldingShape(
            copiedVolume,
            copiedQuads,
            surface,
            collision.boxes(),
            collision.complexityExceeded()
        );
    }

    /** 从持久化的零厚度表面恢复六邻接阻隔面，供内腔与液面算法共用。 */
    public static Set<MoldingBarrierFace> barrierFacesFromZeroThickness(
        List<MoldingQuad> zeroThicknessQuads
    ) {
        int sizeX = MoldingVolumeMask.SIZE;
        int sizeY = MoldingVolumeMask.SIZE;
        int sizeZ = MoldingVolumeMask.SIZE;
        for (MoldingQuad quad : zeroThicknessQuads) {
            for (MoldingVec3 point : List.of(quad.first(), quad.second(), quad.third(), quad.fourth())) {
                sizeX = Math.max(sizeX, (int) Math.ceil(point.x()));
                sizeY = Math.max(sizeY, (int) Math.ceil(point.y()));
                sizeZ = Math.max(sizeZ, (int) Math.ceil(point.z()));
            }
        }
        return barrierFacesFromZeroThickness(zeroThicknessQuads, sizeX, sizeY, sizeZ);
    }

    public static Set<MoldingBarrierFace> barrierFacesFromZeroThickness(
        List<MoldingQuad> zeroThicknessQuads,
        MoldingVolumeMask dimensions
    ) {
        return barrierFacesFromZeroThickness(
            zeroThicknessQuads,
            dimensions.sizeX(),
            dimensions.sizeY(),
            dimensions.sizeZ()
        );
    }

    private static Set<MoldingBarrierFace> barrierFacesFromZeroThickness(
        List<MoldingQuad> zeroThicknessQuads,
        int sizeX,
        int sizeY,
        int sizeZ
    ) {
        Set<MoldingBarrierFace> barriers = new HashSet<>();
        for (MoldingQuad quad : zeroThicknessQuads) {
            if (!quad.doubleSided()) {
                throw new IllegalArgumentException("Manufactured zero-thickness surfaces must be double-sided");
            }
            List<MoldingVec3> vertices = List.of(quad.first(), quad.second(), quad.third(), quad.fourth());
            Bounds bounds = Bounds.of(vertices);
            scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.X, sizeX, sizeY, sizeZ);
            scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Y, sizeX, sizeY, sizeZ);
            scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Z, sizeX, sizeY, sizeZ);
        }
        return Set.copyOf(barriers);
    }

    private static boolean touchesFormedVolume(MoldingQuad quad, MoldingVolumeMask volume) {
        List<MoldingVec3> vertices = List.of(quad.first(), quad.second(), quad.third(), quad.fourth());
        Bounds bounds = Bounds.of(vertices);
        Set<MoldingBarrierFace> barriers = new HashSet<>();
        scanZeroThicknessAxis(
            barriers,
            vertices,
            bounds,
            MoldingFaceDirection.Axis.X,
            volume.sizeX(),
            volume.sizeY(),
            volume.sizeZ()
        );
        scanZeroThicknessAxis(
            barriers,
            vertices,
            bounds,
            MoldingFaceDirection.Axis.Y,
            volume.sizeX(),
            volume.sizeY(),
            volume.sizeZ()
        );
        scanZeroThicknessAxis(
            barriers,
            vertices,
            bounds,
            MoldingFaceDirection.Axis.Z,
            volume.sizeX(),
            volume.sizeY(),
            volume.sizeZ()
        );
        return barriers.stream().anyMatch(barrier -> hasFormedCellBeside(barrier, volume));
    }

    private static boolean hasFormedCellBeside(MoldingBarrierFace barrier, MoldingVolumeMask volume) {
        int plane = barrier.plane();
        int u = barrier.u();
        int v = barrier.v();
        return switch (barrier.axis()) {
            case X -> volume.get(plane - 1, u, v) || volume.get(plane, u, v);
            case Y -> volume.get(u, plane - 1, v) || volume.get(u, plane, v);
            case Z -> volume.get(u, v, plane - 1) || volume.get(u, v, plane);
        };
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

    /** 保留每个可见源 Cube 的连续表面，完整成型制品不得显示制造体素的拟合轮廓。 */
    public static List<MoldingQuad> createExactSurfaceMesh(EditableMoldingModel model) {
        return createManufacturedGeometry(model, 1.0D).surfaceMesh();
    }

    /** 按熔体成型比例从模型底部向上水平裁切，并为每个被切开的源 Cube 生成平整封顶。 */
    public static ManufacturedMoldingGeometry createManufacturedGeometry(
        EditableMoldingModel model,
        double formedProportion
    ) {
        if (!Double.isFinite(formedProportion) || formedProportion < 0.0D || formedProportion > 1.0D) {
            throw new IllegalArgumentException("Manufactured proportion must be between zero and one");
        }
        ManufacturedKey key = new ManufacturedKey(
            bake(model).modelHash(),
            Double.doubleToLongBits(formedProportion)
        );
        synchronized (MANUFACTURED_CACHE) {
            ManufacturedMoldingGeometry cached = MANUFACTURED_CACHE.get(key);
            if (cached != null) return cached;
        }
        ManufacturedMoldingGeometry manufactured = createManufacturedGeometryUncached(model, formedProportion);
        synchronized (MANUFACTURED_CACHE) {
            ManufacturedMoldingGeometry cached = MANUFACTURED_CACHE.get(key);
            if (cached != null) return cached;
            MANUFACTURED_CACHE.put(key, manufactured);
        }
        return manufactured;
    }

    /** 将中心采样掩码补齐为与源凸体存在正体积交集的全部像素格。 */
    public static MoldingVolumeMask createIntersectingVolumeMask(
        EditableMoldingModel model,
        MoldingVolumeMask sampledVolume
    ) {
        MoldingVolumeMask result = sampledVolume.copy();
        for (MoldingConvexHull hull : createManufacturedGeometry(model, 1.0D).collisionHulls()) {
            MoldingConvexHull.Bounds bounds = hull.bounds();
            int minX = cellMinimum(bounds.minimum().x(), result.sizeX());
            int minY = cellMinimum(bounds.minimum().y(), result.sizeY());
            int minZ = cellMinimum(bounds.minimum().z(), result.sizeZ());
            int maxX = cellMaximum(bounds.maximum().x(), result.sizeX());
            int maxY = cellMaximum(bounds.maximum().y(), result.sizeY());
            int maxZ = cellMaximum(bounds.maximum().z(), result.sizeZ());
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        if (!result.get(x, y, z) && convexHullIntersectsCell(hull, x, y, z)) {
                            result.set(x, y, z);
                        }
                    }
                }
            }
        }
        return result;
    }

    /** 返回源凸体与一个 1 px 单元正体积相交后形成的闭合表面。 */
    public static List<MoldingQuad> clipConvexHullToCell(
        MoldingConvexHull hull,
        int x,
        int y,
        int z
    ) {
        return clipConvexHull(hull, x, y, z, x + 1.0D, y + 1.0D, z + 1.0D);
    }

    public static List<MoldingQuad> clipConvexHull(
        MoldingConvexHull hull,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        if (maxX <= minX || maxY <= minY || maxZ <= minZ) return List.of();
        List<FacePolygon> clipped = clipConvexHullToBox(hull, minX, minY, minZ, maxX, maxY, maxZ);
        if (!hasPositiveVolume(clipped)) return List.of();
        List<MoldingQuad> surface = new ArrayList<>();
        for (FacePolygon face : clipped) addPolygonSurfaces(surface, face, false);
        return List.copyOf(surface);
    }

    public static boolean convexHullIntersectsCell(MoldingConvexHull hull, int x, int y, int z) {
        return hasPositiveVolume(clipConvexHullToBox(hull, x, y, z, x + 1.0D, y + 1.0D, z + 1.0D));
    }

    private static List<FacePolygon> clipConvexHullToBox(
        MoldingConvexHull hull,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        MoldingConvexHull.Bounds bounds = hull.bounds();
        if (bounds.maximum().x() <= minX + EPSILON || bounds.minimum().x() >= maxX - EPSILON
            || bounds.maximum().y() <= minY + EPSILON || bounds.minimum().y() >= maxY - EPSILON
            || bounds.maximum().z() <= minZ + EPSILON || bounds.minimum().z() >= maxZ - EPSILON) {
            return List.of();
        }
        List<FacePolygon> faces = hull.faces().stream().map(face -> new FacePolygon(
            face.vertices().stream().map(hull.vertices()::get).toList(),
            face.normal()
        )).toList();
        faces = clipPolyhedron(faces, 0, minX, true);
        faces = clipPolyhedron(faces, 0, maxX, false);
        faces = clipPolyhedron(faces, 1, minY, true);
        faces = clipPolyhedron(faces, 1, maxY, false);
        faces = clipPolyhedron(faces, 2, minZ, true);
        return clipPolyhedron(faces, 2, maxZ, false);
    }

    private static List<FacePolygon> clipPolyhedron(
        List<FacePolygon> input,
        int axis,
        double boundary,
        boolean keepGreater
    ) {
        if (input.isEmpty()) return List.of();
        List<FacePolygon> output = new ArrayList<>(input.size() + 1);
        List<MoldingVec3> capVertices = new ArrayList<>();
        for (FacePolygon face : input) {
            List<MoldingVec3> clipped = clipPolygon(face.vertices(), axis, boundary, keepGreater, capVertices);
            if (clipped.size() >= 3 && polygonAreaSquared(clipped) > EPSILON * EPSILON) {
                output.add(new FacePolygon(clipped, face.normal()));
            }
        }
        capVertices = removeCollinearVertices(capVertices);
        if (capVertices.size() >= 3) {
            MoldingVec3 normal = axisVector(axis, keepGreater ? -1.0D : 1.0D);
            List<MoldingVec3> cap = orientCap(capVertices, axis, normal);
            if (polygonAreaSquared(cap) > EPSILON * EPSILON) output.add(new FacePolygon(cap, normal));
        }
        return List.copyOf(output);
    }

    private static List<MoldingVec3> clipPolygon(
        List<MoldingVec3> input,
        int axis,
        double boundary,
        boolean keepGreater,
        List<MoldingVec3> capVertices
    ) {
        List<MoldingVec3> output = new ArrayList<>(input.size() + 2);
        MoldingVec3 previous = input.getLast();
        double previousCoordinate = coordinate(previous, axis);
        boolean previousInside = inside(previousCoordinate, boundary, keepGreater);
        for (MoldingVec3 current : input) {
            double currentCoordinate = coordinate(current, axis);
            boolean currentInside = inside(currentCoordinate, boundary, keepGreater);
            if (currentInside != previousInside) {
                double amount = Math.clamp(
                    (boundary - previousCoordinate) / (currentCoordinate - previousCoordinate),
                    0.0D,
                    1.0D
                );
                MoldingVec3 intersection = previous.add(current.subtract(previous).scale(amount));
                addDistinct(output, intersection);
                addDistinct(capVertices, intersection);
            }
            if (currentInside) addDistinct(output, current);
            previous = current;
            previousCoordinate = currentCoordinate;
            previousInside = currentInside;
        }
        if (output.size() > 1 && samePoint(output.getFirst(), output.getLast())) output.removeLast();
        return List.copyOf(output);
    }

    private static List<MoldingVec3> orientCap(
        List<MoldingVec3> vertices,
        int axis,
        MoldingVec3 normal
    ) {
        MoldingVec3 center = MoldingVec3.ZERO;
        for (MoldingVec3 vertex : vertices) center = center.add(vertex);
        center = center.scale(1.0D / vertices.size());
        MoldingVec3 capCenter = center;
        int firstTransverse = axis == 0 ? 1 : 0;
        int secondTransverse = axis == 2 ? 1 : 2;
        List<MoldingVec3> result = new ArrayList<>(vertices);
        result.sort(Comparator.comparingDouble(vertex -> Math.atan2(
            coordinate(vertex, secondTransverse) - coordinate(capCenter, secondTransverse),
            coordinate(vertex, firstTransverse) - coordinate(capCenter, firstTransverse)
        )));
        MoldingVec3 measured = unitNormal(result.get(0), result.get(1), result.get(2));
        if (measured.dot(normal) < 0.0D) Collections.reverse(result);
        return List.copyOf(result);
    }

    private static List<MoldingVec3> removeCollinearVertices(List<MoldingVec3> vertices) {
        List<MoldingVec3> unique = new ArrayList<>();
        for (MoldingVec3 vertex : vertices) addDistinct(unique, vertex);
        if (unique.size() < 3) return List.copyOf(unique);
        MoldingVec3 center = MoldingVec3.ZERO;
        for (MoldingVec3 vertex : unique) center = center.add(vertex);
        center = center.scale(1.0D / unique.size());
        Bounds bounds = Bounds.of(unique);
        MoldingVec3 extent = bounds.max().subtract(bounds.min());
        int axis = extent.x() <= EPSILON ? 0 : extent.y() <= EPSILON ? 1 : 2;
        List<MoldingVec3> sorted = orientUnorientedPolygon(unique, center, axis);
        List<MoldingVec3> result = new ArrayList<>(sorted.size());
        for (int index = 0; index < sorted.size(); index++) {
            MoldingVec3 vertex = sorted.get(index);
            MoldingVec3 previous = sorted.get(Math.floorMod(index - 1, sorted.size()));
            MoldingVec3 next = sorted.get((index + 1) % sorted.size());
            if (vertex.subtract(previous).cross(next.subtract(vertex)).lengthSquared() > EPSILON * EPSILON) {
                result.add(vertex);
            }
        }
        return List.copyOf(result);
    }

    private static List<MoldingVec3> orientUnorientedPolygon(
        List<MoldingVec3> vertices,
        MoldingVec3 center,
        int axis
    ) {
        int firstTransverse = axis == 0 ? 1 : 0;
        int secondTransverse = axis == 2 ? 1 : 2;
        return vertices.stream().sorted(Comparator.comparingDouble(vertex -> Math.atan2(
            coordinate(vertex, secondTransverse) - coordinate(center, secondTransverse),
            coordinate(vertex, firstTransverse) - coordinate(center, firstTransverse)
        ))).toList();
    }

    private static boolean hasPositiveVolume(List<FacePolygon> faces) {
        double volumeTimesSix = 0.0D;
        for (FacePolygon face : faces) {
            MoldingVec3 first = face.vertices().getFirst();
            for (int index = 1; index + 1 < face.vertices().size(); index++) {
                volumeTimesSix += first.dot(
                    face.vertices().get(index).cross(face.vertices().get(index + 1))
                );
            }
        }
        return Math.abs(volumeTimesSix) > VOLUME_EPSILON * 6.0D;
    }

    private static double polygonAreaSquared(List<MoldingVec3> vertices) {
        MoldingVec3 area = MoldingVec3.ZERO;
        MoldingVec3 first = vertices.getFirst();
        for (int index = 1; index + 1 < vertices.size(); index++) {
            area = area.add(vertices.get(index).subtract(first).cross(vertices.get(index + 1).subtract(first)));
        }
        return area.lengthSquared();
    }

    private static boolean inside(double coordinate, double boundary, boolean keepGreater) {
        return keepGreater ? coordinate >= boundary - EPSILON : coordinate <= boundary + EPSILON;
    }

    private static double coordinate(MoldingVec3 point, int axis) {
        return switch (axis) {
            case 0 -> point.x();
            case 1 -> point.y();
            case 2 -> point.z();
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static MoldingVec3 axisVector(int axis, double direction) {
        return switch (axis) {
            case 0 -> new MoldingVec3(direction, 0.0D, 0.0D);
            case 1 -> new MoldingVec3(0.0D, direction, 0.0D);
            case 2 -> new MoldingVec3(0.0D, 0.0D, direction);
            default -> throw new IllegalArgumentException("Unknown molding axis " + axis);
        };
    }

    private static ManufacturedMoldingGeometry createManufacturedGeometryUncached(
        EditableMoldingModel model,
        double formedProportion
    ) {
        BakeSpace space = spaceFor(model);
        Map<UUID, MoldingGroup> groups = model.groupMap();
        List<ElementGeometry> elements = new ArrayList<>();
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        for (MoldingElement element : model.elements()) {
            List<MoldingGroup> hierarchy = hierarchy(element, groups);
            if (!element.visible() || hierarchy.stream().anyMatch(group -> !group.visible())) continue;
            List<MoldingVec3> vertices = translatedVertices(element, hierarchy, space.offset());
            validateWorkspace(vertices, space);
            elements.add(new ElementGeometry(element.hasVolume(), vertices));
            for (MoldingVec3 vertex : vertices) {
                minimumY = Math.min(minimumY, vertex.y());
                maximumY = Math.max(maximumY, vertex.y());
            }
        }
        if (elements.isEmpty()) return new ManufacturedMoldingGeometry(List.of(), List.of());

        double cutY = formedProportion >= 1.0D
            ? maximumY
            : minimumY + (maximumY - minimumY) * formedProportion;
        List<MoldingQuad> surface = new ArrayList<>();
        List<MoldingConvexHull> hulls = new ArrayList<>();
        for (ElementGeometry element : elements) {
            if (element.hasVolume()) {
                List<FacePolygon> faces = clipCube(element.vertices(), cutY);
                if (faces.isEmpty()) continue;
                for (FacePolygon face : faces) addPolygonSurfaces(surface, face, false);
                hulls.add(createConvexHull(faces));
            } else {
                MoldingVec3 normal = unitNormal(
                    element.vertices().get(0),
                    element.vertices().get(1),
                    element.vertices().get(2)
                );
                List<MoldingVec3> clipped = clipBelow(element.vertices(), cutY);
                if (clipped.size() >= 3) {
                    addPolygonSurfaces(surface, new FacePolygon(clipped, normal), true);
                }
            }
        }
        return new ManufacturedMoldingGeometry(surface, hulls);
    }

    private static <K, V> Map<K, V> boundedCache(int maximumSize) {
        return new LinkedHashMap<>(maximumSize, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return this.size() > maximumSize;
            }
        };
    }

    private static void cacheIdentity(EditableMoldingModel model, BakedMoldingModel baked) {
        synchronized (IDENTITY_BAKED_CACHE) {
            if (IDENTITY_BAKED_CACHE.containsKey(model)) return;
            while (IDENTITY_BAKED_CACHE.size() >= BAKED_CACHE_LIMIT) {
                IDENTITY_BAKED_CACHE.remove(BAKED_IDENTITY_ORDER.removeFirst());
            }
            IDENTITY_BAKED_CACHE.put(model, baked);
            BAKED_IDENTITY_ORDER.addLast(model);
        }
    }

    private record ManufacturedKey(String modelHash, long formedProportionBits) {
    }

    private static List<FacePolygon> orientedCubeFaces(List<MoldingVec3> vertices) {
        MoldingVec3 center = MoldingVec3.ZERO;
        for (MoldingVec3 vertex : vertices) center = center.add(vertex);
        center = center.scale(1.0D / vertices.size());
        List<FacePolygon> faces = new ArrayList<>(CUBE_FACES.length);
        for (int[] face : CUBE_FACES) {
            MoldingVec3 first = vertices.get(face[0]);
            MoldingVec3 second = vertices.get(face[1]);
            MoldingVec3 third = vertices.get(face[2]);
            MoldingVec3 fourth = vertices.get(face[3]);
            MoldingVec3 normal = unitNormal(first, second, third);
            MoldingVec3 faceCenter = first.add(second).add(third).add(fourth).scale(0.25D);
            if (normal.dot(faceCenter.subtract(center)) < 0.0D) {
                MoldingVec3 swap = first;
                first = fourth;
                fourth = swap;
                swap = second;
                second = third;
                third = swap;
                normal = normal.scale(-1.0D);
            }
            faces.add(new FacePolygon(List.of(first, second, third, fourth), normal));
        }
        return faces;
    }

    private static List<FacePolygon> clipCube(List<MoldingVec3> vertices, double cutY) {
        Bounds bounds = Bounds.of(vertices);
        if (cutY <= bounds.min().y() + EPSILON) return List.of();
        List<FacePolygon> original = orientedCubeFaces(vertices);
        if (cutY >= bounds.max().y() - EPSILON) return original;

        List<FacePolygon> clippedFaces = new ArrayList<>(7);
        List<MoldingVec3> capVertices = new ArrayList<>(6);
        for (FacePolygon face : original) {
            List<MoldingVec3> clipped = clipBelow(face.vertices(), cutY);
            if (clipped.size() < 3) continue;
            clippedFaces.add(new FacePolygon(clipped, face.normal()));
            for (MoldingVec3 vertex : clipped) {
                if (Math.abs(vertex.y() - cutY) <= EPSILON) addDistinct(capVertices, vertex);
            }
        }
        if (capVertices.size() >= 3) {
            MoldingVec3 center = MoldingVec3.ZERO;
            for (MoldingVec3 vertex : capVertices) center = center.add(vertex);
            center = center.scale(1.0D / capVertices.size());
            MoldingVec3 capCenter = center;
            capVertices.sort(Comparator.comparingDouble((MoldingVec3 vertex) ->
                Math.atan2(vertex.z() - capCenter.z(), vertex.x() - capCenter.x())
            ).reversed());
            clippedFaces.add(new FacePolygon(capVertices, new MoldingVec3(0.0D, 1.0D, 0.0D)));
        }
        return List.copyOf(clippedFaces);
    }

    private static List<MoldingVec3> clipBelow(List<MoldingVec3> polygon, double cutY) {
        List<MoldingVec3> result = new ArrayList<>(polygon.size() + 1);
        MoldingVec3 previous = polygon.getLast();
        boolean previousInside = previous.y() <= cutY + EPSILON;
        for (MoldingVec3 current : polygon) {
            boolean currentInside = current.y() <= cutY + EPSILON;
            if (previousInside != currentInside) {
                double denominator = current.y() - previous.y();
                double progress = Math.clamp((cutY - previous.y()) / denominator, 0.0D, 1.0D);
                addDistinct(result, previous.add(current.subtract(previous).scale(progress)));
            }
            if (currentInside) addDistinct(result, current);
            previous = current;
            previousInside = currentInside;
        }
        if (result.size() > 1 && samePoint(result.getFirst(), result.getLast())) {
            result.removeLast();
        }
        return List.copyOf(result);
    }

    private static void addPolygonSurfaces(
        List<MoldingQuad> output,
        FacePolygon polygon,
        boolean doubleSided
    ) {
        List<MoldingVec3> vertices = polygon.vertices();
        if (vertices.size() == 4) {
            output.add(new MoldingQuad(
                vertices.get(0),
                vertices.get(1),
                vertices.get(2),
                vertices.get(3),
                polygon.normal(),
                doubleSided
            ));
            return;
        }
        for (int index = 1; index < vertices.size() - 1; index++) {
            MoldingVec3 first = vertices.getFirst();
            MoldingVec3 second = vertices.get(index);
            MoldingVec3 third = vertices.get(index + 1);
            output.add(new MoldingQuad(
                first,
                second,
                third,
                first.add(third).scale(0.5D),
                polygon.normal(),
                doubleSided
            ));
        }
    }

    private static MoldingConvexHull createConvexHull(List<FacePolygon> polygons) {
        List<MoldingVec3> vertices = new ArrayList<>(MoldingConvexHull.MAX_VERTICES);
        List<MoldingConvexFace> faces = new ArrayList<>(polygons.size());
        for (FacePolygon polygon : polygons) {
            List<Integer> indices = new ArrayList<>(polygon.vertices().size());
            for (MoldingVec3 vertex : polygon.vertices()) {
                int index = indexOf(vertices, vertex);
                if (index < 0) {
                    index = vertices.size();
                    vertices.add(vertex);
                }
                indices.add(index);
            }
            faces.add(new MoldingConvexFace(indices, polygon.normal()));
        }
        return new MoldingConvexHull(vertices, faces);
    }

    private static int indexOf(List<MoldingVec3> vertices, MoldingVec3 target) {
        for (int index = 0; index < vertices.size(); index++) {
            if (samePoint(vertices.get(index), target)) return index;
        }
        return -1;
    }

    private static void addDistinct(List<MoldingVec3> vertices, MoldingVec3 candidate) {
        if (vertices.stream().noneMatch(vertex -> samePoint(vertex, candidate))) vertices.add(candidate);
    }

    private static boolean samePoint(MoldingVec3 first, MoldingVec3 second) {
        return first.subtract(second).lengthSquared() <= EPSILON * EPSILON;
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

    private static List<MoldingVec3> translatedVertices(
        MoldingElement element,
        List<MoldingGroup> hierarchy,
        MoldingVec3 offset
    ) {
        return transformedVertices(element, hierarchy).stream().map(vertex -> vertex.add(offset)).toList();
    }

    /** 根据模型外接范围选择兼容的烘焙坐标空间，超限模型平移到局部原点。 */
    private static BakeSpace spaceFor(EditableMoldingModel model) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        MoldingVec3 minimum = null;
        MoldingVec3 maximum = null;
        for (MoldingElement element : model.elements()) {
            List<MoldingGroup> hierarchy = hierarchy(element, groups);
            for (MoldingVec3 vertex : transformedVertices(element, hierarchy)) {
                minimum = minimum == null ? vertex : minimum.min(vertex);
                maximum = maximum == null ? vertex : maximum.max(vertex);
            }
        }
        if (minimum == null) return new BakeSpace(48, 48, 48, MoldingVec3.ZERO);
        MoldingVec3 size = maximum.subtract(minimum);
        if (minimum.x() >= -EPSILON && minimum.y() >= -EPSILON && minimum.z() >= -EPSILON
            && maximum.x() <= 48.0D + EPSILON && maximum.y() <= 48.0D + EPSILON
            && maximum.z() <= 48.0D + EPSILON) {
            return new BakeSpace(48, 48, 48, MoldingVec3.ZERO);
        }
        int minX = (int) Math.floor(minimum.x());
        int minY = (int) Math.floor(minimum.y());
        int minZ = (int) Math.floor(minimum.z());
        int maxX = (int) Math.ceil(maximum.x());
        int maxY = (int) Math.ceil(maximum.y());
        int maxZ = (int) Math.ceil(maximum.z());
        int sizeX = Math.max(1, maxX - minX);
        int sizeY = Math.max(1, maxY - minY);
        int sizeZ = Math.max(1, maxZ - minZ);
        if (sizeX > MoldingVolumeMask.MAX_SIZE || sizeY > MoldingVolumeMask.MAX_SIZE
            || sizeZ > MoldingVolumeMask.MAX_SIZE) {
            throw new IllegalArgumentException("Molding model exceeds the supported dynamic workspace");
        }
        return new BakeSpace(sizeX, sizeY, sizeZ, new MoldingVec3(-minX, -minY, -minZ));
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

    private static void validateWorkspace(List<MoldingVec3> vertices, BakeSpace space) {
        for (MoldingVec3 vertex : vertices) {
            if (vertex.x() < -EPSILON || vertex.x() > space.sizeX() + EPSILON
                || vertex.y() < -EPSILON || vertex.y() > space.sizeY() + EPSILON
                || vertex.z() < -EPSILON || vertex.z() > space.sizeZ() + EPSILON) {
                throw new IllegalArgumentException("Molding element exceeds the supported molding workspace");
            }
        }
    }

    private static void rasterizeCube(
        MoldingVolumeMask volume,
        MoldingElement element,
        List<MoldingGroup> hierarchy,
        List<MoldingVec3> vertices,
        BakeSpace space
    ) {
        Bounds bounds = Bounds.of(vertices);
        int minX = cellMinimum(bounds.min().x(), space.sizeX());
        int minY = cellMinimum(bounds.min().y(), space.sizeY());
        int minZ = cellMinimum(bounds.min().z(), space.sizeZ());
        int maxX = cellMaximum(bounds.max().x(), space.sizeX());
        int maxY = cellMaximum(bounds.max().y(), space.sizeY());
        int maxZ = cellMaximum(bounds.max().z(), space.sizeZ());
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    MoldingVec3 local = inverseTransforms(
                        new MoldingVec3(x + 0.5D, y + 0.5D, z + 0.5D).subtract(space.offset()),
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

    private static int cellMinimum(double coordinate, int size) {
        return Math.clamp((int) Math.floor(coordinate), 0, size - 1);
    }

    private static int cellMaximum(double coordinate, int size) {
        return Math.clamp((int) Math.ceil(coordinate) - 1, 0, size - 1);
    }

    private static void rasterizeZeroThicknessCube(
        Set<MoldingBarrierFace> barriers,
        List<MoldingQuad> surface,
        List<MoldingVec3> vertices,
        BakeSpace space
    ) {
        surface.add(createZeroThicknessQuad(vertices));

        Bounds bounds = Bounds.of(vertices);
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.X,
            space.sizeX(), space.sizeY(), space.sizeZ());
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Y,
            space.sizeX(), space.sizeY(), space.sizeZ());
        scanZeroThicknessAxis(barriers, vertices, bounds, MoldingFaceDirection.Axis.Z,
            space.sizeX(), space.sizeY(), space.sizeZ());
    }

    private static MoldingQuad createZeroThicknessQuad(List<MoldingVec3> vertices) {
        return new MoldingQuad(
            vertices.get(0),
            vertices.get(1),
            vertices.get(2),
            vertices.get(3),
            unitNormal(vertices.get(0), vertices.get(1), vertices.get(2)),
            true
        );
    }

    private static MoldingVec3 unitNormal(MoldingVec3 first, MoldingVec3 second, MoldingVec3 third) {
        MoldingVec3 normal = second.subtract(first).cross(third.subtract(first));
        double length = Math.sqrt(normal.lengthSquared());
        if (length <= EPSILON) throw new IllegalArgumentException("Degenerate molding surface");
        return normal.scale(1.0D / length);
    }

    private static void scanZeroThicknessAxis(
        Set<MoldingBarrierFace> barriers,
        List<MoldingVec3> vertices,
        Bounds bounds,
        MoldingFaceDirection.Axis axis,
        int sizeX,
        int sizeY,
        int sizeZ
    ) {
        double axialMin = component(bounds.min(), axis);
        double axialMax = component(bounds.max(), axis);
        MoldingFaceDirection.Axis uAxis = axis == MoldingFaceDirection.Axis.X
            ? MoldingFaceDirection.Axis.Y
            : MoldingFaceDirection.Axis.X;
        MoldingFaceDirection.Axis vAxis = axis == MoldingFaceDirection.Axis.Z
            ? MoldingFaceDirection.Axis.Y
            : MoldingFaceDirection.Axis.Z;
        int axialSize = axis == MoldingFaceDirection.Axis.X ? sizeX
            : axis == MoldingFaceDirection.Axis.Y ? sizeY : sizeZ;
        int uSize = uAxis == MoldingFaceDirection.Axis.X ? sizeX
            : uAxis == MoldingFaceDirection.Axis.Y ? sizeY : sizeZ;
        int vSize = vAxis == MoldingFaceDirection.Axis.X ? sizeX
            : vAxis == MoldingFaceDirection.Axis.Y ? sizeY : sizeZ;
        int minPlane = Math.clamp((int) Math.ceil(axialMin - 0.5D - EPSILON), 0, axialSize);
        int maxPlane = Math.clamp((int) Math.floor(axialMax + 0.5D + EPSILON), 0, axialSize);
        int minU = transverseMinimum(component(bounds.min(), uAxis), uSize);
        int maxU = transverseMaximum(component(bounds.max(), uAxis), uSize);
        int minV = transverseMinimum(component(bounds.min(), vAxis), vSize);
        int maxV = transverseMaximum(component(bounds.max(), vAxis), vSize);

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

    private static int transverseMinimum(double coordinate, int size) {
        return Math.clamp((int) Math.ceil(coordinate - 0.5D - EPSILON) - 1, 0, size - 1);
    }

    private static int transverseMaximum(double coordinate, int size) {
        return Math.clamp((int) Math.floor(coordinate - 0.5D + EPSILON) + 1, 0, size - 1);
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
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    if (volume.get(x, y, z)) result[output++] = volume.indexOf(x, y, z);
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
        int planeSize = axisSize(volume, direction.axis());
        int uSize = transverseSize(volume, direction.axis(), false);
        int vSize = transverseSize(volume, direction.axis(), true);
        for (int plane = 0; plane <= planeSize; plane++) {
            boolean[][] active = new boolean[uSize][vSize];
            for (int u = 0; u < uSize; u++) {
                for (int v = 0; v < vSize; v++) {
                    active[u][v] = hasExposedFace(volume, direction, plane, u, v);
                }
            }
            mergeSurfacePlane(active, direction, plane, uSize, vSize, output);
        }
    }

    private static int axisSize(MoldingVolumeMask volume, MoldingFaceDirection.Axis axis) {
        return switch (axis) {
            case X -> volume.sizeX();
            case Y -> volume.sizeY();
            case Z -> volume.sizeZ();
        };
    }

    private static int transverseSize(
        MoldingVolumeMask volume,
        MoldingFaceDirection.Axis axis,
        boolean second
    ) {
        return switch (axis) {
            case X -> second ? volume.sizeZ() : volume.sizeY();
            case Y -> second ? volume.sizeZ() : volume.sizeX();
            case Z -> second ? volume.sizeY() : volume.sizeX();
        };
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
        int uSize,
        int vSize,
        List<MoldingQuad> output
    ) {
        for (int u = 0; u < uSize; u++) {
            for (int v = 0; v < vSize; v++) {
                if (!active[u][v]) continue;
                int vLength = 1;
                while (v + vLength < vSize && active[u][v + vLength]) vLength++;
                int uLength = 1;
                while (u + uLength < uSize
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
        BitSet consumed = new BitSet(volume.cellCount());
        List<MoldingCollisionBox> boxes = new ArrayList<>();
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    int index = volume.indexOf(x, y, z);
                    if (!volume.get(x, y, z) || consumed.get(index)) continue;
                    if (boxes.size() >= MAX_COLLISION_BOXES) {
                        return new CollisionResult(boxes, true);
                    }
                    int maxX = extendX(volume, consumed, x, y, z);
                    int maxZ = extendZ(volume, consumed, x, maxX, y, z);
                    int maxY = extendY(volume, consumed, x, maxX, y, z, maxZ);
                    markConsumed(volume, consumed, x, maxX, y, maxY, z, maxZ);
                    boxes.add(new MoldingCollisionBox(x, y, z, maxX, maxY, maxZ));
                }
            }
        }
        return new CollisionResult(boxes, false);
    }

    private static int extendX(MoldingVolumeMask volume, BitSet consumed, int x, int y, int z) {
        int result = x + 1;
        while (result < volume.sizeX()
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
        while (result < volume.sizeZ()
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
        while (result < volume.sizeY()
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
        return volume.get(x, y, z) && !consumed.get(volume.indexOf(x, y, z));
    }

    private static void markConsumed(
        MoldingVolumeMask volume,
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
                    consumed.set(volume.indexOf(x, y, z));
                }
            }
        }
    }

    private static List<MoldingCavity> findCavities(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers
    ) {
        int paddedX = volume.sizeX() + 2;
        int paddedY = volume.sizeY() + 2;
        int paddedZ = volume.sizeZ() + 2;
        BitSet exterior = new BitSet(paddedX * paddedY * paddedZ);
        flood(volume, barriers, -1, -1, -1, exterior, null);
        BitSet assigned = new BitSet(volume.cellCount());
        List<MoldingCavity> cavities = new ArrayList<>();
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    int volumeIndex = volume.indexOf(x, y, z);
                    if (volume.get(x, y, z)
                        || exterior.get(paddedIndex(volume, x, y, z))
                        || assigned.get(volumeIndex)) {
                        continue;
                    }
                    BitSet component = new BitSet(volume.cellCount());
                    BoundsAccumulator bounds = new BoundsAccumulator(volume);
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
            if (!inPaddedBounds(volume, cell.x(), cell.y(), cell.z())
                || volume.get(cell.x(), cell.y(), cell.z())) continue;
            int visitIndex = cavity == null
                ? paddedIndex(volume, cell.x(), cell.y(), cell.z())
                : volume.indexOf(cell.x(), cell.y(), cell.z());
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
                if (!inPaddedBounds(volume, next.x(), next.y(), next.z())
                    || blocked(volume, barriers, cell, direction)) {
                    continue;
                }
                queue.addLast(next);
            }
        }
    }

    private static boolean blocked(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        GridCell cell,
        MoldingFaceDirection direction
    ) {
        int x = cell.x();
        int y = cell.y();
        int z = cell.z();
        return switch (direction.axis()) {
            case X -> transitionCoordinate(x, direction.stepX(), volume.sizeX())
                && y >= 0 && y < volume.sizeY() && z >= 0 && z < volume.sizeZ()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Y -> transitionCoordinate(y, direction.stepY(), volume.sizeY())
                && x >= 0 && x < volume.sizeX() && z >= 0 && z < volume.sizeZ()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Z -> transitionCoordinate(z, direction.stepZ(), volume.sizeZ())
                && x >= 0 && x < volume.sizeX() && y >= 0 && y < volume.sizeY()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
        };
    }

    private static boolean transitionCoordinate(int coordinate, int step, int size) {
        return coordinate >= 0 && coordinate < size
            || coordinate == -1 && step > 0
            || coordinate == size && step < 0;
    }

    private static boolean inPaddedBounds(MoldingVolumeMask volume, int x, int y, int z) {
        return x >= -1 && x <= volume.sizeX()
            && y >= -1 && y <= volume.sizeY()
            && z >= -1 && z <= volume.sizeZ();
    }

    private static int paddedIndex(MoldingVolumeMask volume, int x, int y, int z) {
        int paddedX = volume.sizeX() + 2;
        int paddedZ = volume.sizeZ() + 2;
        return ((y + 1) * paddedX + x + 1) * paddedZ + z + 1;
    }

    private record ElementGeometry(boolean hasVolume, List<MoldingVec3> vertices) {
        private ElementGeometry {
            vertices = List.copyOf(vertices);
        }
    }

    private record BakeSpace(int sizeX, int sizeY, int sizeZ, MoldingVec3 offset) {
    }

    private record FacePolygon(List<MoldingVec3> vertices, MoldingVec3 normal) {
        private FacePolygon {
            vertices = List.copyOf(vertices);
        }
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
        private int minX;
        private int minY;
        private int minZ;
        private int maxX;
        private int maxY;
        private int maxZ;

        private BoundsAccumulator(MoldingVolumeMask volume) {
            this.minX = volume.sizeX();
            this.minY = volume.sizeY();
            this.minZ = volume.sizeZ();
        }

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
