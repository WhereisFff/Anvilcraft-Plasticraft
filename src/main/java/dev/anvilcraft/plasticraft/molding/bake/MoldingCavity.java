package dev.anvilcraft.plasticraft.molding.bake;

import java.util.BitSet;

/** 一个六邻接封闭内腔。 */
public record MoldingCavity(
    BitSet cells,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ
) {
    public MoldingCavity {
        cells = (BitSet) cells.clone();
    }

    @Override
    public BitSet cells() {
        return (BitSet) this.cells.clone();
    }

    public int volume() {
        return this.cells.cardinality();
    }
}
