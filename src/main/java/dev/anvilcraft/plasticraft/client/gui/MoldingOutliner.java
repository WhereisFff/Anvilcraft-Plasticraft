package dev.anvilcraft.plasticraft.client.gui;

import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class MoldingOutliner {
    private MoldingOutliner() {
    }

    public static List<Row> rows(EditableMoldingModel model, Set<UUID> collapsed, String search) {
        String query = search.strip().toLowerCase(Locale.ROOT);
        Map<UUID, MoldingGroup> groups = model.groupMap();
        Set<UUID> matching = new HashSet<>();
        Set<UUID> matchingGroups = new HashSet<>();
        if (!query.isEmpty()) {
            for (MoldingGroup group : model.groups()) {
                if (group.name().toLowerCase(Locale.ROOT).contains(query)) {
                    matchingGroups.add(group.id());
                    includeAncestors(matching, Optional.of(group.id()), groups);
                }
            }
            for (MoldingElement element : model.elements()) {
                if (element.name().toLowerCase(Locale.ROOT).contains(query)) {
                    matching.add(element.id());
                    includeAncestors(matching, element.groupId(), groups);
                }
            }
        }
        List<Row> rows = new ArrayList<>();
        append(rows, model, Optional.empty(), 0, true, false, collapsed, matching, matchingGroups, query.isEmpty(), false);
        return List.copyOf(rows);
    }

    private static void includeAncestors(Set<UUID> ids, Optional<UUID> parent, Map<UUID, MoldingGroup> groups) {
        while (parent.isPresent() && ids.add(parent.get())) {
            MoldingGroup group = groups.get(parent.get());
            parent = group == null ? Optional.empty() : group.parentId();
        }
    }

    private static void append(
        List<Row> result, EditableMoldingModel model, Optional<UUID> parent, int depth,
        boolean parentVisible, boolean parentLocked, Set<UUID> collapsed, Set<UUID> matching, Set<UUID> matchingGroups,
        boolean unfiltered, boolean matchedParent
    ) {
        for (MoldingGroup group : model.groups()) {
            if (!group.parentId().equals(parent)) continue;
            if (!unfiltered && !matchedParent && !matching.contains(group.id())) continue;
            boolean visible = parentVisible && group.visible();
            boolean locked = parentLocked || group.locked();
            result.add(new Row(group.id(), group.name(), true, depth, visible, locked));
            if (!unfiltered || !collapsed.contains(group.id())) {
                append(result, model, Optional.of(group.id()), depth + 1, visible, locked,
                    collapsed, matching, matchingGroups, unfiltered, matchedParent || matchingGroups.contains(group.id()));
            }
        }
        for (MoldingElement element : model.elements()) {
            if (!element.groupId().equals(parent)) continue;
            if (!unfiltered && !matchedParent && !matching.contains(element.id())) continue;
            result.add(new Row(element.id(), element.name(), false, depth,
                parentVisible && element.visible(), parentLocked || element.locked()));
        }
    }

    public record Row(UUID id, String name, boolean group, int depth, boolean visible, boolean locked) {
    }
}
