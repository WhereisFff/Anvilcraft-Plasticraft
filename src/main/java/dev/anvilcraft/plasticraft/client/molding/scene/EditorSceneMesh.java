package dev.anvilcraft.plasticraft.client.molding.scene;

import java.util.List;

/** 不引用纹理、buffer 或图形 API 的完整不可变视口网格。 */
public record EditorSceneMesh(long staticRevision, long dynamicRevision, List<EditorScenePart> parts) {
    public EditorSceneMesh {
        parts = List.copyOf(parts);
    }

    public List<EditorScenePart> parts(EditorDrawPhase phase) {
        return this.parts.stream().filter(part -> part.phase() == phase).toList();
    }
}
