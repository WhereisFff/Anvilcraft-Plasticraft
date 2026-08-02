package dev.anvilcraft.plasticraft.client.molding.scene;

public enum EditorDrawPhase {
    MANUFACTURING_SURFACE(EditorDrawState.OPAQUE),
    ZERO_THICKNESS_SURFACE(EditorDrawState.DOUBLE_SIDED),
    FINE_GRID(EditorDrawState.TRANSLUCENT),
    MAIN_GRID(EditorDrawState.TRANSLUCENT),
    WORKSPACE_OUTLINE(EditorDrawState.TRANSLUCENT),
    SOURCE_OUTLINE(EditorDrawState.TRANSLUCENT),
    HOVER_SURFACE(EditorDrawState.TRANSLUCENT),
    SELECTION(EditorDrawState.ALWAYS_VISIBLE),
    GIZMO(EditorDrawState.ALWAYS_VISIBLE);

    private final EditorDrawState drawState;
    EditorDrawPhase(EditorDrawState drawState) {
        this.drawState = drawState;
    }

    public EditorDrawState drawState() {
        return this.drawState;
    }
}
