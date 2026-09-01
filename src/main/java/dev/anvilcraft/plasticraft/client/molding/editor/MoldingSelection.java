package dev.anvilcraft.plasticraft.client.molding.editor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record MoldingSelection(List<UUID> ids) {
    public static final MoldingSelection EMPTY = new MoldingSelection(List.of());

    public MoldingSelection {
        ids = List.copyOf(new LinkedHashSet<>(ids));
    }

    public boolean contains(UUID id) {
        return this.ids.contains(id);
    }

    public boolean isEmpty() {
        return this.ids.isEmpty();
    }

    public Optional<UUID> primary() {
        return this.ids.isEmpty() ? Optional.empty() : Optional.of(this.ids.getLast());
    }

    public MoldingSelection select(UUID id, boolean additive) {
        if (!additive) return new MoldingSelection(List.of(id));
        List<UUID> result = new ArrayList<>(this.ids);
        if (result.remove(id)) return new MoldingSelection(result);
        result.add(id);
        return new MoldingSelection(result);
    }

    public MoldingSelection selectRange(List<UUID> orderedIds, UUID anchor, UUID target, boolean additive) {
        int targetIndex = orderedIds.indexOf(target);
        if (targetIndex < 0) return this;
        List<UUID> result = additive ? new ArrayList<>(this.ids) : new ArrayList<>();
        int anchorIndex = orderedIds.indexOf(anchor);
        if (anchorIndex < 0) {
            result.remove(target);
            result.add(target);
            return new MoldingSelection(result);
        }
        int step = targetIndex >= anchorIndex ? 1 : -1;
        for (int index = anchorIndex; ; index += step) {
            UUID id = orderedIds.get(index);
            result.remove(id);
            result.add(id);
            if (index == targetIndex) return new MoldingSelection(result);
        }
    }

    public MoldingSelection retain(Set<UUID> available) {
        return new MoldingSelection(this.ids.stream().filter(available::contains).toList());
    }
}
