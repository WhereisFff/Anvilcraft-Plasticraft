package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** 在不改变可接触外轮廓的前提下移除重复、被包含或被轴对齐 Cube 完全包裹的凸体。 */
public final class PlasticConvexShapeOptimizer {
    private static final double CONTAINMENT_EPSILON = 1.0E-9D;

    private PlasticConvexShapeOptimizer() {
    }

    public static List<PlasticConvexShape> optimize(List<PlasticConvexShape> shapes) {
        return optimizeWithStats(shapes).shapes();
    }

    public static Result optimizeWithStats(List<PlasticConvexShape> shapes) {
        Objects.requireNonNull(shapes, "shapes");
        if (shapes.size() < 2) return new Result(shapes, shapes.size(), 0, 0);

        List<PlasticConvexShape> source = new ArrayList<>(shapes.size());
        for (PlasticConvexShape shape : shapes) source.add(Objects.requireNonNull(shape, "shape"));
        List<PlasticConvexShape> directlyRetained = new ArrayList<>(source.size());
        for (int candidateIndex = 0; candidateIndex < source.size(); candidateIndex++) {
            PlasticConvexShape candidate = source.get(candidateIndex);
            if (!isDirectlyContained(candidate, candidateIndex, source)) directlyRetained.add(candidate);
        }
        int directlyContained = source.size() - directlyRetained.size();
        boolean[] enclosed = new boolean[directlyRetained.size()];
        int enclosedCount = 0;
        for (int candidateIndex = 0; candidateIndex < directlyRetained.size(); candidateIndex++) {
            enclosed[candidateIndex] = isAxisAlignedEnclosed(
                directlyRetained.get(candidateIndex),
                candidateIndex,
                directlyRetained
            );
            if (enclosed[candidateIndex]) enclosedCount++;
        }
        List<PlasticConvexShape> retained = new ArrayList<>(directlyRetained.size() - enclosedCount);
        for (int index = 0; index < directlyRetained.size(); index++) {
            if (!enclosed[index]) retained.add(directlyRetained.get(index));
        }
        return new Result(retained, shapes.size(), directlyContained, enclosedCount);
    }

    private static boolean isDirectlyContained(
        PlasticConvexShape candidate,
        int candidateIndex,
        List<PlasticConvexShape> shapes
    ) {
        for (int containerIndex = 0; containerIndex < shapes.size(); containerIndex++) {
            if (candidateIndex == containerIndex) continue;
            PlasticConvexShape container = shapes.get(containerIndex);
            if (!container.contains(candidate, CONTAINMENT_EPSILON)) continue;
            if (!candidate.contains(container, CONTAINMENT_EPSILON) || containerIndex < candidateIndex) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAxisAlignedEnclosed(
        PlasticConvexShape candidate,
        int candidateIndex,
        List<PlasticConvexShape> shapes
    ) {
        if (!candidate.isAxisAlignedBox()) return false;
        AABB bounds = candidate.bounds();
        for (Direction face : Direction.values()) {
            List<Rectangle> covers = new ArrayList<>();
            for (int index = 0; index < shapes.size(); index++) {
                if (index == candidateIndex) continue;
                PlasticConvexShape other = shapes.get(index);
                if (!other.isAxisAlignedBox()) continue;
                Rectangle cover = faceCover(bounds, other.bounds(), face);
                if (cover != null) covers.add(cover);
            }
            Rectangle faceArea = faceArea(bounds, face);
            if (!coversRectangle(faceArea, covers)) return false;
        }
        return true;
    }

    private static Rectangle faceCover(AABB candidate, AABB other, Direction face) {
        double plane = switch (face) {
            case DOWN -> candidate.minY;
            case UP -> candidate.maxY;
            case NORTH -> candidate.minZ;
            case SOUTH -> candidate.maxZ;
            case WEST -> candidate.minX;
            case EAST -> candidate.maxX;
        };
        double otherMinimum = axisMinimum(other, face.getAxis());
        double otherMaximum = axisMaximum(other, face.getAxis());
        if (otherMinimum > plane + CONTAINMENT_EPSILON
            || otherMaximum < plane - CONTAINMENT_EPSILON
            || otherMaximum - otherMinimum <= CONTAINMENT_EPSILON) {
            return null;
        }
        return switch (face.getAxis()) {
            case X -> clippedRectangle(
                candidate.minY, candidate.maxY, candidate.minZ, candidate.maxZ,
                other.minY, other.maxY, other.minZ, other.maxZ
            );
            case Y -> clippedRectangle(
                candidate.minX, candidate.maxX, candidate.minZ, candidate.maxZ,
                other.minX, other.maxX, other.minZ, other.maxZ
            );
            case Z -> clippedRectangle(
                candidate.minX, candidate.maxX, candidate.minY, candidate.maxY,
                other.minX, other.maxX, other.minY, other.maxY
            );
        };
    }

    private static Rectangle faceArea(AABB bounds, Direction face) {
        return switch (face.getAxis()) {
            case X -> new Rectangle(bounds.minY, bounds.maxY, bounds.minZ, bounds.maxZ);
            case Y -> new Rectangle(bounds.minX, bounds.maxX, bounds.minZ, bounds.maxZ);
            case Z -> new Rectangle(bounds.minX, bounds.maxX, bounds.minY, bounds.maxY);
        };
    }

    private static Rectangle clippedRectangle(
        double targetMinFirst,
        double targetMaxFirst,
        double targetMinSecond,
        double targetMaxSecond,
        double coverMinFirst,
        double coverMaxFirst,
        double coverMinSecond,
        double coverMaxSecond
    ) {
        double minFirst = Math.max(targetMinFirst, coverMinFirst);
        double maxFirst = Math.min(targetMaxFirst, coverMaxFirst);
        double minSecond = Math.max(targetMinSecond, coverMinSecond);
        double maxSecond = Math.min(targetMaxSecond, coverMaxSecond);
        if (maxFirst - minFirst <= CONTAINMENT_EPSILON
            || maxSecond - minSecond <= CONTAINMENT_EPSILON) {
            return null;
        }
        return new Rectangle(minFirst, maxFirst, minSecond, maxSecond);
    }

    private static boolean coversRectangle(Rectangle target, List<Rectangle> covers) {
        if (covers.isEmpty()) return false;
        List<Double> firstCoordinates = new ArrayList<>(covers.size() * 2 + 2);
        firstCoordinates.add(target.minFirst);
        firstCoordinates.add(target.maxFirst);
        for (Rectangle cover : covers) {
            firstCoordinates.add(cover.minFirst);
            firstCoordinates.add(cover.maxFirst);
        }
        firstCoordinates.sort(Double::compare);
        double previous = firstCoordinates.getFirst();
        for (int index = 1; index < firstCoordinates.size(); index++) {
            double next = firstCoordinates.get(index);
            if (next - previous <= CONTAINMENT_EPSILON) continue;
            double middle = (previous + next) * 0.5D;
            if (middle >= target.minFirst - CONTAINMENT_EPSILON
                && middle <= target.maxFirst + CONTAINMENT_EPSILON
                && !coversSecondInterval(target, covers, middle)) {
                return false;
            }
            previous = next;
        }
        return true;
    }

    private static boolean coversSecondInterval(Rectangle target, List<Rectangle> covers, double first) {
        List<Interval> intervals = new ArrayList<>();
        for (Rectangle cover : covers) {
            if (first < cover.minFirst - CONTAINMENT_EPSILON
                || first > cover.maxFirst + CONTAINMENT_EPSILON) {
                continue;
            }
            intervals.add(new Interval(cover.minSecond, cover.maxSecond));
        }
        intervals.sort(Comparator.comparingDouble(Interval::minimum));
        double coveredUntil = target.minSecond;
        for (Interval interval : intervals) {
            if (interval.maximum < coveredUntil + CONTAINMENT_EPSILON) continue;
            if (interval.minimum > coveredUntil + CONTAINMENT_EPSILON) return false;
            coveredUntil = Math.max(coveredUntil, interval.maximum);
            if (coveredUntil >= target.maxSecond - CONTAINMENT_EPSILON) return true;
        }
        return false;
    }

    private static double axisMinimum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.minX;
            case Y -> box.minY;
            case Z -> box.minZ;
        };
    }

    private static double axisMaximum(AABB box, Direction.Axis axis) {
        return switch (axis) {
            case X -> box.maxX;
            case Y -> box.maxY;
            case Z -> box.maxZ;
        };
    }

    public record Result(
        List<PlasticConvexShape> shapes,
        int inputCount,
        int directlyContainedCount,
        int enclosedCount
    ) {
        public Result {
            shapes = List.copyOf(shapes);
        }

        public int removedCount() {
            return this.inputCount - this.shapes.size();
        }
    }

    private record Rectangle(double minFirst, double maxFirst, double minSecond, double maxSecond) {
    }

    private record Interval(double minimum, double maximum) {
    }
}
