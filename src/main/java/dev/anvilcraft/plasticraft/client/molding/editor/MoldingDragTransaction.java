package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
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
            replacements.add(this.selectedIds.contains(element.id())
                && !isEffectivelyLocked(this.originalModel, element)
                ? transform(this.originalModel, element, this.tool, this.axis, this.axisDirection, this.value)
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
        EditableMoldingModel model,
        MoldingElement element,
        MoldingTool tool,
        MoldingAxis axis,
        double axisDirection,
        double value
    ) {
        MoldingTransform transform = element.transform();
        return switch (tool) {
            case MOVE -> translate(model, element, axis, value);
            case ROTATE -> rotate(element, axis, value);
            case PIVOT -> withWorldPivot(model, element, add(worldPivot(model, element), axis, value));
            case SCALE -> resize(element, axis, axisDirection, value);
            case NONE, MIRROR -> throw new IllegalStateException("Selected tool cannot be dragged");
        };
    }

    static MoldingVec3 worldPivot(EditableMoldingModel model, MoldingElement element) {
        return MoldingModelBaker.transformedPoint(model, element, element.transform().pivot());
    }

    static MoldingElement withWorldPivot(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingVec3 worldPivot
    ) {
        MoldingTransform transform = element.transform();
        MoldingVec3 sourcePoint = MoldingModelBaker.inverseTransformedPoint(model, element, worldPivot);
        MoldingVec3 parentPoint = transform.apply(sourcePoint);
        return replaceTransform(element, new MoldingTransform(
            transform.translation(),
            transform.rotation(),
            transform.scale(),
            parentPoint.subtract(transform.translation())
        ));
    }

    private static MoldingElement translate(
        EditableMoldingModel model,
        MoldingElement element,
        MoldingAxis axis,
        double amount
    ) {
        MoldingTransform transform = element.transform();
        MoldingVec3 target = add(worldPivot(model, element), axis, amount);
        MoldingVec3 sourcePoint = MoldingModelBaker.inverseTransformedPoint(model, element, target);
        MoldingVec3 parentPoint = transform.apply(sourcePoint);
        return replaceTransform(element, new MoldingTransform(
            parentPoint.subtract(transform.pivot()),
            transform.rotation(),
            transform.scale(),
            transform.pivot()
        ));
    }

    private static MoldingElement rotate(MoldingElement element, MoldingAxis axis, double degrees) {
        MoldingTransform transform = element.transform();
        MoldingVec3 rotation = RotationMatrix.fromDegrees(transform.rotation())
            .prepend(axis, Math.toRadians(degrees))
            .toDegrees();
        return replaceTransform(element, new MoldingTransform(
            transform.translation(),
            rotation,
            transform.scale(),
            transform.pivot()
        ));
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

    /** 以固定世界轴左乘增量旋转，避免共同枢轴的多选因原有姿态不同而散开。 */
    private record RotationMatrix(
        double xx,
        double xy,
        double xz,
        double yx,
        double yy,
        double yz,
        double zx,
        double zy,
        double zz
    ) {
        private static final double GIMBAL_EPSILON = 1.0E-8D;

        static RotationMatrix fromDegrees(MoldingVec3 rotation) {
            double x = Math.toRadians(rotation.x());
            double y = Math.toRadians(rotation.y());
            double z = Math.toRadians(rotation.z());
            double sinX = Math.sin(x);
            double cosX = Math.cos(x);
            double sinY = Math.sin(y);
            double cosY = Math.cos(y);
            double sinZ = Math.sin(z);
            double cosZ = Math.cos(z);
            return new RotationMatrix(
                cosZ * cosY,
                cosZ * sinY * sinX - sinZ * cosX,
                cosZ * sinY * cosX + sinZ * sinX,
                sinZ * cosY,
                sinZ * sinY * sinX + cosZ * cosX,
                sinZ * sinY * cosX - cosZ * sinX,
                -sinY,
                cosY * sinX,
                cosY * cosX
            );
        }

        RotationMatrix prepend(MoldingAxis axis, double angle) {
            double sin = Math.sin(angle);
            double cos = Math.cos(angle);
            return switch (axis) {
                case X -> new RotationMatrix(
                    this.xx,
                    this.xy,
                    this.xz,
                    cos * this.yx - sin * this.zx,
                    cos * this.yy - sin * this.zy,
                    cos * this.yz - sin * this.zz,
                    sin * this.yx + cos * this.zx,
                    sin * this.yy + cos * this.zy,
                    sin * this.yz + cos * this.zz
                );
                case Y -> new RotationMatrix(
                    cos * this.xx + sin * this.zx,
                    cos * this.xy + sin * this.zy,
                    cos * this.xz + sin * this.zz,
                    this.yx,
                    this.yy,
                    this.yz,
                    -sin * this.xx + cos * this.zx,
                    -sin * this.xy + cos * this.zy,
                    -sin * this.xz + cos * this.zz
                );
                case Z -> new RotationMatrix(
                    cos * this.xx - sin * this.yx,
                    cos * this.xy - sin * this.yy,
                    cos * this.xz - sin * this.yz,
                    sin * this.xx + cos * this.yx,
                    sin * this.xy + cos * this.yy,
                    sin * this.xz + cos * this.yz,
                    this.zx,
                    this.zy,
                    this.zz
                );
            };
        }

        MoldingVec3 toDegrees() {
            double y = Math.asin(Math.clamp(-this.zx, -1.0D, 1.0D));
            double x;
            double z;
            if (Math.abs(Math.cos(y)) > GIMBAL_EPSILON) {
                x = Math.atan2(this.zy, this.zz);
                z = Math.atan2(this.yx, this.xx);
            } else {
                x = 0.0D;
                z = Math.atan2(-this.xy, this.yy);
            }
            return new MoldingVec3(cleanDegrees(x), cleanDegrees(y), cleanDegrees(z));
        }

        private static double cleanDegrees(double radians) {
            double degrees = Math.toDegrees(radians);
            return Math.abs(degrees) < 1.0E-10D ? 0.0D : degrees;
        }
    }
}
