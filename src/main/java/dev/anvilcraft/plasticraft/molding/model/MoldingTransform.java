package dev.anvilcraft.plasticraft.molding.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 依次执行缩放、X/Y/Z 旋转和平移的稳定建模变换。 */
public record MoldingTransform(
    MoldingVec3 translation,
    MoldingVec3 rotation,
    MoldingVec3 scale,
    MoldingVec3 pivot
) {
    private static final double MIN_SCALE = 1.0E-6D;
    public static final MoldingTransform IDENTITY = new MoldingTransform(
        MoldingVec3.ZERO,
        MoldingVec3.ZERO,
        MoldingVec3.ONE,
        MoldingVec3.ZERO
    );
    public static final Codec<MoldingTransform> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MoldingVec3.CODEC.optionalFieldOf("translation", MoldingVec3.ZERO)
            .forGetter(MoldingTransform::translation),
        MoldingVec3.CODEC.optionalFieldOf("rotation", MoldingVec3.ZERO)
            .forGetter(MoldingTransform::rotation),
        MoldingVec3.CODEC.optionalFieldOf("scale", MoldingVec3.ONE)
            .forGetter(MoldingTransform::scale),
        MoldingVec3.CODEC.optionalFieldOf("pivot", MoldingVec3.ZERO)
            .forGetter(MoldingTransform::pivot)
    ).apply(instance, MoldingTransform::new));

    public MoldingTransform {
        if (Math.abs(scale.x()) < MIN_SCALE
            || Math.abs(scale.y()) < MIN_SCALE
            || Math.abs(scale.z()) < MIN_SCALE) {
            throw new IllegalArgumentException("Molding transform scale cannot be zero");
        }
    }

    public MoldingVec3 apply(MoldingVec3 point) {
        MoldingVec3 result = point.subtract(this.pivot).multiply(this.scale);
        result = rotateX(result, Math.toRadians(this.rotation.x()));
        result = rotateY(result, Math.toRadians(this.rotation.y()));
        result = rotateZ(result, Math.toRadians(this.rotation.z()));
        return result.add(this.pivot).add(this.translation);
    }

    public MoldingVec3 inverse(MoldingVec3 point) {
        MoldingVec3 result = point.subtract(this.translation).subtract(this.pivot);
        result = rotateZ(result, Math.toRadians(-this.rotation.z()));
        result = rotateY(result, Math.toRadians(-this.rotation.y()));
        result = rotateX(result, Math.toRadians(-this.rotation.x()));
        return new MoldingVec3(
            result.x() / this.scale.x(),
            result.y() / this.scale.y(),
            result.z() / this.scale.z()
        ).add(this.pivot);
    }

    private static MoldingVec3 rotateX(MoldingVec3 value, double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new MoldingVec3(
            value.x(),
            value.y() * cos - value.z() * sin,
            value.y() * sin + value.z() * cos
        );
    }

    private static MoldingVec3 rotateY(MoldingVec3 value, double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new MoldingVec3(
            value.x() * cos + value.z() * sin,
            value.y(),
            -value.x() * sin + value.z() * cos
        );
    }

    private static MoldingVec3 rotateZ(MoldingVec3 value, double angle) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        return new MoldingVec3(
            value.x() * cos - value.y() * sin,
            value.x() * sin + value.y() * cos,
            value.z()
        );
    }
}
