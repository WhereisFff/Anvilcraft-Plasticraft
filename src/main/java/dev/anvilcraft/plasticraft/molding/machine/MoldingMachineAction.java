package dev.anvilcraft.plasticraft.molding.machine;

/** 成型舱 GUI 发往服务端的稳定机器动作 ID。 */
public enum MoldingMachineAction {
    TOGGLE_LOCK(0),
    SET_MODE(1),
    SET_CLAY_LIMIT(2),
    INTERACT_STAGING_FLUID(3);

    private final int protocolId;

    MoldingMachineAction(int protocolId) {
        this.protocolId = protocolId;
    }

    public int protocolId() {
        return this.protocolId;
    }

    public static MoldingMachineAction fromProtocolId(int id) {
        for (MoldingMachineAction action : values()) {
            if (action.protocolId == id) return action;
        }
        throw new IllegalArgumentException("Unknown molding machine action: " + id);
    }
}
