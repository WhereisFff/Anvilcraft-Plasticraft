package dev.anvilcraft.plasticraft.molding.machine;

import net.minecraft.util.StringRepresentable;

/** 由顶部打印组件结构自动决定的成型方式，与三种生产触发方式相互独立。 */
public enum MoldingFormingMode implements StringRepresentable {
    CASTING("casting"),
    PRINTING("printing");

    private final String serializedName;

    MoldingFormingMode(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public static MoldingFormingMode fromSerializedName(String name) {
        for (MoldingFormingMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return CASTING;
    }
}
