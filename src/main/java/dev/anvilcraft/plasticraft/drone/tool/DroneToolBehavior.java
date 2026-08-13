package dev.anvilcraft.plasticraft.drone.tool;

import dev.anvilcraft.plasticraft.entity.drone.DroneEntity;

/**
 * 服务端任务动作执行器边界。工种差异全部经工具定义的 behavior 进入,
 * 无人机实体类不按工种分支。
 */
public interface DroneToolBehavior {
    DroneToolBehavior NONE = drone -> {
    };

    void serverTick(DroneEntity drone);
}
