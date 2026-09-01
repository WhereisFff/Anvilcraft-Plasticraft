package dev.anvilcraft.plasticraft.block;

import net.minecraft.util.StringRepresentable;

/** 成型区内的深度、左右和高度索引。 */
public enum MoldingRegionPart implements StringRepresentable {
    D1_RN1_U0(1, -1, 0), D1_R0_U0(1, 0, 0), D1_R1_U0(1, 1, 0),
    D2_RN1_U0(2, -1, 0), D2_R0_U0(2, 0, 0), D2_R1_U0(2, 1, 0),
    D3_RN1_U0(3, -1, 0), D3_R0_U0(3, 0, 0), D3_R1_U0(3, 1, 0),
    D1_RN1_U1(1, -1, 1), D1_R0_U1(1, 0, 1), D1_R1_U1(1, 1, 1),
    D2_RN1_U1(2, -1, 1), D2_R0_U1(2, 0, 1), D2_R1_U1(2, 1, 1),
    D3_RN1_U1(3, -1, 1), D3_R0_U1(3, 0, 1), D3_R1_U1(3, 1, 1),
    D1_RN1_U2(1, -1, 2), D1_R0_U2(1, 0, 2), D1_R1_U2(1, 1, 2),
    D2_RN1_U2(2, -1, 2), D2_R0_U2(2, 0, 2), D2_R1_U2(2, 1, 2),
    D3_RN1_U2(3, -1, 2), D3_R0_U2(3, 0, 2), D3_R1_U2(3, 1, 2);

    private final int depth;
    private final int right;
    private final int up;
    private final String serializedName;

    MoldingRegionPart(int depth, int right, int up) {
        this.depth = depth;
        this.right = right;
        this.up = up;
        this.serializedName = "d" + depth + "_r" + (right < 0 ? "n1" : right) + "_u" + up;
    }

    public int depth() {
        return this.depth;
    }

    public int right() {
        return this.right;
    }

    public int up() {
        return this.up;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
