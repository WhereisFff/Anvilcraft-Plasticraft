package dev.anvilcraft.plasticraft.molding.bake;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 按固定世界 Y 轴判断塑料模型是否具有三段式铁砧外形。 */
public final class MoldingAnvilShapeAnalyzer {
    public static final int MIN_BOTTOM_WIDTH = 12;
    public static final int MIN_BOTTOM_DEPTH = 12;
    public static final int MIN_TOTAL_THICKNESS = 10;
    public static final int MIN_BOTTOM_THICKNESS = 3;
    public static final int MIN_MIDDLE_THICKNESS = 3;
    public static final int MIN_TOP_THICKNESS = 4;
    public static final int MAX_TOP_AREA_DEFICIT = 16;
    public static final int MIN_GIANT_BOTTOM_WIDTH = 40;
    public static final int MIN_GIANT_BOTTOM_DEPTH = 40;
    private static final int NEGATIVE_BOTTOM_OUTLINE_BORDER = 1;
    private static final double EPSILON = 1.0E-7D;
    private static final int[][] CUBE_FACES = {
        {0, 4, 6, 2}, {1, 3, 7, 5},
        {0, 1, 5, 4}, {2, 6, 7, 3},
        {0, 2, 3, 1}, {4, 5, 7, 6}
    };

    private MoldingAnvilShapeAnalyzer() {
    }

    public static MoldingAnvilShapeAnalysis analyze(MoldingVolumeMask volume) {
        return analyze(volume, false);
    }

    public static MoldingAnvilShapeAnalysis analyze(
        EditableMoldingModel model,
        MoldingVolumeMask volume
    ) {
        return analyze(volume, hasMinimumNegativeBottom(model));
    }

    public static boolean hasGiantBottom(MoldingVolumeMask volume) {
        BitSet cells = volume.copyBits();
        int minimumY = volume.sizeY();
        for (int index = cells.nextSetBit(0); index >= 0; index = cells.nextSetBit(index + 1)) {
            minimumY = Math.min(minimumY, volume.yOf(index));
        }
        return minimumY < volume.sizeY()
            && hasFlatRectangle(
                layer(volume, minimumY),
                volume.sizeZ(),
                MIN_GIANT_BOTTOM_WIDTH,
                MIN_GIANT_BOTTOM_DEPTH
            );
    }

    private static MoldingAnvilShapeAnalysis analyze(
        MoldingVolumeMask volume,
        boolean hasNegativeBottom
    ) {
        if (volume.isEmpty()) return MoldingAnvilShapeAnalysis.invalid("anvil_empty");

        BitSet cells = volume.copyBits();
        int minimumY = volume.sizeY();
        int maximumY = -1;
        for (int index = cells.nextSetBit(0); index >= 0; index = cells.nextSetBit(index + 1)) {
            int y = volume.yOf(index);
            minimumY = Math.min(minimumY, y);
            maximumY = Math.max(maximumY, y);
        }
        int maximumYExclusive = maximumY + 1;
        int totalThickness = maximumYExclusive - minimumY;
        if (totalThickness < MIN_TOTAL_THICKNESS) {
            return MoldingAnvilShapeAnalysis.invalid("anvil_height_too_short");
        }

        BitSet bottomLayer = layer(volume, minimumY);
        Bounds bottomBounds = bounds(bottomLayer, volume.sizeZ());
        boolean standardBottom = hasFlatRectangle(
            bottomLayer,
            volume.sizeZ(),
            MIN_BOTTOM_WIDTH,
            MIN_BOTTOM_DEPTH
        );
        boolean negativeBottom = hasNegativeBottom && hasFlatRectangle(
            bottomLayer,
            volume.sizeZ(),
            MIN_BOTTOM_WIDTH - NEGATIVE_BOTTOM_OUTLINE_BORDER * 2,
            MIN_BOTTOM_DEPTH - NEGATIVE_BOTTOM_OUTLINE_BORDER * 2
        );
        if (bottomBounds == null
            || (!standardBottom && !negativeBottom)
            || !isConnected(bottomLayer, volume.sizeZ())) {
            return MoldingAnvilShapeAnalysis.invalid("anvil_bottom_too_small");
        }
        boolean giant = hasFlatRectangle(
            bottomLayer,
            volume.sizeZ(),
            MIN_GIANT_BOTTOM_WIDTH,
            MIN_GIANT_BOTTOM_DEPTH
        );

        for (int y = minimumY; y < maximumYExclusive; y++) {
            if (layer(volume, y).isEmpty()) {
                return MoldingAnvilShapeAnalysis.invalid("anvil_segments_disconnected");
            }
        }
        for (int y = minimumY + 1; y < maximumYExclusive; y++) {
            BitSet overlap = layer(volume, y - 1);
            overlap.and(layer(volume, y));
            if (overlap.isEmpty()) {
                return MoldingAnvilShapeAnalysis.invalid("anvil_segments_disconnected");
            }
        }

        boolean sawBottomThickness = false;
        boolean sawMiddleThickness = false;
        boolean sawNeck = false;
        boolean sawTop = false;
        for (int bottomEnd = minimumY + MIN_BOTTOM_THICKNESS;
             bottomEnd <= maximumYExclusive - MIN_MIDDLE_THICKNESS - MIN_TOP_THICKNESS;
             bottomEnd++) {
            int bottomThickness = bottomEnd - minimumY;
            BitSet bottomProjection = union(volume, minimumY, bottomEnd);
            Bounds base = bounds(bottomProjection, volume.sizeZ());
            if (base == null) continue;
            sawBottomThickness = true;
            for (int middleEnd = bottomEnd + MIN_MIDDLE_THICKNESS;
                 middleEnd <= maximumYExclusive - MIN_TOP_THICKNESS;
                 middleEnd++) {
                int middleThickness = middleEnd - bottomEnd;
                int topThickness = maximumYExclusive - middleEnd;
                if (topThickness < MIN_TOP_THICKNESS || topThickness <= bottomThickness) continue;
                sawMiddleThickness = true;

                BitSet middleProjection = union(volume, bottomEnd, middleEnd);
                Bounds middleBounds = bounds(middleProjection, volume.sizeZ());
                if (middleBounds == null
                    || !strictlyInside(middleBounds, bottomBounds)
                    || !strictlyInsideProjection(
                        middleProjection,
                        bottomLayer,
                        middleBounds,
                        bottomBounds
                    )) {
                    continue;
                }
                sawNeck = true;

                BitSet topProjection = union(volume, middleEnd, maximumYExclusive);
                int topArea = topProjection.cardinality();
                if (topArea + MAX_TOP_AREA_DEFICIT < bottomLayer.cardinality()) continue;
                sawTop = true;

                return new MoldingAnvilShapeAnalysis(
                    true,
                    giant,
                    "",
                    minimumY,
                    maximumYExclusive,
                    totalThickness,
                    bottomThickness,
                    middleThickness,
                    topThickness,
                    bottomLayer.cardinality(),
                    middleProjection.cardinality(),
                    topArea,
                    bottomBounds.width(),
                    bottomBounds.depth(),
                    middleBounds.width(),
                    middleBounds.depth()
                );
            }
        }

        if (!sawBottomThickness) return MoldingAnvilShapeAnalysis.invalid("anvil_bottom_too_thin");
        if (!sawMiddleThickness) return MoldingAnvilShapeAnalysis.invalid("anvil_middle_too_thin");
        if (!sawNeck) return MoldingAnvilShapeAnalysis.invalid("anvil_neck_not_narrow");
        if (!sawTop) return MoldingAnvilShapeAnalysis.invalid("anvil_top_not_wide");
        return MoldingAnvilShapeAnalysis.invalid("anvil_segments_invalid");
    }

    private static boolean hasMinimumNegativeBottom(EditableMoldingModel model) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        List<SourceElement> elements = new ArrayList<>();
        double minimumY = Double.POSITIVE_INFINITY;
        for (MoldingElement element : model.elements()) {
            if (!element.hasVolume() || !isVisible(element, groups)) continue;
            List<MoldingVec3> vertices = MoldingModelBaker.transformedVertices(model, element);
            elements.add(new SourceElement(vertices));
            for (MoldingVec3 vertex : vertices) minimumY = Math.min(minimumY, vertex.y());
        }
        if (!Double.isFinite(minimumY)) return false;

        for (SourceElement element : elements) {
            List<MoldingVec3> vertices = element.vertices();
            if (!MoldingModelBaker.hasNegativeOrientation(vertices)) continue;
            for (int[] face : CUBE_FACES) {
                if (!isLowestHorizontalFace(vertices, face, minimumY)) continue;
                double minimumX = Double.POSITIVE_INFINITY;
                double minimumZ = Double.POSITIVE_INFINITY;
                double maximumX = Double.NEGATIVE_INFINITY;
                double maximumZ = Double.NEGATIVE_INFINITY;
                for (int index : face) {
                    MoldingVec3 vertex = vertices.get(index);
                    minimumX = Math.min(minimumX, vertex.x());
                    minimumZ = Math.min(minimumZ, vertex.z());
                    maximumX = Math.max(maximumX, vertex.x());
                    maximumZ = Math.max(maximumZ, vertex.z());
                }
                int width = pixelSpan(minimumX, maximumX) + NEGATIVE_BOTTOM_OUTLINE_BORDER * 2;
                int depth = pixelSpan(minimumZ, maximumZ) + NEGATIVE_BOTTOM_OUTLINE_BORDER * 2;
                if (width >= MIN_BOTTOM_WIDTH && depth >= MIN_BOTTOM_DEPTH) return true;
            }
        }
        return false;
    }

    private static boolean isVisible(MoldingElement element, Map<UUID, MoldingGroup> groups) {
        if (!element.visible()) return false;
        UUID groupId = element.groupId().orElse(null);
        while (groupId != null) {
            MoldingGroup group = groups.get(groupId);
            if (group == null || !group.visible()) return false;
            groupId = group.parentId().orElse(null);
        }
        return true;
    }

    private static boolean isLowestHorizontalFace(
        List<MoldingVec3> vertices,
        int[] face,
        double minimumY
    ) {
        for (int index : face) {
            if (Math.abs(vertices.get(index).y() - minimumY) > EPSILON) return false;
        }
        return true;
    }

    private static int pixelSpan(double minimum, double maximum) {
        return (int) Math.ceil(maximum - EPSILON) - (int) Math.floor(minimum + EPSILON);
    }

    private static BitSet layer(MoldingVolumeMask volume, int y) {
        BitSet result = new BitSet(volume.sizeX() * volume.sizeZ());
        for (int x = 0; x < volume.sizeX(); x++) {
            for (int z = 0; z < volume.sizeZ(); z++) {
                if (volume.get(x, y, z)) result.set(x * volume.sizeZ() + z);
            }
        }
        return result;
    }

    private static BitSet union(MoldingVolumeMask volume, int minimumY, int maximumYExclusive) {
        BitSet result = new BitSet(volume.sizeX() * volume.sizeZ());
        for (int y = minimumY; y < maximumYExclusive; y++) result.or(layer(volume, y));
        return result;
    }

    private static boolean containedIn(BitSet projection, BitSet footprint) {
        BitSet outside = (BitSet) projection.clone();
        outside.andNot(footprint);
        return outside.isEmpty();
    }

    private static boolean strictlyInsideProjection(
        BitSet projection,
        BitSet footprint,
        Bounds projectionBounds,
        Bounds footprintBounds
    ) {
        return containedIn(projection, footprint)
            && !projection.isEmpty()
            && projectionBounds.minX > footprintBounds.minX
            && projectionBounds.maxX < footprintBounds.maxX
            && projectionBounds.minZ > footprintBounds.minZ
            && projectionBounds.maxZ < footprintBounds.maxZ;
    }

    private static boolean hasFlatRectangle(BitSet layer, int sizeZ, int width, int depth) {
        Bounds area = bounds(layer, sizeZ);
        if (area == null || area.width() < width || area.depth() < depth) return false;
        int maxX = area.maxX - width + 1;
        int maxZ = area.maxZ - depth + 1;
        int[] prefix = new int[(area.width() + 1) * (area.depth() + 1)];
        for (int x = area.minX; x <= area.maxX; x++) {
            int row = (x - area.minX + 1) * (area.depth() + 1);
            int previousRow = (x - area.minX) * (area.depth() + 1);
            for (int z = area.minZ; z <= area.maxZ; z++) {
                int column = z - area.minZ + 1;
                prefix[row + column] = prefix[previousRow + column]
                    + prefix[row + column - 1]
                    - prefix[previousRow + column - 1]
                    + (layer.get(x * sizeZ + z) ? 1 : 0);
            }
        }
        int stride = area.depth() + 1;
        for (int x = area.minX; x <= maxX; x++) {
            int localX = x - area.minX;
            for (int z = area.minZ; z <= maxZ; z++) {
                int localZ = z - area.minZ;
                int x2 = localX + width;
                int z2 = localZ + depth;
                int count = prefix[(localX) * stride + localZ]
                    - prefix[x2 * stride + localZ]
                    - prefix[localX * stride + z2]
                    + prefix[x2 * stride + z2];
                if (count == width * depth) return true;
            }
        }
        return false;
    }

    private static boolean isConnected(BitSet layer, int sizeZ) {
        int first = layer.nextSetBit(0);
        if (first < 0) return false;
        BitSet visited = new BitSet(layer.length());
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(first);
        visited.set(first);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            int x = index / sizeZ;
            int z = index % sizeZ;
            int[] neighbours = {index - sizeZ, index + sizeZ, index - 1, index + 1};
            for (int neighbour : neighbours) {
                if (neighbour < 0 || (neighbour == index - 1 && z == 0)
                    || (neighbour == index + 1 && z == sizeZ - 1)
                    || !layer.get(neighbour) || visited.get(neighbour)) continue;
                visited.set(neighbour);
                queue.addLast(neighbour);
            }
        }
        BitSet remaining = (BitSet) layer.clone();
        remaining.andNot(visited);
        return remaining.isEmpty();
    }

    private static boolean strictlyInside(Bounds inner, Bounds outer) {
        return inner.minX > outer.minX
            && inner.maxX < outer.maxX
            && inner.minZ > outer.minZ
            && inner.maxZ < outer.maxZ;
    }

    private static Bounds bounds(BitSet projection, int sizeZ) {
        if (projection.isEmpty()) return null;
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int index = projection.nextSetBit(0); index >= 0; index = projection.nextSetBit(index + 1)) {
            int x = index / sizeZ;
            int z = index % sizeZ;
            minX = Math.min(minX, x);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxZ = Math.max(maxZ, z);
        }
        return new Bounds(minX, minZ, maxX, maxZ);
    }

    private record Bounds(int minX, int minZ, int maxX, int maxZ) {
        private int width() {
            return this.maxX - this.minX + 1;
        }

        private int depth() {
            return this.maxZ - this.minZ + 1;
        }
    }

    private record SourceElement(List<MoldingVec3> vertices) {
        private SourceElement {
            vertices = List.copyOf(vertices);
        }
    }
}
