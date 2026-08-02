package dev.anvilcraft.plasticraft.molding.bake;

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
}
