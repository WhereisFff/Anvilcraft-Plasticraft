package dev.anvilcraft.plasticraft.client.molding.editor;

import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingElement;
import dev.anvilcraft.plasticraft.molding.model.MoldingTransform;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;
import org.joml.Vector3d;
import org.jetbrains.annotations.Nullable;

import java.util.function.UnaryOperator;

/** 在父组坐标与制造空间之间转换编辑操作，无法由旋转和缩放表达的剪切必须拒绝。 */
final class MoldingElementTransforms {
    private static final double EPSILON = 1.0E-7D;

    private MoldingElementTransforms() {
    }

    static MoldingVec3 worldPivot(EditableMoldingModel model, MoldingElement element) {
        return MoldingModelBaker.transformedPoint(model, element, element.transform().pivot());
    }

    static MoldingElement move(EditableMoldingModel model, MoldingElement element, MoldingVec3 delta) {
        MoldingTransform old = element.transform();
        MoldingVec3 current = worldPivot(model, element);
        MoldingVec3 parentDelta = parentPoint(model, element, current.add(delta))
            .subtract(parentPoint(model, element, current));
        return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
            old.translation().add(parentDelta), old.rotation(), old.scale(), old.pivot()
        ));
    }

    static MoldingElement pivot(EditableMoldingModel model, MoldingElement element, MoldingVec3 target) {
        MoldingTransform old = element.transform();
        MoldingVec3 pivot = MoldingModelBaker.inverseTransformedPoint(model, element, target);
        MoldingVec3 translation = parentPoint(model, element, target).subtract(pivot);
        return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
            translation, old.rotation(), old.scale(), pivot
        ));
    }

    static MoldingElement rotate(
        EditableMoldingModel model, MoldingElement element, MoldingVec3 center, MoldingAxis axis, double degrees
    ) {
        double angle = Math.toRadians(degrees);
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return worldTransform(model, element, point -> {
            MoldingVec3 offset = point.subtract(center);
            MoldingVec3 rotated = switch (axis) {
                case X -> new MoldingVec3(offset.x(), offset.y() * cos - offset.z() * sin,
                    offset.y() * sin + offset.z() * cos);
                case Y -> new MoldingVec3(offset.x() * cos + offset.z() * sin, offset.y(),
                    -offset.x() * sin + offset.z() * cos);
                case Z -> new MoldingVec3(offset.x() * cos - offset.y() * sin,
                    offset.x() * sin + offset.y() * cos, offset.z());
            };
            return rotated.add(center);
        }, null);
    }

    static MoldingElement mirror(
        EditableMoldingModel model, MoldingElement element, MoldingVec3 center, MoldingAxis axis
    ) {
        MoldingElement mirrored = worldTransform(model, element, point -> MoldingDragTransaction.withComponent(
            point, axis, 2.0D * MoldingDragTransaction.component(center, axis)
                - MoldingDragTransaction.component(point, axis)
        ), axis);
        // 制造判定把反向绕序视为减材；镜像只改变形状，不能顺带翻转加材与减材属性。
        return new MoldingElement(
            mirrored.id(), mirrored.name(), mirrored.groupId(),
            MoldingDragTransaction.withComponent(mirrored.from(), axis,
                MoldingDragTransaction.component(element.to(), axis)),
            MoldingDragTransaction.withComponent(mirrored.to(), axis,
                MoldingDragTransaction.component(element.from(), axis)),
            mirrored.transform(), mirrored.visible(), mirrored.locked()
        );
    }

    private static MoldingElement worldTransform(
        EditableMoldingModel model,
        MoldingElement element,
        UnaryOperator<MoldingVec3> operation,
        @Nullable MoldingAxis reflectedAxis
    ) {
        MoldingTransform old = element.transform();
        MoldingVec3 origin = transformedParentPoint(model, element, MoldingVec3.ZERO, operation);
        Vector3d x = vector(transformedParentPoint(model, element, new MoldingVec3(1, 0, 0), operation).subtract(origin));
        Vector3d y = vector(transformedParentPoint(model, element, new MoldingVec3(0, 1, 0), operation).subtract(origin));
        Vector3d z = vector(transformedParentPoint(model, element, new MoldingVec3(0, 0, 1), operation).subtract(origin));
        double sx = Math.copySign(x.length(), old.scale().x()) * (reflectedAxis == MoldingAxis.X ? -1 : 1);
        double sy = Math.copySign(y.length(), old.scale().y()) * (reflectedAxis == MoldingAxis.Y ? -1 : 1);
        double sz = Math.copySign(z.length(), old.scale().z()) * (reflectedAxis == MoldingAxis.Z ? -1 : 1);
        x.div(sx);
        y.div(sy);
        z.div(sz);
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()
            || Math.abs(x.dot(y)) > EPSILON || Math.abs(x.dot(z)) > EPSILON || Math.abs(y.dot(z)) > EPSILON
            || new Vector3d(x).cross(y).dot(z) < 1.0D - EPSILON) {
            throw new IllegalArgumentException("World transform would shear an element inside its scaled group");
        }
        double rotationY = Math.asin(Math.clamp(-x.z, -1.0D, 1.0D));
        double rotationX;
        double rotationZ;
        if (Math.abs(Math.cos(rotationY)) > EPSILON) {
            rotationX = Math.atan2(y.z, z.z);
            rotationZ = Math.atan2(x.y, x.x);
        } else {
            rotationX = 0.0D;
            rotationZ = Math.atan2(-y.x, y.y);
        }
        MoldingVec3 pivot = old.pivot();
        MoldingVec3 translation = transformedParentPoint(model, element, pivot, operation).subtract(pivot);
        return MoldingDragTransaction.replaceTransform(element, new MoldingTransform(
            translation,
            new MoldingVec3(degrees(rotationX), degrees(rotationY), degrees(rotationZ)),
            new MoldingVec3(sx, sy, sz), pivot
        ));
    }

    private static MoldingVec3 transformedParentPoint(
        EditableMoldingModel model, MoldingElement element, MoldingVec3 point, UnaryOperator<MoldingVec3> operation
    ) {
        return parentPoint(model, element, operation.apply(MoldingModelBaker.transformedPoint(model, element, point)));
    }

    private static MoldingVec3 parentPoint(EditableMoldingModel model, MoldingElement element, MoldingVec3 world) {
        return element.transform().apply(MoldingModelBaker.inverseTransformedPoint(model, element, world));
    }

    private static Vector3d vector(MoldingVec3 value) {
        return new Vector3d(value.x(), value.y(), value.z());
    }

    private static double degrees(double angle) {
        double value = Math.toDegrees(angle);
        return Math.abs(value) < 1.0E-9D ? 0.0D : value;
    }
}
