package dev.anvilcraft.plasticraft.molding.bake;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.List;

/** 成型凸体中按外侧环绕顺序保存的一个平面。 */
public record MoldingConvexFace(List<Integer> vertices, MoldingVec3 normal) {
    public static final int MAX_VERTICES = 12;
    private static final Codec<List<Integer>> VERTICES_CODEC = Codec.INT.listOf().validate(vertices ->
        vertices.size() >= 3 && vertices.size() <= MAX_VERTICES
            ? DataResult.success(vertices)
            : DataResult.error(() -> "Invalid molding convex face vertex count")
    );
    public static final Codec<MoldingConvexFace> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        VERTICES_CODEC.fieldOf("vertices").forGetter(MoldingConvexFace::vertices),
        MoldingVec3.CODEC.fieldOf("normal").forGetter(MoldingConvexFace::normal)
    ).apply(instance, MoldingConvexFace::new));

    public MoldingConvexFace {
        vertices = List.copyOf(vertices);
        if (vertices.size() < 3 || vertices.size() > MAX_VERTICES
            || vertices.stream().distinct().count() != vertices.size()
            || vertices.stream().anyMatch(index -> index < 0)) {
            throw new IllegalArgumentException("Invalid molding convex face vertices");
        }
        double normalLength = normal.lengthSquared();
        if (Math.abs(normalLength - 1.0D) > 1.0E-6D) {
            throw new IllegalArgumentException("Molding convex face normal must be a unit vector");
        }
    }
}
