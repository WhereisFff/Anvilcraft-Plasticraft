package dev.anvilcraft.plasticraft.molding.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 建模工作空间使用的、与渲染 API 无关的三维坐标。 */
public record MoldingVec3(double x, double y, double z) {
    public static final MoldingVec3 ZERO = new MoldingVec3(0.0D, 0.0D, 0.0D);
    public static final MoldingVec3 ONE = new MoldingVec3(1.0D, 1.0D, 1.0D);
    public static final Codec<MoldingVec3> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.DOUBLE.fieldOf("x").forGetter(MoldingVec3::x),
        Codec.DOUBLE.fieldOf("y").forGetter(MoldingVec3::y),
        Codec.DOUBLE.fieldOf("z").forGetter(MoldingVec3::z)
    ).apply(instance, MoldingVec3::new));

    public MoldingVec3 {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Molding coordinates must be finite");
        }
    }

    public MoldingVec3 add(MoldingVec3 other) {
        return new MoldingVec3(this.x + other.x, this.y + other.y, this.z + other.z);
    }

    public MoldingVec3 subtract(MoldingVec3 other) {
        return new MoldingVec3(this.x - other.x, this.y - other.y, this.z - other.z);
    }

    public MoldingVec3 multiply(MoldingVec3 other) {
        return new MoldingVec3(this.x * other.x, this.y * other.y, this.z * other.z);
    }

    public MoldingVec3 scale(double factor) {
        return new MoldingVec3(this.x * factor, this.y * factor, this.z * factor);
    }

    public double dot(MoldingVec3 other) {
        return this.x * other.x + this.y * other.y + this.z * other.z;
    }

    public MoldingVec3 cross(MoldingVec3 other) {
        return new MoldingVec3(
            this.y * other.z - this.z * other.y,
            this.z * other.x - this.x * other.z,
            this.x * other.y - this.y * other.x
        );
    }

    public double lengthSquared() {
        return this.dot(this);
    }

    public MoldingVec3 min(MoldingVec3 other) {
        return new MoldingVec3(
            Math.min(this.x, other.x),
            Math.min(this.y, other.y),
            Math.min(this.z, other.z)
        );
    }

    public MoldingVec3 max(MoldingVec3 other) {
        return new MoldingVec3(
            Math.max(this.x, other.x),
            Math.max(this.y, other.y),
            Math.max(this.z, other.z)
        );
    }
}
