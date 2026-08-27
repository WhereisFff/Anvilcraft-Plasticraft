package dev.anvilcraft.plasticraft.molding.bake;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.Set;

/** 对制造网格执行带一圈 padding 的六邻接密封与内腔分析。 */
public final class MoldingShellAnalyzer {
    private static final MoldingFaceDirection[] DIRECTIONS = MoldingFaceDirection.values();

    private MoldingShellAnalyzer() {
    }

    public static MoldingFunctionalAnalysis analyze(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers
    ) {
        int paddedCellCount = (volume.sizeX() + 2) * (volume.sizeY() + 2) * (volume.sizeZ() + 2);
        BitSet exterior = new BitSet(paddedCellCount);
        floodExterior(volume, barriers, exterior);
        BitSet cavity = new BitSet(volume.cellCount());
        int cavityCount = findCavities(volume, barriers, exterior, cavity);
        BitSet shell = new BitSet(volume.cellCount());
        boolean decoration = false;
        for (int index = cavity.nextSetBit(0); index >= 0; index = cavity.nextSetBit(index + 1)) {
            int x = volume.xOf(index);
            int y = volume.yOf(index);
            int z = volume.zOf(index);
            for (MoldingFaceDirection direction : DIRECTIONS) {
                int nextX = x + direction.stepX();
                int nextY = y + direction.stepY();
                int nextZ = z + direction.stepZ();
                if (volume.get(nextX, nextY, nextZ)
                    && !addShellRun(volume, exterior, shell, nextX, nextY, nextZ, direction)) {
                    decoration = true;
                }
            }
        }
        decoration |= hasInteriorBarrierDecoration(volume, barriers, exterior, cavity);
        // 开顶容器的内腔与外界连通，上面的密闭腔洪泛对它只会得到空腔，炼药锅另行扫描并复用这里的外界可达集
        return new MoldingFunctionalAnalysis(
            MoldingVolumeMask.fromLongArray(
                volume.sizeX(),
                volume.sizeY(),
                volume.sizeZ(),
                cavity.toLongArray()
            ),
            cavityCount,
            cavity.cardinality(),
            shell.cardinality(),
            decoration,
            MoldingAnvilShapeAnalyzer.analyze(volume)
        ).withCauldronShape(MoldingCauldronShapeAnalyzer.analyze(volume, barriers, exterior));
    }

    static void floodExterior(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet exterior
    ) {
        Deque<Cell> queue = new ArrayDeque<>();
        queue.add(new Cell(-1, -1, -1));
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            if (!inPaddedBounds(volume, cell.x, cell.y, cell.z) || volume.get(cell.x, cell.y, cell.z)) continue;
            int index = paddedIndex(volume, cell.x, cell.y, cell.z);
            if (exterior.get(index)) continue;
            exterior.set(index);
            for (MoldingFaceDirection direction : DIRECTIONS) {
                if (!blocked(volume, barriers, cell, direction)) queue.addLast(cell.relative(direction));
            }
        }
    }

    private static int findCavities(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet exterior,
        BitSet cavities
    ) {
        int count = 0;
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int x = 0; x < volume.sizeX(); x++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    int index = volume.indexOf(x, y, z);
                    if (volume.get(x, y, z)
                        || exterior.get(paddedIndex(volume, x, y, z))
                        || cavities.get(index)) continue;
                    count++;
                    Deque<Cell> queue = new ArrayDeque<>();
                    queue.add(new Cell(x, y, z));
                    while (!queue.isEmpty()) {
                        Cell cell = queue.removeFirst();
                        if (!volume.containsCoordinate(cell.x, cell.y, cell.z)
                            || volume.get(cell.x, cell.y, cell.z)) continue;
                        int cavityIndex = volume.indexOf(cell.x, cell.y, cell.z);
                        if (cavities.get(cavityIndex)) continue;
                        cavities.set(cavityIndex);
                        for (MoldingFaceDirection direction : DIRECTIONS) {
                            if (!blocked(volume, barriers, cell, direction)) {
                                queue.addLast(cell.relative(direction));
                            }
                        }
                    }
                }
            }
        }
        return count;
    }

    private static boolean addShellRun(
        MoldingVolumeMask volume,
        BitSet exterior,
        BitSet shell,
        int x,
        int y,
        int z,
        MoldingFaceDirection inwardDirection
    ) {
        int stepX = inwardDirection.stepX();
        int stepY = inwardDirection.stepY();
        int stepZ = inwardDirection.stepZ();
        int currentX = x;
        int currentY = y;
        int currentZ = z;
        BitSet run = new BitSet(volume.cellCount());
        while (volume.containsCoordinate(currentX, currentY, currentZ)
            && volume.get(currentX, currentY, currentZ)) {
            run.set(volume.indexOf(currentX, currentY, currentZ));
            currentX += stepX;
            currentY += stepY;
            currentZ += stepZ;
        }
        boolean reachesExterior = inPaddedBounds(volume, currentX, currentY, currentZ)
            && exterior.get(paddedIndex(volume, currentX, currentY, currentZ));
        if (reachesExterior) shell.or(run);
        return reachesExterior;
    }

    private static boolean hasInteriorBarrierDecoration(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet exterior,
        BitSet cavity
    ) {
        for (MoldingBarrierFace barrier : barriers) {
            Cell first = firstSide(barrier);
            Cell second = secondSide(barrier);
            boolean firstCavity = isCavity(volume, first, cavity);
            boolean secondCavity = isCavity(volume, second, cavity);
            if (!firstCavity && !secondCavity) continue;
            boolean firstExterior = isExterior(volume, first, exterior);
            boolean secondExterior = isExterior(volume, second, exterior);
            boolean firstSolid = volume.get(first.x, first.y, first.z);
            boolean secondSolid = volume.get(second.x, second.y, second.z);
            if (!(firstCavity && (secondExterior || secondSolid)
                || secondCavity && (firstExterior || firstSolid))) return true;
        }
        return false;
    }

    private static Cell firstSide(MoldingBarrierFace face) {
        return switch (face.axis()) {
            case X -> new Cell(face.plane() - 1, face.u(), face.v());
            case Y -> new Cell(face.u(), face.plane() - 1, face.v());
            case Z -> new Cell(face.u(), face.v(), face.plane() - 1);
        };
    }

    private static Cell secondSide(MoldingBarrierFace face) {
        return switch (face.axis()) {
            case X -> new Cell(face.plane(), face.u(), face.v());
            case Y -> new Cell(face.u(), face.plane(), face.v());
            case Z -> new Cell(face.u(), face.v(), face.plane());
        };
    }

    private static boolean isCavity(MoldingVolumeMask volume, Cell cell, BitSet cavity) {
        return volume.containsCoordinate(cell.x, cell.y, cell.z)
            && cavity.get(volume.indexOf(cell.x, cell.y, cell.z));
    }

    private static boolean isExterior(MoldingVolumeMask volume, Cell cell, BitSet exterior) {
        return isExterior(volume, cell.x, cell.y, cell.z, exterior);
    }

    static boolean isExterior(MoldingVolumeMask volume, int x, int y, int z, BitSet exterior) {
        return inPaddedBounds(volume, x, y, z) && exterior.get(paddedIndex(volume, x, y, z));
    }

    private static boolean blocked(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        Cell cell,
        MoldingFaceDirection direction
    ) {
        return blocked(volume, barriers, cell.x, cell.y, cell.z, direction);
    }

    static boolean blocked(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        int x,
        int y,
        int z,
        MoldingFaceDirection direction
    ) {
        return switch (direction.axis()) {
            case X -> transitionCoordinate(x, direction.stepX(), volume.sizeX())
                && y >= 0 && y < volume.sizeY() && z >= 0 && z < volume.sizeZ()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Y -> transitionCoordinate(y, direction.stepY(), volume.sizeY())
                && x >= 0 && x < volume.sizeX() && z >= 0 && z < volume.sizeZ()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
            case Z -> transitionCoordinate(z, direction.stepZ(), volume.sizeZ())
                && x >= 0 && x < volume.sizeX() && y >= 0 && y < volume.sizeY()
                && barriers.contains(MoldingBarrierFace.between(x, y, z, direction));
        };
    }

    private static boolean transitionCoordinate(int coordinate, int step, int size) {
        return coordinate >= 0 && coordinate < size
            || coordinate == -1 && step > 0
            || coordinate == size && step < 0;
    }

    private static boolean inPaddedBounds(MoldingVolumeMask volume, int x, int y, int z) {
        return x >= -1 && x <= volume.sizeX()
            && y >= -1 && y <= volume.sizeY()
            && z >= -1 && z <= volume.sizeZ();
    }

    private static int paddedIndex(MoldingVolumeMask volume, int x, int y, int z) {
        int paddedX = volume.sizeX() + 2;
        int paddedZ = volume.sizeZ() + 2;
        return ((y + 1) * paddedX + x + 1) * paddedZ + z + 1;
    }

    private record Cell(int x, int y, int z) {
        private Cell relative(MoldingFaceDirection direction) {
            return new Cell(
                this.x + direction.stepX(),
                this.y + direction.stepY(),
                this.z + direction.stepZ()
            );
        }
    }
}
