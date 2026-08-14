package dev.anvilcraft.plasticraft.blueprint;

/** 施工任务当前阻塞原因;首次变化时向所有者报告一次,之后只在 GUI 中显示。 */
public enum ConstructionWaitReason {
    NONE,
    MATERIAL,
    OCCUPIED,
    WORLD,
    SOURCE,
    ENERGY,
    DEMOLITION,
    PERMISSION;

    public static ConstructionWaitReason byId(int id) {
        ConstructionWaitReason[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }
}
