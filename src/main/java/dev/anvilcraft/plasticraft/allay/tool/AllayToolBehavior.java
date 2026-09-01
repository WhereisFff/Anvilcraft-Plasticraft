package dev.anvilcraft.plasticraft.allay.tool;

import dev.anvilcraft.plasticraft.entity.allay.WorkingAllayEntity;

/**
 * 服务端任务动作执行器边界。工种差异全部经工具定义的 behavior 进入,
 * 悦灵实体类不按工种分支。
 */
public interface AllayToolBehavior {
    AllayToolBehavior NONE = worker -> {
    };

    void serverTick(WorkingAllayEntity worker);
}
