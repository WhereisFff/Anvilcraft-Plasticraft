package dev.anvilcraft.plasticraft.molding.bake;

import java.util.BitSet;
import java.util.Set;

/**
 * 扫描炼药锅的开顶内腔。
 *
 * <p>开顶容器的内腔与外界连通，{@link MoldingShellAnalyzer} 的密闭腔洪泛对它只会得到空腔，
 * 因此这里改用「逐层水平封闭 + 按列自底向上堆叠」：先在每个 y 层做 2D 洪泛判断哪些格被侧壁围住，
 * 再要求内腔格的下方是实心或已属内腔。侧壁不等高、侧面漏孔、圆形与异形轮廓都由这两步自然覆盖。
 */
public final class MoldingCauldronShapeAnalyzer {
    /** 内腔顶面开口的最小像素面积；12×12 使原版炼药锅形状刚好合格。 */
    public static final int MINIMUM_OPENING_AREA = 144;
    /** 内腔的最小整体深度，即最矮壁高。 */
    public static final int MINIMUM_DEPTH = 8;
    /** 开口面积超过该值升级为大型塑料炼药锅。 */
    public static final int LARGE_OPENING_AREA = 1600;
    /** 原版炼药锅内腔 12×12×12，作为 1 B 的容量基准。 */
    public static final int UNITS_PER_BUCKET = 1728;
    private static final MoldingFaceDirection[] HORIZONTAL = {
        MoldingFaceDirection.NEGATIVE_X,
        MoldingFaceDirection.POSITIVE_X,
        MoldingFaceDirection.NEGATIVE_Z,
        MoldingFaceDirection.POSITIVE_Z
    };

    private MoldingCauldronShapeAnalyzer() {
    }

    public static MoldingCauldronShapeAnalysis analyze(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers
    ) {
        int paddedCellCount = (volume.sizeX() + 2) * (volume.sizeY() + 2) * (volume.sizeZ() + 2);
        BitSet exterior = new BitSet(paddedCellCount);
        MoldingShellAnalyzer.floodExterior(volume, barriers, exterior);
        return analyze(volume, barriers, exterior);
    }

    /** 供 {@link MoldingShellAnalyzer} 复用它已经算好的外界可达集，避免再跑一遍整个 3D 洪泛。 */
    static MoldingCauldronShapeAnalysis analyze(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet exterior
    ) {
        if (volume.isEmpty()) return MoldingCauldronShapeAnalysis.invalid("cauldron_not_sealed");
        int paddedZ = volume.sizeZ() + 2;
        int layerCells = (volume.sizeX() + 2) * paddedZ;
        BitSet outside = new BitSet(layerCells);
        BitSet cavity = new BitSet(volume.cellCount());
        int[] layerStack = new int[layerCells];
        int cavityVolume = 0;
        int minY = Integer.MAX_VALUE;
        int maxY = -1;
        for (int y = 0; y < volume.sizeY(); y++) {
            outside.clear();
            floodLayerExterior(volume, barriers, y, outside, layerStack, paddedZ);
            for (int x = 0; x < volume.sizeX(); x++) {
                for (int z = 0; z < volume.sizeZ(); z++) {
                    if (volume.get(x, y, z) || outside.get((x + 1) * paddedZ + z + 1)) continue;
                    if (!restsOnCavityFloor(volume, barriers, cavity, x, y, z)) continue;
                    cavity.set(volume.indexOf(x, y, z));
                    cavityVolume++;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (cavityVolume == 0) return MoldingCauldronShapeAnalysis.invalid("cauldron_not_sealed");
        int openingArea = openingArea(volume, barriers, exterior, cavity);
        MoldingVolumeMask cavityMask = MoldingVolumeMask.fromLongArray(
            volume.sizeX(),
            volume.sizeY(),
            volume.sizeZ(),
            cavity.toLongArray()
        );
        if (openingArea < MINIMUM_OPENING_AREA) {
            return MoldingCauldronShapeAnalysis.provisional(
                "cauldron_opening_too_small",
                cavityMask,
                cavityVolume,
                openingArea,
                0
            );
        }
        int depth = maxY + 1 - minY;
        if (depth < MINIMUM_DEPTH) {
            return MoldingCauldronShapeAnalysis.provisional(
                "cauldron_too_shallow",
                cavityMask,
                cavityVolume,
                openingArea,
                depth
            );
        }
        return new MoldingCauldronShapeAnalysis(
            true,
            "",
            cavityMask,
            cavityVolume,
            openingArea,
            depth,
            openingArea > LARGE_OPENING_AREA
        );
    }

    /**
     * 强制类型与创造模式预览按外接盒估算最大属性，这里把整个盒子当成内腔。
     *
     * <p>不做阈值判定：调用方本来就绕过了形状校验，这个结果只用于给出容量、内腔掩码和是否升级为大型锅。
     */
    public static MoldingCauldronShapeAnalysis maximumCase(MoldingVolumeMask cavity) {
        if (cavity.isEmpty()) return MoldingCauldronShapeAnalysis.invalid("cauldron_not_sealed");
        BitSet cells = cavity.copyBits();
        BitSet columns = new BitSet(cavity.sizeX() * cavity.sizeZ());
        for (int index = cells.nextSetBit(0); index >= 0; index = cells.nextSetBit(index + 1)) {
            columns.set(cavity.xOf(index) * cavity.sizeZ() + cavity.zOf(index));
        }
        int openingArea = columns.cardinality();
        int minY = cavity.yOf(cells.nextSetBit(0));
        int maxY = cavity.yOf(cells.length() - 1);
        return new MoldingCauldronShapeAnalysis(
            true,
            "",
            cavity,
            cavity.volume(),
            openingArea,
            maxY + 1 - minY,
            openingArea > LARGE_OPENING_AREA
        );
    }

    /** 单层 2D 洪泛：加垫环恒为非实心且互相连通，所以只需从一个角落起洪泛就能标出层内的「层外」区域。 */
    private static void floodLayerExterior(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        int y,
        BitSet outside,
        int[] stack,
        int paddedZ
    ) {
        int top = 0;
        outside.set(0);
        stack[top++] = 0;
        while (top > 0) {
            int cell = stack[--top];
            int x = cell / paddedZ - 1;
            int z = cell % paddedZ - 1;
            for (MoldingFaceDirection direction : HORIZONTAL) {
                if (MoldingShellAnalyzer.blocked(volume, barriers, x, y, z, direction)) continue;
                int nextX = x + direction.stepX();
                int nextZ = z + direction.stepZ();
                if (nextX < -1 || nextX > volume.sizeX() || nextZ < -1 || nextZ > volume.sizeZ()) continue;
                if (volume.get(nextX, y, nextZ)) continue;
                int nextCell = (nextX + 1) * paddedZ + nextZ + 1;
                if (outside.get(nextCell)) continue;
                outside.set(nextCell);
                stack[top++] = nextCell;
            }
        }
    }

    /** 工作区底面不是锅壁，锅底必须由模型自己给出；已是内腔的下方格把腔体向上延续。 */
    private static boolean restsOnCavityFloor(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet cavity,
        int x,
        int y,
        int z
    ) {
        if (MoldingShellAnalyzer.blocked(volume, barriers, x, y, z, MoldingFaceDirection.NEGATIVE_Y)) return true;
        if (y == 0) return false;
        return volume.get(x, y - 1, z) || cavity.get(volume.indexOf(x, y - 1, z));
    }

    /** 按 (x,z) 列去重统计开口：正上方非实心、未被 barrier 封顶、不属内腔且可达外界，才算这一列开着。 */
    private static int openingArea(
        MoldingVolumeMask volume,
        Set<MoldingBarrierFace> barriers,
        BitSet exterior,
        BitSet cavity
    ) {
        BitSet columns = new BitSet(volume.sizeX() * volume.sizeZ());
        for (int index = cavity.nextSetBit(0); index >= 0; index = cavity.nextSetBit(index + 1)) {
            int x = volume.xOf(index);
            int y = volume.yOf(index);
            int z = volume.zOf(index);
            if (MoldingShellAnalyzer.blocked(volume, barriers, x, y, z, MoldingFaceDirection.POSITIVE_Y)) continue;
            if (volume.get(x, y + 1, z)) continue;
            if (volume.containsCoordinate(x, y + 1, z) && cavity.get(volume.indexOf(x, y + 1, z))) continue;
            if (!MoldingShellAnalyzer.isExterior(volume, x, y + 1, z, exterior)) continue;
            columns.set(x * volume.sizeZ() + z);
        }
        return columns.cardinality();
    }
}
