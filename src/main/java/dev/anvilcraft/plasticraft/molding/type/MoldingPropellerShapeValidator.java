package dev.anvilcraft.plasticraft.molding.type;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;
import dev.anvilcraft.plasticraft.molding.model.MoldingModelBounds;
import dev.anvilcraft.plasticraft.molding.model.MoldingVec3;

import java.util.ArrayDeque;
import java.util.BitSet;

/** 对螺旋桨的中心、尺寸、厚度和分叉间隙执行有界几何扫描。 */
public final class MoldingPropellerShapeValidator {
    private static final int CENTER_MIN = 16;
    private static final int CENTER_MAX = 32;
    private static final int CENTER_AREA = (CENTER_MAX - CENTER_MIN) * (CENTER_MAX - CENTER_MIN);
    private static final int CORE_MIN = 23;
    private static final int CORE_MAX = 25;
    private static final int BRANCH_ROOT_MIN = CORE_MIN - 1;
    private static final int BRANCH_ROOT_MAX = CORE_MAX + 1;
    private static final double MAX_THICKNESS = 2.0D;
    private static final int[][] BRANCH_DIRECTIONS = {
        {-1, 0},
        {1, 0},
        {0, -1},
        {0, 1}
    };
    private static final double EPSILON = 1.0E-7D;

    private MoldingPropellerShapeValidator() {
    }

    public static MoldingTypeValidation validate(EditableMoldingModel model, BakedMoldingModel baked) {
        MoldingModelBounds bounds = MoldingModelBounds.visible(model).orElse(null);
        if (bounds == null) return MoldingTypeValidation.invalid("propeller_empty");
        double width = bounds.sizePixels().x();
        double depth = bounds.sizePixels().z();
        double height = bounds.sizePixels().y();
        if (Math.max(width, depth) < 8.0D - EPSILON) {
            return MoldingTypeValidation.invalid("propeller_too_small");
        }
        if (width > 16.0D + EPSILON || depth > 16.0D + EPSILON
            || bounds.minimum().x() < CENTER_MIN - EPSILON
            || bounds.maximum().x() > CENTER_MAX + EPSILON
            || bounds.minimum().z() < CENTER_MIN - EPSILON
            || bounds.maximum().z() > CENTER_MAX + EPSILON) {
            return MoldingTypeValidation.invalid("propeller_outside_center");
        }
        if (height > MAX_THICKNESS + EPSILON) {
            return MoldingTypeValidation.invalid("propeller_too_thick");
        }

        BitSet footprint = footprint(baked);
        for (int x = CORE_MIN; x < CORE_MAX; x++) {
            for (int z = CORE_MIN; z < CORE_MAX; z++) {
                if (!footprint.get(index(x, z))) {
                    return MoldingTypeValidation.invalid("propeller_center_missing");
                }
            }
        }
        BitSet branches = (BitSet) footprint.clone();
        for (int x = BRANCH_ROOT_MIN; x < BRANCH_ROOT_MAX; x++) {
            for (int z = BRANCH_ROOT_MIN; z < BRANCH_ROOT_MAX; z++) branches.clear(index(x, z));
        }
        int branchCount = countBranches(branches);
        if (branchCount < 2) return MoldingTypeValidation.invalid("propeller_branches_missing");
        if (footprint.cardinality() >= 0.85D * CENTER_AREA) {
            return MoldingTypeValidation.invalid("propeller_branches_touching");
        }
        return MoldingTypeValidation.valid(0);
    }

    private static BitSet footprint(BakedMoldingModel baked) {
        BitSet result = new BitSet(MoldingVolumeMask.SIZE * MoldingVolumeMask.SIZE);
        MoldingVolumeMask volume = baked.volumeMask();
        for (int y = 0; y < volume.sizeY(); y++) {
            for (int x = CENTER_MIN; x < CENTER_MAX; x++) {
                for (int z = CENTER_MIN; z < CENTER_MAX; z++) {
                    if (volume.containsCoordinate(x, y, z) && volume.get(x, y, z)) result.set(index(x, z));
                }
            }
        }
        for (MoldingQuad quad : baked.surfaceMesh()) {
            if (!quad.doubleSided()) continue;
            for (int x = CENTER_MIN; x < CENTER_MAX; x++) {
                for (int z = CENTER_MIN; z < CENTER_MAX; z++) {
                    if (containsProjected(quad, x + 0.5D, z + 0.5D)) result.set(index(x, z));
                }
            }
        }
        return result;
    }

    private static boolean containsProjected(MoldingQuad quad, double x, double z) {
        return containsProjectedTriangle(quad.first(), quad.second(), quad.third(), x, z)
            || containsProjectedTriangle(quad.first(), quad.third(), quad.fourth(), x, z);
    }

    private static boolean containsProjectedTriangle(
        MoldingVec3 first,
        MoldingVec3 second,
        MoldingVec3 third,
        double x,
        double z
    ) {
        if (Math.abs(cross(first, second, third.x(), third.z())) <= EPSILON) return false;
        double firstCross = cross(first, second, x, z);
        double secondCross = cross(second, third, x, z);
        double thirdCross = cross(third, first, x, z);
        boolean hasNegative = firstCross < -EPSILON || secondCross < -EPSILON || thirdCross < -EPSILON;
        boolean hasPositive = firstCross > EPSILON || secondCross > EPSILON || thirdCross > EPSILON;
        return !(hasNegative && hasPositive);
    }

    private static double cross(
        MoldingVec3 first,
        MoldingVec3 second,
        double x,
        double z
    ) {
        return (second.x() - first.x()) * (z - first.z())
            - (second.z() - first.z()) * (x - first.x());
    }

    private static int countBranches(BitSet remaining) {
        int count = 0;
        while (!remaining.isEmpty()) {
            int start = remaining.nextSetBit(0);
            int size = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            remaining.clear(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                int current = queue.removeFirst();
                size++;
                int x = current / MoldingVolumeMask.SIZE;
                int z = current % MoldingVolumeMask.SIZE;
                for (int[] direction : BRANCH_DIRECTIONS) {
                    int nx = x + direction[0];
                    int nz = z + direction[1];
                    if (nx < CENTER_MIN || nx >= CENTER_MAX || nz < CENTER_MIN || nz >= CENTER_MAX) continue;
                    int neighbor = index(nx, nz);
                    if (!remaining.get(neighbor)) continue;
                    remaining.clear(neighbor);
                    queue.addLast(neighbor);
                }
            }
            if (size >= 2) count++;
        }
        return count;
    }

    private static int index(int x, int z) {
        return x * MoldingVolumeMask.SIZE + z;
    }
}
