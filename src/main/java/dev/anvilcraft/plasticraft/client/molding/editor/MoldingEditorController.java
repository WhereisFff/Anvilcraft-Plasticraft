package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingCommand;
import dev.anvilcraft.plasticraft.molding.model.MoldingCoordinateSystem;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 客户端编辑状态只产生语义命令，服务端快照始终可以覆盖并重建它。 */
public final class MoldingEditorController {
    private final CommandSink commandSink;
    private EditableMoldingModel model;
    private long authoritativeRevision;
    private boolean writable;
    private boolean pending;
    private boolean invalidPreview;
    private MoldingSelection selection = MoldingSelection.EMPTY;
    private MoldingTool tool = MoldingTool.MOVE;
    private List<MoldingElement> clipboardElements = List.of();
    private List<MoldingGroup> clipboardGroups = List.of();
    @Nullable
    private MoldingDragTransaction dragTransaction;

    public MoldingEditorController(
        EditableMoldingModel model,
        long revision,
        boolean writable,
        CommandSink commandSink
    ) {
        this.model = model;
        this.authoritativeRevision = revision;
        this.writable = writable;
        this.commandSink = commandSink;
    }

    public EditableMoldingModel model() {
        return this.model;
    }

    public long authoritativeRevision() {
        return this.authoritativeRevision;
    }

    public boolean writable() {
        return this.writable;
    }

    public boolean pending() {
        return this.pending;
    }

    public boolean invalidPreview() {
        return this.invalidPreview;
    }

    public MoldingSelection selection() {
        return this.selection;
    }

    public MoldingTool tool() {
        return this.tool;
    }

    public void setTool(MoldingTool tool) {
        this.cancelDrag();
        this.tool = tool;
    }

    public void syncAuthoritative(EditableMoldingModel authoritativeModel, long revision, boolean canWrite) {
        this.model = authoritativeModel;
        this.authoritativeRevision = revision;
        this.writable = canWrite;
        this.pending = false;
        this.invalidPreview = false;
        this.dragTransaction = null;
        Set<UUID> available = new HashSet<>();
        authoritativeModel.elements().forEach(element -> available.add(element.id()));
        authoritativeModel.groups().forEach(group -> available.add(group.id()));
        this.selection = this.selection.retain(available);
    }

    public void select(UUID id, boolean additive) {
        this.selection = this.selection.select(id, additive);
    }

    public void clearSelection() {
        this.selection = MoldingSelection.EMPTY;
    }

    public Optional<MoldingElement> primaryElement() {
        Optional<UUID> primary = this.selection.primary();
        if (primary.isEmpty()) return Optional.empty();
        return this.model.elements().stream().filter(element -> element.id().equals(primary.get())).findFirst();
    }

    public Optional<String> primaryName() {
        Optional<UUID> primary = this.selection.primary();
        if (primary.isEmpty()) return Optional.empty();
        Optional<MoldingElement> element = this.model.elements().stream()
            .filter(candidate -> candidate.id().equals(primary.get()))
            .findFirst();
        if (element.isPresent()) return element.map(MoldingElement::name);
        return this.model.groups().stream()
            .filter(group -> group.id().equals(primary.get()))
            .map(MoldingGroup::name)
            .findFirst();
    }

    public Vector3d selectionCenter() {
        List<MoldingVec3> vertices = selectedElements().stream()
            .flatMap(element -> MoldingModelBaker.transformedVertices(this.model, element).stream())
            .toList();
        if (vertices.isEmpty()) return new Vector3d(24.0D, 24.0D, 24.0D);
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (MoldingVec3 vertex : vertices) {
            minX = Math.min(minX, vertex.x());
            minY = Math.min(minY, vertex.y());
            minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x());
            maxY = Math.max(maxY, vertex.y());
            maxZ = Math.max(maxZ, vertex.z());
        }
        return new Vector3d((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
    }

    public Vector3d gizmoOrigin() {
        if (this.tool != MoldingTool.ROTATE && this.tool != MoldingTool.PIVOT) return selectionCenter();
        Optional<MoldingElement> primary = primaryElement();
        if (primary.isEmpty()) return selectionCenter();
        MoldingVec3 pivot = MoldingModelBaker.transformedPoint(
            this.model,
            primary.get(),
            primary.get().transform().pivot()
        );
        return new Vector3d(pivot.x(), pivot.y(), pivot.z());
    }

    public double selectionExtent() {
        List<MoldingVec3> vertices = selectedElements().stream()
            .flatMap(element -> MoldingModelBaker.transformedVertices(this.model, element).stream())
            .toList();
        Vector3d center = this.selectionCenter();
        return vertices.stream().mapToDouble(vertex -> center.distance(vertex.x(), vertex.y(), vertex.z()))
            .max().orElse(24.0D);
    }

    public boolean createCube() {
        MoldingVec3 from = MoldingCoordinateSystem.ORIGIN;
        return createCube(from, from.add(new MoldingVec3(2.0D, 2.0D, 2.0D)));
    }

    private boolean createCube(MoldingVec3 from, MoldingVec3 to) {
        MoldingElement cube = new MoldingElement(
            UUID.randomUUID(),
            nextName("Cube"),
            Optional.empty(),
            from,
            to,
            new MoldingTransform(MoldingVec3.ZERO, MoldingVec3.ZERO, MoldingVec3.ONE, from),
            true,
            false
        );
        if (!this.submit(new MoldingCommand.AddElement(cube))) return false;
        this.selection = new MoldingSelection(List.of(cube.id()));
        return true;
    }

    public boolean deleteSelection() {
        List<UUID> elementIds = selectedElements().stream().map(MoldingElement::id).toList();
        Set<UUID> groupIds = selectedGroupClosure();
        List<MoldingCommand> commands = new ArrayList<>();
        if (!elementIds.isEmpty()) commands.add(new MoldingCommand.RemoveElements(elementIds));
        this.model.groups().stream()
            .filter(group -> groupIds.contains(group.id()))
            .sorted((first, second) -> Integer.compare(groupDepth(second), groupDepth(first)))
            .map(group -> new MoldingCommand.RemoveGroup(group.id()))
            .forEach(commands::add);
        if (commands.isEmpty() || commands.size() > MoldingCommand.MAX_BATCH_SIZE) return false;
        boolean submitted = this.submit(
            commands.size() == 1 ? commands.getFirst() : new MoldingCommand.Batch(commands)
        );
        if (submitted) this.selection = MoldingSelection.EMPTY;
        return submitted;
    }

    public void copySelection() {
        Set<UUID> contentGroupIds = selectedGroupClosure();
        Set<UUID> requiredGroupIds = new HashSet<>(contentGroupIds);
        Map<UUID, MoldingGroup> groups = this.model.groupMap();
        for (MoldingElement element : this.model.elements()) {
            if (this.selection.contains(element.id())
                || element.groupId().filter(contentGroupIds::contains).isPresent()) {
                addGroupAncestors(requiredGroupIds, element.groupId(), groups);
            }
        }
        for (UUID groupId : contentGroupIds) {
            MoldingGroup group = groups.get(groupId);
            if (group != null) addGroupAncestors(requiredGroupIds, group.parentId(), groups);
        }
        this.clipboardGroups = this.model.groups().stream()
            .filter(group -> requiredGroupIds.contains(group.id()))
            .sorted((first, second) -> Integer.compare(groupDepth(first), groupDepth(second)))
            .toList();
        this.clipboardElements = this.model.elements().stream()
            .filter(element -> this.selection.contains(element.id())
                || element.groupId().filter(contentGroupIds::contains).isPresent())
            .toList();
    }

    public boolean cutSelection() {
        this.copySelection();
        return this.deleteSelection();
    }

    public boolean paste() {
        if (this.clipboardElements.isEmpty() && this.clipboardGroups.isEmpty()) return false;
        Map<UUID, UUID> replacements = new HashMap<>();
        this.clipboardGroups.forEach(group -> replacements.put(group.id(), UUID.randomUUID()));
        this.clipboardElements.forEach(element -> replacements.put(element.id(), UUID.randomUUID()));
        List<MoldingCommand> commands = new ArrayList<>();
        for (MoldingGroup group : this.clipboardGroups) {
            commands.add(new MoldingCommand.AddGroup(new MoldingGroup(
                replacements.get(group.id()),
                group.name(),
                group.parentId().map(replacements::get).map(Optional::of).orElse(Optional.empty()),
                group.transform(),
                group.visible(),
                false
            )));
        }
        List<UUID> pastedIds = new ArrayList<>();
        for (MoldingElement element : this.clipboardElements) {
            UUID id = replacements.get(element.id());
            pastedIds.add(id);
            commands.add(new MoldingCommand.AddElement(new MoldingElement(
                id,
                element.name(),
                element.groupId().map(replacements::get).map(Optional::of).orElse(Optional.empty()),
                element.from(),
                element.to(),
                element.transform(),
                element.visible(),
                false
            )));
        }
        if (commands.size() > MoldingCommand.MAX_BATCH_SIZE) return false;
        MoldingCommand command = commands.size() == 1 ? commands.getFirst() : new MoldingCommand.Batch(commands);
        if (!this.submit(command)) return false;
        this.selection = new MoldingSelection(pastedIds);
        return true;
    }

    public boolean groupSelection() {
        Map<UUID, MoldingGroup> groups = this.model.groupMap();
        Set<UUID> selectedGroups = this.model.groups().stream()
            .filter(group -> this.selection.contains(group.id()))
            .map(MoldingGroup::id)
            .collect(HashSet::new, Set::add, Set::addAll);
        List<MoldingGroup> directGroups = this.model.groups().stream()
            .filter(group -> selectedGroups.contains(group.id()))
            .filter(group -> !hasSelectedAncestor(group.parentId(), selectedGroups, groups))
            .toList();
        List<MoldingElement> directElements = this.model.elements().stream()
            .filter(element -> this.selection.contains(element.id()))
            .filter(element -> !hasSelectedAncestor(element.groupId(), selectedGroups, groups))
            .toList();
        if (directGroups.isEmpty() && directElements.isEmpty()) return false;
        Optional<UUID> parent = directGroups.isEmpty()
            ? directElements.getFirst().groupId()
            : directGroups.getFirst().parentId();
        boolean sameParent = directGroups.stream().allMatch(group -> group.parentId().equals(parent))
            && directElements.stream().allMatch(element -> element.groupId().equals(parent));
        if (!sameParent || parent.map(groups::get).map(MoldingGroup::locked).orElse(false)) return false;
        if (directGroups.stream().anyMatch(group -> isEffectivelyLocked(group, groups))
            || directElements.stream().anyMatch(element -> isEffectivelyLocked(element, groups))) {
            return false;
        }
        int commandCount = directGroups.size() + directElements.size() + 1;
        if (commandCount > MoldingCommand.MAX_BATCH_SIZE) return false;
        UUID groupId = UUID.randomUUID();
        List<MoldingCommand> commands = new ArrayList<>();
        commands.add(new MoldingCommand.AddGroup(new MoldingGroup(
            groupId,
            nextName("Group"),
            parent,
            MoldingTransform.IDENTITY,
            true,
            false
        )));
        for (MoldingGroup group : directGroups) {
            commands.add(new MoldingCommand.ReplaceGroup(new MoldingGroup(
                group.id(), group.name(), Optional.of(groupId), group.transform(), group.visible(), group.locked()
            )));
        }
        for (MoldingElement element : directElements) {
            commands.add(new MoldingCommand.ReplaceElement(element.withGroup(Optional.of(groupId))));
        }
        if (!this.submit(new MoldingCommand.Batch(commands))) return false;
        this.selection = new MoldingSelection(List.of(groupId));
        return true;
    }

    public boolean toggleHidden() {
        return replaceSelectedObjects(
            element -> new MoldingElement(
                element.id(), element.name(), element.groupId(), element.from(), element.to(),
                element.transform(), !element.visible(), element.locked()
            ),
            group -> new MoldingGroup(
                group.id(), group.name(), group.parentId(), group.transform(), !group.visible(), group.locked()
            ),
            false
        );
    }

    public boolean toggleLocked() {
        return replaceSelectedObjects(
            element -> new MoldingElement(
                element.id(), element.name(), element.groupId(), element.from(), element.to(),
                element.transform(), element.visible(), !element.locked()
            ),
            group -> new MoldingGroup(
                group.id(), group.name(), group.parentId(), group.transform(), group.visible(), !group.locked()
            ),
            true
        );
    }

    public boolean renamePrimary(String name) {
        if (name.isBlank() || name.length() > MoldingElement.MAX_NAME_LENGTH) return false;
        Optional<UUID> primaryId = this.selection.primary();
        if (primaryId.isEmpty()) return false;
        Optional<MoldingElement> primary = this.model.elements().stream()
            .filter(element -> element.id().equals(primaryId.get()))
            .findFirst();
        if (primary.isPresent()) {
            MoldingElement element = primary.get();
            if (isEffectivelyLocked(element, this.model.groupMap())) return false;
            return this.submit(new MoldingCommand.ReplaceElement(new MoldingElement(
                element.id(), name, element.groupId(), element.from(), element.to(),
                element.transform(), element.visible(), element.locked()
            )));
        }
        Optional<MoldingGroup> primaryGroup = this.model.groups().stream()
            .filter(group -> group.id().equals(primaryId.get()))
            .findFirst();
        if (primaryGroup.isEmpty() || isEffectivelyLocked(primaryGroup.get(), this.model.groupMap())) return false;
        MoldingGroup group = primaryGroup.get();
        return this.submit(new MoldingCommand.ReplaceGroup(new MoldingGroup(
            group.id(), name, group.parentId(), group.transform(), group.visible(), group.locked()
        )));
    }

    public boolean centerPivot() {
        return replaceSelected(element -> {
            MoldingVec3 pivot = element.from().add(element.to()).scale(0.5D);
            MoldingTransform old = element.transform();
            return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                old.translation(), old.rotation(), old.scale(), pivot
            ));
        }, false);
    }

    public boolean align(MoldingAxis axis) {
        Optional<MoldingElement> primary = this.primaryElement();
        if (primary.isEmpty()) return false;
        double target = position(primary.get(), axis);
        return replaceSelected(element -> {
            MoldingTransform old = element.transform();
            double delta = target - position(element, axis);
            return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                MoldingDragTransaction.add(old.translation(), axis, delta),
                old.rotation(), old.scale(), old.pivot()
            ));
        }, false);
    }

    public boolean mirror(MoldingAxis axis) {
        return replaceSelected(element -> {
            MoldingTransform old = element.transform();
            MoldingVec3 scale = MoldingDragTransaction.withComponent(
                old.scale(), axis, -MoldingDragTransaction.component(old.scale(), axis)
            );
            return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                old.translation(), old.rotation(), scale, old.pivot()
            ));
        }, false);
    }

    public boolean setNumeric(MoldingNumericProperty property, MoldingAxis axis, double value) {
        if (!Double.isFinite(value)) return false;
        try {
            return replaceSelected(element -> setNumeric(element, property, axis, value), false);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Optional<Double> numericValue(MoldingNumericProperty property, MoldingAxis axis) {
        List<MoldingElement> selected = selectedElements();
        if (selected.isEmpty()) return Optional.empty();
        double value = numericValue(selected.getFirst(), property, axis);
        return selected.stream().allMatch(element -> Math.abs(numericValue(element, property, axis) - value) < 1.0E-8D)
            ? Optional.of(value)
            : Optional.empty();
    }

    public boolean beginDrag(MoldingAxis axis) {
        return beginDrag(axis, 1.0D);
    }

    public boolean beginDrag(MoldingAxis axis, double axisDirection) {
        if (!canSubmit()
            || selectedElements().isEmpty()
            || this.tool == MoldingTool.NONE
            || this.tool == MoldingTool.MIRROR) {
            return false;
        }
        this.dragTransaction = new MoldingDragTransaction(
            this.model,
            this.selection,
            this.tool,
            axis,
            axisDirection
        );
        return true;
    }

    public void updateDrag(double amount) {
        if (this.dragTransaction == null) return;
        this.model = this.dragTransaction.preview(amount);
        try {
            MoldingModelBaker.bake(this.model);
            this.invalidPreview = false;
        } catch (IllegalArgumentException exception) {
            this.invalidPreview = true;
        }
    }

    public boolean finishDrag() {
        if (this.dragTransaction == null) return false;
        MoldingDragTransaction transaction = this.dragTransaction;
        this.dragTransaction = null;
        this.model = transaction.originalModel();
        if (!transaction.changed() || this.invalidPreview) {
            this.invalidPreview = false;
            return false;
        }
        this.invalidPreview = false;
        return this.submit(transaction.command());
    }

    public void cancelDrag() {
        if (this.dragTransaction != null) this.model = this.dragTransaction.originalModel();
        this.dragTransaction = null;
        this.invalidPreview = false;
    }

    public boolean undo() {
        return submitHistory(new MoldingCommand.Undo());
    }

    public boolean redo() {
        return submitHistory(new MoldingCommand.Redo());
    }

    private boolean submitHistory(MoldingCommand command) {
        if (!canSubmit()) return false;
        this.pending = true;
        this.commandSink.send(this.authoritativeRevision, command);
        return true;
    }

    private boolean replaceSelected(ElementReplacement replacement, boolean includeLocked) {
        List<MoldingCommand> commands = new ArrayList<>();
        Map<UUID, MoldingGroup> groups = this.model.groupMap();
        for (MoldingElement element : selectedElements()) {
            if (isEffectivelyLocked(element, groups) && !includeLocked) continue;
            MoldingElement replaced = replacement.replace(element);
            if (!replaced.equals(element)) commands.add(new MoldingCommand.ReplaceElement(replaced));
        }
        if (commands.isEmpty() || commands.size() > MoldingCommand.MAX_BATCH_SIZE) return false;
        return this.submit(commands.size() == 1 ? commands.getFirst() : new MoldingCommand.Batch(commands));
    }

    private boolean replaceSelectedObjects(
        ElementReplacement elementReplacement,
        GroupReplacement groupReplacement,
        boolean includeLocked
    ) {
        Map<UUID, MoldingGroup> groups = this.model.groupMap();
        List<MoldingCommand> commands = new ArrayList<>();
        for (MoldingGroup group : this.model.groups()) {
            if (!this.selection.contains(group.id())) continue;
            if (hasLockedAncestor(group.parentId(), groups)) continue;
            if (group.locked() && !includeLocked) continue;
            MoldingGroup replaced = groupReplacement.replace(group);
            if (!replaced.equals(group)) commands.add(new MoldingCommand.ReplaceGroup(replaced));
        }
        for (MoldingElement element : this.model.elements()) {
            if (!this.selection.contains(element.id())) continue;
            if (hasLockedAncestor(element.groupId(), groups)) continue;
            if (element.locked() && !includeLocked) continue;
            MoldingElement replaced = elementReplacement.replace(element);
            if (!replaced.equals(element)) commands.add(new MoldingCommand.ReplaceElement(replaced));
        }
        if (commands.isEmpty() || commands.size() > MoldingCommand.MAX_BATCH_SIZE) return false;
        return this.submit(commands.size() == 1 ? commands.getFirst() : new MoldingCommand.Batch(commands));
    }

    private boolean submit(MoldingCommand command) {
        if (!canSubmit()) return false;
        try {
            EditableMoldingModel next = command.apply(this.model);
            MoldingModelBaker.bake(next);
            this.model = next;
            this.pending = true;
            this.commandSink.send(this.authoritativeRevision, command);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean canSubmit() {
        return this.writable && !this.pending && this.dragTransaction == null;
    }

    private List<MoldingElement> selectedElements() {
        Set<UUID> selected = Set.copyOf(this.selection.ids());
        Set<UUID> groups = selectedGroupClosure();
        return this.model.elements().stream()
            .filter(element -> selected.contains(element.id()) || element.groupId().filter(groups::contains).isPresent())
            .toList();
    }

    private Set<UUID> selectedGroupClosure() {
        Set<UUID> result = new HashSet<>();
        for (MoldingGroup group : this.model.groups()) {
            if (this.selection.contains(group.id())) result.add(group.id());
        }
        boolean changed;
        do {
            changed = false;
            for (MoldingGroup group : this.model.groups()) {
                if (group.parentId().filter(result::contains).isPresent()) changed |= result.add(group.id());
            }
        } while (changed);
        return result;
    }

    private static void addGroupAncestors(
        Set<UUID> output,
        Optional<UUID> start,
        Map<UUID, MoldingGroup> groups
    ) {
        MoldingGroup current = start.map(groups::get).orElse(null);
        while (current != null) {
            output.add(current.id());
            current = current.parentId().map(groups::get).orElse(null);
        }
    }

    private static boolean hasSelectedAncestor(
        Optional<UUID> start,
        Set<UUID> selectedGroups,
        Map<UUID, MoldingGroup> groups
    ) {
        MoldingGroup current = start.map(groups::get).orElse(null);
        while (current != null) {
            if (selectedGroups.contains(current.id())) return true;
            current = current.parentId().map(groups::get).orElse(null);
        }
        return false;
    }

    private static boolean isEffectivelyLocked(MoldingElement element, Map<UUID, MoldingGroup> groups) {
        return element.locked() || hasLockedAncestor(element.groupId(), groups);
    }

    private static boolean isEffectivelyLocked(MoldingGroup group, Map<UUID, MoldingGroup> groups) {
        return group.locked() || hasLockedAncestor(group.parentId(), groups);
    }

    private static boolean hasLockedAncestor(Optional<UUID> start, Map<UUID, MoldingGroup> groups) {
        MoldingGroup current = start.map(groups::get).orElse(null);
        while (current != null) {
            if (current.locked()) return true;
            current = current.parentId().map(groups::get).orElse(null);
        }
        return false;
    }

    private int groupDepth(MoldingGroup group) {
        Map<UUID, MoldingGroup> groups = this.model.groupMap();
        int depth = 0;
        MoldingGroup current = group;
        while (current.parentId().isPresent()) {
            current = groups.get(current.parentId().orElseThrow());
            depth++;
        }
        return depth;
    }

    private String nextName(String prefix) {
        Set<String> names = new HashSet<>();
        this.model.elements().forEach(element -> names.add(element.name()));
        this.model.groups().forEach(group -> names.add(group.name()));
        int suffix = 1;
        while (names.contains(prefix + " " + suffix)) suffix++;
        return prefix + " " + suffix;
    }

    private static MoldingElement setNumeric(
        MoldingElement element,
        MoldingNumericProperty property,
        MoldingAxis axis,
        double value
    ) {
        MoldingTransform old = element.transform();
        return switch (property) {
            case POSITION -> {
                double rounded = Math.rint(value);
                double delta = rounded - position(element, axis);
                yield MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                    MoldingDragTransaction.add(old.translation(), axis, delta),
                    old.rotation(),
                    old.scale(),
                    MoldingDragTransaction.add(old.pivot(), axis, -delta)
                ));
            }
            case PIVOT -> MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                old.translation(), old.rotation(), old.scale(),
                MoldingDragTransaction.withComponent(
                    old.pivot(),
                    axis,
                    MoldingDragTransaction.component(MoldingCoordinateSystem.ORIGIN, axis)
                        + Math.rint(value)
                        - MoldingDragTransaction.component(old.translation(), axis)
                )
            ));
            case ROTATION -> MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
                old.translation(), MoldingDragTransaction.withComponent(old.rotation(), axis, value),
                old.scale(), old.pivot()
            ));
            case SIZE -> resizeTo(element, axis, value);
        };
    }

    private static MoldingElement resizeTo(MoldingElement element, MoldingAxis axis, double rawValue) {
        double size = Math.rint(rawValue);
        MoldingVec3 to = MoldingDragTransaction.withComponent(
            element.to(),
            axis,
            MoldingDragTransaction.component(element.from(), axis) + size
        );
        return new MoldingElement(
            element.id(), element.name(), element.groupId(), element.from(), to,
            element.transform(), element.visible(), element.locked()
        );
    }

    private static double numericValue(
        MoldingElement element,
        MoldingNumericProperty property,
        MoldingAxis axis
    ) {
        return switch (property) {
            case POSITION -> position(element, axis);
            case SIZE -> MoldingDragTransaction.component(element.to(), axis)
                - MoldingDragTransaction.component(element.from(), axis);
            case PIVOT -> MoldingDragTransaction.component(
                element.transform().pivot()
                    .add(element.transform().translation())
                    .subtract(MoldingCoordinateSystem.ORIGIN),
                axis
            );
            case ROTATION -> MoldingDragTransaction.component(element.transform().rotation(), axis);
        };
    }

    private static double position(MoldingElement element, MoldingAxis axis) {
        return MoldingDragTransaction.component(element.from(), axis)
            + MoldingDragTransaction.component(element.transform().translation(), axis)
            - MoldingDragTransaction.component(MoldingCoordinateSystem.ORIGIN, axis);
    }

    @FunctionalInterface
    public interface CommandSink {
        void send(long baseRevision, MoldingCommand command);
    }

    @FunctionalInterface
    private interface ElementReplacement {
        MoldingElement replace(MoldingElement element);
    }

    @FunctionalInterface
    private interface GroupReplacement {
        MoldingGroup replace(MoldingGroup group);
    }
}
