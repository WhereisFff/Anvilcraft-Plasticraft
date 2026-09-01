package dev.anvilcraft.plasticraft.molding.bake;

import java.util.List;

/** 按本批熔体比例水平裁切源模型后得到的连续表面与凸碰撞体。 */
public record ManufacturedMoldingGeometry(
    List<MoldingQuad> surfaceMesh,
    List<MoldingConvexHull> collisionHulls
) {
    public ManufacturedMoldingGeometry {
        surfaceMesh = List.copyOf(surfaceMesh);
        collisionHulls = List.copyOf(collisionHulls);
    }
}
