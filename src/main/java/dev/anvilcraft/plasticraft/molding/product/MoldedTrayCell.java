package dev.anvilcraft.plasticraft.molding.product;

import com.mojang.serialization.Codec;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/** 支架建模区域内固定的三乘三承载格。 */
public record MoldedTrayCell(int x, int z) implements Comparable<MoldedTrayCell> {
    public static final int GRID_SIZE = 3;
    public static final int CELL_COUNT = GRID_SIZE * GRID_SIZE;
    public static final MoldedTrayCell CENTER = new MoldedTrayCell(1, 1);
    public static final List<MoldedTrayCell> VALUES = IntStream.range(0, CELL_COUNT)
        .mapToObj(MoldedTrayCell::fromIndex)
        .toList();
    public static final Codec<MoldedTrayCell> CODEC = Codec.intRange(0, CELL_COUNT - 1)
        .xmap(MoldedTrayCell::fromIndex, MoldedTrayCell::index);

    public MoldedTrayCell {
        if (x < 0 || x >= GRID_SIZE || z < 0 || z >= GRID_SIZE) {
            throw new IllegalArgumentException("Molded tray cell is outside the 3 x 3 grid");
        }
    }

    public int index() {
        return this.z * GRID_SIZE + this.x;
    }

    public int bit() {
        return 1 << this.index();
    }

    public Optional<MoldedTrayCell> relative(Direction direction) {
        int nextX = this.x + direction.getStepX();
        int nextZ = this.z + direction.getStepZ();
        if (direction.getAxis() == Direction.Axis.Y
            || nextX < 0 || nextX >= GRID_SIZE
            || nextZ < 0 || nextZ >= GRID_SIZE) {
            return Optional.empty();
        }
        return Optional.of(new MoldedTrayCell(nextX, nextZ));
    }

    public static MoldedTrayCell fromIndex(int index) {
        if (index < 0 || index >= CELL_COUNT) {
            throw new IllegalArgumentException("Invalid molded tray cell index");
        }
        return new MoldedTrayCell(index % GRID_SIZE, index / GRID_SIZE);
    }

    @Override
    public int compareTo(MoldedTrayCell other) {
        return Integer.compare(this.index(), other.index());
    }
}
