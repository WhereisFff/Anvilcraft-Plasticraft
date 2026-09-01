package dev.anvilcraft.plasticraft.molding.blueprint;

/** 字段顺序稳定的磁盘操作协议枚举。 */
public enum MoldingBlueprintDiskAction {
    LOAD(0),
    STORE(1),
    WRITE_SHARED(2);

    private final int protocolId;

    MoldingBlueprintDiskAction(int protocolId) {
        this.protocolId = protocolId;
    }

    public int protocolId() {
        return this.protocolId;
    }

    public static MoldingBlueprintDiskAction fromProtocolId(int id) {
        for (MoldingBlueprintDiskAction action : values()) {
            if (action.protocolId == id) return action;
        }
        throw new IllegalArgumentException("Unknown blueprint disk action " + id);
    }
}
