package dev.anvilcraft.plasticraft.molding.machine;

import dev.anvilcraft.plasticraft.molding.bake.BakedMoldingModel;
import dev.anvilcraft.plasticraft.molding.bake.MoldingModelBaker;
import dev.anvilcraft.plasticraft.molding.bake.MoldingQuad;
import dev.anvilcraft.plasticraft.molding.bake.MoldingVolumeMask;
import dev.anvilcraft.plasticraft.molding.model.EditableMoldingModel;

/** 将烘焙模型转换为打印头逐像素执行的稳定扫描计划。 */
public record MoldingPrintingPlan(MoldingVolumeMask voxelMask, int[] order) {
    private static final double EPSILON = 1.0E-7D;

    public MoldingPrintingPlan {
        voxelMask = voxelMask.copy();
        order = order.clone();
    }

    @Override
    public MoldingVolumeMask voxelMask() {
        return this.voxelMask.copy();
    }

    @Override
    public int[] order() {
        return this.order.clone();
    }

    public MoldingVolumeMask prefix(int count) {
        MoldingVolumeMask result = new MoldingVolumeMask(
            this.voxelMask.sizeX(),
            this.voxelMask.sizeY(),
            this.voxelMask.sizeZ()
        );
        int limit = Math.clamp(count, 0, this.order.length);
        for (int index = 0; index < limit; index++) {
            int cell = this.order[index];
            result.set(this.voxelMask.xOf(cell), this.voxelMask.yOf(cell), this.voxelMask.zOf(cell));
        }
        return result;
    }

    public int size() {
        return this.order.length;
    }

    public int cellAt(int index) {
        return this.order[index];
    }

    public MoldingPrintingVoxel voxelAt(int index) {
        int cell = this.order[index];
        return new MoldingPrintingVoxel(
            this.voxelMask.xOf(cell),
            this.voxelMask.yOf(cell),
            this.voxelMask.zOf(cell)
        );
    }

    public static MoldingPrintingPlan create(EditableMoldingModel model, BakedMoldingModel baked) {
        MoldingVolumeMask mask = MoldingModelBaker.createIntersectingVolumeMask(model, baked.volumeMask());
        for (MoldingQuad quad : baked.surfaceMesh()) {
            if (quad.doubleSided()) rasterizeSurface(mask, quad);
        }
        int[] order = new int[mask.volume()];
        int orderIndex = 0;
        // 每层沿 X 换行，Z 轴交替往返，避免打印头在行尾空程复位。
        for (int y = 0; y < mask.sizeY(); y++) {
            for (int x = 0; x < mask.sizeX(); x++) {
                boolean forward = (x & 1) == 0;
                for (int offset = 0; offset < mask.sizeZ(); offset++) {
                    int z = forward ? offset : mask.sizeZ() - 1 - offset;
                    if (mask.get(x, y, z)) order[orderIndex++] = mask.indexOf(x, y, z);
                }
            }
        }
        if (orderIndex != order.length) {
            throw new IllegalStateException("Molding printing plan volume changed while scanning");
        }
        return new MoldingPrintingPlan(mask, order);
    }

    private static void rasterizeSurface(MoldingVolumeMask mask, MoldingQuad quad) {
        double minX = Math.min(Math.min(quad.first().x(), quad.second().x()),
            Math.min(quad.third().x(), quad.fourth().x()));
        double maxX = Math.max(Math.max(quad.first().x(), quad.second().x()),
            Math.max(quad.third().x(), quad.fourth().x()));
        double minY = Math.min(Math.min(quad.first().y(), quad.second().y()),
            Math.min(quad.third().y(), quad.fourth().y()));
        double maxY = Math.max(Math.max(quad.first().y(), quad.second().y()),
            Math.max(quad.third().y(), quad.fourth().y()));
        double minZ = Math.min(Math.min(quad.first().z(), quad.second().z()),
            Math.min(quad.third().z(), quad.fourth().z()));
        double maxZ = Math.max(Math.max(quad.first().z(), quad.second().z()),
            Math.max(quad.third().z(), quad.fourth().z()));
        int fromX = Math.clamp((int) Math.floor(minX - 0.5D - EPSILON), 0, mask.sizeX() - 1);
        int toX = Math.clamp((int) Math.ceil(maxX + 0.5D + EPSILON), 0, mask.sizeX());
        int fromY = Math.clamp((int) Math.floor(minY - 0.5D - EPSILON), 0, mask.sizeY() - 1);
        int toY = Math.clamp((int) Math.ceil(maxY + 0.5D + EPSILON), 0, mask.sizeY());
        int fromZ = Math.clamp((int) Math.floor(minZ - 0.5D - EPSILON), 0, mask.sizeZ() - 1);
        int toZ = Math.clamp((int) Math.ceil(maxZ + 0.5D + EPSILON), 0, mask.sizeZ());
        for (int y = fromY; y < toY; y++) {
            for (int x = fromX; x < toX; x++) {
                for (int z = fromZ; z < toZ; z++) {
                    if (quadBoundsIntersectCell(
                        minX,
                        maxX,
                        minY,
                        maxY,
                        minZ,
                        maxZ,
                        x,
                        y,
                        z
                    )) {
                        mask.set(x, y, z);
                    }
                }
            }
        }
    }

    private static boolean quadBoundsIntersectCell(
        double minX,
        double maxX,
        double minY,
        double maxY,
        double minZ,
        double maxZ,
        int x,
        int y,
        int z
    ) {
        return maxX >= x - EPSILON && minX <= x + 1.0D + EPSILON
            && maxY >= y - EPSILON && minY <= y + 1.0D + EPSILON
            && maxZ >= z - EPSILON && minZ <= z + 1.0D + EPSILON;
    }
}
