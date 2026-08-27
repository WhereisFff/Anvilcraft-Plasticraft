# 悦灵施工系统交接

> 交接时间：2026-08-26。设计规格见 `docs/construction-allay-design.zh_cn.md`（以下简称“设计文档”）。
> 旧无人机文档只保留术语对照，不要再按 `construction-drone-*.zh_cn.md` 的物品、螺旋桨、内部 FE 或硬碰撞实现。
> 接手后续任务前，必须完整阅读设计文档与仓库 `AGENTS.md`，并先写该 TODO 的实施 Plan 再动手。
> TODO 01 至 TODO 14 的代码、自动化验证与玩家游戏内验收已全部完成（TODO 13 于 2026-08-19，TODO 14 于 2026-08-24），设计文档复选框已勾选。TODO 15 的范围与自动化验证已于 2026-08-26 完成，游戏内验收仍待玩家执行，因此设计文档里 TODO 15 的复选框保持未勾选。下一项是 TODO 16，玩家验收 TODO 15 之前不要开始。

## 1. 当前进度

| TODO | 状态 |
| --- | --- |
| 戴帽转换、手持工种、空闲原版游荡、无硬碰撞 | 已实现；手册与 GameTest 已对齐代码 |
| 悦灵休息室 16 记录 / 不耗电 / 破坏放出 | 已实现 |
| TODO 04 原版结构蓝图、世界部署与结构磁盘交互 | 已实现，游戏内验收通过（`feat(blueprint)` 79c6c23） |
| TODO 05 Create 与 Litematica 蓝图导入兼容 | 已实现，游戏内验收通过（`feat(blueprint)` f960eb6） |
| 无休息室建设 / 拆除 / 收集闭环 | 已从无人机改对施工悦灵；未认领时创造模式所有者不扣料 |
| 删除无人机物品、螺旋桨、内部 FE | 已完成；`runData` 后 generated 不应再有 `*_drone` / `propeller` |
| TODO 11 单休息室认领与材料物流 | 已实现，游戏内验收通过（`feat(allay)` e76c194，本轮含验收修正） |
| TODO 12 | 已完成：异步分层寻路、装配并发与防自封 |
| TODO 13 | 已完成：实时所有权、可选 FTB Teams、世界权限等待、团队调度和离队回收 |
| TODO 14 | 已完成并由玩家验收（2026-08-24）：远程休息室转运图（每段 128 格）、ETA 借调决策（与协调室本室出库同刻并行发起，各源室按自己的 20gt 通道每秒一只）、逐跳转发、最后一跳以客工挂靠协调室（不入栈、不占协调室 20gt 通道与托管位）、返程到家、64 参与上限与 20gt 单通道吞吐 |
| TODO 15 | 代码与自动化验证已完成（2026-08-26），等待玩家游戏内验收后再勾选设计文档复选框：望远镜观察悦灵与托管其记录的休息室各持一份九柱完整高度实体刻票、世界级加载索引与重启补发/裁票、跨区块先加新票再撤旧票、按持票人的重叠引用计数、任务级观察租约与前沿收缩、完全无覆盖来源时 `WAITING_OBSERVER`、窗口未刻时闲置工人结队跟随 |

工作流程约定：每个 TODO 完成“范围 + 自动化验证”后按 conventional-commits（中文 subject，scope 用 `allay` 或 `blueprint`）提交一次，然后暂停等待玩家游戏内验收，验收通过才继续下一个；验收通过后勾选设计文档中的复选框。玩家已声明后续会自制全新贴图模型并配套改代码，当前程序生成资产只是可用占位。

### 联调与环境备注

- 本体扫描器已补齐方块实体 NBT 与实体保存，并新增 `StructureDiskData.upsideDown`；Plasticraft 当前依赖远程 AnvilCraft `1.6.0+snapshot.2151` 与 AnvilLib `2.0.0+snapshot.507`
- 本体 HEAD 的炼药锅配方 API 改为 `FluidStackPredicate`/`FluidStack` 转换列表，Plasticraft 已完成迁移（`refactor(recipe)` 672aa44）；等离子喷流虚拟气体独立为 `gas` 字段
- 并发开发注意：两个智能体同时跑 gradle/GameTest 会互抢 `session.lock`、互杀 Java 进程、互相覆盖 datagen 产物与哈希缓存；若再出现 datagen 结果与代码不符，先删 `src/generated/resources/.cache` 强制全量重写
- GameTest 世界级施工 SavedData 会留在 `run/gameTestServer/world/data/`。下次跑全量前若蓝图生命周期测试报“index should contain exactly one job”或“no leftover jobs after reimport”，只删这两份文件再跑，不要删整个 `run/gameTestServer/world`：
  - `anvilcraftplasticraft_construction_jobs.dat`
  - `anvilcraftplasticraft_construction_job_progress.dat`
- GameTestServer 有时跑完会卡在 `Saving worlds` 不退出，背后的 `runGameTestServer` 会长期挂着；判断结果以日志 `= N GAME TESTS COMPLETE` 与 `All N required tests passed` 为准，跑完清理命令行含 `runGameTestServer` 或 `fml.modFolders` 的残留 java 进程

### 已通过的蓝图 / 无休息室施工验收（2026-08-14，载体已改为悦灵）

1. 无休息室建设悦灵从所有者背包取料，在 128 格内把普通方块交付为可碰撞施工投影；创造模式不扣物品；玩家可站在已交付格子上
2. 已交付木头等实心方块看起来就是世界上的不透明方块，不是绿色半透明幽灵；世界格仍是空气，Jade 显示「空气」是预期
3. 菜单停止施工时，已取料的悦灵飞回玩家身边再把物品塞进背包，然后交还给原版游荡；物品不会老远瞬移进包
4. 全部交付并安静提交后，悦灵先飞离蓝图包围盒再交还给原版游荡；所有者在工地外时飞回其身边
5. 中途取消只把已交付格子写成真实方块，未交付保持世界原状；缺料默认暂停，改为跳过则其余继续并残缺提交
6. 实心方块上部署蓝图：拆除悦灵可以进入已清空、未来才建造的蓝图格继续由外向内拆，全部拆完后才建造；掉落、经验与不可破坏判定与铁砧砸切石机一致；拆除不吸物品
7. 基岩格被跳过，其余继续。只有建设能力时，PAUSE 进入等待拆除，SKIP 保留障碍并残缺继续；没有绑定休息室的世界悦灵固定暂停
8. 水池 / 无限水 / 区外流水：真实临时填充严格自下而上排液，最低未完成层会阻塞上层；有限填充 + 1 格壳完成后拆除全部区内填充再建造，不追海洋；壳留到提交后再砸
9. 封堵中途取消：立刻停，已放填充块仍在，未放材料返还，不会无掉落删除或继续拆
10. 拆除不得把该格写成已交付实心投影；未交付格子仍是蓝图目标的半透明预览
11. 磁铁自由拾取：每次吸入后重扫当前 16 格，不飞向更远未扫到的堆；九格只是临时库存，装满后停止吸入并立刻去卸货
12. 空手和磁铁卸货都飞回所有者背包或休息室下方容器；两边都塞不下就扔旁边地上，不能把收集物留在身上
13. 不吸经验球；磁铁无耐久损耗
14. 拆除施工无收集能力：掉落留世界，任务照常建造
15. 有收集能力：只收带任务标记的掉落，不与玩家原有掉落混淆或复制
16. 满载先去卸货腾空，塞不下的扔旁边地上，不阻塞建设/拆除继续；任务收完或腾空离场后先飞离蓝图范围
17. 装满物品的箱子：内容按槽真实扣料，提交后写入对应库存
18. 巨型铁砧等大型多方块只消耗一份核心，其余 part 随核心交付且不重复扣料
19. 已交付栅栏/墙/门按邻接覆盖显示成品连接形状
20. 施工期间机器不运行、红石不亮不走；提交后锁存中继器、比较器朝向、稳定伸出活塞保持蓝图状态
21. 铁砧工艺红石导线按蓝图四向写入本体端口覆盖表；平行线中间保持断开，比较器背面不补臂
22. 源液体按满桶扣除，提交后世界是对应流体，空桶返还；分层锅/储罐按精确 mB 抽取
23. 船、刷怪蛋、含生物的树脂块/高粘块/树脂铁砧、带内容塑料实体按适配器生成；树脂块/高粘块交付后先返还 1–3 个树脂，树脂铁砧整砧消耗
24. 塑料实体施工期只显示投影、提交后才生成硬碰撞；六向都被未交付方块占住时，不写假碰撞的实体目标允许悦灵进入目标格交付

### 已通过的 TODO 11 验收（2026-08-15）

1. 放置休息室即可召回、出库与入库，不接入电网、不耗电，始终使用 `allay_lounge` 外观
2. 已部署磁盘进入休息室槽只认领该室为唯一协调站，槽底保持黄色；空手右击才黄底变绿并启动，同时暂停所有者其他任务。入槽本身不启动、不暂停其他任务
3. 认领后无室建设/拆除/观察退出该任务，已持料的无室建设悦灵先把物品还给所有者；无室收集继续自由拾取，不领中央租约
4. 认领后只从正下方朝上的物品/流体能力取还；创造模式玩家不能绕过普通箱子。下方若是创造板条箱，任意蓝图材料无限供应，不扣箱内过滤物
5. 顶部严格一秒一架：`tryLaunch` 与 GUI 放出共用 20 gt 通道。协调器只在当前阶段、当前层存在可认领操作时放人；最低封堵层占用时不会为受阻上层继续放人，收集先放磁铁
6. 完工、暂停、缺料/缺拆除 PAUSE 后，绑定悦灵还物再回库；完成撤离和卸货后，无租约、无携带物或收集物且当前没有可认领工作的绑定悦灵立即回库，即使任务仍处于可使用该工种的阶段；抽盘或取消把无实体台账插回下方容器，满了扔在室旁
7. 下方容器整体消失进入 `SOURCE_UNAVAILABLE`：相关悦灵立即悬停，保留租约和携带物；容器回来后续做。空箱子是缺料，不是来源不可用
8. 四个水平侧面只画已预约的台账物品，不制造隐藏库存
9. 成对大箱子一次扣两只并两半一起交付；取消时落单 LEFT/RIGHT 收成单箱，不把半个大箱子写进世界。门上半随下半交付
10. 空手可放普通单格和门/床/成对大箱子；巨型铁砧与超过 1 格宽或 2 格高的塑料实体必须蟹钳
11. 已交付 MODEL 格（石头、树叶、玻璃等）出现在蓝图位置，看起来像世界方块，不再消失或漂到原点；世界格仍是空气，Jade 显示空气是预期

### 已通过的 TODO 12 自动化验收（2026-08-16）

1. 异步分层寻路覆盖直线扫掠、16³ 区块段走廊、局部 A*、动态方块重规划、薄墙扫掠、边缘碰撞逃生、分段下行/抬升、待处理搜索 watchdog 与封闭目标强制召回；迎面工人按工作优先级和 UUID 稳定让行，单格通道无避让位时互相穿过
2. `zzz_construction_enclose` 覆盖空心立方体、门形腔体、矿车/石英最后封口、未来格预约、台阶袋形腔与 64 只参与上限；封口互相等待时按 `order/id` 串行，洪泛超预算按不安全处理
3. 普通 PLACE 同物品、同组件每趟最多一组，未来批内操作保持 `CARRIED`；每放一格重选安全目标，批内无安全目标时截断并返还余料。操作索引、状态摘要、材料缓存和租约释放/跨任务返还均有一致性断言
4. 休息室稳定队列、重复托管记录恢复、封闭环境回库强制贴靠、20 gt 共享通道和无工可领立即回库均通过；接近位、撤离段、窄廊及休息室预约没有进入 A* 硬碰撞
5. `BlueprintConstructionGameTests` 覆盖世界可建造范围、任务重叠及无实际进度任务移动后重规划；成对大箱子、门、巨型铁砧、铁轨与漏斗矿车占用等边界也有自动化覆盖
6. `./gradlew runGameTestServer` 全量结果：`467 GAME TESTS COMPLETE IN 55.07 s`，`All 467 required tests passed :)`

### 已通过的 TODO 13 自动化验收（2026-08-17）

1. `ConstructionPermission` 统一实时查询所有者、当前同队关系和世界写入权限；FTB Teams API 只出现在独立可选适配器中，未安装或查询失败时收紧为 owner-only
2. 蓝图导入、部署、快照读取、启动、暂停和取消，以及悦灵交互、休息室菜单、磁盘槽、召回、放出、设置和破坏均在服务端复核当前关系；任务结束会清理所有在线玩家当前物品栏及打开容器中的任务磁盘引用
3. 团队调度优先当前协调休息室任务，再选悦灵所有者自己的无协调任务，最后按距离和任务 UUID 稳定选择队友任务；已被其他任务占用的实体和托管记录不计为可用工人
4. 入队与离队不写入任务或悦灵 NBT；持有租约的队友离队后会释放操作、掉落物和交通预约，并按台账结清在途材料或收集库存
5. 规划、封堵、拆除、投影交付、内容/流体/实体写入、安静提交、边界更新、红石端口恢复和最终壳清理都在写入前复核世界权限；拒绝时进入 `WAITING_PERMISSION`，恢复后按提交日志和原台账续做，SKIP 不能推进
6. 自由收集优先向悦灵所有者卸货；所有者不在当前维度时可向当前在线同队成员卸货，转入玩家背包不被误判为世界写入
7. `compileJava`、`compileGameTestJava`、`runData`、`build` 与 `runGameTestServer` 均通过；最近一次全量报告为 `489` 项通过、`0` 项失败

### 已通过的 TODO 14 自动化验收（2026-08-24）

1. `AllayLoungeNetwork` 路由图按 `ServerLevel` 分桶、弱引用键，自身 `0` 跳、不可达 `-1`、边为 ≤128 格且两端协作；异主休息室既不入路由也不作借调源；破坏与区块卸载退出注册表
2. `ConstructionTransferOptimizer` 判据锁定：在途工人与已到场工人同权计入 `W`，剩余操作为 `0` 不借，`W×transferTicks < C_OP×remainingOps` 才有收益，`transferTicks` 按每跳 40gt 通道开销加沿链累计距离估算
3. 借调与协调室本室出库同刻并行发起：协调室本地仍有匹配托管记录时远端也照借，借调只占源室通道，协调室通道仍空闲可立即本室出库；同一源室第二只被自己的 20gt 通道挡住，不同源室同刻各出一只
4. 逐跳转发按普通入库/出库推进，最后一跳走 `checkInAsGuest` 挂靠：清 `transit_job`、保留 `origin_lounge`、不入栈、协调室通道保持空闲、`inTransitCount` 归零
5. 滞留在协调室托管栈里的外室记录即使任务仍在进行也立刻沿链遣返，到家清 `origin_lounge` 与 `transit_job`；协调室被破坏解除认领后客工立即失去绑定并清在途台账
6. 建造期间的真实方块冲突、`CLEAR_AREA` / `KEEP_BLANK` 动态策略、精确目标材料返还、任务标记掉落与实体投影重置均有回归
7. `runData`、`build` 与 `runGameTestServer` 均通过；最近一次全量报告为 `517 GAME TESTS COMPLETE IN 56.09 s`、`All 517 required tests passed`（含 10 项 `ConstructionTransferGameTests`）

### 已通过的 TODO 15 自动化验收（2026-08-26）

1. 加载单位是完整高度区块柱：`ObservationChunkLoader` 对九柱逐柱调用 `TicketController.forceChunk(..., ticking = true)`（一张票只把自己那一柱抬到实体刻等级，邻柱逐级衰减，所以必须各发一张）。`nineColumnCoverageSpansFullHeight` 断言九柱全部 `isCoverageTicking`、每柱从 `getMinBuildHeight() + 1` 到 `getMaxBuildHeight() - 1` 都 `isWorkable`，第十柱既不进覆盖索引也不可派发
2. 重叠覆盖按持票人引用计数：世界悦灵按 UUID 持票、休息室按方块坐标持票，同一测试断言重叠柱 `refCount == 2`，撤掉其中一份后剩 `1` 且第一只观察悦灵的九柱仍然实体刻；观察悦灵消失后九张票全撤，远处空白柱停止实体刻，不留无主加载区
3. 跨区块先加新票再撤旧票：旧柱进入待撤集，确认新覆盖实体刻后才释放，连续移动不重新计时（`RELEASE_CONFIRM_TICKS = 100` 只防异常滞留）。`borderHandoverNeverStopsTicking` 逐刻断言交界柱从不掉出实体刻
4. 资格只看世界实体或休息室托管记录：`observerEligibilityRules` 覆盖戴帽 + 主手望远镜持票、换成蟹钳/空手当刻撤票、脱帽当刻撤票、休息室只托管非望远镜记录不持票、托管记录换掉望远镜即撤票
5. 休息室与实体的覆盖原子交接：`loungeHandoverKeepsCoverage` 断言出库先由实体持票再退休息室票、入库先接休息室票（含仍在 20gt 通道中的记录）再撤实体票，交接刻两票并存且休息室所在柱始终被覆盖
6. 观察租约按任务与观察者双向索引：`observationLeaseBookkeeping` 断言同一窗口重新指派不留两份租约、被替换的观察者立即解除占用、不同窗口各自登记、任务结束只清自己的租约
7. 数量不足只降并发不失败：`waitingObserverHoldsUntilAnyLoadingSource` 断言完全无覆盖来源时任务停在 `WAITING_OBSERVER`、缺口在 `1..MAX_WINDOWS` 之间，任一加载来源出现即自行恢复且不占租约；`idleWorkersConvoyUntilWindowTicks` 断言窗口尚未实体刻时闲置工人结队跟随该窗口的观察悦灵
8. `launchedObserverWaitsForWindowAssignment` 锁住出库空窗期：规划按 `PLAN_INTERVAL_TICKS = 20` 节流，缺口还在等人接手时刚出库的观察悦灵必须原地待命，需求消失后才返库——出库与入库共用同一条 20 gt 通道，当刻掉头会让任务在出库/入库之间空转
9. `compileGameTestJava`、`build` 与 `runGameTestServer` 均通过；最近一次全量报告为 `536 GAME TESTS COMPLETE`、`All 536 required tests passed`（含 8 项 `ObservationAllayGameTests`）

## 2. 代码地图

### 公共玩法层 `allay/`

| 类 | 职责 |
| --- | --- |
| `AllayWorkRecord` | 世界实体与休息室托管往返的完整数据。字段序冻结：`entity_id, hard_hat, held_tool, owner, shortage_strategy, collection_inventory, assigned_job_id, hosted_carry, custom_name`。工种 ID 不存盘，由主手解析。后续任务字段**只能追加** |
| `AllayShortageStrategy` | 缺料/缺拆除策略 enum（PAUSE/SKIP）。权限拒绝不读取该配置 |
| `AllayClearanceStrategy` | 空白格清场策略 enum（CLEAR_AREA/KEEP_BLANK），设置存于协调室并同步到认领任务进度。未认领或未规划任务在下一次 `plan` 读取当前设置；已规划任务切换按钮立即补/跳过未完成显式空白格 `DEMOLISH`，切回时只恢复仍有真实方块的跳过项，已完成拆除不恢复；未认领协调室按 CLEAR_AREA，`resetPlan` 只在重新部署时触发 |
| `AllayFlightState` | 飞行状态机 enum：`HOVERING, FLYING, DOCKING`；同步字节值按序冻结，只能追加。没有 `LANDED` |
| `AllayFlightPlanner` | 异步分层规划器：主线程 `snapToFree` + 自身 AABB 直线扫掠，受阻后才捕获快照；A* 起步使用悦灵真实位置，保留正确转角并复核简化后的每一段。当前分段飞完前不得重复提交同一全局目标，等待 future 时不重复扫掠或快照。每只悦灵最多一个请求；搜索实时超过 `5 s`、离快照起点超过 `4` 格，或请求满 `40 gt` 后偏离超过 `0.5` 格时取消重算。失败返回空路径并悬停，嵌入投影时仍先 `snapToFree`；召回连续 `3` 次确认不可达后才强制贴靠。规划起点先经 `alignToGrid` 拉回所在格落脚点（该点被占或扫不过去时保持原姿态），亚格漂移不再让每一步扫掠连带把上一格的天花板算进去并整体否决；失效判定仍锚在真实姿态上。同一目标连续规划失败时按失败次数交替使用「分段走廊」与「直取最终目标」裁出的局部目标 |
| `AllayFlightNavigator` | 沿路点移动；每 tick 验证下一步的方块/投影碰撞，连续 `2 gt` 水平碰撞或连续 `20 gt` 无进展才按 `STUCK` 重算，旧碰撞标志只能失效当前路径一次。预测前方 `4 gt` 的悦灵冲突，工作飞行优先于返航飞行，同类再按 UUID 让唯一低优先级者侧移或垂直避让；选定方向后保持 `8 gt`，候选运动同时检查方块、投影和附近全部高优先级悦灵。所有候选都被窄洞几何挡住时继续原运动，让无硬碰撞的施工悦灵互相通过 |
| `allay/path/AllayPathExecutor` / `AllayPathPriority` | 悦灵专用守护线程池与优先级队列；线程数 `clamp(processors/2, 1, 4)`，优先级固定为 `ESCAPE, STUCK, DOCK_HEAD, DELIVER, PICKUP, LEAVE`。取消标志会在碰撞编译和 A* 节点扩展中检查，不与 `plasticraft-adhesive-path-search-*` 混用 |
| `allay/path/AllayPathSnapshot` | 每维度每 tick 最多捕获 `2` 份真实方块与已交付投影快照；工作线程按真实 `VoxelShape` 编译碰撞并跑六向 AABB A*。远距使用 `16³` 区块段走廊，预算 `96` 节点；局部目标限制 `24` 格，通常外扩 `16` 格且预算 `65,536` 节点。空碰撞状态保持可通行，交通预约不进入快照 |
| `ConstructionEnclosure` | 蓝图外部与工地边界按布局版本缓存，租约/交付只清候选腔体而不重建整份目标覆盖；候选腔体按封闭版本与服务器 tick 复用，洪泛只查世界/投影几何，实体在结果阶段统一查询。单次候选洪泛预算 `8192`，超预算按不安全处理。碰撞封口互相成为最后工作时按 `order/id` 只放行唯一领头，其他封口等领头交付后复核。挂在 `chooseApproach` / `tryDeliver` |
| `ConstructionTraffic` | 运行时接近位/撤离段/窄廊/休息室时隙预约，不落盘；接近位、撤离段和休息室只通过 `isReserved` 排斥重复认领最终工位，任何预约都不成为路径碰撞。窄廊只作让行提示且不参与 `isReserved`；不同预约种类分别记持有者，替换一种不能误删同悦灵的另一种预约 |
| `ConstructionWorkerSpace` | `0.35×0.6` 可飞判定，规划器与封闭洪泛共用 |
| `AllayWorkMotions` | `flyTo` / `holdStation` / `releaseToVanilla` / `arrived`。完工用 `leaveSiteThenIdle` 一类路径，不要再写降落 |
| `AllayDefaultHardHat` | 从 jar 资源 `assets/anvilcraftplasticraft/allay/default_hard_hat.json` 加载默认安全帽 |
| `AllayHardHats` | 通用塑料且成型类型为 `allay_hard_hat` |
| `AllayHardHatTraits` | 安全帽材料能力；当前只有 `FIRE_RESISTANT` 判定边界，耐热塑料尚未加入游戏 |
| `allay/tool/AllayToolDefinition(+s)` | 工具定义注册边界。`none` / `construction` / `demolition` / `collection` / `observation`。无能量报价。`CONSTRUCTION` 触及 `4`，其余工作工具 `1`，观察 `0`，收集九格容量未改 |
| `allay/transfer/AllayLoungeNetwork` | 运行时同维度休息室注册表，非持久化，按 `ServerLevel` 分桶且键为弱引用。`routesTo` 从目标做一次反向 Dijkstra（跳数优先、同跳数比沿链累计距离），一次给出全部节点的下一跳/跳数/距离；`nextHop` / `hopCount` 是其薄封装，`hopCount` 起终点相同为 `0`、不可达为 `-1`。边为欧氏距离 ≤128 且两端所有者都与调用者协作；调度调用传 `requireTicking=true`，只落在实体刻活跃节点上。由休息室 `serverTick` 每刻补登记，`onLoad`/`onChunkUnloaded`/`setRemoved` 维护退出 |
| `allay/transfer/ConstructionTransferOptimizer` | 纯函数 ETA：工人数 `W`（已到场 + 在途）为 0 且仍有剩余操作必借，否则只有 `W×transferTicks < C_OP×remainingOps` 才有收益；`transferTicks` 按每跳 40gt 通道开销加沿链累计距离 ÷ 0.25 格/gt 估算 |
| `allay/transfer/ConstructionTransferService` | 借调/转发/返程/在途台账，运行时状态按 `MinecraftServer` 分桶且键为弱引用。`considerBorrow` 与协调室本室出库同刻推进（不再要求协调室本地已无匹配托管记录，也没有全局借调冷却），按一次反向路由列出全部候选源室（协作、出库通道空闲、未协调其他活动任务、托管未借调过的匹配记录），按 `transferTicks` 升序遍历，凡仍满足 ETA 收益判据的源室本轮都各出库一只，一旦某室无收益即 `break`（判据随转运耗时与在途工人数单调变严，更远的室必然也无收益），节流全靠各源室自己的 20gt 出库通道；`advanceTo` 判定这一跳是否就是协调室，是则走 `checkInAsGuest`（只指 `homeLounge`、清 `transit_job`、不入栈、不占协调室通道），否则正常 `startDockingTo`，下一跳此刻拒收就清 `homeLounge` 让世界兜底重试；`tickLoungeTransit` 先做台账维护（到家清两字段、到达协调室清 `transit_job`、任务结束或协调室不可达转返程、原室已消失就地落户，带 `origin` 且不在家的记录一律尽快遣返），再每刻最多转发一只；`isGuest`/`sendGuestHome` 供四个工种行为在返库分支里优先让客工返程；`routeWorldWorkerHome`/`isIdleTransitWorker` 兜底掉队悦灵，沿图挑最少跳数入口（`relayPriority` 让正在协调任务的休息室排在最后），无入口时就近入栈。`inTransitCount` 计入任务 64 参与上限，客工到场即从其中交接出去 |
| `GeneralAllayToolBehavior` | 空手与加强工具共用的调度器：按能力叠加建设、拆除、收集；所有能力都没有可认领工作后，绑定休息室且无租约、携带物、收集物或撤离状态的悦灵立即返室——返室分支先试 `ConstructionTransferService.sendGuestHome`，客工沿链返回原室，只有本室悦灵才走 `startDockingTo`；其余悦灵才把飞行交还给原版大脑。窗口尚未实体刻时按 `ObservationCoverageService.convoyObserver` 结队跟随该窗口的观察悦灵，`FOLLOW_RADIUS = 3.0` 内不再重复下路点 |
| `ConstructionAllayToolBehavior` 等 | 建设 / 拆除 / 收集执行器，同时覆盖无室与休息室认领闭环。三者的“无活可做就返库”分支都先调 `ConstructionTransferService.sendGuestHome`，客工返原室、本室悦灵才入栈。完工 `leaveSiteThenIdle`，不要再写降落 |
| `allay/observation/ObservationOwner` | 持票人 sealed interface：`Observer(dimension, entityId)` 与 `Lounge(dimension, pos)`。区块票按持票人分组，撤其中一张不影响另一张——这就是重叠覆盖的引用计数来源，不要另存计数字段 |
| `allay/observation/ObservationCoverage` | 覆盖几何与资格：`RADIUS = 1`、`COLUMNS = 9`，只枚举 X/Z 九柱，没有任何 Y 向窗口。`isEligible` 三个重载分别判世界悦灵（戴帽 + 主手望远镜 + 实体在该维度）、托管记录、休息室（16 条记录里有望远镜观察悦灵，含仍在入库通道中的那条）。`isTicking` 只问「当前是否有任何来源在保持实体刻」，查询不触发加载 |
| `allay/observation/ObservationLoadingIndex` | 世界级 `SavedData`：持票人 → 覆盖中心/维度，任务 → 观察租约（双向索引）。引用计数由记录集合派生。必须独立于实体存在——重启时先按索引补票再等实体/方块实体加载校验，指望悦灵自己唤醒自己会死锁，它所在区块正是要靠这张票才加载 |
| `allay/observation/ObservationLease` | 一份任务级窗口租约 `(dimension, center, observer)`。租约只表示"这个窗口由谁负责去罩住"，票本身仍由持票人自己维护——租约被撤不等于票当刻消失，撤票走 `ObservationChunkLoader` 的延后释放 |
| `allay/observation/ObservationChunkLoader` | 覆盖 → 九张 `TicketController.forceChunk(..., ticking = true)`。`syncObserver` / `syncLounge` 即时同步（出入库交接用），`syncLoungeThrottled` 按坐标错峰摊进 `VERIFY_INTERVAL_TICKS = 20`；跨区块先加新票、旧柱进待撤集，确认新覆盖实体刻或超 `RELEASE_CONFIRM_TICKS = 100` 才撤，连续移动不重新计时。重启首刻按索引补发，`VERIFY_GRACE_TICKS = 200` 内等实体/方块实体出现，超时即失效撤票；`validateTickets` 把索引之外的残票一律裁掉 |
| `allay/observation/ObservationCoverageService` | 调度与覆盖之间的唯一接口：`isWorkable` / `isCoordinatorTicking` 供派发闸门，`tick` 按 `PLAN_INTERVAL_TICKS = 20`（阶段一变立即重算）重规划并指派观察者，`isWaitingObserver` / `wantedObservers` / `awaitsWindow` / `convoyObserver` 供状态机与工种行为读结论，`releaseJob` 在任务结束/暂停时清租约与规划缓存。单任务窗口上限 `MAX_WINDOWS = 4`，协调室那一柱是硬需求、不占预算 |
| `allay/tool/ObservationAllayToolBehavior` | 望远镜工种行为：有租约就飞到目标柱的任意安全观察位待着，窗口需求还在但租约尚未派到手时原地待命，两者都没有时才按绑定关系回库或交还原版游荡。它不领操作、不取料、不占协调室出库通道以外的任何调度资源 |

### 实体与物品

- `entity/allay/WorkingAllayEntity`：唯一施工悦灵。继承原版 `Allay`；`0.35 × 0.6`；`canBeCollidedWith()==false`；`isPushable()==true`；可拴绳。`convertFrom` / `toWorkRecord` / `applyWorkRecord` / `spawnFromRecord`。`prepareAction` 每 4 gt 且对准后才允许动手。`shortageStrategy()` 只在已加载的 `homeLoungePos` 上读休息室策略，否则固定 `PAUSE`。`HomeLounge` 写在实体 NBT、不进 `AllayWorkRecord`；自动任务出库/转运可写 home，GUI 手动放出会清空 home、origin 和 transit。收集库存：`tryInsertCollection` / `canAcceptCollection` / `isCollectionFull` / `unloadCollectionTo(ConstructionMaterialAccess)`；装入前剥任务标记。无室卸货优先给所有者，所有者不在当前维度时可给在线同队成员；有室走下方容器，塞不下的扔旁边。潜行右击摘帽、换帽和主手交互只允许所有者或当前同队成员，并复核当前位置世界权限。死亡/死亡动画停止任务与回库调度，安全帽、托管物和收集库存受一次性掉落保护
- 没有 `DroneItem`、`DroneAssemblyRecipe`、`DroneData` 或工人创造变体

### 悦灵休息室

- `block/AllayLoungeBlock`：只有 `FACING`；本体始终完整方块碰撞；破坏调用 `releaseAllToWorld()`（会先 `unclaimLounge`）
- `block/entity/AllayLoungeBlockEntity`：16 条托管、1 磁盘槽、不实现 `IPowerConsumer`、顶部 20 gt 单通道入/出库。休息室保存放置者 `Owner`，只有所有者或当前同队成员能打开、召回、放出、改设置、插拔任务磁盘或破坏；无所有者时不隐式认领。召回扫描休息室所有者及当前同队成员在 16 格内的悦灵且不超过剩余容量。手动召回与自动返室都按登记瞬间到中心的距离和 UUID 稳定插入队列，不按实时位置重排；中心只给队首，其余在室顶上方 3 格、半径 3 格的四边环使用 1.2 格间距固定槽位，每层 16 位后向上叠 1 格。到达目标槽位清掉旧飞行任务、路点和过境预约；队首入库不触发其余等待者整体换位。`onDiskChanged` 只尝试 `claimLounge` / `unclaimLounge`，权限暂时失败时保留磁盘并重试，不启动任务。`tryLaunch(Predicate)` 是任务出库；`releaseHosted` 是 GUI 手动放出，二者都占通道；自动任务出库/转运由 `spawnBound` 写 `homeLoungePos`，GUI 放出随后清空 home/origin/transit，且死亡队列会被取消。NBT 既有字段后依次追加 `PickupDisplays`、`Owner`
- `client/renderer/blockentity/AllayLoungeRenderer`：只读已同步的四面预约物，用原版物品渲染器画在水平侧面
- 菜单：`PlasticraftMenuTypes.ALLAY_LOUNGE`；界面 `AllayLoungeScreen` 画 4×4 卡片、召回、暂停/跳过。磁盘槽空手右击走全局黄底变绿启动，与背包槽相同。没有工人单机菜单

### 施工任务 `blueprint/`（投影部署已接到多机施工）

| 类 | 职责 |
| --- | --- |
| `ConstructionJob` | 服务端任务条目。`state` 字节：`0` INACTIVE、`1` ACTIVE（冻结兼容值）、`2` PLANNING … `16` FAILED。`isActive()` = 非 INACTIVE 且非终态。`activate()` 写入 `PLANNING` 而不是 `1`。新状态只能追加 |
| `ConstructionJobIndex` | 主世界 SavedData 任务索引；`activeJobOf` 只返回进行中任务 |
| `ConstructionJobStore` / `ConstructionJobProgress` | 与索引分开的世界级进度库：操作、台账、已交付集合。文件名 `anvilcraftplasticraft_construction_job_progress`。进度 NBT 在既有字段后追加 `Debris`，再追加 `CommitLog`、`CoordinatorLounge`、`DeclaredTargets` 和 `ClearanceStrategy`，不改既有字段序。运行时租约 `entityUUID → allayUUID` 不落盘，实体合并消失则作废重领。操作 ID、世界位置与父子关系有非持久化索引，状态摘要按状态版本惰性重建；加载时必须重建这些索引。领取材料必须按 `allay UUID + operation id` 精确找 `CARRIED`，返还时即使实体任务号已清空也要跨进度库结清该悦灵全部旧 `CARRIED` |
| `ConstructionBuildOp` | 单格规范操作：`PLACE` / `ATTACHED` / `UNSUPPORTED` / `SEAL` / `DEMOLISH` / `CONTENT` / `FLUID` / `ENTITY`（按名存盘）。NBT 在 `Shell` 之后追加 `ParentId` / `BlockEntity` / `Slot` / `Fluid` / `EntityNbt` / `Return`，再追加 `LongReach`、`WorldSatisfied`、`Reactive`。`WorldSatisfied` 表示真实世界已满足放置状态，`Reactive` 表示施工期间追加的拆除；租约字段是 `LeaseAllay`，不要读 `LeaseDrone`。`writesProjection()` 只有未被真实世界满足的 PLACE/ATTACHED 为真 |
| `OrdinaryBlockAdapter` | 普通方块映射：有放置物品则 `PLACE` 扣一份；门上半/床头/活塞头 `ATTACHED` 不重复扣料；成对大箱子 RIGHT 一次扣 2、LEFT 为 `ATTACHED`，落单半箱仍按单箱。`projectionState` / `commitState` 把不成对的 LEFT/RIGHT 收成 `SINGLE`。流体源改由 `FluidBuildAdapter` 规划 |
| `ConstructionMaterialAccess` | 只读休息室正下方一格朝上的 `IItemHandler` / `IFluidHandler`。能力存在但空是缺料；能力整体消失才是来源不可用。下方是创造板条箱则 `infinite`，按 `creativeSupply` 给出蓝图物品且不扣箱 |
| `ConstructionPlacementLimits` | 多方块 `PLACE` 与 `longReach` 的 `ENTITY` 必须蟹钳触及（`>= 4`）。大型塑料 = AABB 宽于 1 或高于 2。门/床/成对大箱子和单格塑料（如硬化树脂锅）空手可放 |
| `ConstructionLedgerEntry` | 托管台账：`CARRIED` / `DELIVERED` / `RETURNED`。执行者字段是 `AllayId`，不要读 `DroneId` |
| `ConstructionLeaseService` | 集中释放操作租约、接近点及按 `allay UUID + operation id` 预约的材料；悦灵尚未真正携带时把预约材料退回休息室、所有者或世界，并清侧面取料展示。实体任务号已先清空时，跨全部任务进度按 UUID 结清旧 `CARRIED`，不能复制或遗失 |
| `ConstructionAssembler` | `peelOrder` 抽出共用：拆除用正向（由外向内，砸开后重算表面），建造顺序仍用逆序。精确外包络只从外扩包围盒的一个外角洪泛；体积超过实际碰撞格数量 `64` 倍且大于 `1,048,576` 格时，把每根竖直柱的连续空气压成区间并合并相邻柱的重叠区间，以精确保留弯折开口和封闭内腔，禁止遍历极稀疏结构的整片空气 |
| `FluidSealPlanner` / `FluidSealFill` | 只在声明格内洪泛可替换纯流体，区外一格壳切断流入，不追海洋。含水固体不替换。填充料先预留 PLACE 数量，再从剩余物品选最多的合法稳定实心块；派发严格按最低未完成 Y 层自下而上进行 |
| `DemolitionPlanner` | 声明格固体 + 封堵位 → `DEMOLISH`；`destroySpeed < 0` 永久障碍跳过对应 PLACE。多方块/门/床/活塞头折到核心。壳 DEMOLISH 标 `shell=true`，拆除阶段不派发。`demolishable` 入参由协调室清场策略给出，`KEEP_BLANK` 时只含蓝图会放置方块的格；封堵位 DEMOLISH 与该策略无关，恒定加入 |
| `StonecutterSmashAdapter` | 对齐普通铁砧砸切石机：`BreakBlockUtil` + `spawnAfterBreak(..., false)` + `IHasMultiBlock.onRemove` + 标记掉落 + 按实际堆叠累加 `Debris` 已生成 + `AnvilUtil.dropItems` + 置空气。禁止 `destroyBlock` / 假玩家。爆炸抗性 ≥ 1200 只伤铁砧，悦灵忽略，黑曜石可拆 |
| `ConstructionDebris` | 数据组件 `construction_debris`（job UUID + op id）。原版合并看组件，任务掉落不与玩家掉落合堆。玩家捡起剥组件并记外部结算；收集吸入也剥组件再入库存 |
| `ConstructionDebrisAccount` | 按拆除 op 对账：已生成 / 已收集 / 外部。盒内标记 + 已收集 + 外部 < 已生成的差额记外部，不复制补发 |
| `ConstructionPermission` | 所有施工权限的唯一公共入口。团队关系每次调用实时查询，不写入 NBT；未安装 FTB Teams 或适配器异常时严格 owner-only。世界权限默认复用原版 `mayInteract` / `mayBuild`，领地集成和 GameTest 通过窄 provider 接入；异常按拒绝处理 |
| `integration/ftbteams/FtbTeamsAdapter` | 仅在 `ModList` 确认 `ftbteams` 已加载后解析嵌套 API 类并调用 `TeamManager.arePlayersInSameTeam`；主模组对 FTB Teams 只 `compileOnly`，不得在公共入口直接引用其 API |
| `ConstructionProjectionIndex` | 已交付假方块的区块段索引。世界格保持空气，不放占位方块实体。同一 tick 的交付与邻接刷新只把 `(job, section)` 标脏，tick 末每段最多发送一次完整快照；客户端已交付视图只在索引变更后重建。`connect()` 对红石导线/二极管/拉杆/按钮/活塞跳过 `updateShape`。`isOccupied` 忽略悦灵、掉落物和经验球 |
| `mixin/ConstructionWorldChangeMixin` | 在服务端 `Level.markAndNotifyBlock` 写入入口观察真实方块变化；只把活动施工任务蓝图显式声明格中的不匹配方块交给 `ConstructionJobController`，不拦截投影、拆除写空气或提交阶段的安静覆盖 |
| `ConstructionEntityProjectionIndex` | 已交付实体投影。不改 `ConstructionProjectionSectionPacket` 字段序，用独立包 `ConstructionEntityProjectionPacket` 同步 |
| `ConstructionOverlayView` | 只读覆盖视图，已交付/规划目标优先于世界，用于栅栏等邻接形状 |
| `BlockEntityContentAdapter` | 方块实体库存按槽拆成 CONTENT；从 capability 抽取库存后递归删除配置 NBT 中的序列化 `SlotItem`，避免 `loadWithComponents` 后再按槽插入时复制内容 |
| `MultiblockBuildAdapter` | AnvilCraft 大型多方块折到核心 PLACE，其余 part 为 ATTACHED；成对大箱子 LEFT 的 `coreOf` 是伙伴格。提交阶段 `restore` 写回 part 状态 |
| `FluidBuildAdapter` | 源液体满桶、分层锅/储罐精确 mB；不改 `OrdinaryBlockAdapter`，含水固体仍是 PLACE 的状态属性 |
| `EntityBuildAdapter` / `EntityBuildAdapters` | 实体适配接口与注册表。瞬态直接跳过。矿车实体等待下方铁轨交付，铁轨交付的占用检测反向忽略既有矿车。注册项在 `init/PlasticraftEntityBuildAdapters` |
| `AnvilCraftRedstoneWirePorts` | 提交后先 `topologyChanged`，再用本体公开 `editConnection` 按蓝图关掉多余端口、补回缺失端口。不要再写红石导线 mixin，也不要把类放进 `dev.dubhe.anvilcraft.block`（JPMS 裂包） |
| `ConstructionCommitLog` | 分区提交相位按方块状态、方块实体、多方块、边界、两次整区粘贴、两轮导线建网/端口恢复、实体与发布顺序持久化。进度 NBT 在 `Debris` 之后追加 `CommitLog` |
| `ConstructionCommitService` | 安静提交：已交付投影静默写入区块段，不跑 `onPlace`/`neighborChanged`。提交日志内各整表阶段都受 `blocksPerTick` 预算约束，`commitState` 复用计划坐标集合，避免大结构退化为平方复杂度；单项方块实体或实体恢复失败记为残缺并继续。每个写入阶段和最终导线端口恢复都复核当前世界权限，拒绝时保留提交游标并回到 `WAITING_PERMISSION`。通用路径禁止假玩家 |
| `ConstructionJobController` | 规划、阶段恢复、封堵/拆除/收集、团队调度和权限等待的中央入口。`jobForWorker` 优先协调休息室任务，再选悦灵所有者自己的无协调任务，最后按距离和 UUID 选同队任务；其他任务已有租约或有效协调绑定的工人不计入可用能力。每个执行 tick 复核团队、协调站和操作位置，失权时释放租约、掉落物及交通预约并结算携带物。规划、分配、交付、提交、最终壳清理和红石邻接写入共享 `ConstructionPermission`；拒绝强制 `WAITING_PERMISSION`，恢复后根据提交日志回原阶段。`fitsWorker` 使用 `0.35 × 0.6`。规划覆盖表按布局版本复用；分配按 `order/id` 遇到首个安全项即返回，但派发给具体悦灵时先在队首同层的前 `24` 个待办候选内按离该悦灵的距离重排，让成环/成排结构转着圈就近施工而不是沿固定扫描方向来回横跳，跨层不重排以保住先下层后上层；同任务、同能力的失败扫描共享 `5 gt` 重试窗口；各能力游标只越过不可逆的完成/无关前缀，仍保留租约和等待项供后续复核，拆除剥离顺序按操作布局缓存。SEAL 只扫描最低未解决 Y 层；DEMOLISH 允许进入已清空内部。每维度每 tick 只捕获一次按所有者分组的已加载工作悦灵快照，按实时团队关系过滤。`extractMaterial` 未认领时从任务所有者取料，认领后走 `extractMaterialFromLounge`。`claimLounge` 只写 `CoordinatorLounge` 并驱逐无室工人，不启动任务。`nextPhase()` 按剩余 SEAL/DEMOLISH/PLACE 回到对应阶段，收集不是必经阶段。`reconcileDebris` 只扫 `worldBox`。`ensureIndex` 只在首次建造或重载恢复已交付格，此后增量维护。缺收集不走 PAUSE/SKIP；工人无能量资格 |
| `ConstructionBlueprintService` | 导入、部署、启动停止和取消的玩家入口都按任务所有者或当前同队成员复核；部署及重摆还逐个校验变换后的方块和实体位置的世界权限。首次部署与重摆都校验完整变换包围盒处于可建造高度/世界边界内，且不与同维度其他任务重叠。活动任务或已有实际进度时禁止移动；无实际进度的暂停/未启动任务移动时清旧方块/实体投影并 `resetPlan()`，保留 `CoordinatorLounge`。取消仍删除任务并按当前权限提交可保留的已交付投影；`cancel` / `complete` 会清协调站及在线玩家打开容器中的 `jobId` |
| `ConstructionWaitReason` | `NONE/MATERIAL/OCCUPIED/WORLD/SOURCE/ENERGY/DEMOLITION/PERMISSION/UNREACHABLE`；`ENERGY` 保留占位，不要删除或重排。`byId` 按序，只能追加 |
| `network/ConstructionProjectionSectionPacket` | 按区块段同步已交付格子；字段顺序冻结，只能追加 |

### 建设、拆除与收集执行器

- `ConstructionAllayToolBehavior`：未认领时从所有者背包取料；认领后只领绑定本室的工人，取料走下方容器。普通 PLACE 材料按物品与组件合批，每趟最多一组；未来格只用 `CARRIED` 台账轻量预留，不提前设 `LEASED`，每交付一格再从本悦灵批次中按现状重跑接近位与防封闭。批内没有安全目标时返还余料并释放预留，保证其他材料或内部格可先派发。方块实体内容、流体、实体与返还物仍单项处理。`SEALING_FLUID` 下按同一取料路径真实 `setBlock` 填充（不是投影），只领取最低未解决 Y 层。`isActiveBuildCarry` 含封堵，取消时在途填充料飞回返还而不是就地落地。无可领操作时分别用 `allSealResolved` / `allPlaceResolved` 判断当前阶段；最后一格封堵后的撤离必须接入回库。非 `BUILDING` / `SEALING_FLUID` 不领 PLACE。`COLLECTING_DEBRIS` 期间不领建造。`claimOp` 再查 `ConstructionPlacementLimits`。`writesProjection()==false` 的 ENTITY/FLUID 允许格内交付，回退点必须落在 `inflate(REACH)` 内。已租建设或封堵操作的安全接近点失效且无替代位时当 tick 释放租约、接近点和交通预约，不向目标上方回退飞行；`WAITING_OCCUPIED` / `WAITING_WORLD` 保持既有阻塞等待。交付/封堵途中完整卡住一轮且既不在触及范围内、也不属于阻塞等待时记一次「确认飞不到」，挑不出可用接近位同样计一次：先释放租约让别的悦灵换方向重试，同一操作累计 `4` 次后走 `ConstructionJobController.deferUnreachable`——按台账退回在途材料、清租约与接近位、退回 `PENDING` 并退避 `UNREACHABLE_RETRY_TICKS` = `200` tick，绝不作废、绝不计入残缺、绝不让任务收敛到完成；重新够得到目标即清零。只释放租约不够——托管台账仍记着在途材料，下一 tick 同一只悦灵会立刻重领同一操作，形成悦灵原地不动、任务永远停在建造阶段的活锁。来源不可用时悬停并保留携带物，不另找活。通用调度器会在所有认领尝试后，把没有任务可领、没有租约、携带物、收集物或撤离状态的绑定工人立即送回休息室，不受当前任务阶段限制
- `DemolitionAllayToolBehavior`：无携带物。认领后只领绑定本室的工人。自领非壳、表面可达的 `DEMOLISH`，128 发现、工具触及，到位砸击；未来 PLACE/SEAL 格不作为接近位禁区，拆除可以进入已清空内部直到全部处理完成。不吸取掉落物。已租接近位失效且找不到替代位时当 tick 释放租约和接近位，不再朝 `op.pos().above(2)` 空飞到 80 gt 卡住检测
- `CollectionAllayToolBehavior`：磁铁（`inventorySize()>0`）按 16 格吸入并装入九格临时库存；空手走近捡 1 个走托管携带物。自由模式每次吸入后重扫当前 16 格任意 `ItemEntity`，不吸经验，不预锁 16 格外目标。任务模式（`DEMOLISHING` / `COLLECTING_DEBRIS`）只领本任务标记掉落；任务已被休息室认领时，无室收集不领中央租约。满载或清场结束后：无室离场再交还给原版游荡，有室卸到下方容器再回库。空手和磁铁都要尽力腾空，两边都塞不下就扔旁边地上，不能留在身上
- 接近点每 tick 用轻量 `isUsableApproach` 刷新几何、预约与触及；完整防自封只在选点、任务拓扑变化及最终交付时复核。建造接近位不能占用已预约的 PLACE/SEAL 格；SEAL 可从目标上方尚未解决的封堵格接近，DEMOLISH 完全忽略未来建造预约。悦灵 AABB 若还插在目标格里，先飞到接近点再执行；非阻塞等待操作一旦没有可用接近点就立即释放，不能持有租约等待卡住检测
- 缺拆除：无本主人、本维度、到最近拆除目标 ≤ 128 的已加载拆除能力时，读休息室策略（无室则 PAUSE）。PAUSE → `WAITING_DEMOLITION`；SKIP → 跳过剩余可拆与仍被占的 PLACE，残缺进入建造。基岩类永久障碍不走该策略
- 缺收集不阻塞：不暂停、不跳过、不加 `WaitReason`；掉落留世界，任务进建造

### 碰撞 Mixin

- `mixin/BlockCollisionsMixin` + `mixin/BlockGetterMixin`：把 `ConstructionProjectionIndex` 的世界 `VoxelShape` 注入方块碰撞。`onlySuffocatingBlocks` 查询跳过假方块，避免把空气格当成窒息方块。不要再为施工投影放占位 BE

### 客户端渲染

- `client/renderer/blueprint/BlueprintProjectionRenderer`：只负责编排场景与非静态内容。`AFTER_BLOCK_ENTITIES` 画已交付方块实体，`AFTER_TRANSLUCENT_BLOCKS` 画已交付流体，`AFTER_ENTITIES` 依次画已交付 MODEL、未交付全息、实体投影和边界。实体投影读 `ConstructionEntityProjectionIndex`，施工期塑料实体只显示、提交后才生成硬碰撞。准备缓存最多 `8` 份，每帧总预算 `8192` 个条目、每场景 `2048` 个；资源重载清空全部投影缓存、网格和离屏目标
- `BlueprintProjectionSectionRenderer`：1.21.1 静态网格后端。方块模型与流体按 `16³` 区块段在工作线程烘焙，并保留每个模型声明的原始 `RenderType`；已交付 MODEL 直接写主目标，全息层写投影目标。经典透明层在相机移动超过 `2` 格后重新排序。硬限制为 `8` 个场景、约 `256 MiB` GPU 网格、`16` 个未完成任务、每帧 `4` 个区段编译、`2` 次上传与 `8 MiB` 上传量
- `BlueprintProjectionTarget1211`：1.21.1 专用离屏目标；每帧复制主目标深度，全息颜色以预乘 Alpha 合成回主目标。未来 26.1.2 只替换该类与分区后端，不把版本专属渲染细节泄漏到玩法层
- `BlueprintProjectionRenderTypes`：包装模型原始材质层，只把全息颜色输出改送投影目标；不替换原 shader、深度状态或材质语义。透明排序由分区后端负责
- `BlueprintRenderView` 与分区内 `NeighborView`：向模型和流体提供目标/已交付邻接状态与真实世界光照，使栅栏、墙、红石粉等按蓝图邻接剔除
- `ClientBlueprintSnapshotCache`：精确校验下载分块；残缺下载 `200 gt` 超时、`20 gt` 重试。单个低优先级守护线程最多排队 `2` 份解码，LRU 最多 `8` 份或估算 `256 MiB`；解析离开客户端线程，完成后在客户端线程原子发布
- `ClientBlueprintJobCache` / `ClientConstructionOverlayLookup`：任务同步是槽底、部署会话与渲染的唯一客户端状态源；任务变换/移除和快照淘汰会使覆盖失效。覆盖 LRU 最多 `8` 份或 `4,194,304` 个方块
- 活动施工不再画青色包围盒，而是由 `ThickLineRenderer` 绘制连续黄黑交替警戒环与向上短距离淡出的施工区；未启动投影仍保留青色边界。`construction_projection.png` 资源继续保留，但已交付方块不叠绿色遮罩
- `WorkingAllayRenderer` / `WorkingAllayModel` / `WorkingAllayHeldItemLayer` / `AllayHardHatLayer` / `AllayHostedCarryLayer`：原版悦灵贴图与光照，再叠加安全帽、主手和托管携带物。蟹钳建设时把托管方块夹在朝下钳口。不要恢复无人机机身或四种机械附件

### 菜单/网络注册

- 菜单：`PlasticraftMenuTypes.ALLAY_LOUNGE`。没有 `working_allay` 工人菜单
- 网络包：`allay_lounge_recall`、`allay_lounge_release`、`allay_lounge_settings`、`allay_lounge_clearance`，以及蓝图投影包 `ConstructionProjectionSectionPacket`、`ConstructionEntityProjectionPacket`，由 AnvilLib `NetworkRegistrar` 按包扫描自动注册。实体投影包字段序冻结，只能追加
- 不要恢复 `allay_settings` / `allay_unequip_hat` 工人包；摘帽走实体交互

## 3. 稳定契约（不得破坏）

- 注册名：实体 `working_allay`；方块 `allay_lounge`；菜单 `allay_lounge`；成型类型 `allay_hard_hat`；组件 `molded_plastic, station_energy, blueprint_task, construction_debris`。不要恢复 `drone` / `drone_station` / `drone_data` / `propeller`
- `AllayWorkRecord` 字段顺序、`AllayFlightState` 字节值、`ConstructionJob.state` 既有字节值（`0`/`1` 冻结，`2`–`16` 已占用，其中 `5/6/7/8` 为封堵/拆除/收集清场/等待拆除）
- NBT：`LeaseAllay`、`AllayId`、`DockingAllay`、`AllayWork`、实体 `HomeLounge`（在 `DockLounge` 之后）。不要读 `LeaseDrone` / `DroneId` / `DroneData`
- `ConstructionProjectionSectionPacket` 与 `ConstructionEntityProjectionPacket` 字段顺序冻结
- `AllayToolDefinitions.CONSTRUCTION` / `DEMOLITION` / `COLLECTION` 只允许换 `behavior`，不要改注册名、触及距离或收集九格容量
- 只追加过：`Kind.SEAL/DEMOLISH/CONTENT/FLUID/ENTITY`、`WaitReason.DEMOLITION/PERMISSION`（`ENERGY` 占位保留）、组件 `construction_debris`、操作 NBT `Shell/ParentId/BlockEntity/Slot/Fluid/EntityNbt/Return/LongReach/WorldSatisfied/Reactive`、进度 NBT `Debris/CommitLog/CoordinatorLounge/DeclaredTargets/ClearanceStrategy`、休息室 NBT `PickupDisplays`。不要改既有字段序或重排 enum
- `AllayWorkRecord` 末尾追加可选字段 `origin_lounge` / `transit_job`（CODEC 与 STREAM_CODEC 同步追加，既有 9 字段顺序不动）；`WorkingAllayEntity` 对应字段经 `AllayWork` 存档往返，`AllayLoungeBlockEntity` 新增 `tryLaunchFor` / `updateHostedRecord`。转运图与在途台账为纯运行时结构，不落盘；`WorkingAllayEntity#remove` 只在 `reason.shouldDestroy()` 且不是入栈托管时才清在途台账（入栈只是转运途中一站）
- 休息室**没有合成配方**：设计文档未定义配方，未擅自发明
- 工人和休息室都不使用 FE，不写内部电量或电容器槽
- 不考虑旧存档兼容，也不添加迁移代码或旧 NBT 读取逻辑

## 4. 资产

休息室方块贴图、GUI 背景和 `pause/skip/return_home` 按钮已在 `textures/`。蓝图按钮已在 `textures/gui/button/blueprint/`。默认安全帽在 `assets/anvilcraftplasticraft/allay/default_hard_hat.json`。

`textures/gui/background/working_allay.png` 已删除。施工悦灵没有设置界面，不要恢复该资源或工人菜单。

玩家重做美术时：替换 PNG 即可；GUI 布局改动需同步 `AllayLoungeScreen` / `AllayLoungeMenu` 中的坐标常量。

## 5. 重要经验（GameTest 与运行时陷阱）

1. `@EmptyTemplate(floor = true)` 的铁块地板占据 helper 相对坐标 **y=1**，可用地面顶面在 **y=2**——实体 spawn、setBlock 都要从 y=2 起算
2. vanilla `GameTestInfo.succeed()` 会丢弃结构 bounds 外扩 1 格内的所有实体；测试断言不要写“实体必须存活”，要断言位置/数据语义，或精确引用自己的实体
3. GameTest 结构间距仅 5 格：128 格发现、召回扫描会波及邻居测试。施工测试只调用窄接口，禁止 `inflate(128)` 扫实体。`level.getAllEntities()` 只允许封装在每维度每 tick 的工作悦灵快照入口，调用方按所有者复用结果，不要在测试或各能力检查里再扫一遍
4. 不要对「起点到终点」做一次巨大 `noCollision(AABB)`：会把地面整段落进检测，也会在 GameTest 里对远距假玩家扫出超大 AABB，并触发 `BondedPlasticShapeIndex` 卡死。寻路必须按自身碰撞箱步进扫掠
5. `findOwner` 先 `PlayerList.getPlayer`，再扫 `level.players()`；不要用 `player.level() != level` 引用比较，GameTest 假玩家会对不上
6. `isOccupied` / `fitsWorker` 必须跳过 `WorkingAllayEntity`、`ItemEntity` 和 `ExperienceOrb`。占用 GameTest 用猪，不用悦灵自己。砸击掉落若算占格，拆除后 PLACE 会永远不可分配，接近点也会被堵死
7. 接近点不能落在仍预约的 PLACE 格上；交付前若悦灵 AABB 还与目标格相交，先飞到接近点
8. A* 失败禁止 `List.of(goal)` 对着墙直线冲。先抬升绕行，再不行返回空路径并悬停/爬升
9. 水平碰撞只失效当前路径一次，残留碰撞标志不得反复取消正在计算的新请求；下一步碰撞与连续 `20 gt` 无进展同样触发重规划。`verticalCollision` 在贴地滑行时很常见，不能当撞墙
10. 瞬时断言与短时序飞行必须分 batch，避免和蓝图索引生命周期测试并行抢世界级 SavedData，也避免 20 gt 窗口打死 400 gt 交付。封堵、拆除、空气建造、收集清场失败条件不同，不要合成一条空气墙或拆除 live 测试
11. 无实体的台账取消仍当场返还（防复制）；有加载悦灵拿着材料时必须飞回再还
12. 全量验证命令：`./gradlew runData`（资产变化时，检查 `src/generated` 差异）→ `./gradlew build` → `./gradlew runGameTestServer`；严禁 UI 自动化操作 Minecraft 客户端。渲染与资源改动只做静态检查，游戏内视觉由玩家验收
13. `ensureIndex` / `tryDeliver` 只能写 PLACE/ATTACHED。DEMOLISH/SEAL 的 `DELIVERED` 若进投影索引，拆除后该格会变成被拆方块的实心外观。`writesProjection()` 是这条边界
14. 拆除 live 测试必须在断言后取消任务，否则 `BUILDING` 残留会污染下次蓝图生命周期测试。缺拆除机会在数 tick 内把任务打进 `WAITING_DEMOLITION`，live 测试应先刷拆除能力再 `start`
15. 拆除必须继续不吸物品。收集收完或满载后必须先离场再交还给原版游荡，不要在工地原地停着
16. 自由拾取测试放 `CollectionAllayGameTests`，不要启动施工任务。任务掉落路径仍进 `ConstructionJobGameTests`。收集 live 不要并进拆除/空气墙 live batch
17. 铁砧工艺红石导线的连接不在 BlockState 里单独持久化，而在 `RedstoneWireConnectionOverrides`。提交后必须走本体 `editConnection` 写入 forced/hidden，再让网络重建；不要 mixin `RedStoneWireBlock.updateShape`，也不要在 `dev.dubhe.anvilcraft.block` 放本模组类（JPMS 裂包会启动失败）
18. 树脂铁砧带 `SAVED_ENTITY` 也是捕获物，但整砧消耗不返还树脂；只有树脂块/高粘块交付后掉 1–3 树脂
19. 已交付塑料实体投影若用硬碰撞，会把玩家卡在工地里。施工期只显示，提交后才生成硬碰撞
20. `docs/construction-anti-enclosure.zh_cn.md` 是 TODO 12 的算法对照笔记，不是实现计划；落地应挂在现有 `ConstructionAssembler` / `chooseApproach` / `tryDeliver` 上，不要另起一套作业补丁
21. 休息室不耗电，不要再写 `IPowerConsumer`、`POWERED`、`allay_lounge_off` 或用电量拒派。旧测试若还接创造发电机，删掉即可
22. 高粘性树脂与树脂铁砧不得捕获 `WorkingAllayEntity`
23. `ConstructionJobGameTests` 部分方法描述仍写 drone，以实体类型 `WorkingAllayEntity` 为准，不要据此恢复无人机
24. 空手通用工不是“只悬停”；未知主手物品也走 `none`
25. 蟹钳触及是 4 格，不要把建设写成一律 1 格，也不要把触及改成让空手能放巨型铁砧
26. 磁盘入槽 ≠ 启动。`claimLounge` 只写协调站；黄底变绿仍走菜单空手右击。入槽不得 `pause` 所有者其他已启动任务
27. 能力存在但空是 `WAITING_MATERIAL`；箱子被挖掉才是 `SOURCE_UNAVAILABLE`。不要把空箱当成来源不可用
28. 投影方块必须在 `BlueprintProjectionSectionRenderer` 中保留模型原始 `RenderType` 并按区块段异步烘焙；不要退回依赖 `ChunkOffset` 的即时地形批次，也不要在多个阶段混用减/不减相机坐标
29. 成对大箱子必须先配对再映射：RIGHT 扣 2、LEFT 为 ATTACHED。`commitState` 漏掉会在取消后留下半宽 LEFT/RIGHT 箱子
30. 休息室物流测试放 `zzz_construction_lounge`，不要并进拆除或收集 live batch
31. 接近位、撤离段和休息室预约只在最终工位筛选时排他，包括它们在内的任何预约都禁止进入 A* 快照硬障碍；窄廊预约连 `fitsWorker` 也不参与。单格洞里避让候选全失败时必须继续原路线，施工悦灵本来就没有硬碰撞
32. `releaseLease` 会先清实体任务号但保留手上材料；返还台账不能只查 `assignedJobId`。取预约材料按操作 ID 精确匹配，回库按悦灵 UUID 跨所有任务进度结清旧记录
33. 光释放租约不能让卡住的悦灵脱困：托管台账仍记着它手上的材料，下一 tick 同一只悦灵会凭 `CARRIED` 立刻重领同一操作。飞不到的目标必须退回材料并压一段退避，让别的位置先建、让别的悦灵重新取料重试；但绝不能按残缺跳过——跳过会满足 `allPlaceResolved` 让任务收敛到完成，在途材料随即被当作无主掉落物扔在地上，玩家看到的就是「活塞被扔了、施工却显示完成」
34. 悦灵被方块包住时寻路救不了它：`plan` 的 `escape` 前缀总会产出非空路径，`repeatedlyUnreachable()` 永远不成立，而碰撞钳制把位移全部吃掉，只会原地窒息。必须由 `serverEmbeddedTick` 在 `aiStep` 服务端分支最前面直接传送脱困，且不能依赖飞行状态——`DOCKING` 下所有施工行为都 early-return
35. 悦灵停靠位常有毫米级亚格漂移，包围盒顶面会探进上一格。扫掠按包围盒并集判定，于是那一格的天花板会让明明只差两格的同层短跳也规划不出路径——规划起点必须先吸附回所在格落脚点，但失效判定要继续锚在真实姿态上，否则静止不动的悦灵会被判成移动过而反复取消搜索
36. 同一目标连续规划失败时不能只是重跑：段级走廊判定比实体尺度粗，选中的门洞可能根本挤不过去，重跑只会得到同一条失败计划。要交替换用另一种局部目标
37. 借调悦灵的最后一跳不能入栈：协调室的 16 个托管位和 20gt 顶部通道一旦被外来悦灵占用，本室悦灵就会被堵在自己的队列里出不去。客工只把 `homeLounge` 指向协调室以取得参与资格与取放点，`origin_lounge` 保留原室，`transit_job` 在到场时清空完成“在途 → 参与者”交接；`inTransitCount` 因此必须归零，否则同一只悦灵会被既算在途又算到场而重复借调
38. 提交阶段的真实写入会被反应式世界观察当成外部干扰：导线拓扑重建与端口编辑属于真实 `setBlock`，若不屏蔽会在提交途中把已交付的导线撤回 `PENDING` 并追加反应式拆除。`ConstructionCommitService.tick` 与 `restoreWirePorts` 必须包在 `withoutWorldChangeObservation` 里
39. 转运图、在途台账这类跨对象运行时状态必须按 `ServerLevel` / `MinecraftServer` 分桶且键用弱引用。进程级静态 Map 会让退出重进后的新存档沿用旧节点与旧计数，GameTest 里表现为跨模板串味
40. 九柱必须逐柱发票。一张 `forceChunk` 只把自己那一柱抬到实体刻等级，邻柱按距离逐级衰减成非实体刻，所以「发一张距离 2 的票罩住 3×3」是错的
41. 加载索引不能依赖持票实体或休息室方块实体存在。重启时先按世界级索引补票、再等实体/方块实体加载后校验资格，指望悦灵自己唤醒自己会死锁——它所在的区块恰恰要靠这张票才加载
42. 覆盖引用计数不要单独存字段。世界悦灵按 UUID 持票、休息室按坐标持票，`TicketController` 天然按持票人分组，重叠柱撤一张不影响另一张；另存计数只会与记录集合分叉
43. 跨区块交接必须「先加新票、旧柱进待撤集、确认新覆盖实体刻后才撤」，且连续移动不重新计时。先撤后加会在交接刻出现空窗，跟随的工人和观察者一起掉出实体刻；每步都重新计时会把 `RELEASE_CONFIRM_TICKS` 的上限拖成无限期扣留
44. `ObservationCoverageService.plan()` 的提前收尾条件必须带上 `useful.size() == leases.size()`。只看「有活可干且窗口预算用尽」就 `break` 会漏掉后续操作的 `markUseful`，紧接着那段撤租约的循环就把正在被工人依赖的窗口当成冗余撤掉，那一柱当刻停刻、里面的悦灵连带冻住
45. 规划按 `PLAN_INTERVAL_TICKS = 20` 节流，因此「已出库但还没拿到租约」是一段必然存在的空窗。这段时间必须由 `awaitsWindow` 让观察悦灵原地待命：出库与入库共用同一条 20 gt 通道，当刻掉头返库会让任务永远在出库/入库之间空转，从此等不到观察者飞到窗口上
46. 协调室那一柱是硬需求，不占 `MAX_WINDOWS` 预算，而且它在非实体刻时方块实体根本不 tick，只能由 `plan()` 代它调 `syncLounge` 重算一次覆盖；少了这一步，托管着有效观察者的协调室会永远等不到唤醒自己的那一刻
47. 观察者的可用性判据比普通工种宽松：不要求绑定协调室、不走出库通道排他。一旦要求绑定，协调室自己那一柱停刻后就再没有任何一只悦灵有资格去覆盖它

## 6. 验收结论与下一项交接

### TODO 13 验收结论（2026-08-19 通过）

- 未安装 FTB Teams 时严格 owner-only；安装后所有者、当前同队成员和陌生玩家对蓝图、悦灵、休息室、磁盘和卸货的权限分别正确
- 入队、离队后下一次派发、取放、卸货和世界写入立即按新关系更新；离队工人的租约、交通预约、携带材料和收集库存被释放或结算
- 受保护区域的规划、封堵、拆除、投影交付、提交和强制覆盖都被拒绝并进入 `WAITING_PERMISSION`；恢复权限后从原台账继续，PAUSE/SKIP 不能绕过
- 同队成员可以共享调度但不能越过任务所有者的世界权限；陌生玩家不能取料、卸货、召回、放出、修改设置、替换磁盘或修改世界

### TODO 14 验收结论（2026-08-24 通过）

玩家在 2026-08-22 的两轮反馈后做过重构，2026-08-24 复验通过。已验收的行为：

- 任务休息室 + 本地/远程休息室（1 跳与多跳各一）：协调室缺某工种且工作量大时远室悦灵逐跳到场施工；本地充足或接近完工时远室不被抽人
- 远室来的悦灵只在协调室下方箱子取放材料，不飞进协调室顶部排队，本室悦灵出库不被卡住
- 四室满载不超过 `64` 只且每室每秒一架；任务完成/取消/抽盘后被借调悦灵沿链返回原室
- 转运途中拆掉中转室，在途悦灵落世界后自行回家或留在可达协作室

需要保留的代码要点（改动这一块前先读完）：

- 转运图与在途台账都是运行时结构，分别按 `ServerLevel` / `MinecraftServer` 分桶且键为弱引用，退出重进不会沿用旧节点与旧计数；跨所有者按 `ConstructionPermission.areCollaborators` 隔离（异主不入路由、不作借调源）；借调从所有“出库通道空闲、未协调其他活动任务”的同主休息室并行发起，与协调室本室出库同刻推进，不要求协调室本地托管已耗尽，且必须 `W×transferTicks < C_OP×remainingOps` 才有收益；64 上限含在途数；吞吐由各室既有顶部 20gt 单通道自然节流，多室因此并行保持每室每秒一只
- **客工模型（第二轮验收反馈的核心重构）**：借调悦灵抵达协调室的最后一跳不入栈——`ConstructionTransferService.checkInAsGuest` 只把 `homeLounge` 指向协调室（据此通过 `isBoundToCoordinator` 取得参与资格，并以协调室下方容器为取放点），同时清掉 `transit_job` 完成“在途 → 参与者”的台账交接，因此 `inTransitCount` 在一跳可达时出库同刻即归零，不会既算在途又算到场而重复借调。`isGuest` 判据是 `origin_lounge != null && !origin_lounge.equals(homeLounge)`。四个工种行为在“无活可做要回库”的分支里统一先调 `sendGuestHome`，客工立刻沿链返程而不是挤进协调室。协调室的 16 个托管位和 20gt 通道因此永远只服务本室悦灵——这正是玩家反馈“别的悦灵进主休息室会卡住主休息室的悦灵”的修复点。中途每一跳仍走正常入库/出库，只有最后一跳变挂靠
- `tickLoungeTransit` 的台账维护：到家清两字段、到达协调室清 `transit_job`、任务结束或协调室不可达转返程、原室已消失就地落户；带 `origin` 且不在家的记录一律尽快转发回家，**不再**因宿主室正在协调任务而留用（客工不入栈，任何滞留别室栈里的外来记录都只是玩家召回等意外路径，留用只会堵住那一室自己的悦灵）
- 验收过程暴露并已修复的问题：入栈托管被当成离开世界而每跳清空在途台账；协调室为空时在途记录永久停在中转室；抽盘/破坏协调室不清台账；本地记录还在时也去抽远端；世界内兜底因布尔取反写反而完全失效；进程级静态状态跨存档串味；每刻 O(N³) BFS；ETA 用直线距离而非沿链距离
- 验收顺带修复的施工竞态：拆除结束进入建造后，服务端真实方块写入不再被忽略。监听只覆盖活动任务的蓝图显式声明格；目标不一致时暂停建设、追加反应式 `DEMOLISH`，由切石机悦灵按既有掉落标记和收集台账回收；`CLEAR_AREA` 处理显式空白格，`KEEP_BLANK` 保留显式 `AIR` 格但仍处理实体目标格；目标状态完全一致的外部方块直接满足放置操作并返还未提交材料。若冲突覆盖了已交付的实体、内容或流体投影，会同步撤销投影、重置子操作并返还台账材料。提交阶段仍按设计允许最终强制覆盖，新方块不会在发布点前插入第二次拆除

### TODO 15 实施结论（代码与自动化验证已完成，等待玩家游戏内验收）

落地形态：`allay/observation` 包六个类 + `ObservationAllayToolBehavior`，全部按 `ChunkPos` 完整高度柱记账，票由 `TicketController.forceChunk(..., ticking = true)` 逐柱申请。

- 加载资格只有两条来源：世界里“仍戴帽 + 主手望远镜 + 在该维度”的悦灵，以及托管着这类有效记录的休息室；`ObservationCoverage.isEligible` 的三个重载是唯一入口，没有 FE、休息室供电或玩家背包分支
- 覆盖中心与租约存在 `ObservationLoadingIndex`（世界级 `SavedData`）里，重启先按索引补发票再等实体/方块实体加载后校验，`ObservationChunkLoader.validateTickets` 裁掉索引里已经没有的票；引用计数由记录派生，索引里没有计数字段
- 跨区块走“先加新票、旧票进 pending、`RELEASE_CONFIRM_TICKS = 100` 后再撤”，重新进入同一柱不重排计时
- 休息室与实体的交接是原子的：入库先由休息室接票再撤实体票，出库先由实体持票再退休息室票，交接刻两票并存
- 窗口不足不失败：`MAX_WINDOWS = 4`、缺口写进 `JobPlan.missing`，闲置工人按 `ObservationCoverageService.convoyObserver` 结队跟随，完全无覆盖来源才写 `WAITING_OBSERVER`；普通工种没有任何自行加载的分支
- 协调室那一柱是硬需求，不占窗口预算，并由 `selfLoadCoordinator` → `syncLounge` 自唤醒，避免非实体刻的休息室永远等不到自己 tick

待玩家游戏内验收（设计文档 TODO 15 复选框在这一步通过前保持未勾选）：

1. 玩家离开后用方块实体计数器和实体行为确认九个完整区块柱持续实体刻，地下与高空同样有效，第十个区块不被该观察者加载
2. 跨区块边界移动以及休息室与实体互相交接时不断刻、不漏票
3. 观察数量不足的远程施工降并发等待，而不会让普通工种自行加载或丢失任务

### TODO 16 交接（下一项：异常恢复、大结构性能与最终回归）

依赖 TODO 11 至 TODO 15，其中 TODO 15 需等玩家验收通过。动手前先写 TODO 16 的实施 Plan。

范围提醒（设计文档 TODO 16）：任务、材料托管、填充、拆除、投影、休息室过渡、观察票和提交日志的重启恢复；超大结构的局部调色板、稀疏位集、可见区域网络调度与压力回归；补齐跨系统 GameTest、Tooltip、手册数值，清除临时开发入口。

现状与既有边界：

- 观察票已有重启补发与裁票路径（`ObservationChunkLoader` 的索引恢复 + `validateTickets`），TODO 16 只需覆盖“重启只恢复一次、不复制票”的回归，不要再写第二套票据存储
- `ObservationLoadingIndex` 的租约是任务级的：任务结束或暂停由 `ObservationCoverageService.releaseJob` 清理，重启恢复任务时不要顺手保留旧租约
- 施工侧的重启恢复已有 `CARRIED` 台账、投影索引与提交日志三条既有路径，回归应锁“恢复一次”而不是新增迁移代码
- 客户端有界缓存与分区网格已在 TODO 13 落地，超大结构优化沿它继续做，不要绕回即时 `ChunkOffset` 地形批次

GameTest 注意：结构间距只有 5 格。九区块覆盖类断言不能靠 `inflate` 扫实体或真实卸载邻居模板，只能查加载索引与票据引用计数这类窄接口；需要真实断言“在刻/不在刻”的场景按 `ObservationAllayGameTests` 的做法挪到 -X/-Z 空白世界互相相距 2048 格以上的锚点。新测试按 `docs/gametest-coverage.zh_cn.md` 的分册规则落位，瞬时断言与短时序飞行分 batch。

## 7. 已知待办与注意点

- 望远镜观察已在 TODO 15 接入：`AllayToolDefinitions.OBSERVATION` 走 `ObservationAllayToolBehavior`，`CHUNK_LOADING` / `GUIDE_FLEET` 两项能力与 `STATE_WAITING_OBSERVER = 4` 都已有真实读写方；游戏内验收仍待玩家执行
- 已交付流体叠在 MODEL 假方块上的深度关系未作为硬验收；可见性优先。若再调叠层，继续沿 `AFTER_TRANSLUCENT_BLOCKS` 与分区网格边界修正，不要绕回即时 `ChunkOffset` 地形批次
- `ConstructionPermission` 已实现实时团队关系和世界权限复核；默认世界 provider 使用原版 `mayInteract` / `mayBuild`，领地模组应通过窄 provider 接入。`WAITING_PERMISSION` 忽略 SKIP；自由收集物可在所有者不在当前维度时卸给当前同队在线成员
- 远程休息室转运与预计完工时间综合优化已在 TODO 14 完成并通过玩家验收，复用了本地顶部 `20 gt` 共享通道、稳定队列和队首独占；最后一跳的客工挂靠不得改回入栈
- 已交付投影的世界格是空气：指向时 Jade 显示空气是当前契约，不是渲染 bug。若后续要让准星识别假方块，应走独立查询，不要改 `Level#getBlockState`
- 施工悦灵本身没有设置界面；缺料/缺拆除只在休息室切换
- 不要恢复无人机物品、装配配方、螺旋桨类型、工人内部 FE、硬碰撞、落地状态或休息室供电

## 8. GameTest 分册

- `AllayGameTests`：戴帽转换、空手通用工、手持加强、原版游荡、推挤、拴绳与无硬碰撞；`headOnWorkersYieldDeterministically` 锁定迎面让行，`opposingWorkersPassSingleCellTunnel` 锁定单格通道穿行
- `AllayLoungeGameTests`：召回节流、稳定环形等待队列、重复托管记录恢复、满员拒绝、破坏放出、封闭环境返室强制贴靠、通道忙时 GUI 放出失败、手动放出解除休息室绑定、死亡动画不回库且安全帽只掉一次、室内暂停/跳过与收集出库优先磁铁。不要再写耗电或断电冻结
- `CollectionAllayGameTests`：九格、16 格重扫、向所有者/当前同队成员或休息室卸货并尽力腾空、短时序吸入。不要启动施工任务
- `BlueprintConstructionGameTests`：完整蓝图越出可建造范围、与另一已部署任务重叠、无实际进度的暂停任务移动后清旧索引并在新锚点重规划
- `ConstructionJobGameTests`：无休息室建设 / 拆除 / 收集闭环；所有者/同队调度、离队租约释放、任务掉落拾取后离队落地、权限等待与恢复、取消结算、多方块拆除逐一复核所有受影响 part、同材一组批次、操作索引与状态缓存一致性、铁轨忽略漏斗矿车、空碰撞状态不制造假障碍、成对大箱子、落单半箱收成单箱、门两半提交、空手跳过巨型铁砧、建设/拆除接近点失效立即释放租约、确认飞不到的目标退回材料并退避重试而不是按残缺跳过、被方块包住的悦灵自行脱困而不是窒息、同层成环按离悦灵的距离就近派发，以及建造期间真实方块冲突、`CLEAR_AREA` / `KEEP_BLANK` 动态策略、方块/实体投影替换后的材料返还和任务标记掉落
- `zzz_construction_enclose`：空心立方体、门形腔体、矿车/石英最后封口、未来格预约、台阶袋形腔、互锁最终封口与 64 只参与上限
- `zzz_construction_flight`：墙体/成型舱绕行、边缘碰撞逃生、待处理搜索 watchdog、分段下行/抬升、预约铁轨隧道、动态重规划、封闭目标强制贴靠、薄墙扫掠和亚格漂移姿态下天花板不再否决同层短跳
- `zzz_construction_lounge`：锁入槽只认领不启动、排除无室工人、下方箱子扣料与返还、创造板条箱无限供料、来源不可用悬停、20 gt 出库、建造阶段磁铁回库、重载后按 `CARRIED` 台账恢复建设绑定、缺失实体材料不凭空重建，以及同阶段全部剩余操作已租时空闲建设悦灵回库。不要再写电量拒派
- `AllayGameTests` / `AllayLoungeGameTests` / `BlueprintConstructionGameTests` / `CollectionAllayGameTests`：覆盖同队交互、离队即时失权、休息室菜单和磁盘槽权限、团队快照读取以及当前同队成员自由收集卸货
- `MoldingProductGameTests` / `PlasticMoldingChamberGameTests`：安全帽占地/高度包络与高精度打印组件
- `ConstructionTransferGameTests`：路由图注册/注销与自身 0 跳、异主隔离、客工不占协调室通道与托管位且本室悦灵仍可立即出库（`zzz_construction_transfer`）、ETA 优化器判定（在途工人与已到场工人同权计入、剩余操作为 0 不借）、借调与协调室本室出库同刻发起并由源室 20 gt 通道节流、逐跳转发、最后一跳挂靠成客工（清 `transit_job` 保留 `origin_lounge`、不入栈、协调室通道保持空闲、在途台账清零）、滞留在协调室栈里的外室记录即使任务活动也立刻沿链遣返到家清两字段、协调室被破坏解除认领后客工立即失去绑定（`zzz_construction_transfer_live` 为短时序飞行）

- `ObservationAllayGameTests`：观测加载的八条契约。`zzz_observation` 为瞬时断言：`observerEligibilityRules`（戴帽 + 主手望远镜的实体与托管这种记录的休息室才持票，换手/脱帽当刻撤票）、`loungeHandoverKeepsCoverage`（出库先由实体持票再退休息室票，入库反向，交接刻两票并存）、`observationLeaseBookkeeping`（任务与观察者双向索引、同一窗口只留一名观察者、任务结束清自己的租约）。`zzz_observation_live` 为实体刻断言：`nineColumnCoverageSpansFullHeight`（九柱完整高度持续实体刻、第十柱不被加载、重叠按持票人计数）、`borderHandoverNeverStopsTicking`（先加新票再延后撤旧票）、`waitingObserverHoldsUntilAnyLoadingSource`（无任何覆盖来源时停在 `WAITING_OBSERVER`、缺口不超过 `MAX_WINDOWS`、任一加载来源出现即恢复且不占租约）、`idleWorkersConvoyUntilWindowTicks`、`launchedObserverWaitsForWindowAssignment`（缺口未指派时刚出库的观察者原地待命，否则与入库共用的 20 gt 通道会让任务空转）。live 断言必须落在 -X/-Z 空白世界相距 2048 格以上的锚点，投放前用原版强制加载票把落脚柱顶成实体刻、建立观察覆盖后立刻撤掉

本轮最近一次全量结果：`536 GAME TESTS COMPLETE`，`All 536 required tests passed`（含 8 项 `ObservationAllayGameTests` 与 10 项 `ConstructionTransferGameTests`）。写不可达退避回归时注意：派发轮空会给该扫描压 5 刻退避，同一刻先断言轮空再断言"退避结束后可重新派发"必然失败，正例要排在轮空断言之前。既有 `quietCommitRestoresRedstoneWithoutBreakingCactus` 已修复：提交阶段的导线拓扑重建与端口编辑是真实写入，会被反应式世界观察当成外部干扰，从而在提交途中把已交付的导线撤回 `PENDING` 并追加反应式拆除；现已把 `ConstructionCommitService.tick` 与 `restoreWirePorts` 包进 `withoutWorldChangeObservation`。
