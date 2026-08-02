package dev.anvilcraft.plasticraft.molding.blueprint;

/** 字段顺序稳定的共享蓝图库操作协议枚举。 */
public enum MoldingBlueprintLibraryAction {
    PIN(0),
    COPY(1),
    OPEN(2),
    DELETE(3),
    REFRESH(4);

    private final int protocolId;

    MoldingBlueprintLibraryAction(int protocolId) {
        this.protocolId = protocolId;
    }

    public int protocolId() {
        return this.protocolId;
    }

    public static MoldingBlueprintLibraryAction fromProtocolId(int id) {
        for (MoldingBlueprintLibraryAction action : values()) {
            if (action.protocolId == id) return action;
        }
        throw new IllegalArgumentException("Unknown blueprint library action " + id);
    }
}
