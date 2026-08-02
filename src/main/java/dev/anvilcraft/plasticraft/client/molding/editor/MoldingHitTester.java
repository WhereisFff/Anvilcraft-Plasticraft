package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class MoldingHitTester {
    private static final double EPSILON = 1.0E-8D;
    private static final int[][] CUBE_FACES = {
        {0, 2, 6, 4}, {1, 5, 7, 3},
        {0, 4, 5, 1}, {2, 3, 7, 6},
        {0, 1, 3, 2}, {4, 6, 7, 5}
    };

    private MoldingHitTester() {
    }

    public static Optional<ElementHit> hitElement(EditableMoldingModel model, ViewportRay ray) {
        ElementHit closest = null;
        Map<UUID, MoldingGroup> groups = model.groupMap();
        for (MoldingElement element : model.elements()) {
            if (!isVisible(element, groups)) continue;
            List<MoldingVec3> moldingVertices = MoldingModelBaker.transformedVertices(model, element);
            List<Vector3d> vertices = moldingVertices.stream().map(MoldingHitTester::vector).toList();
            double distance = element.hasVolume()
                ? hitCube(ray, vertices)
                : hitQuad(ray, vertices.get(0), vertices.get(1), vertices.get(2), vertices.get(3));
            if (!Double.isFinite(distance) || closest != null && distance >= closest.distance()) continue;
            closest = new ElementHit(element.id(), distance, ray.pointAt(distance));
        }
        return Optional.ofNullable(closest);
    }

    private static boolean isVisible(MoldingElement element, Map<UUID, MoldingGroup> groups) {
        if (!element.visible()) return false;
        MoldingGroup group = element.groupId().map(groups::get).orElse(null);
        while (group != null) {
            if (!group.visible()) return false;
            group = group.parentId().map(groups::get).orElse(null);
        }
        return true;
    }

    public static Optional<GizmoHit> hitGizmo(
        ViewportRay ray,
        Vector3d origin,
        MoldingTool tool,
        double worldUnitsPerPixel,
        Vector3d cameraDirection
    ) {
        if (tool == MoldingTool.NONE) return Optional.empty();
        if (!(worldUnitsPerPixel > 0.0D) || !Double.isFinite(worldUnitsPerPixel)) return Optional.empty();
        GizmoCandidate closest = null;
        double length = MoldingGizmoGeometry.axisLength(worldUnitsPerPixel);
        double hitRadius = MoldingGizmoGeometry.hitRadius(worldUnitsPerPixel);
        if (tool == MoldingTool.ROTATE) {
            double radius = MoldingGizmoGeometry.ringRadius(worldUnitsPerPixel);
            for (MoldingAxis axis : MoldingAxis.values()) {
                for (int segment = 0; segment < MoldingGizmoGeometry.RING_SEGMENTS; segment++) {
                    double startAngle = Math.TAU * segment / MoldingGizmoGeometry.RING_SEGMENTS;
                    double endAngle = Math.TAU * (segment + 1) / MoldingGizmoGeometry.RING_SEGMENTS;
                    Vector3d start = MoldingGizmoGeometry.ringPoint(origin, axis, radius, startAngle);
                    Vector3d end = MoldingGizmoGeometry.ringPoint(origin, axis, radius, endAngle);
                    Vector3d midpoint = new Vector3d(start).add(end).mul(0.5D);
                    if (!MoldingGizmoGeometry.isFrontFacing(midpoint, origin, cameraDirection)) continue;
                    closest = closerCandidate(closest, ray, axis, 1.0D, start, end, hitRadius);
                }
            }
        } else {
            for (MoldingAxis axis : MoldingAxis.values()) {
                Vector3d positive = axis.vector().mul(length).add(origin);
                closest = closerCandidate(closest, ray, axis, 1.0D, origin, positive, hitRadius);
                if (tool != MoldingTool.SCALE) continue;
                Vector3d negative = axis.vector().mul(-length).add(origin);
                closest = closerCandidate(closest, ray, axis, -1.0D, origin, negative, hitRadius);
            }
        }
        return Optional.ofNullable(closest).map(GizmoCandidate::hit);
    }

    private static GizmoCandidate closerCandidate(
        GizmoCandidate current,
        ViewportRay ray,
        MoldingAxis axis,
        double direction,
        Vector3d start,
        Vector3d end,
        double hitRadius
    ) {
        RaySegmentDistance distance = distance(ray, start, end);
        if (distance.distanceSquared() > hitRadius * hitRadius) return current;
        if (current != null
            && (distance.distanceSquared() > current.proximitySquared() + EPSILON
            || Math.abs(distance.distanceSquared() - current.proximitySquared()) <= EPSILON
            && distance.rayDistance() >= current.hit().distance())) {
            return current;
        }
        return new GizmoCandidate(
            new GizmoHit(axis, direction, distance.rayDistance(), ray.pointAt(distance.rayDistance())),
            distance.distanceSquared()
        );
    }

    private static double hitCube(ViewportRay ray, List<Vector3d> vertices) {
        double closest = Double.POSITIVE_INFINITY;
        for (int[] face : CUBE_FACES) {
            closest = Math.min(closest, hitQuad(
                ray,
                vertices.get(face[0]),
                vertices.get(face[1]),
                vertices.get(face[2]),
                vertices.get(face[3])
            ));
        }
        return closest;
    }

    private static double hitQuad(
        ViewportRay ray,
        Vector3d first,
        Vector3d second,
        Vector3d third,
        Vector3d fourth
    ) {
        return Math.min(
            hitTriangle(ray, first, second, third),
            hitTriangle(ray, first, third, fourth)
        );
    }

    private static double hitTriangle(ViewportRay ray, Vector3d first, Vector3d second, Vector3d third) {
        Vector3d direction = ray.direction();
        Vector3d edgeOne = new Vector3d(second).sub(first);
        Vector3d edgeTwo = new Vector3d(third).sub(first);
        Vector3d h = new Vector3d(direction).cross(edgeTwo);
        double determinant = edgeOne.dot(h);
        if (Math.abs(determinant) < EPSILON) return Double.POSITIVE_INFINITY;
        double inverse = 1.0D / determinant;
        Vector3d s = ray.origin().sub(first);
        double u = inverse * s.dot(h);
        if (u < -EPSILON || u > 1.0D + EPSILON) return Double.POSITIVE_INFINITY;
        Vector3d q = new Vector3d(s).cross(edgeOne);
        double v = inverse * direction.dot(q);
        if (v < -EPSILON || u + v > 1.0D + EPSILON) return Double.POSITIVE_INFINITY;
        double distance = inverse * edgeTwo.dot(q);
        return distance >= 0.0D ? distance : Double.POSITIVE_INFINITY;
    }

    private static RaySegmentDistance distance(ViewportRay ray, Vector3d start, Vector3d end) {
        Vector3d rayDirection = ray.direction();
        Vector3d segment = new Vector3d(end).sub(start);
        Vector3d separation = ray.origin().sub(start);
        double segmentLengthSquared = segment.lengthSquared();
        double directionDotSegment = rayDirection.dot(segment);
        double directionDotSeparation = rayDirection.dot(separation);
        double segmentDotSeparation = segment.dot(separation);
        double denominator = segmentLengthSquared - directionDotSegment * directionDotSegment;
        double segmentFactor = denominator > EPSILON
            ? Math.clamp(
                (segmentDotSeparation - directionDotSegment * directionDotSeparation) / denominator,
                0.0D,
                1.0D
            )
            : 0.0D;
        double rayDistance = Math.max(0.0D, directionDotSegment * segmentFactor - directionDotSeparation);
        Vector3d rayPoint = ray.pointAt(rayDistance);
        Vector3d segmentPoint = new Vector3d(segment).mul(segmentFactor).add(start);
        return new RaySegmentDistance(rayPoint.distanceSquared(segmentPoint), rayDistance);
    }

    private static Vector3d vector(MoldingVec3 value) {
        return new Vector3d(value.x(), value.y(), value.z());
    }

    public record ElementHit(UUID id, double distance, Vector3d point) {
        public ElementHit {
            point = new Vector3d(point);
        }

        @Override
        public Vector3d point() {
            return new Vector3d(this.point);
        }
    }

    public record GizmoHit(MoldingAxis axis, double direction, double distance, Vector3d point) {
        public GizmoHit {
            direction = Math.copySign(1.0D, direction);
            point = new Vector3d(point);
        }

        @Override
        public Vector3d point() {
            return new Vector3d(this.point);
        }
    }

    private record RaySegmentDistance(double distanceSquared, double rayDistance) {
    }

    private record GizmoCandidate(GizmoHit hit, double proximitySquared) {
    }
}
