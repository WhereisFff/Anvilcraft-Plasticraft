package dev.anvilcraft.plasticraft.client.molding.scene;

import java.util.List;

public record EditorScenePart(EditorDrawPhase phase, List<EditorVertex> vertices, int[] indices) {
    public EditorScenePart {
        vertices = List.copyOf(vertices);
        indices = indices.clone();
        if (indices.length % 4 != 0) throw new IllegalArgumentException("Scene part indices must contain quads");
        for (int index : indices) {
            if (index < 0 || index >= vertices.size()) throw new IllegalArgumentException("Scene index is out of range");
        }
    }

    @Override
    public int[] indices() {
        return this.indices.clone();
    }

}
