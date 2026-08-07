package dev.anvilcraft.plasticraft.gametest;

import dev.anvilcraft.plasticraft.client.molding.editor.MoldingAxis;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingEditorController;
import dev.anvilcraft.plasticraft.client.molding.editor.MoldingTool;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;
import org.joml.Vector3d;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class MoldingEditorGameTests {
    private static final double EPSILON = 1.0E-7D;

    private MoldingEditorGameTests() {
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "A shared gizmo moves every selected cube and its pivot along the same world axis")
    static void sharedGizmoMovesEverySelectedCube(ExtendedGameTestHelper helper) {
        MoldingElement first = rotatedCube(
            "First",
            Optional.empty(),
            new MoldingVec3(16.0D, 16.0D, 16.0D),
            new MoldingVec3(18.0D, 18.0D, 18.0D),
            new MoldingVec3(0.0D, 0.0D, 30.0D)
        );
        UUID groupId = UUID.randomUUID();
        MoldingVec3 secondCenter = new MoldingVec3(26.0D, 18.0D, 17.0D);
        MoldingGroup group = new MoldingGroup(
            groupId,
            "Rotated Group",
            Optional.empty(),
            new MoldingTransform(
                MoldingVec3.ZERO,
                new MoldingVec3(0.0D, 0.0D, 90.0D),
                MoldingVec3.ONE,
                secondCenter
            ),
            true,
            false
        );
        MoldingElement second = rotatedCube(
            "Second",
            Optional.of(groupId),
            new MoldingVec3(24.0D, 16.0D, 16.0D),
            new MoldingVec3(28.0D, 20.0D, 18.0D),
            new MoldingVec3(0.0D, 0.0D, -20.0D)
        );
        EditableMoldingModel model = EditableMoldingModel.empty()
            .withGroups(List.of(group))
            .withElements(List.of(first, second));
        MoldingEditorController editor = editor(model);
        select(editor, first, second);

        Vector3d sharedCenter = editor.selectionCenter();
        editor.setTool(MoldingTool.ROTATE);
        assertVector(editor.gizmoOrigin(), sharedCenter, "multi-selection rotation gizmo used a primary pivot");

        List<MoldingVec3> firstVertices = MoldingModelBaker.transformedVertices(model, first);
        List<MoldingVec3> secondVertices = MoldingModelBaker.transformedVertices(model, second);
        MoldingVec3 firstPivot = worldPivot(model, first);
        MoldingVec3 secondPivot = worldPivot(model, second);
        editor.setTool(MoldingTool.MOVE);
        check(editor.beginDrag(MoldingAxis.X), "multi-selection move drag did not start");
        editor.updateDrag(3.4D);

        EditableMoldingModel moved = editor.model();
        MoldingElement movedFirst = element(moved, first.id());
        MoldingElement movedSecond = element(moved, second.id());
        check(movedFirst.transform().rotation().equals(first.transform().rotation()),
            "moving the first cube changed its rotation");
        check(movedSecond.transform().rotation().equals(second.transform().rotation()),
            "moving the grouped cube changed its rotation");
        MoldingVec3 offset = new MoldingVec3(3.0D, 0.0D, 0.0D);
        assertVector(worldPivot(moved, movedFirst), firstPivot.add(offset), "first pivot did not move with its cube");
        assertVector(
            worldPivot(moved, movedSecond),
            secondPivot.add(offset),
            "grouped pivot moved on a local axis"
        );
        assertTranslated(firstVertices, MoldingModelBaker.transformedVertices(moved, movedFirst), offset, "first cube");
        assertTranslated(
            secondVertices,
            MoldingModelBaker.transformedVertices(moved, movedSecond),
            offset,
            "grouped cube"
        );
        assertVector(
            editor.gizmoOrigin(),
            new Vector3d(sharedCenter).add(3.0D, 0.0D, 0.0D),
            "move gizmo did not follow the shared selection center"
        );
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Center Pivot gives selected cubes one pivot and preserves their layout while rotating")
    static void centeredPivotsRotateSelectionAsOneLayout(ExtendedGameTestHelper helper) {
        MoldingElement first = MoldingElement.cube(
            "First",
            new MoldingVec3(17.0D, 18.0D, 18.0D),
            new MoldingVec3(19.0D, 20.0D, 20.0D)
        );
        MoldingElement second = MoldingElement.cube(
            "Second",
            new MoldingVec3(25.0D, 20.0D, 18.0D),
            new MoldingVec3(29.0D, 24.0D, 22.0D)
        );
        EditableMoldingModel original = EditableMoldingModel.empty().withElements(List.of(first, second));
        MoldingEditorController editor = editor(original);
        select(editor, first, second);
        Vector3d selectionCenter = editor.selectionCenter();
        MoldingVec3 sharedPivot = new MoldingVec3(selectionCenter.x(), selectionCenter.y(), selectionCenter.z());
        check(editor.centerPivot(), "multi-selection center pivot command was rejected");
        EditableMoldingModel centered = editor.model();
        MoldingElement centeredFirst = element(centered, first.id());
        MoldingElement centeredSecond = element(centered, second.id());
        assertVector(worldPivot(centered, centeredFirst), sharedPivot, "first cube did not receive the shared pivot");
        assertVector(worldPivot(centered, centeredSecond), sharedPivot, "second cube did not receive the shared pivot");
        assertVerticesEqual(
            MoldingModelBaker.transformedVertices(original, first),
            MoldingModelBaker.transformedVertices(centered, centeredFirst),
            "centering moved the unrotated first cube"
        );
        assertVerticesEqual(
            MoldingModelBaker.transformedVertices(original, second),
            MoldingModelBaker.transformedVertices(centered, centeredSecond),
            "centering moved the unrotated second cube"
        );

        MoldingElement preparedFirst = withRotation(centeredFirst, new MoldingVec3(10.0D, 20.0D, 30.0D));
        MoldingElement preparedSecond = withRotation(centeredSecond, new MoldingVec3(-15.0D, 35.0D, -25.0D));
        EditableMoldingModel prepared = centered.withElements(List.of(preparedFirst, preparedSecond));
        List<MoldingVec3> preparedFirstVertices = MoldingModelBaker.transformedVertices(prepared, preparedFirst);
        List<MoldingVec3> preparedSecondVertices = MoldingModelBaker.transformedVertices(prepared, preparedSecond);
        MoldingVec3 firstCenter = visualCenter(prepared, preparedFirst);
        MoldingVec3 secondCenter = visualCenter(prepared, preparedSecond);

        editor.syncAuthoritative(prepared, 1L, true);
        editor.setTool(MoldingTool.ROTATE);
        assertVector(editor.gizmoOrigin(), selectionCenter, "rotation gizmo did not use the shared pivot");
        check(editor.beginDrag(MoldingAxis.X), "shared-pivot rotation drag did not start");
        editor.updateDrag(90.0D);
        assertVector(editor.gizmoOrigin(), selectionCenter, "shared rotation gizmo drifted during the drag");
        check(editor.finishDrag(), "shared-pivot rotation command was rejected");

        EditableMoldingModel rotated = editor.model();
        MoldingElement rotatedFirst = element(rotated, first.id());
        MoldingElement rotatedSecond = element(rotated, second.id());
        MoldingVec3 expectedFirstCenter = rotateX(firstCenter, sharedPivot);
        MoldingVec3 expectedSecondCenter = rotateX(secondCenter, sharedPivot);
        assertVector(visualCenter(rotated, rotatedFirst), expectedFirstCenter,
            "first cube did not rotate around the shared pivot");
        assertVector(visualCenter(rotated, rotatedSecond), expectedSecondCenter,
            "second cube did not rotate around the shared pivot");
        assertRotated(preparedFirstVertices, MoldingModelBaker.transformedVertices(rotated, rotatedFirst), sharedPivot,
            "first cube");
        assertRotated(
            preparedSecondVertices,
            MoldingModelBaker.transformedVertices(rotated, rotatedSecond),
            sharedPivot,
            "second cube"
        );
        assertVector(
            visualCenter(rotated, rotatedSecond).subtract(visualCenter(rotated, rotatedFirst)),
            rotateX(secondCenter.subtract(firstCenter), MoldingVec3.ZERO),
            "rotation changed the selected cubes' relative layout"
        );
        assertVector(worldPivot(rotated, rotatedFirst), sharedPivot, "first shared pivot moved during rotation");
        assertVector(worldPivot(rotated, rotatedSecond), sharedPivot, "second shared pivot moved during rotation");
        assertVector(editor.gizmoOrigin(), selectionCenter, "shared rotation gizmo moved after commit");
        helper.succeed();
    }

    @GameTest(timeoutTicks = 20)
    @EmptyTemplate(value = "3x3x3", floor = true)
    @TestHolder(description = "Element-list selection supports single, toggle, range, and additive range selection")
    static void elementListSelectionUsesDesktopModifierSemantics(ExtendedGameTestHelper helper) {
        List<MoldingElement> cubes = List.of(
            MoldingElement.cube(
                "First",
                new MoldingVec3(16.0D, 16.0D, 16.0D),
                new MoldingVec3(17.0D, 17.0D, 17.0D)
            ),
            MoldingElement.cube(
                "Second",
                new MoldingVec3(18.0D, 16.0D, 16.0D),
                new MoldingVec3(19.0D, 17.0D, 17.0D)
            ),
            MoldingElement.cube(
                "Third",
                new MoldingVec3(20.0D, 16.0D, 16.0D),
                new MoldingVec3(21.0D, 17.0D, 17.0D)
            ),
            MoldingElement.cube(
                "Fourth",
                new MoldingVec3(22.0D, 16.0D, 16.0D),
                new MoldingVec3(23.0D, 17.0D, 17.0D)
            ),
            MoldingElement.cube(
                "Fifth",
                new MoldingVec3(24.0D, 16.0D, 16.0D),
                new MoldingVec3(25.0D, 17.0D, 17.0D)
            )
        );
        List<UUID> order = cubes.stream().map(MoldingElement::id).toList();
        MoldingEditorController editor = editor(EditableMoldingModel.empty().withElements(cubes));

        editor.select(order.get(0), false);
        editor.select(order.get(4), true);
        check(editor.selection().ids().equals(List.of(order.get(0), order.get(4))),
            "control selection did not retain the original cube");
        editor.select(order.get(4), true);
        check(editor.selection().ids().equals(List.of(order.get(0))),
            "control selection did not toggle the clicked cube off");

        editor.select(order.get(4), true);
        editor.selectRange(order, order.get(4), order.get(1), false);
        check(editor.selection().ids().equals(List.of(order.get(4), order.get(3), order.get(2), order.get(1))),
            "shift selection did not replace the selection with the anchored range");
        check(editor.selection().primary().orElseThrow().equals(order.get(1)),
            "shift selection did not make the clicked cube primary");

        editor.select(order.get(0), false);
        editor.select(order.get(4), true);
        editor.selectRange(order, order.get(0), order.get(2), true);
        check(editor.selection().ids().equals(List.of(order.get(4), order.get(0), order.get(1), order.get(2))),
            "control-shift selection did not add the complete range");
        helper.succeed();
    }

    private static MoldingEditorController editor(EditableMoldingModel model) {
        return new MoldingEditorController(model, 0L, true, (revision, command) -> {
        });
    }

    private static void select(MoldingEditorController editor, MoldingElement first, MoldingElement second) {
        editor.select(first.id(), false);
        editor.select(second.id(), true);
    }

    private static MoldingElement rotatedCube(
        String name,
        Optional<UUID> groupId,
        MoldingVec3 from,
        MoldingVec3 to,
        MoldingVec3 rotation
    ) {
        MoldingElement cube = MoldingElement.cube(name, from, to);
        MoldingTransform transform = cube.transform();
        return new MoldingElement(
            cube.id(),
            cube.name(),
            groupId,
            cube.from(),
            cube.to(),
            new MoldingTransform(transform.translation(), rotation, transform.scale(), transform.pivot()),
            cube.visible(),
            cube.locked()
        );
    }

    private static MoldingElement withRotation(MoldingElement element, MoldingVec3 rotation) {
        MoldingTransform transform = element.transform();
        return new MoldingElement(
            element.id(),
            element.name(),
            element.groupId(),
            element.from(),
            element.to(),
            new MoldingTransform(transform.translation(), rotation, transform.scale(), transform.pivot()),
            element.visible(),
            element.locked()
        );
    }

    private static MoldingElement element(EditableMoldingModel model, UUID id) {
        return model.elements().stream()
            .filter(element -> element.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new GameTestAssertException("missing edited cube " + id));
    }

    private static MoldingVec3 worldPivot(EditableMoldingModel model, MoldingElement element) {
        return MoldingModelBaker.transformedPoint(model, element, element.transform().pivot());
    }

    private static MoldingVec3 visualCenter(EditableMoldingModel model, MoldingElement element) {
        List<MoldingVec3> vertices = MoldingModelBaker.transformedVertices(model, element);
        MoldingVec3 minimum = vertices.getFirst();
        MoldingVec3 maximum = vertices.getFirst();
        for (MoldingVec3 vertex : vertices) {
            minimum = minimum.min(vertex);
            maximum = maximum.max(vertex);
        }
        return minimum.add(maximum).scale(0.5D);
    }

    private static MoldingVec3 rotateX(MoldingVec3 point, MoldingVec3 pivot) {
        MoldingVec3 local = point.subtract(pivot);
        return new MoldingVec3(local.x(), -local.z(), local.y()).add(pivot);
    }

    private static void assertTranslated(
        List<MoldingVec3> before,
        List<MoldingVec3> after,
        MoldingVec3 offset,
        String label
    ) {
        check(before.size() == after.size(), label + " changed its vertex count");
        for (int index = 0; index < before.size(); index++) {
            assertVector(after.get(index), before.get(index).add(offset), label + " did not move rigidly");
        }
    }

    private static void assertVerticesEqual(List<MoldingVec3> expected, List<MoldingVec3> actual, String message) {
        check(expected.size() == actual.size(), message + ": vertex count changed");
        for (int index = 0; index < expected.size(); index++) {
            assertVector(actual.get(index), expected.get(index), message);
        }
    }

    private static void assertRotated(
        List<MoldingVec3> before,
        List<MoldingVec3> after,
        MoldingVec3 pivot,
        String label
    ) {
        check(before.size() == after.size(), label + " changed its vertex count");
        for (int index = 0; index < before.size(); index++) {
            assertVector(after.get(index), rotateX(before.get(index), pivot),
                label + " did not preserve its rigid transform");
        }
    }

    private static void assertVector(Vector3d actual, Vector3d expected, String message) {
        if (actual.distance(expected) > EPSILON) {
            throw new GameTestAssertException(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void assertVector(MoldingVec3 actual, MoldingVec3 expected, String message) {
        double dx = actual.x() - expected.x();
        double dy = actual.y() - expected.y();
        double dz = actual.z() - expected.z();
        if (dx * dx + dy * dy + dz * dz > EPSILON * EPSILON) {
            throw new GameTestAssertException(message + ": expected=" + expected + ", actual=" + actual);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
