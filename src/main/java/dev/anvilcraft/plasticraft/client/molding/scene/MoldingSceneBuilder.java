package dev.anvilcraft.plasticraft.client.molding.scene;

import dev.anvilcraft.plasticraft.client.molding.editor.MoldingAxis;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingGizmoGeometry;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingSelection;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingTool;
import dev.anvilcraft.plasticraft.client.molding.editor.ViewportTransform;
import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MoldingSceneBuilder {
    private static final int FINE_GRID_COLOR = 0xFF343A3F;
    private static final int MAIN_GRID_COLOR = 0xFF69737C;
    private static final int WORKSPACE_OUTLINE_COLOR = 0xFF8A9197;
    private static final int SOURCE_COLOR = 0xFFA4A8B0;
    private static final int SURFACE_COLOR = 0xFFFFFFFF;
    private static final int HOVER_COLOR = 0x78FFFFFF;
    private static final int SELECTION_COLOR = 0xE8FFD75A;
    private static final int INVALID_COLOR = 0xE8FF4A4A;
    private static final int X_COLOR = 0xFFEC4B4B;
    private static final int Y_COLOR = 0xFF55C86A;
    private static final int Z_COLOR = 0xFF4C83E8;
    private static final int PIVOT_COLOR = 0xD8E7E7E7;
    private static final int OUTER_RING_COLOR = 0xFFFFFFFF;
    private static final double SURFACE_AMBIENT_LIGHT = 0.42D;
    private static final Vector3d SURFACE_LIGHT_DIRECTION = new Vector3d(-0.4D, 0.85D, -0.35D).normalize();
    private static final double FINE_LINE_WIDTH_PIXELS = 0.5D;
    private static final double MAIN_LINE_WIDTH_PIXELS = 0.6D;
    private static final double WORKSPACE_LINE_WIDTH_PIXELS = 0.65D;
    private static final double SOURCE_LINE_WIDTH_PIXELS = 0.65D;
    private static final double GROUND_AXIS_WIDTH_PIXELS = 0.75D;
    private static final double WORLD_MAIN_LINE_WIDTH_PIXELS = 1.0D;
    private static final double WORLD_WORKSPACE_LINE_WIDTH_PIXELS = 1.1D;
    private static final double WORLD_GROUND_AXIS_WIDTH_PIXELS = 1.25D;
    private static final double WORLD_UNITS_PER_PIXEL = 0.05D;
    private static final int[][] CUBE_EDGES = {
        {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
        {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
    };
    private static final int[][] CUBE_FACES = {
        {0, 4, 6, 2}, {1, 3, 7, 5},
        {0, 1, 5, 4}, {2, 6, 7, 3},
        {0, 2, 3, 1}, {4, 5, 7, 6}
    };

    private MoldingSceneBuilder() {
    }

    public static EditorSceneMesh buildEditor(
        long revision,
        long dynamicRevision,
        EditableMoldingModel model,
        @Nullable BakedMoldingModel baked,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d gizmoOrigin,
        double gizmoWorldUnitsPerPixel,
        Vector3d cameraDirection,
        boolean invalidPreview,
        boolean showGrid,
        boolean showAxes
    ) {
        return buildEditor(
            revision,
            dynamicRevision,
            model,
            baked,
            selection,
            tool,
            gizmoOrigin,
            gizmoWorldUnitsPerPixel,
            cameraDirection,
            invalidPreview,
            showGrid,
            showAxes,
            null,
            null
        );
    }

    public static EditorSceneMesh buildEditor(
        long revision,
        long dynamicRevision,
        EditableMoldingModel model,
        @Nullable BakedMoldingModel baked,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d gizmoOrigin,
        double gizmoWorldUnitsPerPixel,
        Vector3d cameraDirection,
        boolean invalidPreview,
        boolean showGrid,
        boolean showAxes,
        @Nullable UUID hoveredElement,
        @Nullable MoldingAxis hoveredGizmoAxis
    ) {
        return buildEditor(
            revision,
            dynamicRevision,
            model,
            baked,
            selection,
            tool,
            gizmoOrigin,
            gizmoWorldUnitsPerPixel,
            cameraDirection,
            invalidPreview,
            showGrid,
            showAxes,
            hoveredElement,
            hoveredGizmoAxis,
            null
        );
    }

    public static EditorSceneMesh buildEditor(
        long revision,
        long dynamicRevision,
        EditableMoldingModel model,
        @Nullable BakedMoldingModel baked,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d gizmoOrigin,
        double gizmoWorldUnitsPerPixel,
        Vector3d cameraDirection,
        boolean invalidPreview,
        boolean showGrid,
        boolean showAxes,
        @Nullable UUID hoveredElement,
        @Nullable MoldingAxis hoveredGizmoAxis,
        @Nullable ViewportTransform viewportTransform
    ) {
        return buildEditorScene(
            revision,
            dynamicRevision,
            model,
            selection,
            tool,
            gizmoOrigin,
            gizmoWorldUnitsPerPixel,
            cameraDirection,
            invalidPreview,
            showGrid,
            showAxes,
            hoveredElement,
            hoveredGizmoAxis,
            viewportTransform,
            true
        );
    }

    private static EditorSceneMesh buildEditorScene(
        long revision,
        long dynamicRevision,
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d gizmoOrigin,
        double gizmoWorldUnitsPerPixel,
        Vector3d cameraDirection,
        boolean invalidPreview,
        boolean showGrid,
        boolean showAxes,
        @Nullable UUID hoveredElement,
        @Nullable MoldingAxis hoveredGizmoAxis,
        @Nullable ViewportTransform viewportTransform,
        boolean includeSourceSurfaces
    ) {
        SceneAccumulators scene = new SceneAccumulators(
            cameraDirection,
            gizmoWorldUnitsPerPixel,
            viewportTransform
        );
        if (showGrid) {
            Vector3d workspaceDirectionToCamera = viewportTransform == null
                ? cameraDirection
                : viewportTransform.directionToCamera(new Vector3d(24.0D, 24.0D, 24.0D));
            addFineGrid(scene.accumulator(EditorDrawPhase.FINE_GRID), gizmoWorldUnitsPerPixel);
            addEditorGrid(scene.accumulator(EditorDrawPhase.MAIN_GRID), gizmoWorldUnitsPerPixel);
            addWorkspaceOutline(
                scene.accumulator(EditorDrawPhase.WORKSPACE_OUTLINE),
                workspaceDirectionToCamera,
                gizmoWorldUnitsPerPixel,
                viewportTransform
            );
        }
        if (showAxes) addGroundAxes(scene.accumulator(EditorDrawPhase.MAIN_GRID), gizmoWorldUnitsPerPixel);
        if (includeSourceSurfaces) addSourceSurfaces(scene, model);
        addSourceOutlines(
            scene.accumulator(EditorDrawPhase.SOURCE_OUTLINE),
            model,
            SOURCE_COLOR,
            null,
            gizmoWorldUnitsPerPixel
        );
        if (hoveredElement != null) {
            addHoveredSurface(scene.accumulator(EditorDrawPhase.HOVER_SURFACE), model, hoveredElement);
            addSourceOutlines(
                scene.accumulator(EditorDrawPhase.HOVER_SURFACE),
                model,
                HOVER_COLOR,
                new MoldingSelection(List.of(hoveredElement)),
                gizmoWorldUnitsPerPixel
            );
        }
        int selectionColor = invalidPreview ? INVALID_COLOR : SELECTION_COLOR;
        addSourceOutlines(
            scene.accumulator(EditorDrawPhase.SELECTION),
            model,
            selectionColor,
            selection,
            gizmoWorldUnitsPerPixel
        );
        if (!selection.isEmpty()) {
            addGizmo(
                scene.accumulator(EditorDrawPhase.GIZMO),
                gizmoOrigin,
                tool,
                gizmoWorldUnitsPerPixel,
                cameraDirection,
                hoveredGizmoAxis
            );
        }
        return scene.build(revision, dynamicRevision);
    }

    public static EditorSceneMesh replaceViewDependent(
        EditorSceneMesh previous,
        long dynamicRevision,
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        Vector3d gizmoOrigin,
        double gizmoWorldUnitsPerPixel,
        Vector3d cameraDirection,
        boolean invalidPreview,
        boolean showGrid,
        boolean showAxes,
        @Nullable UUID hoveredElement,
        @Nullable MoldingAxis hoveredGizmoAxis,
        @Nullable ViewportTransform viewportTransform
    ) {
        EditorSceneMesh dynamic = buildEditorScene(
            previous.staticRevision(),
            dynamicRevision,
            model,
            selection,
            tool,
            gizmoOrigin,
            gizmoWorldUnitsPerPixel,
            cameraDirection,
            invalidPreview,
            showGrid,
            showAxes,
            hoveredElement,
            hoveredGizmoAxis,
            viewportTransform,
            false
        );
        Map<EditorDrawPhase, EditorScenePart> partsByPhase = new EnumMap<>(EditorDrawPhase.class);
        for (EditorScenePart part : previous.parts()) {
            if (part.phase() == EditorDrawPhase.MANUFACTURING_SURFACE
                || part.phase() == EditorDrawPhase.ZERO_THICKNESS_SURFACE) {
                partsByPhase.put(part.phase(), part);
            }
        }
        for (EditorScenePart part : dynamic.parts()) partsByPhase.put(part.phase(), part);
        List<EditorScenePart> parts = new ArrayList<>(partsByPhase.size());
        for (EditorDrawPhase phase : EditorDrawPhase.values()) {
            EditorScenePart part = partsByPhase.get(phase);
            if (part != null) parts.add(part);
        }
        return new EditorSceneMesh(previous.staticRevision(), dynamicRevision, parts);
    }

    public static EditorSceneMesh buildWorldProjection(long revision, EditableMoldingModel model) {
        return buildWorldProjection(revision, model, new Vector3d(1.0D, 1.0D, -1.0D));
    }

    public static EditorSceneMesh buildWorldProjection(
        long revision,
        EditableMoldingModel model,
        Vector3d directionToCamera
    ) {
        List<EditorScenePart> parts = new ArrayList<>();
        parts.addAll(buildWorldSurfaces(revision, model).parts());
        parts.addAll(buildWorldGuides(directionToCamera).parts());
        return new EditorSceneMesh(revision, 0L, parts);
    }

    public static EditorSceneMesh buildWorldGuides(Vector3d directionToCamera) {
        return buildWorldGuides(directionToCamera, WORLD_UNITS_PER_PIXEL);
    }

    public static EditorSceneMesh buildWorldGuides(
        Vector3d directionToCamera,
        double worldUnitsPerPixel
    ) {
        return buildWorldGuides(directionToCamera, worldUnitsPerPixel, null);
    }

    public static EditorSceneMesh buildWorldGuides(
        Vector3d directionToCamera,
        double worldUnitsPerPixel,
        @Nullable Vector3d cameraPosition
    ) {
        double pixelScale = Math.max(1.0E-5D, worldUnitsPerPixel);
        SceneAccumulators scene = new SceneAccumulators(directionToCamera, pixelScale, null);
        addFineGrid(scene.accumulator(EditorDrawPhase.FINE_GRID), pixelScale);
        addEditorGrid(
            scene.accumulator(EditorDrawPhase.MAIN_GRID),
            pixelScale,
            WORLD_MAIN_LINE_WIDTH_PIXELS
        );
        addGroundAxes(
            scene.accumulator(EditorDrawPhase.MAIN_GRID),
            pixelScale,
            WORLD_GROUND_AXIS_WIDTH_PIXELS
        );
        addWorkspaceOutline(
            scene.accumulator(EditorDrawPhase.WORKSPACE_OUTLINE),
            directionToCamera,
            pixelScale,
            null,
            cameraPosition,
            WORLD_WORKSPACE_LINE_WIDTH_PIXELS
        );
        return scene.build(0L, 0L);
    }

    public static EditorSceneMesh buildWorldSurfaces(long revision, EditableMoldingModel model) {
        SceneAccumulators scene = new SceneAccumulators(
            new Vector3d(0.0D, 0.0D, 1.0D),
            WORLD_UNITS_PER_PIXEL,
            null
        );
        addSourceSurfaces(scene, model);
        return scene.build(revision, 0L);
    }

    public static EditorSceneMesh replaceGizmo(
        EditorSceneMesh scene,
        long dynamicRevision,
        Vector3d center,
        MoldingTool tool,
        double worldUnitsPerPixel,
        Vector3d cameraDirection
    ) {
        return replaceGizmo(scene, dynamicRevision, center, tool, worldUnitsPerPixel, cameraDirection, null);
    }

    public static EditorSceneMesh replaceGizmo(
        EditorSceneMesh scene,
        long dynamicRevision,
        Vector3d center,
        MoldingTool tool,
        double worldUnitsPerPixel,
        Vector3d cameraDirection,
        @Nullable MoldingAxis hoveredAxis
    ) {
        return replaceGizmo(
            scene,
            dynamicRevision,
            center,
            tool,
            worldUnitsPerPixel,
            cameraDirection,
            hoveredAxis,
            null
        );
    }

    public static EditorSceneMesh replaceGizmo(
        EditorSceneMesh scene,
        long dynamicRevision,
        Vector3d center,
        MoldingTool tool,
        double worldUnitsPerPixel,
        Vector3d cameraDirection,
        @Nullable MoldingAxis hoveredAxis,
        @Nullable ViewportTransform viewportTransform
    ) {
        List<EditorScenePart> parts = new ArrayList<>(scene.parts().size());
        scene.parts().stream()
            .filter(part -> part.phase() != EditorDrawPhase.GIZMO)
            .forEach(parts::add);
        MeshAccumulator gizmo = new MeshAccumulator(cameraDirection, worldUnitsPerPixel, viewportTransform);
        addGizmo(gizmo, center, tool, worldUnitsPerPixel, cameraDirection, hoveredAxis);
        if (!gizmo.indices.isEmpty()) parts.add(gizmo.build(EditorDrawPhase.GIZMO));
        return new EditorSceneMesh(scene.staticRevision(), dynamicRevision, parts);
    }

    private static void addFineGrid(MeshAccumulator mesh, double worldUnitsPerPixel) {
        double width = FINE_LINE_WIDTH_PIXELS * worldUnitsPerPixel;
        for (int value = 17; value < 32; value++) {
            mesh.segment(
                new Vector3d(value, 16.0D, 16.0D),
                new Vector3d(value, 16.0D, 32.0D),
                width,
                FINE_GRID_COLOR
            );
            mesh.segment(
                new Vector3d(16.0D, 16.0D, value),
                new Vector3d(32.0D, 16.0D, value),
                width,
                FINE_GRID_COLOR
            );
        }
    }

    private static void addEditorGrid(MeshAccumulator mesh, double worldUnitsPerPixel) {
        addEditorGrid(mesh, worldUnitsPerPixel, MAIN_LINE_WIDTH_PIXELS);
    }

    private static void addEditorGrid(
        MeshAccumulator mesh,
        double worldUnitsPerPixel,
        double lineWidthPixels
    ) {
        double width = lineWidthPixels * worldUnitsPerPixel;
        for (int value = 0; value <= 48; value += 16) {
            mesh.segment(
                new Vector3d(value, 16.0D, 0.0D),
                new Vector3d(value, 16.0D, 48.0D),
                width,
                MAIN_GRID_COLOR
            );
            mesh.segment(
                new Vector3d(0.0D, 16.0D, value),
                new Vector3d(48.0D, 16.0D, value),
                width,
                MAIN_GRID_COLOR
            );
        }
    }

    private static void addWorkspaceOutline(
        MeshAccumulator mesh,
        Vector3d directionToCamera,
        double worldUnitsPerPixel,
        @Nullable ViewportTransform viewportTransform
    ) {
        addWorkspaceOutline(
            mesh,
            directionToCamera,
            worldUnitsPerPixel,
            viewportTransform,
            null,
            WORKSPACE_LINE_WIDTH_PIXELS
        );
    }

    private static void addWorkspaceOutline(
        MeshAccumulator mesh,
        Vector3d directionToCamera,
        double worldUnitsPerPixel,
        @Nullable ViewportTransform viewportTransform,
        @Nullable Vector3d perspectiveCameraPosition
    ) {
        addWorkspaceOutline(
            mesh,
            directionToCamera,
            worldUnitsPerPixel,
            viewportTransform,
            perspectiveCameraPosition,
            WORKSPACE_LINE_WIDTH_PIXELS
        );
    }

    private static void addWorkspaceOutline(
        MeshAccumulator mesh,
        Vector3d directionToCamera,
        double worldUnitsPerPixel,
        @Nullable ViewportTransform viewportTransform,
        @Nullable Vector3d perspectiveCameraPosition,
        double lineWidthPixels
    ) {
        double width = lineWidthPixels * worldUnitsPerPixel;
        List<MoldingWorkspaceOutline.Edge> edges;
        if (viewportTransform != null) {
            edges = MoldingWorkspaceOutline.silhouetteEdges(
                viewportTransform.position(),
                viewportTransform.perspective(),
                directionToCamera
            );
        } else if (perspectiveCameraPosition != null) {
            edges = MoldingWorkspaceOutline.silhouetteEdges(
                perspectiveCameraPosition,
                true,
                directionToCamera
            );
        } else {
            edges = MoldingWorkspaceOutline.silhouetteEdges(directionToCamera);
        }
        for (MoldingWorkspaceOutline.Edge edge : edges) {
            mesh.segment(edge.from(), edge.to(), width, WORKSPACE_OUTLINE_COLOR);
        }
    }

    private static void addGroundAxes(MeshAccumulator mesh, double worldUnitsPerPixel) {
        addGroundAxes(mesh, worldUnitsPerPixel, GROUND_AXIS_WIDTH_PIXELS);
    }

    private static void addGroundAxes(
        MeshAccumulator mesh,
        double worldUnitsPerPixel,
        double lineWidthPixels
    ) {
        Vector3d origin = new Vector3d(
            MoldingCoordinateSystem.ORIGIN.x(),
            MoldingCoordinateSystem.ORIGIN.y() + 0.04D,
            MoldingCoordinateSystem.ORIGIN.z()
        );
        double width = lineWidthPixels * worldUnitsPerPixel;
        mesh.segment(origin, new Vector3d(32.0D, 16.04D, 16.0D), width, X_COLOR);
        mesh.segment(origin, new Vector3d(16.0D, 16.04D, 32.0D), width, Z_COLOR);
    }

    private static void addSourceOutlines(
        MeshAccumulator mesh,
        EditableMoldingModel model,
        int color,
        @Nullable MoldingSelection selection,
        double worldUnitsPerPixel
    ) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        Set<UUID> selectedElements = selection == null ? null : selectedElementIds(model, selection);
        for (MoldingElement element : model.elements()) {
            if (!isVisible(element, groups)
                || selectedElements != null && !selectedElements.contains(element.id())) {
                continue;
            }
            List<Vector3d> vertices = MoldingModelBaker.transformedVertices(model, element).stream()
                .map(MoldingSceneBuilder::vector)
                .toList();
            if (element.hasVolume()) {
                for (int[] edge : CUBE_EDGES) {
                    mesh.segment(
                        vertices.get(edge[0]),
                        vertices.get(edge[1]),
                        SOURCE_LINE_WIDTH_PIXELS * worldUnitsPerPixel,
                        color
                    );
                }
            } else {
                for (int index = 0; index < 4; index++) {
                    mesh.segment(
                        vertices.get(index),
                        vertices.get((index + 1) % 4),
                        SOURCE_LINE_WIDTH_PIXELS * worldUnitsPerPixel,
                        color
                    );
                }
            }
        }
    }

    private static void addSourceSurfaces(SceneAccumulators scene, EditableMoldingModel model) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        for (MoldingElement element : model.elements()) {
            if (!isVisible(element, groups)) continue;
            MeshAccumulator target = scene.accumulator(element.hasVolume()
                ? EditorDrawPhase.MANUFACTURING_SURFACE
                : EditorDrawPhase.ZERO_THICKNESS_SURFACE);
            addElementSurface(target, model, element, SURFACE_COLOR);
        }
    }

    private static void addHoveredSurface(
        MeshAccumulator mesh,
        EditableMoldingModel model,
        UUID hoveredElement
    ) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        model.elements().stream()
            .filter(element -> element.id().equals(hoveredElement) && isVisible(element, groups))
            .findFirst()
            .ifPresent(element -> addElementSurface(mesh, model, element, HOVER_COLOR));
    }

    private static void addElementSurface(
        MeshAccumulator mesh,
        EditableMoldingModel model,
        MoldingElement element,
        int color
    ) {
        List<Vector3d> vertices = MoldingModelBaker.transformedVertices(model, element).stream()
            .map(MoldingSceneBuilder::vector)
            .toList();
        if (element.hasVolume()) {
            boolean inward = hasInwardWinding(element);
            Vector3d center = new Vector3d();
            vertices.forEach(center::add);
            center.div(vertices.size());
            for (int[] face : CUBE_FACES) {
                Vector3d first = vertices.get(face[0]);
                Vector3d second = vertices.get(face[1]);
                Vector3d third = vertices.get(face[2]);
                Vector3d fourth = vertices.get(face[3]);
                addDirectedSurface(mesh, center, first, second, third, fourth, color, inward);
            }
            return;
        }
        mesh.quad(
            vertices.get(0),
            vertices.get(1),
            vertices.get(2),
            vertices.get(3),
            shadeSurface(color, vertices.get(0), vertices.get(1), vertices.get(2))
        );
    }

    private static boolean hasInwardWinding(MoldingElement element) {
        int negativeAxes = 0;
        if (element.to().x() < element.from().x()) negativeAxes++;
        if (element.to().y() < element.from().y()) negativeAxes++;
        if (element.to().z() < element.from().z()) negativeAxes++;
        return (negativeAxes & 1) != 0;
    }

    private static void addDirectedSurface(
        MeshAccumulator mesh,
        Vector3d center,
        Vector3d first,
        Vector3d second,
        Vector3d third,
        Vector3d fourth,
        int color,
        boolean inward
    ) {
        Vector3d normal = new Vector3d(second).sub(first).cross(new Vector3d(third).sub(first));
        Vector3d faceCenter = new Vector3d(first).add(second).add(third).add(fourth).mul(0.25D);
        boolean pointsInward = normal.dot(new Vector3d(faceCenter).sub(center)) < 0.0D;
        // cube 的端点顺序同时决定可见正面和逐面光照，两者必须使用同一绕序。
        if (pointsInward != inward) {
            mesh.quad(fourth, third, second, first, shadeSurface(color, fourth, third, second));
            return;
        }
        mesh.quad(first, second, third, fourth, shadeSurface(color, first, second, third));
    }

    private static int shadeSurface(int color, Vector3d first, Vector3d second, Vector3d third) {
        Vector3d normal = new Vector3d(second).sub(first).cross(new Vector3d(third).sub(first));
        if (normal.lengthSquared() < 1.0E-12D) return color;
        double diffuse = Math.max(0.0D, normal.normalize().dot(SURFACE_LIGHT_DIRECTION));
        double brightness = SURFACE_AMBIENT_LIGHT + (1.0D - SURFACE_AMBIENT_LIGHT) * diffuse;
        int alpha = color >>> 24;
        int red = (int) Math.round(((color >> 16) & 0xFF) * brightness);
        int green = (int) Math.round(((color >> 8) & 0xFF) * brightness);
        int blue = (int) Math.round((color & 0xFF) * brightness);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static Set<UUID> selectedElementIds(EditableMoldingModel model, MoldingSelection selection) {
        Set<UUID> selectedGroups = new HashSet<>();
        for (MoldingGroup group : model.groups()) {
            if (selection.contains(group.id())) selectedGroups.add(group.id());
        }
        boolean changed;
        do {
            changed = false;
            for (MoldingGroup group : model.groups()) {
                if (group.parentId().filter(selectedGroups::contains).isPresent()) {
                    changed |= selectedGroups.add(group.id());
                }
            }
        } while (changed);
        Set<UUID> result = new HashSet<>();
        for (MoldingElement element : model.elements()) {
            if (selection.contains(element.id()) || element.groupId().filter(selectedGroups::contains).isPresent()) {
                result.add(element.id());
            }
        }
        return Set.copyOf(result);
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

    private static void addGizmo(
        MeshAccumulator mesh,
        Vector3d center,
        MoldingTool tool,
        double worldUnitsPerPixel,
        Vector3d cameraDirection,
        @Nullable MoldingAxis hoveredAxis
    ) {
        switch (tool) {
            case NONE -> {
            }
            case MOVE, PIVOT -> addMoveGizmo(mesh, center, worldUnitsPerPixel, hoveredAxis);
            case SCALE -> addScaleGizmo(mesh, center, worldUnitsPerPixel, hoveredAxis);
            case ROTATE -> addRotateGizmo(mesh, center, worldUnitsPerPixel, cameraDirection, hoveredAxis);
            case MIRROR -> addMirrorGizmo(mesh, center, worldUnitsPerPixel, hoveredAxis);
        }
    }

    private static void addMoveGizmo(
        MeshAccumulator mesh,
        Vector3d center,
        double worldUnitsPerPixel,
        @Nullable MoldingAxis hoveredAxis
    ) {
        double length = MoldingGizmoGeometry.axisLength(worldUnitsPerPixel);
        double arrowLength = MoldingGizmoGeometry.arrowLength(worldUnitsPerPixel);
        for (MoldingAxis axis : MoldingAxis.values()) {
            addMoveAxis(mesh, center, axis, length, arrowLength, worldUnitsPerPixel, axisColor(axis));
            if (axis == hoveredAxis) {
                addMoveAxis(mesh, center, axis, length, arrowLength, worldUnitsPerPixel, HOVER_COLOR);
            }
        }
    }

    private static void addMoveAxis(
        MeshAccumulator mesh,
        Vector3d center,
        MoldingAxis axis,
        double length,
        double arrowLength,
        double worldUnitsPerPixel,
        int color
    ) {
        Vector3d direction = axis.vector();
        Vector3d base = new Vector3d(direction).mul(length - arrowLength).add(center);
        Vector3d tip = new Vector3d(direction).mul(length).add(center);
        mesh.segment(center, base, MoldingGizmoGeometry.shaftWidth(worldUnitsPerPixel), color);
        mesh.pyramid(base, tip, MoldingGizmoGeometry.arrowRadius(worldUnitsPerPixel), color);
    }

    private static void addScaleGizmo(
        MeshAccumulator mesh,
        Vector3d center,
        double worldUnitsPerPixel,
        @Nullable MoldingAxis hoveredAxis
    ) {
        double length = MoldingGizmoGeometry.axisLength(worldUnitsPerPixel);
        double halfSize = MoldingGizmoGeometry.handleHalfSize(worldUnitsPerPixel);
        for (MoldingAxis axis : MoldingAxis.values()) {
            addScaleAxis(mesh, center, axis, length, halfSize, worldUnitsPerPixel, axisColor(axis));
            if (axis == hoveredAxis) {
                addScaleAxis(mesh, center, axis, length, halfSize, worldUnitsPerPixel, HOVER_COLOR);
            }
        }
    }

    private static void addScaleAxis(
        MeshAccumulator mesh,
        Vector3d center,
        MoldingAxis axis,
        double length,
        double halfSize,
        double worldUnitsPerPixel,
        int color
    ) {
        Vector3d offset = axis.vector().mul(length);
        Vector3d negative = new Vector3d(center).sub(offset);
        Vector3d positive = new Vector3d(center).add(offset);
        mesh.segment(negative, positive, MoldingGizmoGeometry.shaftWidth(worldUnitsPerPixel), color);
        mesh.box(
            new Vector3d(negative).sub(halfSize, halfSize, halfSize),
            new Vector3d(negative).add(halfSize, halfSize, halfSize),
            color
        );
        mesh.box(
            new Vector3d(positive).sub(halfSize, halfSize, halfSize),
            new Vector3d(positive).add(halfSize, halfSize, halfSize),
            color
        );
    }

    private static void addRotateGizmo(
        MeshAccumulator mesh,
        Vector3d center,
        double worldUnitsPerPixel,
        Vector3d cameraDirection,
        @Nullable MoldingAxis hoveredAxis
    ) {
        mesh.sphere(center, MoldingGizmoGeometry.pivotRadius(worldUnitsPerPixel), PIVOT_COLOR);
        double radius = MoldingGizmoGeometry.ringRadius(worldUnitsPerPixel);
        for (MoldingAxis axis : MoldingAxis.values()) {
            addRotationAxis(mesh, center, axis, radius, worldUnitsPerPixel, cameraDirection, axisColor(axis));
            if (axis == hoveredAxis) {
                addRotationAxis(mesh, center, axis, radius, worldUnitsPerPixel, cameraDirection, HOVER_COLOR);
            }
        }
        addViewRing(mesh, center, worldUnitsPerPixel, cameraDirection);
    }

    private static void addRotationAxis(
        MeshAccumulator mesh,
        Vector3d center,
        MoldingAxis axis,
        double radius,
        double worldUnitsPerPixel,
        Vector3d cameraDirection,
        int color
    ) {
        for (int segment = 0; segment < MoldingGizmoGeometry.RING_SEGMENTS; segment++) {
            double startAngle = Math.TAU * segment / MoldingGizmoGeometry.RING_SEGMENTS;
            double endAngle = Math.TAU * (segment + 1) / MoldingGizmoGeometry.RING_SEGMENTS;
            Vector3d start = MoldingGizmoGeometry.ringPoint(center, axis, radius, startAngle);
            Vector3d end = MoldingGizmoGeometry.ringPoint(center, axis, radius, endAngle);
            Vector3d midpoint = new Vector3d(start).add(end).mul(0.5D);
            if (!MoldingGizmoGeometry.isFrontFacing(midpoint, center, cameraDirection)) continue;
            mesh.segment(start, end, MoldingGizmoGeometry.ringWidth(worldUnitsPerPixel), color);
        }
    }

    private static void addViewRing(
        MeshAccumulator mesh,
        Vector3d center,
        double worldUnitsPerPixel,
        Vector3d cameraDirection
    ) {
        double radius = MoldingGizmoGeometry.outerRingRadius(worldUnitsPerPixel);
        for (int segment = 0; segment < MoldingGizmoGeometry.RING_SEGMENTS; segment++) {
            double startAngle = Math.TAU * segment / MoldingGizmoGeometry.RING_SEGMENTS;
            double endAngle = Math.TAU * (segment + 1) / MoldingGizmoGeometry.RING_SEGMENTS;
            mesh.segment(
                MoldingGizmoGeometry.viewRingPoint(center, cameraDirection, radius, startAngle),
                MoldingGizmoGeometry.viewRingPoint(center, cameraDirection, radius, endAngle),
                MoldingGizmoGeometry.outerRingWidth(worldUnitsPerPixel),
                OUTER_RING_COLOR
            );
        }
    }

    private static void addMirrorGizmo(
        MeshAccumulator mesh,
        Vector3d center,
        double worldUnitsPerPixel,
        @Nullable MoldingAxis hoveredAxis
    ) {
        double length = MoldingGizmoGeometry.axisLength(worldUnitsPerPixel);
        for (MoldingAxis axis : MoldingAxis.values()) {
            addMirrorAxis(mesh, center, axis, length, worldUnitsPerPixel, axisColor(axis));
            if (axis == hoveredAxis) {
                addMirrorAxis(mesh, center, axis, length, worldUnitsPerPixel, HOVER_COLOR);
            }
        }
    }

    private static void addMirrorAxis(
        MeshAccumulator mesh,
        Vector3d center,
        MoldingAxis axis,
        double length,
        double worldUnitsPerPixel,
        int color
    ) {
        Vector3d tip = axis.vector().mul(length).add(center);
        mesh.segment(center, tip, MoldingGizmoGeometry.shaftWidth(worldUnitsPerPixel), color);
        mesh.diamond(tip, axis.vector(), MoldingGizmoGeometry.mirrorRadius(worldUnitsPerPixel), color);
    }

    private static int axisColor(MoldingAxis axis) {
        return switch (axis) {
            case X -> X_COLOR;
            case Y -> Y_COLOR;
            case Z -> Z_COLOR;
        };
    }

    private static Vector3d vector(MoldingVec3 value) {
        return new Vector3d(value.x(), value.y(), value.z());
    }

    private static final class SceneAccumulators {
        private final Map<EditorDrawPhase, MeshAccumulator> accumulators = new EnumMap<>(EditorDrawPhase.class);
        private final Vector3d directionToCamera;
        private final double fallbackWorldUnitsPerPixel;
        @Nullable
        private final ViewportTransform viewportTransform;

        private SceneAccumulators(
            Vector3d directionToCamera,
            double fallbackWorldUnitsPerPixel,
            @Nullable ViewportTransform viewportTransform
        ) {
            this.directionToCamera = new Vector3d(directionToCamera);
            this.fallbackWorldUnitsPerPixel = fallbackWorldUnitsPerPixel;
            this.viewportTransform = viewportTransform;
        }

        private MeshAccumulator accumulator(EditorDrawPhase phase) {
            return this.accumulators.computeIfAbsent(phase, ignored -> new MeshAccumulator(
                this.directionToCamera,
                this.fallbackWorldUnitsPerPixel,
                this.viewportTransform
            ));
        }

        private EditorSceneMesh build(long staticRevision, long dynamicRevision) {
            List<EditorScenePart> parts = new ArrayList<>();
            for (EditorDrawPhase phase : EditorDrawPhase.values()) {
                MeshAccumulator accumulator = this.accumulators.get(phase);
                if (accumulator != null && !accumulator.indices.isEmpty()) parts.add(accumulator.build(phase));
            }
            return new EditorSceneMesh(staticRevision, dynamicRevision, parts);
        }
    }

    private static final class MeshAccumulator {
        private final List<EditorVertex> vertices = new ArrayList<>();
        private final List<Integer> indices = new ArrayList<>();
        private final Vector3d fallbackDirectionToCamera;
        private final double fallbackWorldUnitsPerPixel;
        @Nullable
        private final ViewportTransform viewportTransform;

        private MeshAccumulator(
            Vector3d fallbackDirectionToCamera,
            double fallbackWorldUnitsPerPixel,
            @Nullable ViewportTransform viewportTransform
        ) {
            this.fallbackDirectionToCamera = new Vector3d(fallbackDirectionToCamera);
            this.fallbackWorldUnitsPerPixel = fallbackWorldUnitsPerPixel;
            this.viewportTransform = viewportTransform;
        }

        private void quad(Vector3d first, Vector3d second, Vector3d third, Vector3d fourth, int color) {
            this.quad(first, color, second, color, third, color, fourth, color);
        }

        private void quad(
            Vector3d first,
            int firstColor,
            Vector3d second,
            int secondColor,
            Vector3d third,
            int thirdColor,
            Vector3d fourth,
            int fourthColor
        ) {
            int start = this.vertices.size();
            this.vertices.add(vertex(first, firstColor));
            this.vertices.add(vertex(second, secondColor));
            this.vertices.add(vertex(third, thirdColor));
            this.vertices.add(vertex(fourth, fourthColor));
            this.indices.add(start);
            this.indices.add(start + 1);
            this.indices.add(start + 2);
            this.indices.add(start + 3);
        }

        private void segment(Vector3d start, Vector3d end, double width, int color) {
            Vector3d direction = new Vector3d(end).sub(start);
            if (direction.lengthSquared() < 1.0E-12D) return;
            direction.normalize();
            Vector3d midpoint = new Vector3d(start).add(end).mul(0.5D);
            Vector3d directionToCamera = this.viewportTransform == null
                ? new Vector3d(this.fallbackDirectionToCamera)
                : this.viewportTransform.directionToCamera(midpoint);
            Vector3d side = new Vector3d(direction).cross(directionToCamera);
            if (side.lengthSquared() < 1.0E-12D) {
                Vector3d reference = Math.abs(direction.y) < 0.9D
                    ? new Vector3d(0.0D, 1.0D, 0.0D)
                    : new Vector3d(1.0D, 0.0D, 0.0D);
                side.set(direction).cross(reference);
            }
            double worldUnitsPerPixel = this.viewportTransform == null
                ? this.fallbackWorldUnitsPerPixel
                : this.viewportTransform.worldUnitsPerPixel(midpoint);
            double linePixels = Math.max(
                0.35D,
                width / Math.max(1.0E-9D, this.fallbackWorldUnitsPerPixel)
            );
            double halfWidth = linePixels * 0.5D * worldUnitsPerPixel;
            side.normalize();
            Vector3d across = new Vector3d(side).mul(halfWidth);
            Vector3d along = new Vector3d(direction).mul(halfWidth);
            Vector3d extendedStart = new Vector3d(start).sub(along);
            Vector3d extendedEnd = new Vector3d(end).add(along);
            this.quad(
                new Vector3d(extendedStart).sub(across),
                new Vector3d(extendedEnd).sub(across),
                new Vector3d(extendedEnd).add(across),
                new Vector3d(extendedStart).add(across),
                color
            );
        }

        private void pyramid(Vector3d base, Vector3d tip, double radius, int color) {
            Vector3d direction = new Vector3d(tip).sub(base).normalize();
            Vector3d reference = Math.abs(direction.y) < 0.9D
                ? new Vector3d(0.0D, 1.0D, 0.0D)
                : new Vector3d(1.0D, 0.0D, 0.0D);
            Vector3d firstAxis = new Vector3d(direction).cross(reference).normalize(radius);
            Vector3d secondAxis = new Vector3d(direction).cross(firstAxis).normalize(radius);
            Vector3d[] corners = {
                new Vector3d(base).add(firstAxis).add(secondAxis),
                new Vector3d(base).sub(firstAxis).add(secondAxis),
                new Vector3d(base).sub(firstAxis).sub(secondAxis),
                new Vector3d(base).add(firstAxis).sub(secondAxis)
            };
            this.quad(corners[3], corners[2], corners[1], corners[0], color);
            for (int index = 0; index < corners.length; index++) {
                Vector3d first = corners[index];
                Vector3d second = corners[(index + 1) % corners.length];
                this.quad(first, second, tip, tip, color);
            }
        }

        private void diamond(Vector3d center, Vector3d normal, double radius, int color) {
            Vector3d direction = new Vector3d(normal).normalize();
            Vector3d reference = Math.abs(direction.y) < 0.9D
                ? new Vector3d(0.0D, 1.0D, 0.0D)
                : new Vector3d(1.0D, 0.0D, 0.0D);
            Vector3d firstAxis = new Vector3d(direction).cross(reference).normalize(radius);
            Vector3d secondAxis = new Vector3d(direction).cross(firstAxis).normalize(radius);
            this.quad(
                new Vector3d(center).add(firstAxis),
                new Vector3d(center).add(secondAxis),
                new Vector3d(center).sub(firstAxis),
                new Vector3d(center).sub(secondAxis),
                color
            );
        }

        private void sphere(Vector3d center, double radius, int color) {
            int latitudeSegments = 6;
            int longitudeSegments = 12;
            for (int latitude = 0; latitude < latitudeSegments; latitude++) {
                double firstLatitude = -Math.PI * 0.5D + Math.PI * latitude / latitudeSegments;
                double secondLatitude = -Math.PI * 0.5D + Math.PI * (latitude + 1) / latitudeSegments;
                for (int longitude = 0; longitude < longitudeSegments; longitude++) {
                    double firstLongitude = Math.TAU * longitude / longitudeSegments;
                    double secondLongitude = Math.TAU * (longitude + 1) / longitudeSegments;
                    this.quad(
                        spherePoint(center, radius, firstLatitude, firstLongitude),
                        spherePoint(center, radius, firstLatitude, secondLongitude),
                        spherePoint(center, radius, secondLatitude, secondLongitude),
                        spherePoint(center, radius, secondLatitude, firstLongitude),
                        color
                    );
                }
            }
        }

        private static Vector3d spherePoint(Vector3d center, double radius, double latitude, double longitude) {
            double horizontal = Math.cos(latitude) * radius;
            return new Vector3d(center).add(
                Math.cos(longitude) * horizontal,
                Math.sin(latitude) * radius,
                Math.sin(longitude) * horizontal
            );
        }

        private void box(Vector3d min, Vector3d max, int color) {
            Vector3d[] corners = {
                new Vector3d(min.x, min.y, min.z), new Vector3d(max.x, min.y, min.z),
                new Vector3d(max.x, max.y, min.z), new Vector3d(min.x, max.y, min.z),
                new Vector3d(min.x, min.y, max.z), new Vector3d(max.x, min.y, max.z),
                new Vector3d(max.x, max.y, max.z), new Vector3d(min.x, max.y, max.z)
            };
            this.quad(corners[0], corners[3], corners[2], corners[1], color);
            this.quad(corners[4], corners[5], corners[6], corners[7], color);
            this.quad(corners[0], corners[4], corners[7], corners[3], color);
            this.quad(corners[1], corners[2], corners[6], corners[5], color);
            this.quad(corners[0], corners[1], corners[5], corners[4], color);
            this.quad(corners[3], corners[7], corners[6], corners[2], color);
        }

        private EditorScenePart build(EditorDrawPhase phase) {
            int[] rawIndices = this.indices.stream().mapToInt(Integer::intValue).toArray();
            return new EditorScenePart(phase, this.vertices, rawIndices);
        }

        private static EditorVertex vertex(Vector3d value, int color) {
            return new EditorVertex((float) value.x, (float) value.y, (float) value.z, color);
        }
    }
}
