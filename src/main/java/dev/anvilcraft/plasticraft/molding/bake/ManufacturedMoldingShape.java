package dev.anvilcraft.plasticraft.molding.bake;

import java.util.List;

/** 一次实际加工得到的体素、表面与合并碰撞快照。 */
public record ManufacturedMoldingShape(
    MoldingVolumeMask volumeMask,
    List<MoldingQuad> zeroThicknessQuads,
    List<MoldingQuad> surfaceMesh,
    List<MoldingCollisionBox> collisionShape,
    boolean collisionComplexityExceeded
) {
    public ManufacturedMoldingShape {
        volumeMask = volumeMask.copy();
        zeroThicknessQuads = List.copyOf(zeroThicknessQuads);
        surfaceMesh = List.copyOf(surfaceMesh);
        collisionShape = List.copyOf(collisionShape);
    }

    @Override
    public MoldingVolumeMask volumeMask() {
        return this.volumeMask.copy();
    }
}
