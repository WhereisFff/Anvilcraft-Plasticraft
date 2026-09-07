package dev.anvilcraft.plasticraft.client.selection;

import dev.anvilcraft.lib.v2.cube.client.SelectionPart;
import dev.anvilcraft.lib.v2.cube.geometry.ConvexShape;
import dev.anvilcraft.lib.v2.cube.geometry.SelectionGeometry;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexFace;
import dev.anvilcraft.plasticraft.molding.bake.MoldingConvexHull;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import dev.dubhe.anvilcraft.block.state.Cube3x3PartHalf;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将冷凝塔模型裁到各实际占位格，保证命中位置仍对应正确的侧面接口。 */
public final class CondenserSelectionGeometry {
    private CondenserSelectionGeometry() {
    }

    public static Map<Cube3x3PartHalf, List<SelectionPart>> bake(List<ConvexShape> model) {
        return bake(model, 16L * 1024 * 1024);
    }

    static Map<Cube3x3PartHalf, List<SelectionPart>> bake(List<ConvexShape> model, long budget) {
        List<MoldingConvexHull> hulls = model.stream().map(CondenserSelectionGeometry::hull).toList();
        Map<Cube3x3PartHalf, List<SelectionPart>> result = new EnumMap<>(Cube3x3PartHalf.class);
        for (Cube3x3PartHalf half : Cube3x3PartHalf.values()) {
            int x = half.getOffsetX(), y = half.getOffsetY() - 1, z = half.getOffsetZ();
            List<ConvexShape> pieces = new ArrayList<>();
            for (MoldingConvexHull hull : hulls) {
                List<MoldingQuad> surfaces = MoldingModelBaker.clipConvexHullToCell(hull, x, y, z);
                if (!surfaces.isEmpty()) pieces.add(piece(surfaces, new Vec3(x, y, z)));
            }
            if (pieces.isEmpty()) {
                result.put(half, List.of());
            } else {
                SelectionGeometry geometry = new SelectionGeometry(pieces);
                budget -= geometry.estimatedBytes() + 512;
                if (budget < 0) throw new IllegalArgumentException("Condenser selection geometry exceeds memory budget");
                result.put(half, List.of(new SelectionPart(geometry,
                    new Matrix4f().scaling(1 / PlasticSelectionGeometry.GEOMETRY_SCALE))));
            }
        }
        return Map.copyOf(result);
    }

    private static MoldingConvexHull hull(ConvexShape shape) {
        List<Vec3> source = shape.vertices();
        List<MoldingVec3> vertices = source.stream().map(v -> new MoldingVec3(
            v.x / PlasticSelectionGeometry.GEOMETRY_SCALE,
            v.y / PlasticSelectionGeometry.GEOMETRY_SCALE,
            v.z / PlasticSelectionGeometry.GEOMETRY_SCALE
        )).toList();
        List<MoldingConvexFace> faces = new ArrayList<>();
        for (ConvexShape.Face face : shape.faces()) {
            List<Integer> indices = new ArrayList<>();
            Vec3 center = Vec3.ZERO;
            for (int i = 0; i < source.size(); i++) {
                if (Math.abs(face.signedDistance(source.get(i))) < 1.0E-5) {
                    indices.add(i);
                    center = center.add(source.get(i));
                }
            }
            if (indices.size() < 3) throw new IllegalArgumentException("Invalid cube face topology");
            Vec3 origin = center.scale(1.0 / indices.size());
            Vec3 u = source.get(indices.getFirst()).subtract(origin).normalize();
            Vec3 v = face.normal().cross(u);
            indices.sort(Comparator.comparingDouble(i -> {
                Vec3 delta = source.get(i).subtract(origin);
                return Math.atan2(delta.dot(v), delta.dot(u));
            }));
            Vec3 normal = face.normal();
            faces.add(new MoldingConvexFace(indices, new MoldingVec3(normal.x, normal.y, normal.z)));
        }
        return new MoldingConvexHull(vertices, faces);
    }

    private static ConvexShape piece(List<MoldingQuad> surfaces, Vec3 cell) {
        Map<MoldingVec3, Integer> indices = new LinkedHashMap<>();
        List<int[]> faces = new ArrayList<>();
        for (MoldingQuad quad : surfaces) {
            List<MoldingVec3> corners = List.of(quad.first(), quad.second(), quad.third(), quad.fourth()).stream().distinct().toList();
            if (corners.size() < 3) continue;
            faces.add(corners.stream().mapToInt(v -> indices.computeIfAbsent(v, ignored -> indices.size())).toArray());
        }
        List<Vec3> vertices = indices.keySet().stream().map(v -> new Vec3(v.x(), v.y(), v.z())
            .subtract(cell).scale(PlasticSelectionGeometry.GEOMETRY_SCALE)).toList();
        return new ConvexShape(vertices, faces.toArray(int[][]::new));
    }
}
