package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 一次按下到释放只生成一条命令，途中预览不会进入服务端历史。 */
public final class MoldingDragTransaction {
    private final EditableMoldingModel originalModel;
    private final Set<UUID> selectedIds;
    private final MoldingTool tool;
    private final MoldingAxis axis;
    private final double axisDirection;
    private final MoldingVec3 rotationCenter;
    private final MoldingGizmoBasis scaleBasis;
    private EditableMoldingModel previewModel;
    private double value;

    public MoldingDragTransaction(
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        MoldingAxis axis
    ) {
        this(model, selection, tool, axis, 1.0D);
    }

    public MoldingDragTransaction(
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        MoldingAxis axis,
        double axisDirection
    ) {
        this(model, selection, tool, axis, axisDirection, null);
    }

    public MoldingDragTransaction(
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool,
        MoldingAxis axis,
        double axisDirection,
        @Nullable MoldingVec3 rotationCenter
    ) {
        this.originalModel = model;
        this.previewModel = model;
        this.selectedIds = selectedElementIds(model, selection);
        this.tool = tool;
        this.axis = axis;
        this.axisDirection = Math.copySign(1.0D, axisDirection);
        this.rotationCenter = rotationCenter == null ? center(model, this.selectedIds) : rotationCenter;
        this.scaleBasis = MoldingGizmoBasis.forSelection(model, selection, MoldingTool.SCALE);
        if (tool == MoldingTool.NONE || tool == MoldingTool.MIRROR) {
            throw new IllegalArgumentException("Selected tool cannot start a drag transaction");
        }
    }

    public EditableMoldingModel originalModel() {
        return this.originalModel;
    }

    public EditableMoldingModel preview(double rawValue) {
        return this.preview(rawValue, this.tool == MoldingTool.ROTATE ? 15.0D : 1.0D);
    }

    public EditableMoldingModel preview(double rawValue, double step) {
        if (!Double.isFinite(rawValue) || !Double.isFinite(step) || step < 0.0D) {
            throw new IllegalArgumentException("Invalid transform step");
        }
        this.value = step == 0.0D ? rawValue : Math.rint(rawValue / step) * step;
        List<MoldingElement> replacements = new ArrayList<>(this.originalModel.elements().size());
        for (MoldingElement element : this.originalModel.elements()) {
            replacements.add(this.selectedIds.contains(element.id())
                && !isEffectivelyLocked(this.originalModel, element)
                ? transform(element, this.value)
                : element);
        }
        this.previewModel = this.originalModel.withElements(replacements);
        return this.previewModel;
    }

    public EditableMoldingModel previewModel() {
        return this.previewModel;
    }

    public boolean changed() {
        return this.value != 0.0D && !this.previewModel.equals(this.originalModel);
    }

    public MoldingCommand command() {
        List<MoldingCommand> commands = new ArrayList<>();
        for (MoldingElement element : this.previewModel.elements()) {
            if (!this.selectedIds.contains(element.id())) continue;
            MoldingElement original = this.originalModel.elements().stream()
                .filter(candidate -> candidate.id().equals(element.id()))
                .findFirst()
                .orElseThrow();
            if (!element.equals(original)) commands.add(new MoldingCommand.ReplaceElement(element));
        }
        if (commands.isEmpty()) throw new IllegalStateException("Drag did not change the model");
        return commands.size() == 1 ? commands.getFirst() : new MoldingCommand.Batch(commands);
    }

    private MoldingElement transform(MoldingElement element, double value) {
        if (value == 0.0D) return element;
        return switch (this.tool) {
            case MOVE -> MoldingElementTransforms.move(this.originalModel, element, add(MoldingVec3.ZERO, this.axis, value));
            case ROTATE -> MoldingElementTransforms.rotate(this.originalModel, element, this.rotationCenter, this.axis, value);
            case PIVOT -> withWorldPivot(this.originalModel, element, add(worldPivot(this.originalModel, element), this.axis, value));
            case SCALE -> {
                MoldingGizmoBasis basis = MoldingGizmoBasis.forElement(this.originalModel, element);
                double localValue = value * this.scaleBasis.worldUnitsPerLocalUnit(this.axis) / basis.worldUnitsPerLocalUnit(this.axis);
                yield resize(element, this.axis, this.axisDirection, localValue);
            }
            case NONE, MIRROR -> throw new IllegalStateException("Selected tool cannot be dragged");
        };
    }

    static MoldingVec3 worldPivot(EditableMoldingModel model, MoldingElement element) {
        return MoldingElementTransforms.worldPivot(model, element);
    }

    static MoldingElement withWorldPivot(EditableMoldingModel model, MoldingElement element, MoldingVec3 worldPivot) {
        return MoldingElementTransforms.pivot(model, element, worldPivot);
    }

    private static MoldingElement resize(MoldingElement element, MoldingAxis axis, double direction, double value) {
        double size = component(element.to(), axis) - component(element.from(), axis);
        boolean moveFrom = (direction < 0.0D) == (size >= 0.0D);
        return new MoldingElement(
            element.id(), element.name(), element.groupId(),
            moveFrom ? add(element.from(), axis, direction * value) : element.from(),
            moveFrom ? element.to() : add(element.to(), axis, direction * value),
            element.transform(), element.visible(), element.locked()
        );
    }

    private static MoldingVec3 center(EditableMoldingModel model, Set<UUID> selected) {
        List<MoldingVec3> vertices = model.elements().stream().filter(element -> selected.contains(element.id()))
            .flatMap(element -> MoldingModelBaker.transformedVertices(model, element).stream()).toList();
        if (vertices.isEmpty()) return MoldingVec3.ZERO;
        return new MoldingVec3(
            (vertices.stream().mapToDouble(MoldingVec3::x).min().orElse(0) + vertices.stream().mapToDouble(MoldingVec3::x).max().orElse(0)) / 2,
            (vertices.stream().mapToDouble(MoldingVec3::y).min().orElse(0) + vertices.stream().mapToDouble(MoldingVec3::y).max().orElse(0)) / 2,
            (vertices.stream().mapToDouble(MoldingVec3::z).min().orElse(0) + vertices.stream().mapToDouble(MoldingVec3::z).max().orElse(0)) / 2
        );
    }

    static MoldingElement replaceTransform(MoldingElement element, MoldingTransform transform) {
        return new MoldingElement(
            element.id(),
            element.name(),
            element.groupId(),
            element.from(),
            element.to(),
            transform,
            element.visible(),
            element.locked()
        );
    }

    static MoldingVec3 add(MoldingVec3 value, MoldingAxis axis, double amount) {
        return withComponent(value, axis, component(value, axis) + amount);
    }

    static MoldingVec3 withComponent(MoldingVec3 value, MoldingAxis axis, double replacement) {
        return switch (axis) {
            case X -> new MoldingVec3(replacement, value.y(), value.z());
            case Y -> new MoldingVec3(value.x(), replacement, value.z());
            case Z -> new MoldingVec3(value.x(), value.y(), replacement);
        };
    }

    static double component(MoldingVec3 value, MoldingAxis axis) {
        return switch (axis) {
            case X -> value.x();
            case Y -> value.y();
            case Z -> value.z();
        };
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

    private static boolean isEffectivelyLocked(EditableMoldingModel model, MoldingElement element) {
        if (element.locked()) return true;
        Map<UUID, MoldingGroup> groups = model.groupMap();
        MoldingGroup group = element.groupId().map(groups::get).orElse(null);
        while (group != null) {
            if (group.locked()) return true;
            group = group.parentId().map(groups::get).orElse(null);
        }
        return false;
    }

}
