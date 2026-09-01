package dev.anvilcraft.plasticraft.molding.bake;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

/** 与渲染 API 无关的显式表面四边形。 */
public record MoldingQuad(
    MoldingVec3 first,
    MoldingVec3 second,
    MoldingVec3 third,
    MoldingVec3 fourth,
    MoldingVec3 normal,
    boolean doubleSided
) {
    public static final Codec<MoldingQuad> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MoldingVec3.CODEC.fieldOf("first").forGetter(MoldingQuad::first),
        MoldingVec3.CODEC.fieldOf("second").forGetter(MoldingQuad::second),
        MoldingVec3.CODEC.fieldOf("third").forGetter(MoldingQuad::third),
        MoldingVec3.CODEC.fieldOf("fourth").forGetter(MoldingQuad::fourth),
        MoldingVec3.CODEC.fieldOf("normal").forGetter(MoldingQuad::normal),
        Codec.BOOL.optionalFieldOf("double_sided", false).forGetter(MoldingQuad::doubleSided)
    ).apply(instance, MoldingQuad::new));

    public MoldingQuad {
        double normalLength = normal.lengthSquared();
        if (Math.abs(normalLength - 1.0D) > 1.0E-6D) {
            throw new IllegalArgumentException("Molding quad normal must be a unit vector");
        }
    }
}
