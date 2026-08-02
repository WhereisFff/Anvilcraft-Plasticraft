package dev.anvilcraft.plasticraft.molding.model;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 客户端只能提交这些可验证的语义编辑，不能直接覆盖方块实体数据。 */
public sealed interface MoldingCommand permits MoldingCommand.AddElement,
    MoldingCommand.ReplaceElement,
    MoldingCommand.RemoveElements,
    MoldingCommand.AddGroup,
    MoldingCommand.ReplaceGroup,
    MoldingCommand.RemoveGroup,
    MoldingCommand.RenameModel,
    MoldingCommand.Batch,
    MoldingCommand.Undo,
    MoldingCommand.Redo {
    int SCHEMA_VERSION = 1;
    int MAX_BATCH_SIZE = EditableMoldingModel.MAX_ELEMENTS + EditableMoldingModel.MAX_GROUPS;

    EditableMoldingModel apply(EditableMoldingModel model);

    record AddElement(MoldingElement element) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            if (hasLockedGroup(model, this.element.groupId())) {
                throw new IllegalArgumentException("Element parent group is locked");
            }
            List<MoldingElement> elements = model.mutableElementCopy();
            elements.add(this.element);
            return model.withElements(elements);
        }
    }

    record ReplaceElement(MoldingElement element) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            List<MoldingElement> elements = model.mutableElementCopy();
            for (int index = 0; index < elements.size(); index++) {
                if (!elements.get(index).id().equals(this.element.id())) continue;
                MoldingElement previous = elements.get(index);
                if (hasLockedGroup(model, previous.groupId()) || hasLockedGroup(model, this.element.groupId())) {
                    throw new IllegalArgumentException("Element parent group is locked");
                }
                if (previous.locked() && !isUnlockOnly(previous, this.element)) {
                    throw new IllegalArgumentException("Element is locked");
                }
                elements.set(index, this.element);
                return model.withElements(elements);
            }
            throw new IllegalArgumentException("Unknown molding element");
        }
    }

    private static boolean isUnlockOnly(MoldingElement before, MoldingElement after) {
        return before.id().equals(after.id())
            && before.name().equals(after.name())
            && before.groupId().equals(after.groupId())
            && before.from().equals(after.from())
            && before.to().equals(after.to())
            && before.transform().equals(after.transform())
            && before.visible() == after.visible()
            && !after.locked();
    }

    record RemoveElements(List<UUID> ids) implements MoldingCommand {
        public RemoveElements {
            ids = List.copyOf(ids);
            if (ids.isEmpty() || ids.size() > EditableMoldingModel.MAX_ELEMENTS) {
                throw new IllegalArgumentException("Invalid molding element removal count");
            }
        }

        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            Set<UUID> removals = new HashSet<>(this.ids);
            boolean locked = model.elements().stream()
                .anyMatch(element -> removals.contains(element.id())
                    && (element.locked() || hasLockedGroup(model, element.groupId())));
            if (locked) throw new IllegalArgumentException("Element is locked");
            List<MoldingElement> elements = model.elements().stream()
                .filter(element -> !removals.contains(element.id()))
                .toList();
            if (elements.size() == model.elements().size()) {
                throw new IllegalArgumentException("No molding elements were removed");
            }
            return model.withElements(elements);
        }
    }

    record AddGroup(MoldingGroup group) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            if (hasLockedGroup(model, this.group.parentId())) {
                throw new IllegalArgumentException("Parent group is locked");
            }
            List<MoldingGroup> groups = model.mutableGroupCopy();
            groups.add(this.group);
            return model.withGroups(groups);
        }
    }

    record ReplaceGroup(MoldingGroup group) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            List<MoldingGroup> groups = model.mutableGroupCopy();
            for (int index = 0; index < groups.size(); index++) {
                if (!groups.get(index).id().equals(this.group.id())) continue;
                MoldingGroup previous = groups.get(index);
                if (hasLockedGroup(model, previous.parentId()) || hasLockedGroup(model, this.group.parentId())) {
                    throw new IllegalArgumentException("Parent group is locked");
                }
                if (previous.locked() && !isUnlockOnly(previous, this.group)) {
                    throw new IllegalArgumentException("Group is locked");
                }
                groups.set(index, this.group);
                return model.withGroups(groups);
            }
            throw new IllegalArgumentException("Unknown molding group");
        }
    }

    private static boolean isUnlockOnly(MoldingGroup before, MoldingGroup after) {
        return before.id().equals(after.id())
            && before.name().equals(after.name())
            && before.parentId().equals(after.parentId())
            && before.transform().equals(after.transform())
            && before.visible() == after.visible()
            && !after.locked();
    }

    record RemoveGroup(UUID id) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            MoldingGroup removed = model.groups().stream()
                .filter(group -> group.id().equals(this.id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown molding group"));
            if (removed.locked() || hasLockedGroup(model, removed.parentId())) {
                throw new IllegalArgumentException("Group is locked");
            }
            List<MoldingGroup> groups = model.groups().stream()
                .filter(group -> !group.id().equals(this.id))
                .map(group -> group.parentId().filter(this.id::equals).isPresent()
                    ? new MoldingGroup(
                        group.id(),
                        group.name(),
                        removed.parentId(),
                        group.transform(),
                        group.visible(),
                        group.locked()
                    )
                    : group)
                .toList();
            List<MoldingElement> elements = model.elements().stream()
                .map(element -> element.groupId().filter(this.id::equals).isPresent()
                    ? element.withGroup(removed.parentId())
                    : element)
                .toList();
            return new EditableMoldingModel(
                model.formatVersion(),
                model.name(),
                model.requestedType(),
                elements,
                groups
            );
        }
    }

    record RenameModel(String name) implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            return model.withName(this.name);
        }
    }

    record Batch(List<MoldingCommand> commands) implements MoldingCommand {
        public Batch {
            commands = List.copyOf(commands);
            if (commands.isEmpty() || commands.size() > MAX_BATCH_SIZE) {
                throw new IllegalArgumentException("Invalid molding command batch size");
            }
            if (commands.stream().anyMatch(command -> command instanceof Undo
                || command instanceof Redo
                || command instanceof Batch)) {
                throw new IllegalArgumentException("Nested or history commands are not allowed in a batch");
            }
        }

        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            EditableMoldingModel result = model;
            for (MoldingCommand command : this.commands) result = command.apply(result);
            return result;
        }
    }

    record Undo() implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            throw new UnsupportedOperationException("Undo requires session history");
        }
    }

    record Redo() implements MoldingCommand {
        @Override
        public EditableMoldingModel apply(EditableMoldingModel model) {
            throw new UnsupportedOperationException("Redo requires session history");
        }
    }

    private static boolean hasLockedGroup(EditableMoldingModel model, Optional<UUID> start) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        MoldingGroup current = start.map(groups::get).orElse(null);
        while (current != null) {
            if (current.locked()) return true;
            current = current.parentId().map(groups::get).orElse(null);
        }
        return false;
    }
}
