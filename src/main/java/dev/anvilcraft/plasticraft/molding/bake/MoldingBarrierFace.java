package dev.anvilcraft.plasticraft.molding.bake;

/**
 * 两个相邻制造单元之间被零厚度 cube 阻断的通路。
 * plane 是 0..48 的格面坐标，u/v 是另外两轴的 0..47 单元坐标。
 */
public record MoldingBarrierFace(MoldingFaceDirection.Axis axis, int plane, int u, int v) {
    public MoldingBarrierFace {
        if (plane < 0 || plane > MoldingVolumeMask.SIZE
            || u < 0 || u >= MoldingVolumeMask.SIZE
            || v < 0 || v >= MoldingVolumeMask.SIZE) {
            throw new IllegalArgumentException("Barrier face is outside the molding workspace");
        }
    }

    public static MoldingBarrierFace between(
        int x,
        int y,
        int z,
        MoldingFaceDirection direction
    ) {
        return switch (direction.axis()) {
            case X -> new MoldingBarrierFace(
                MoldingFaceDirection.Axis.X,
                direction.stepX() > 0 ? x + 1 : x,
                y,
                z
            );
            case Y -> new MoldingBarrierFace(
                MoldingFaceDirection.Axis.Y,
                direction.stepY() > 0 ? y + 1 : y,
                x,
                z
            );
            case Z -> new MoldingBarrierFace(
                MoldingFaceDirection.Axis.Z,
                direction.stepZ() > 0 ? z + 1 : z,
                x,
                y
            );
        };
    }
}
