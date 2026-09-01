package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingGroup;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record MoldingGizmoBasis(MoldingVec3 x, MoldingVec3 y, MoldingVec3 z) {
    private static final double MIN_LENGTH_SQUARED = 1.0E-18D;
    public static final MoldingGizmoBasis WORLD = new MoldingGizmoBasis(
        new MoldingVec3(1.0D, 0.0D, 0.0D),
        new MoldingVec3(0.0D, 1.0D, 0.0D),
        new MoldingVec3(0.0D, 0.0D, 1.0D)
    );

    public MoldingGizmoBasis {
        if (x.lengthSquared() < MIN_LENGTH_SQUARED
            || y.lengthSquared() < MIN_LENGTH_SQUARED
            || z.lengthSquared() < MIN_LENGTH_SQUARED) {
            throw new IllegalArgumentException("Molding gizmo basis axis is too small");
        }
    }

    public Vector3d direction(MoldingAxis axis) {
        MoldingVec3 value = vector(axis);
        return new Vector3d(value.x(), value.y(), value.z()).normalize();
    }

    public double worldUnitsPerLocalUnit(MoldingAxis axis) {
        return Math.sqrt(vector(axis).lengthSquared());
    }

    public static MoldingGizmoBasis forSelection(
        EditableMoldingModel model,
        MoldingSelection selection,
        MoldingTool tool
    ) {
        if (tool != MoldingTool.SCALE) return WORLD;
        List<MoldingElement> selected = selectedElements(model, selection);
        if (selected.size() != 1) return WORLD;
        MoldingElement element = selected.getFirst();
        MoldingVec3 pivot = element.transform().pivot();
        MoldingVec3 worldPivot = MoldingModelBaker.transformedPoint(model, element, pivot);
        MoldingVec3 x = transformedAxis(model, element, pivot, worldPivot, MoldingAxis.X);
        MoldingVec3 y = transformedAxis(model, element, pivot, worldPivot, MoldingAxis.Y);
        MoldingVec3 z = transformedAxis(model, element, pivot, worldPivot, MoldingAxis.Z);
        if (x.lengthSquared() < MIN_LENGTH_SQUARED
            || y.lengthSquared() < MIN_LENGTH_SQUARED
            || z.lengthSquared() < MIN_LENGTH_SQUARED) {
            return WORLD;
        }
        return new MoldingGizmoBasis(x, y, z);
    }

    private MoldingVec3 vector(MoldingAxis axis) {
        return switch (axis) {
            case X -> this.x;
            case Y -> this.y;
            case Z -> this.z;
        };
    }

    private static MoldingVec3 transformedAxis(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingVec3 pivot,
        MoldingVec3 worldPivot,
        MoldingAxis axis
    ) {
        MoldingVec3 endpoint = switch (axis) {
            case X -> pivot.add(new MoldingVec3(1.0D, 0.0D, 0.0D));
            case Y -> pivot.add(new MoldingVec3(0.0D, 1.0D, 0.0D));
            case Z -> pivot.add(new MoldingVec3(0.0D, 0.0D, 1.0D));
        };
        return MoldingModelBaker.transformedPoint(model, element, endpoint).subtract(worldPivot);
    }

    private static List<MoldingElement> selectedElements(
        EditableMoldingModel model,
        MoldingSelection selection
    ) {
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
        List<MoldingElement> result = new ArrayList<>();
        for (MoldingElement element : model.elements()) {
            if (selection.contains(element.id()) || element.groupId().filter(selectedGroups::contains).isPresent()) {
                result.add(element);
            }
        }
        return List.copyOf(result);
    }
}
