package dev.anvilcraft.plasticraft.molding.model;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** 模型预览、成型舱限制与制品提示共用的精确外接范围。 */
public record MoldingModelBounds(MoldingVec3 minimum, MoldingVec3 maximum) {
    public static final double PIXELS_PER_BLOCK = 16.0D;
    public static final double PRINTING_WORKSPACE_HORIZONTAL_MIN = 8.0D;
    public static final double PRINTING_WORKSPACE_HORIZONTAL_MAX = 40.0D;
    public static final double PRINTING_WORKSPACE_VERTICAL_MIN = 1.0D;
    public static final double PRINTING_WORKSPACE_VERTICAL_MAX = 33.0D;
    private static final double EPSILON = 1.0E-7D;
    private static final MoldingModelBounds EMPTY = new MoldingModelBounds(MoldingVec3.ZERO, MoldingVec3.ZERO);

    public MoldingModelBounds {
        if (minimum.x() > maximum.x() || minimum.y() > maximum.y() || minimum.z() > maximum.z()) {
            throw new IllegalArgumentException("Molding model bounds are inverted");
        }
    }

    public static Optional<MoldingModelBounds> all(EditableMoldingModel model) {
        return inspect(model, false);
    }

    public static Optional<MoldingModelBounds> visible(EditableMoldingModel model) {
        return inspect(model, true);
    }

    public static MoldingModelBounds empty() {
        return EMPTY;
    }

    public MoldingVec3 sizePixels() {
        return this.maximum.subtract(this.minimum);
    }

    public MoldingVec3 sizeBlocks() {
        return this.sizePixels().scale(1.0D / PIXELS_PER_BLOCK);
    }

    public boolean fitsWorkspaceSize() {
        MoldingVec3 size = this.sizePixels();
        return size.x() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON
            && size.y() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON
            && size.z() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON;
    }

    public boolean fitsWorkspace() {
        return this.fitsWorkspaceSize()
            && this.minimum.x() >= MoldingCoordinateSystem.WORKSPACE_MIN - EPSILON
            && this.minimum.y() >= MoldingCoordinateSystem.WORKSPACE_MIN - EPSILON
            && this.minimum.z() >= MoldingCoordinateSystem.WORKSPACE_MIN - EPSILON
            && this.maximum.x() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON
            && this.maximum.y() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON
            && this.maximum.z() <= MoldingCoordinateSystem.WORKSPACE_MAX + EPSILON;
    }

    public boolean fitsPrintingWorkspace() {
        return this.minimum.x() >= PRINTING_WORKSPACE_HORIZONTAL_MIN - EPSILON
            && this.minimum.y() >= PRINTING_WORKSPACE_VERTICAL_MIN - EPSILON
            && this.minimum.z() >= PRINTING_WORKSPACE_HORIZONTAL_MIN - EPSILON
            && this.maximum.x() <= PRINTING_WORKSPACE_HORIZONTAL_MAX + EPSILON
            && this.maximum.y() <= PRINTING_WORKSPACE_VERTICAL_MAX + EPSILON
            && this.maximum.z() <= PRINTING_WORKSPACE_HORIZONTAL_MAX + EPSILON;
    }

    public static String formatBlocks(double size) {
        return BigDecimal.valueOf(size)
            .setScale(4, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }

    private static Optional<MoldingModelBounds> inspect(EditableMoldingModel model, boolean visibleOnly) {
        Map<UUID, MoldingGroup> groups = model.groupMap();
        MoldingVec3 minimum = null;
        MoldingVec3 maximum = null;
        for (MoldingElement element : model.elements()) {
            if (visibleOnly && !isVisible(element, groups)) continue;
            for (MoldingVec3 vertex : MoldingModelBaker.transformedVertices(model, element)) {
                minimum = minimum == null ? vertex : minimum.min(vertex);
                maximum = maximum == null ? vertex : maximum.max(vertex);
            }
        }
        return minimum == null ? Optional.empty() : Optional.of(new MoldingModelBounds(minimum, maximum));
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
}
