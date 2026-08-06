package dev.anvilcraft.plasticraft.molding.bake;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

/** 固定为 48x48x48 的制造体素并集。 */
public final class MoldingVolumeMask {
    public static final int SIZE = 48;
    public static final int CELL_COUNT = SIZE * SIZE * SIZE;
    public static final int MAX_LONG_COUNT = (CELL_COUNT + Long.SIZE - 1) / Long.SIZE;
    public static final Codec<MoldingVolumeMask> CODEC = Codec.LONG.listOf()
        .validate(values -> values.size() <= MAX_LONG_COUNT
            ? DataResult.success(values)
            : DataResult.error(() -> "Molding volume mask is too large"))
        .xmap(MoldingVolumeMask::fromLongList, MoldingVolumeMask::toLongList);
    private final BitSet cells;
    private int volume;

    public MoldingVolumeMask() {
        this.cells = new BitSet(CELL_COUNT);
    }

    private MoldingVolumeMask(BitSet cells) {
        this.cells = cells;
        this.volume = cells.cardinality();
    }

    public boolean get(int x, int y, int z) {
        return inBounds(x, y, z) && this.cells.get(index(x, y, z));
    }

    public void set(int x, int y, int z) {
        if (!inBounds(x, y, z)) throw new IndexOutOfBoundsException("Molding cell is outside the workspace");
        int index = index(x, y, z);
        if (!this.cells.get(index)) {
            this.cells.set(index);
            this.volume++;
        }
    }

    public boolean isEmpty() {
        return this.cells.isEmpty();
    }

    public int volume() {
        return this.volume;
    }

    public BitSet copyBits() {
        return (BitSet) this.cells.clone();
    }

    public MoldingVolumeMask copy() {
        return new MoldingVolumeMask(this.copyBits());
    }

    public long[] toLongArray() {
        return this.cells.toLongArray();
    }

    public static MoldingVolumeMask fromLongArray(long[] values) {
        if (values.length > MAX_LONG_COUNT) {
            throw new IllegalArgumentException("Molding volume mask is too large");
        }
        BitSet cells = BitSet.valueOf(values);
        if (cells.length() > CELL_COUNT) {
            throw new IllegalArgumentException("Molding volume mask contains cells outside the workspace");
        }
        return new MoldingVolumeMask(cells);
    }

    private static MoldingVolumeMask fromLongList(List<Long> values) {
        long[] packed = new long[values.size()];
        for (int index = 0; index < values.size(); index++) packed[index] = values.get(index);
        return fromLongArray(packed);
    }

    private List<Long> toLongList() {
        return Arrays.stream(this.toLongArray()).boxed().toList();
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

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MoldingVolumeMask mask && this.cells.equals(mask.cells);
    }

    @Override
    public int hashCode() {
        return this.cells.hashCode();
    }
}
