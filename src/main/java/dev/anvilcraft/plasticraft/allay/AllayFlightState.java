package dev.anvilcraft.plasticraft.allay;

/** 戴帽悦灵飞行状态机;顺序即同步字节值。无落地态,空闲时原地悬停。 */
public enum AllayFlightState {
    HOVERING,
    FLYING,
    DOCKING;

    public static AllayFlightState byId(int id) {
        AllayFlightState[] values = values();
        return id >= 0 && id < values.length ? values[id] : HOVERING;
    }
}
