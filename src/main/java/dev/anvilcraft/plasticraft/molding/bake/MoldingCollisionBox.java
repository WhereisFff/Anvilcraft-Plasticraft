package dev.anvilcraft.plasticraft.molding.bake;

/** 以制造单元计量的合并物理碰撞盒。 */
public record MoldingCollisionBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public MoldingCollisionBox {
        if (minX < 0 || minY < 0 || minZ < 0
            || maxX > MoldingVolumeMask.MAX_SIZE
            || maxY > MoldingVolumeMask.MAX_SIZE
            || maxZ > MoldingVolumeMask.MAX_SIZE
            || minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("Invalid molding collision box");
        }
    }
}
