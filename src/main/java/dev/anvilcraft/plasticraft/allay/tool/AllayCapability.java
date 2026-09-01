package dev.anvilcraft.plasticraft.allay.tool;

/** 工具定义可以声明的任务能力;调度器按能力而不是按实体类型派发任务。 */
public enum AllayCapability {
    PICK_UP_MATERIAL,
    CARRY_ITEM,
    DELIVER_PROJECTION,
    SEAL_FLUID,
    DEMOLISH,
    COLLECT_ITEMS,
    CHUNK_LOADING,
    GUIDE_FLEET
}
