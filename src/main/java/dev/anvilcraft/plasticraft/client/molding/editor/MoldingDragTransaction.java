package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

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
        this.originalModel = model;
        this.previewModel = model;
        this.selectedIds = selectedElementIds(model, selection);
        this.tool = tool;
        this.axis = axis;
        this.axisDirection = Math.copySign(1.0D, axisDirection);
        if (tool == MoldingTool.NONE || tool == MoldingTool.MIRROR) {
            throw new IllegalArgumentException("Selected tool cannot start a drag transaction");
        }
    }

    public EditableMoldingModel originalModel() {
        return this.originalModel;
    }

    public EditableMoldingModel preview(double rawValue) {
        this.value = Math.rint(rawValue);
        List<MoldingElement> replacements = new ArrayList<>(this.originalModel.elements().size());
        for (MoldingElement element : this.originalModel.elements()) {
            replacements.add(this.selectedIds.contains(element.id()) && !isEffectivelyLocked(this.originalModel, element)
                ? transform(element, this.tool, this.axis, this.axisDirection, this.value)
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

    private static MoldingElement transform(
        MoldingElement element,
        MoldingTool tool,
        MoldingAxis axis,
        double axisDirection,
        double value
    ) {
        MoldingTransform transform = element.transform();
        return switch (tool) {
            case MOVE -> replaceTransform(element, new MoldingTransform(
                add(transform.translation(), axis, value),
                transform.rotation(),
                transform.scale(),
                transform.pivot()
            ));
            case ROTATE -> replaceTransform(element, new MoldingTransform(
                transform.translation(),
                add(transform.rotation(), axis, value),
                transform.scale(),
                transform.pivot()
            ));
            case PIVOT -> replaceTransform(element, new MoldingTransform(
                transform.translation(),
                transform.rotation(),
                transform.scale(),
                add(transform.pivot(), axis, value)
            ));
            case SCALE -> resize(element, axis, axisDirection, value);
            case NONE, MIRROR -> throw new IllegalStateException("Selected tool cannot be dragged");
        };
    }

    private static MoldingElement resize(
        MoldingElement element,
        MoldingAxis axis,
        double axisDirection,
        double value
    ) {
        double current = component(element.to(), axis) - component(element.from(), axis);
        double size = current + value;
        if (size == 0.0D && hasZeroSizeOnOtherAxis(element, axis)) {
            size = Math.copySign(1.0D, current);
        }
        double applied = size - current;
        MoldingVec3 from = axisDirection < 0.0D
            ? withComponent(element.from(), axis, component(element.from(), axis) - applied)
            : element.from();
        MoldingVec3 to = axisDirection < 0.0D
            ? element.to()
            : withComponent(element.to(), axis, component(element.to(), axis) + applied);
        return new MoldingElement(
            element.id(),
            element.name(),
            element.groupId(),
            from,
            to,
            element.transform(),
            element.visible(),
            element.locked()
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

    private static boolean hasZeroSizeOnOtherAxis(MoldingElement element, MoldingAxis resizedAxis) {
        for (MoldingAxis axis : MoldingAxis.values()) {
            if (axis != resizedAxis && component(element.from(), axis) == component(element.to(), axis)) return true;
        }
        return false;
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
