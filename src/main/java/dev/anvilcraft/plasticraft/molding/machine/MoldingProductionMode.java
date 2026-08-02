package dev.anvilcraft.plasticraft.molding.machine;

import net.minecraft.util.StringRepresentable;

public enum MoldingProductionMode implements StringRepresentable {
    CONTINUOUS("continuous", 0),
    REDSTONE("redstone", 1),
    SINGLE("single", 2);

    private final String serializedName;
    private final int protocolId;

    MoldingProductionMode(String serializedName, int protocolId) {
        this.serializedName = serializedName;
        this.protocolId = protocolId;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public int protocolId() {
        return this.protocolId;
    }

    public static MoldingProductionMode fromSerializedName(String name) {
        for (MoldingProductionMode mode : values()) {
            if (mode.serializedName.equals(name)) return mode;
        }
        return REDSTONE;
    }

    public static MoldingProductionMode fromProtocolId(int id) {
        for (MoldingProductionMode mode : values()) {
            if (mode.protocolId == id) return mode;
        }
        throw new IllegalArgumentException("Unknown molding production mode: " + id);
    }
}
