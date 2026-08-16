# 无人机设计存档（已并入悦灵）

> 无人机施工已整段替换为戴帽原版悦灵。本文不再维护独立规格。
>
> **完整设计规格**：[`docs/construction-allay-design.zh_cn.md`](construction-allay-design.zh_cn.md)
>
> **交接与代码地图**：[`docs/construction-allay-handover.zh_cn.md`](construction-allay-handover.zh_cn.md)
>
> 不要再按无人机物品、装配配方、螺旋桨、工人内部 FE、`0.5³` 硬碰撞或落地状态机实现。文档与代码不一致时以当前代码为准。

旧文档里的施工阶段、蓝图导入、流体封堵、真实拆除、施工投影、安静提交、材料台账和 TODO 11–16 玩法边界都已按当前悦灵实现改写入上述两份文档。实现 TODO 时只读悦灵文档，不必再对照本文。

## 术语对照

| 旧无人机说法 | 现在 |
| --- | --- |
| 无人机 / `DroneEntity` | 施工悦灵 / `WorkingAllayEntity` |
| 四种无人机物品与装配配方 | 没有工人物品；手持 `allay_hard_hat` 右击原版悦灵转换 |
| 螺旋桨 / `propeller` | 悦灵安全帽 / 成型类型 `allay_hard_hat` |
| `DroneToolDefinition` | `AllayToolDefinition`；工种只看主手，不存盘 |
| 空手或未知工具 | 通用工：建设 + 近距收集，不是只悬停 |
| 蟹钳建设触及 1 格 | 蟹钳触及 **4** 格；空手 / 拆除 / 空手收集为 1 格 |
| 无人机站 / `drone_station` | 悦灵休息室 / `allay_lounge` |
| 16 个工人物品槽 + 电容器槽 + 内部 FE | 16 条 `AllayWorkRecord` + 1 磁盘槽；休息室不耗电，无内部电量 |
| 工人 `256 FE/空中 gt` 等能耗与报价 | 工人不使用 FE；`ConstructionWaitReason.ENERGY` 只保留占位 |
| `0.5 × 0.5 × 0.5` 硬碰撞、可站立 | `0.35 × 0.6`，无硬碰撞，玩家不能站上去 |
| `LANDED` / 落地 / 离地 4 格悬停 | 只有 `HOVERING` / `FLYING` / `DOCKING`；空闲复用原版游荡 |
| 单机设置界面、潜行右击改策略 | 施工悦灵没有设置界面；潜行右击摘帽；策略只在休息室 |
| `LeaseDrone` / `DroneId` / `DroneData` | `LeaseAllay` / `AllayId` / `AllayWorkRecord` |
| `fitsDrone` | `fitsWorker`（`0.35 × 0.6`） |
| `leaveSiteThenLand` | `leaveSiteThenIdle` / `releaseToVanilla` |
| 区外壳在拆除阶段拆掉 | 区内填充拆除阶段拆；区外壳 `shell=true` 留到提交后再砸 |

下一任务是 TODO 13。不要顺带做 TODO 14 / 15。
