package dev.anvilcraft.plasticraft.drone.tool;

/** 工具定义可以声明的任务能力;调度器按能力而不是按实体类型派发任务。 */
public enum DroneCapability {
    /** 从玩家或任务站取得建造材料。 */
    PICK_UP_MATERIAL,
    /** 携带一份任务托管物飞行。 */
    CARRY_ITEM,
    /** 交付施工投影。 */
    DELIVER_PROJECTION,
    /** 放置临时流体封堵块。 */
    SEAL_FLUID,
    /** 瞬间真实拆除方块;该能力保持切石机独占。 */
    DEMOLISH,
    /** 收取任务掉落物或自由拾取物品并卸载。 */
    COLLECT_ITEMS,
    /** 加载自身所在区块及水平相邻八个区块。 */
    CHUNK_LOADING,
    /** 在任务中引导机群移动。 */
    GUIDE_FLEET
}
