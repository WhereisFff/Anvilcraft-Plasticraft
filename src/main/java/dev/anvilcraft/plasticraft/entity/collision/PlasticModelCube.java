package dev.anvilcraft.plasticraft.entity.collision;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/** 服务端可用的模型 Cube，坐标、旋转轴、角度和枢轴均与方块模型 element 一致。 */
public record PlasticModelCube(
    Vec3 from,
    Vec3 to,
    Direction.Axis rotationAxis,
    double rotationDegrees,
    Vec3 rotationOrigin
) {
    public PlasticModelCube {
        from = requireFinite(from, "from");
        to = requireFinite(to, "to");
        rotationAxis = Objects.requireNonNull(rotationAxis, "rotationAxis");
        rotationOrigin = requireFinite(rotationOrigin, "rotationOrigin");
        if (!Double.isFinite(rotationDegrees)) {
            throw new IllegalArgumentException("Model cube rotation must be finite");
        }
        if (from.x == to.x || from.y == to.y || from.z == to.z) {
            throw new IllegalArgumentException("Physical model cube must have volume");
        }
    }

    public static PlasticModelCube axisAligned(Vec3 from, Vec3 to) {
        return new PlasticModelCube(from, to, Direction.Axis.Y, 0.0D, Vec3.ZERO);
    }

    public static PlasticModelCube rotated(
        Vec3 from,
        Vec3 to,
        Direction.Axis rotationAxis,
        double rotationDegrees,
        Vec3 rotationOrigin
    ) {
        return new PlasticModelCube(from, to, rotationAxis, rotationDegrees, rotationOrigin);
    }

    public PlasticConvexShape convexShape(double scale) {
        if (!Double.isFinite(scale) || scale <= 0.0D) {
            throw new IllegalArgumentException("Model cube scale must be positive");
        }
        PlasticConvexShape shape = PlasticConvexShape.box(new AABB(
            Math.min(this.from.x, this.to.x) * scale,
            Math.min(this.from.y, this.to.y) * scale,
            Math.min(this.from.z, this.to.z) * scale,
            Math.max(this.from.x, this.to.x) * scale,
            Math.max(this.from.y, this.to.y) * scale,
            Math.max(this.from.z, this.to.z) * scale
        ));
        if (this.rotationDegrees == 0.0D) return shape;
        return shape.rotateAroundAxis(
            this.rotationAxis,
            Math.toRadians(this.rotationDegrees),
            this.rotationOrigin.scale(scale)
        );
    }

    private static Vec3 requireFinite(Vec3 vector, String name) {
        Vec3 value = Objects.requireNonNull(vector, name);
        if (!Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }
}
