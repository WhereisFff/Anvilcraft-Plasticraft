package dev.anvilcraft.plasticraft.drone.tool;

/**
 * 服务端任务动作执行器边界。四类工具的取料、拆除、收集和观察行为
 * 由后续任务系统 TODO 提供实现;TODO 01 只冻结该扩展点,
 * 保证新增工具时不需要修改无人机实体类。
 */
public interface DroneToolBehavior {
    DroneToolBehavior NONE = new DroneToolBehavior() {
    };
}
