package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 计算一组凸碰撞体并集的真实棱线，供调试轮廓和其他几何检查复用。 */
public final class PlasticConvexCollisionOutline {
    private static final double GEOMETRY_EPSILON = 1.0E-7D;
    private static final double LINE_EPSILON = 1.0E-7D;
    private static final double SAMPLE_DISTANCE = 2.0E-6D;
    private static final double MERGE_EPSILON = 1.0E-6D;

    private PlasticConvexCollisionOutline() {
    }

    /** 返回并集外轮廓的线段；共享共面接缝不会出现在结果中。 */
    public static List<Segment> build(List<PlasticConvexShape> shapes) {
        Objects.requireNonNull(shapes, "shapes");
        List<PlasticConvexShape> source = shapes.stream()
            .map(shape -> Objects.requireNonNull(shape, "shape"))
            .toList();
        if (source.isEmpty()) return List.of();

        List<Segment> candidates = new ArrayList<>();
        for (PlasticConvexShape shape : source) {
            for (PlasticConvexShape.Edge edge : shape.edges()) {
                addCandidate(candidates, edge.start(), edge.end());
            }
        }
        addFaceIntersectionCandidates(source, candidates);

        List<Segment> visible = new ArrayList<>();
        for (Segment candidate : candidates) {
            addVisiblePieces(candidate, source, visible);
        }
        return mergeSegments(visible);
    }

    private static void addFaceIntersectionCandidates(
        List<PlasticConvexShape> shapes,
        List<Segment> output
    ) {
        for (int firstIndex = 0; firstIndex < shapes.size(); firstIndex++) {
            PlasticConvexShape first = shapes.get(firstIndex);
            for (int secondIndex = firstIndex + 1; secondIndex < shapes.size(); secondIndex++) {
                PlasticConvexShape second = shapes.get(secondIndex);
                if (!first.bounds().inflate(GEOMETRY_EPSILON).intersects(
                    second.bounds().inflate(GEOMETRY_EPSILON)
                )) {
                    continue;
                }
                for (PlasticConvexShape.Face firstFace : first.faces()) {
                    for (PlasticConvexShape.Face secondFace : second.faces()) {
                        Vec3 direction = firstFace.normal().cross(secondFace.normal());
                        double directionLengthSqr = direction.lengthSqr();
                        if (directionLengthSqr <= LINE_EPSILON * LINE_EPSILON) continue;
                        Vec3 lineDirection = direction.scale(1.0D / Math.sqrt(directionLengthSqr));
                        Vec3 linePoint = planeIntersection(firstFace, secondFace, directionLengthSqr);
                        Interval firstInterval = clipLine(first, linePoint, lineDirection);
                        if (firstInterval == null) continue;
                        Interval secondInterval = clipLine(second, linePoint, lineDirection);
                        if (secondInterval == null) continue;
                        double minimum = Math.max(firstInterval.minimum(), secondInterval.minimum());
                        double maximum = Math.min(firstInterval.maximum(), secondInterval.maximum());
                        if (maximum - minimum <= GEOMETRY_EPSILON) continue;
                        addCandidate(
                            output,
                            linePoint.add(lineDirection.scale(minimum)),
                            linePoint.add(lineDirection.scale(maximum))
                        );
                    }
                }
            }
        }
    }

    private static Vec3 planeIntersection(
        PlasticConvexShape.Face first,
        PlasticConvexShape.Face second,
        double directionLengthSqr
    ) {
        Vec3 direction = first.normal().cross(second.normal());
        double firstOffset = first.planeOffset();
        double secondOffset = second.planeOffset();
        return second.normal().cross(direction).scale(firstOffset)
            .add(direction.cross(first.normal()).scale(secondOffset))
            .scale(1.0D / directionLengthSqr);
    }

    private static Interval clipLine(
        PlasticConvexShape shape,
        Vec3 point,
        Vec3 direction
    ) {
        double minimum = Double.NEGATIVE_INFINITY;
        double maximum = Double.POSITIVE_INFINITY;
        for (PlasticConvexShape.Face face : shape.faces()) {
            double distance = face.normal().dot(point) - face.planeOffset();
            double projectedDirection = face.normal().dot(direction);
            if (Math.abs(projectedDirection) <= LINE_EPSILON) {
                if (distance > GEOMETRY_EPSILON) return null;
                continue;
            }
            double boundary = (GEOMETRY_EPSILON - distance) / projectedDirection;
            if (projectedDirection > 0.0D) {
                maximum = Math.min(maximum, boundary);
            } else {
                minimum = Math.max(minimum, boundary);
            }
            if (minimum > maximum + GEOMETRY_EPSILON) return null;
        }
        return new Interval(minimum, maximum);
    }

    private static void addVisiblePieces(
        Segment candidate,
        List<PlasticConvexShape> shapes,
        List<Segment> output
    ) {
        Vec3 delta = candidate.end().subtract(candidate.start());
        double length = delta.length();
        if (length <= GEOMETRY_EPSILON) return;
        Vec3 direction = delta.scale(1.0D / length);
        List<Double> cuts = new ArrayList<>();
        cuts.add(0.0D);
        cuts.add(1.0D);
        for (PlasticConvexShape shape : shapes) {
            Interval interval = clipLine(shape, candidate.start(), delta);
            if (interval == null) continue;
            addCut(cuts, interval.minimum());
            addCut(cuts, interval.maximum());
        }
        cuts.sort(Double::compare);
        List<Double> distinctCuts = new ArrayList<>(cuts.size());
        for (double cut : cuts) {
            double clamped = Math.clamp(cut, 0.0D, 1.0D);
            if (distinctCuts.isEmpty()
                || clamped - distinctCuts.getLast() > GEOMETRY_EPSILON / Math.max(length, 1.0D)) {
                distinctCuts.add(clamped);
            }
        }
        for (int index = 1; index < distinctCuts.size(); index++) {
            double minimum = distinctCuts.get(index - 1);
            double maximum = distinctCuts.get(index);
            if (maximum - minimum <= GEOMETRY_EPSILON / Math.max(length, 1.0D)) continue;
            Vec3 start = candidate.start().lerp(candidate.end(), minimum);
            Vec3 end = candidate.start().lerp(candidate.end(), maximum);
            Vec3 midpoint = start.lerp(end, 0.5D);
            if (isVisibleEdge(midpoint, direction, shapes)) {
                output.add(new Segment(start, end));
            }
        }
    }

    private static void addCut(List<Double> cuts, double value) {
        if (Double.isFinite(value) && value > GEOMETRY_EPSILON && value < 1.0D - GEOMETRY_EPSILON) {
            cuts.add(value);
        }
    }

    private static boolean isVisibleEdge(
        Vec3 point,
        Vec3 lineDirection,
        List<PlasticConvexShape> shapes
    ) {
        for (PlasticConvexShape shape : shapes) {
            if (strictlyContains(shape, point)) return false;
        }
        List<Vec3> exposedNormals = new ArrayList<>(4);
        for (PlasticConvexShape shape : shapes) {
            if (!shape.contains(point, GEOMETRY_EPSILON * 4.0D)) continue;
            for (PlasticConvexShape.Face face : shape.faces()) {
                if (Math.abs(face.signedDistance(point)) > GEOMETRY_EPSILON * 4.0D) continue;
                Vec3 tangent = face.normal().cross(lineDirection);
                double tangentLengthSqr = tangent.lengthSqr();
                if (tangentLengthSqr <= LINE_EPSILON * LINE_EPSILON) continue;
                tangent = tangent.scale(SAMPLE_DISTANCE / Math.sqrt(tangentLengthSqr));
                for (int sign : new int[] {-1, 1}) {
                    Vec3 facePoint = point.add(tangent.scale(sign));
                    if (!shape.contains(facePoint, GEOMETRY_EPSILON * 8.0D)) continue;
                    Vec3 outside = facePoint.add(face.normal().scale(SAMPLE_DISTANCE));
                    Vec3 inside = facePoint.subtract(face.normal().scale(SAMPLE_DISTANCE));
                    if (insideUnion(inside, shapes) && !insideUnion(outside, shapes)) {
                        addDistinctNormal(exposedNormals, face.normal());
                        break;
                    }
                }
            }
        }
        for (int first = 0; first < exposedNormals.size(); first++) {
            for (int second = first + 1; second < exposedNormals.size(); second++) {
                if (Math.abs(exposedNormals.get(first).dot(exposedNormals.get(second)))
                    < 1.0D - 1.0E-6D) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean strictlyContains(PlasticConvexShape shape, Vec3 point) {
        for (PlasticConvexShape.Face face : shape.faces()) {
            if (face.signedDistance(point) >= -GEOMETRY_EPSILON * 4.0D) return false;
        }
        return true;
    }

    private static boolean insideUnion(Vec3 point, List<PlasticConvexShape> shapes) {
        return shapes.stream().anyMatch(shape -> shape.contains(point, GEOMETRY_EPSILON * 4.0D));
    }

    private static void addDistinctNormal(List<Vec3> normals, Vec3 candidate) {
        if (normals.stream().noneMatch(normal -> normal.dot(candidate) > 1.0D - 1.0E-6D)) {
            normals.add(candidate);
        }
    }

    private static void addCandidate(List<Segment> output, Vec3 start, Vec3 end) {
        if (start.distanceToSqr(end) > GEOMETRY_EPSILON * GEOMETRY_EPSILON) {
            output.add(new Segment(start, end));
        }
    }

    private static List<Segment> mergeSegments(List<Segment> source) {
        List<Segment> merged = new ArrayList<>();
        for (Segment segment : source) {
            Segment canonical = canonical(segment);
            boolean mergedSegment;
            do {
                mergedSegment = false;
                for (int index = 0; index < merged.size(); index++) {
                    Segment existing = merged.get(index);
                    Segment combined = combineIfTouching(existing, canonical);
                    if (combined == null) continue;
                    canonical = combined;
                    merged.remove(index);
                    mergedSegment = true;
                    break;
                }
            } while (mergedSegment);
            merged.add(canonical);
        }
        merged.sort(Comparator
            .comparingDouble((Segment segment) -> segment.start().x)
            .thenComparingDouble(segment -> segment.start().y)
            .thenComparingDouble(segment -> segment.start().z)
            .thenComparingDouble(segment -> segment.end().x)
            .thenComparingDouble(segment -> segment.end().y)
            .thenComparingDouble(segment -> segment.end().z));
        return List.copyOf(merged);
    }

    private static Segment combineIfTouching(Segment first, Segment second) {
        Vec3 firstDelta = first.end().subtract(first.start());
        Vec3 secondDelta = second.end().subtract(second.start());
        double firstLength = firstDelta.length();
        double secondLength = secondDelta.length();
        if (firstLength <= GEOMETRY_EPSILON || secondLength <= GEOMETRY_EPSILON) return null;
        Vec3 direction = firstDelta.scale(1.0D / firstLength);
        Vec3 otherDirection = secondDelta.scale(1.0D / secondLength);
        if (direction.cross(otherDirection).length() > LINE_EPSILON
            || first.start().subtract(second.start()).cross(direction).length() > MERGE_EPSILON) {
            return null;
        }
        double firstMinimum = 0.0D;
        double firstMaximum = firstLength;
        double secondMinimum = second.start().subtract(first.start()).dot(direction);
        double secondMaximum = second.end().subtract(first.start()).dot(direction);
        if (secondMinimum > secondMaximum) {
            double swap = secondMinimum;
            secondMinimum = secondMaximum;
            secondMaximum = swap;
        }
        if (Math.max(firstMinimum, secondMinimum) > Math.min(firstMaximum, secondMaximum) + MERGE_EPSILON) {
            return null;
        }
        double minimum = Math.min(firstMinimum, secondMinimum);
        double maximum = Math.max(firstMaximum, secondMaximum);
        return new Segment(
            first.start().add(direction.scale(minimum)),
            first.start().add(direction.scale(maximum))
        );
    }

    private static Segment canonical(Segment segment) {
        return compare(segment.start(), segment.end()) <= 0
            ? segment
            : new Segment(segment.end(), segment.start());
    }

    private static int compare(Vec3 first, Vec3 second) {
        int x = Double.compare(first.x, second.x);
        if (x != 0) return x;
        int y = Double.compare(first.y, second.y);
        if (y != 0) return y;
        return Double.compare(first.z, second.z);
    }

    public record Segment(Vec3 start, Vec3 end) {
        public Segment {
            start = Objects.requireNonNull(start, "start");
            end = Objects.requireNonNull(end, "end");
        }
    }

    private record Interval(double minimum, double maximum) {
    }
}
