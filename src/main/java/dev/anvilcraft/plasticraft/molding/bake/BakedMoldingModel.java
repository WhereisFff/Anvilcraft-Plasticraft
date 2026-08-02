package dev.anvilcraft.plasticraft.molding.bake;

import java.util.List;
import java.util.Set;

/** 编辑源数据的权威、确定性制造派生结果。 */
public record BakedMoldingModel(
    int bakeVersion,
    MoldingVolumeMask volumeMask,
    Set<MoldingBarrierFace> barrierFaces,
    int[] fillOrder,
    List<MoldingQuad> surfaceMesh,
    List<MoldingCollisionBox> collisionShape,
    List<MoldingCavity> cavities,
    MoldingAnalysis analysis,
    String modelHash
) {
    public BakedMoldingModel {
        volumeMask = volumeMask.copy();
        barrierFaces = Set.copyOf(barrierFaces);
        fillOrder = fillOrder.clone();
        surfaceMesh = List.copyOf(surfaceMesh);
        collisionShape = List.copyOf(collisionShape);
        cavities = List.copyOf(cavities);
    }

    @Override
    public MoldingVolumeMask volumeMask() {
        return this.volumeMask.copy();
    }

    @Override
    public int[] fillOrder() {
        return this.fillOrder.clone();
    }
}
