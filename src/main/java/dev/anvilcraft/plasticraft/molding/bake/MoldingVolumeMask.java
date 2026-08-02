package dev.anvilcraft.plasticraft.molding.bake;

import java.util.BitSet;

/** 固定为 48x48x48 的制造体素并集。 */
public final class MoldingVolumeMask {
    public static final int SIZE = 48;
    public static final int CELL_COUNT = SIZE * SIZE * SIZE;
    private final BitSet cells;

    public MoldingVolumeMask() {
        this.cells = new BitSet(CELL_COUNT);
    }

    private MoldingVolumeMask(BitSet cells) {
        this.cells = cells;
    }

    public boolean get(int x, int y, int z) {
        return inBounds(x, y, z) && this.cells.get(index(x, y, z));
    }

    public void set(int x, int y, int z) {
        if (!inBounds(x, y, z)) throw new IndexOutOfBoundsException("Molding cell is outside the workspace");
        this.cells.set(index(x, y, z));
    }

    public boolean isEmpty() {
        return this.cells.isEmpty();
    }

    public int volume() {
        return this.cells.cardinality();
    }

    public BitSet copyBits() {
        return (BitSet) this.cells.clone();
    }

    public MoldingVolumeMask copy() {
        return new MoldingVolumeMask(this.copyBits());
    }

    public static int index(int x, int y, int z) {
        return (y * SIZE + x) * SIZE + z;
    }

    public static int x(int index) {
        return index / SIZE % SIZE;
    }

    public static int y(int index) {
        return index / (SIZE * SIZE);
    }

    public static int z(int index) {
        return index % SIZE;
    }

    public static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }
}
