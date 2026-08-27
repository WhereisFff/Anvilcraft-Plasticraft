package dev.anvilcraft.plasticraft.molding.bake;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;

/** 制造体素并集；普通模型使用 48x48x48，超限模型按整数像素外接尺寸分配。 */
public final class MoldingVolumeMask {
    public static final int SIZE = 48;
    public static final int CELL_COUNT = SIZE * SIZE * SIZE;
    public static final int MAX_LONG_COUNT = (CELL_COUNT + Long.SIZE - 1) / Long.SIZE;
    /** 防止恶意蓝图通过极端尺寸分配不可控的位图。 */
    public static final int MAX_SIZE = 512;
    public static final int MAX_CELL_COUNT = MAX_SIZE * MAX_SIZE * MAX_SIZE;
    public static final Codec<MoldingVolumeMask> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.listOf().fieldOf("size").forGetter(mask -> List.of(mask.sizeX, mask.sizeY, mask.sizeZ)),
        Codec.LONG.listOf().fieldOf("cells").forGetter(mask -> mask.toLongList())
    ).apply(instance, MoldingVolumeMask::fromCodec));
    private final BitSet cells;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private int volume;

    public MoldingVolumeMask() {
        this(SIZE, SIZE, SIZE);
    }

    public MoldingVolumeMask(int sizeX, int sizeY, int sizeZ) {
        validateDimensions(sizeX, sizeY, sizeZ);
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = new BitSet(cellCount(sizeX, sizeY, sizeZ));
    }

    /** 创建指定尺寸的完整外接体素空间，用于创造模式的最大属性估算。 */
    public static MoldingVolumeMask filled(int sizeX, int sizeY, int sizeZ) {
        int count = cellCount(sizeX, sizeY, sizeZ);
        BitSet cells = new BitSet(count);
        cells.set(0, count);
        return new MoldingVolumeMask(sizeX, sizeY, sizeZ, cells);
    }

    public static MoldingVolumeMask filledBox(
        int sizeX,
        int sizeY,
        int sizeZ,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ
    ) {
        validateDimensions(sizeX, sizeY, sizeZ);
        if (minX < 0 || minY < 0 || minZ < 0
            || maxX < minX || maxY < minY || maxZ < minZ
            || maxX > sizeX || maxY > sizeY || maxZ > sizeZ) {
            throw new IllegalArgumentException("Molding filled box is outside its dimensions");
        }
        BitSet cells = new BitSet(cellCount(sizeX, sizeY, sizeZ));
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                int first = (y * sizeX + x) * sizeZ + minZ;
                cells.set(first, first + maxZ - minZ);
            }
        }
        return new MoldingVolumeMask(sizeX, sizeY, sizeZ, cells);
    }

    private MoldingVolumeMask(int sizeX, int sizeY, int sizeZ, BitSet cells) {
        validateDimensions(sizeX, sizeY, sizeZ);
        if (cells.length() > cellCount(sizeX, sizeY, sizeZ)) {
            throw new IllegalArgumentException("Molding volume mask contains cells outside its dimensions");
        }
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.cells = cells;
        this.volume = cells.cardinality();
    }

    public int sizeX() {
        return this.sizeX;
    }

    public int sizeY() {
        return this.sizeY;
    }

    public int sizeZ() {
        return this.sizeZ;
    }

    public int cellCount() {
        return cellCount(this.sizeX, this.sizeY, this.sizeZ);
    }

    public boolean get(int x, int y, int z) {
        return this.containsCoordinate(x, y, z) && this.cells.get(this.indexOf(x, y, z));
    }

    public void set(int x, int y, int z) {
        if (!this.containsCoordinate(x, y, z)) {
            throw new IndexOutOfBoundsException("Molding cell is outside the workspace");
        }
        int index = this.indexOf(x, y, z);
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

    /** 已置位格子的像素包围盒；空掩码返回空。 */
    public Optional<Bounds> bounds() {
        if (this.cells.isEmpty()) return Optional.empty();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int index = this.cells.nextSetBit(0); index >= 0; index = this.cells.nextSetBit(index + 1)) {
            int x = this.xOf(index);
            int y = this.yOf(index);
            int z = this.zOf(index);
            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (z < minZ) minZ = z;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
            if (z > maxZ) maxZ = z;
        }
        return Optional.of(new Bounds(minX, minY, minZ, maxX, maxY, maxZ));
    }

    public BitSet copyBits() {
        return (BitSet) this.cells.clone();
    }

    public MoldingVolumeMask copy() {
        return new MoldingVolumeMask(this.sizeX, this.sizeY, this.sizeZ, this.copyBits());
    }

    public long[] toLongArray() {
        return this.cells.toLongArray();
    }

    public static MoldingVolumeMask fromLongArray(long[] values) {
        return fromLongArray(SIZE, SIZE, SIZE, values);
    }

    public static MoldingVolumeMask fromLongArray(int sizeX, int sizeY, int sizeZ, long[] values) {
        validateDimensions(sizeX, sizeY, sizeZ);
        if (values.length > maxLongCount(sizeX, sizeY, sizeZ)) {
            throw new IllegalArgumentException("Molding volume mask is too large");
        }
        BitSet cells = BitSet.valueOf(values);
        return new MoldingVolumeMask(sizeX, sizeY, sizeZ, cells);
    }

    private static MoldingVolumeMask fromCodec(List<Integer> dimensions, List<Long> values) {
        if (dimensions.size() != 3) throw new IllegalArgumentException("Molding volume mask size must have three axes");
        return fromLongArray(dimensions.get(0), dimensions.get(1), dimensions.get(2), toLongArray(values));
    }

    private static long[] toLongArray(List<Long> values) {
        long[] packed = new long[values.size()];
        for (int index = 0; index < values.size(); index++) packed[index] = values.get(index);
        return packed;
    }

    private List<Long> toLongList() {
        return Arrays.stream(this.toLongArray()).boxed().toList();
    }

    public static int index(int x, int y, int z) {
        return (y * SIZE + x) * SIZE + z;
    }

    public int indexOf(int x, int y, int z) {
        return (y * this.sizeX + x) * this.sizeZ + z;
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

    public int xOf(int index) {
        return index / this.sizeZ % this.sizeX;
    }

    public int yOf(int index) {
        return index / (this.sizeX * this.sizeZ);
    }

    public int zOf(int index) {
        return index % this.sizeZ;
    }

    public static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE && z >= 0 && z < SIZE;
    }

    public boolean containsCoordinate(int x, int y, int z) {
        return x >= 0 && x < this.sizeX && y >= 0 && y < this.sizeY && z >= 0 && z < this.sizeZ;
    }

    public static int maxLongCount(int sizeX, int sizeY, int sizeZ) {
        return (int) ((cellCount(sizeX, sizeY, sizeZ) + Long.SIZE - 1L) / Long.SIZE);
    }

    private static int cellCount(int sizeX, int sizeY, int sizeZ) {
        validateDimensions(sizeX, sizeY, sizeZ);
        return Math.toIntExact((long) sizeX * sizeY * sizeZ);
    }

    private static void validateDimensions(int sizeX, int sizeY, int sizeZ) {
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0
            || sizeX > MAX_SIZE || sizeY > MAX_SIZE || sizeZ > MAX_SIZE) {
            throw new IllegalArgumentException("Molding volume mask dimensions are outside the supported range");
        }
        if ((long) sizeX * sizeY * sizeZ > MAX_CELL_COUNT) {
            throw new IllegalArgumentException("Molding volume mask is too large");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof MoldingVolumeMask mask
            && this.sizeX == mask.sizeX && this.sizeY == mask.sizeY && this.sizeZ == mask.sizeZ
            && this.cells.equals(mask.cells);
    }

    @Override
    public int hashCode() {
        int result = this.cells.hashCode();
        result = 31 * result + this.sizeX;
        result = 31 * result + this.sizeY;
        return 31 * result + this.sizeZ;
    }

    /** 像素包围盒，两端均为闭区间的格坐标。 */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }
}
