package dev.anvilcraft.plasticraft.molding.bake;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.List;

/** 一个源 Cube 完整成型或被水平裁切后得到的凸碰撞体。 */
public record MoldingConvexHull(List<MoldingVec3> vertices, List<MoldingConvexFace> faces) {
    public static final int MAX_VERTICES = 16;
    public static final int MAX_FACES = 12;
    private static final Codec<List<MoldingVec3>> VERTICES_CODEC = MoldingVec3.CODEC.listOf().validate(vertices ->
        vertices.size() >= 4 && vertices.size() <= MAX_VERTICES
            ? DataResult.success(vertices)
            : DataResult.error(() -> "Invalid molding convex hull vertex count")
    );
    private static final Codec<List<MoldingConvexFace>> FACES_CODEC = MoldingConvexFace.CODEC.listOf().validate(faces ->
        faces.size() >= 4 && faces.size() <= MAX_FACES
            ? DataResult.success(faces)
            : DataResult.error(() -> "Invalid molding convex hull face count")
    );
    public static final Codec<MoldingConvexHull> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        VERTICES_CODEC.fieldOf("vertices").forGetter(MoldingConvexHull::vertices),
        FACES_CODEC.fieldOf("faces").forGetter(MoldingConvexHull::faces)
    ).apply(instance, MoldingConvexHull::new));

    public MoldingConvexHull {
        vertices = List.copyOf(vertices);
        faces = List.copyOf(faces);
        if (vertices.size() < 4 || vertices.size() > MAX_VERTICES
            || vertices.stream().distinct().count() != vertices.size()) {
            throw new IllegalArgumentException("Invalid molding convex hull vertices");
        }
        if (faces.size() < 4 || faces.size() > MAX_FACES) {
            throw new IllegalArgumentException("Invalid molding convex hull faces");
        }
        int vertexCount = vertices.size();
        for (MoldingConvexFace face : faces) {
            if (face.vertices().stream().anyMatch(index -> index >= vertexCount)) {
                throw new IllegalArgumentException("Molding convex face references a missing vertex");
            }
        }
    }

    public Bounds bounds() {
        MoldingVec3 minimum = this.vertices.getFirst();
        MoldingVec3 maximum = minimum;
        for (MoldingVec3 vertex : this.vertices) {
            minimum = minimum.min(vertex);
            maximum = maximum.max(vertex);
        }
        return new Bounds(minimum, maximum);
    }

    public record Bounds(MoldingVec3 minimum, MoldingVec3 maximum) {
    }
}
