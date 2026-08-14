# 悦灵施工系统交接

> 交接时间：2026-08-15。设计规格见 `docs/construction-allay-design.zh_cn.md`（以下简称“设计文档”）。
> 旧无人机文档只保留术语对照，不要再按 `construction-drone-*.zh_cn.md` 的物品、螺旋桨、内部 FE 或硬碰撞实现。
> 接手后续任务前，必须完整阅读设计文档与仓库 `AGENTS.md`，并先写该 TODO 的实施 Plan 再动手。
> TODO 01 至 TODO 10 对应的无人机交付物已由悦灵替换覆盖，且玩家已完成游戏内验收。下一任务是 TODO 11。不要顺带做 TODO 12 / 13 / 15。

## 1. 当前进度

| TODO | 状态 |
| --- | --- |
| 戴帽转换、手持工种、空闲原版游荡、无硬碰撞 | 已实现；手册与 GameTest 已对齐代码 |
| 悦灵休息室 16 记录 / 固定 16 功率 / 破坏放出 | 已实现 |
| TODO 04 原版结构蓝图、世界部署与结构磁盘交互 | 已实现，游戏内验收通过（`feat(blueprint)` 79c6c23） |
| TODO 05 Create 与 Litematica 蓝图导入兼容 | 已实现，游戏内验收通过（`feat(blueprint)` f960eb6） |
| 无休息室建设 / 拆除 / 收集闭环 | 已从无人机改对施工悦灵；创造模式所有者不扣料 |
| 删除无人机物品、螺旋桨、内部 FE | 已完成；`runData` 后 generated 不应再有 `*_drone` / `propeller` |
| TODO 11+ | 未开始；下一任务是 TODO 11。不要做 TODO 12 / 13 / 15 |

工作流程约定：每个 TODO 完成“范围 + 自动化验证”后按 conventional-commits（中文 subject，scope 用 `allay` 或 `blueprint`）提交一次，然后暂停等待玩家游戏内验收，验收通过才继续下一个；验收通过后勾选设计文档中的复选框。玩家已声明后续会自制全新贴图模型并配套改代码，当前程序生成资产只是可用占位。

### 联调与环境备注

- 本体扫描器已补齐方块实体 NBT 与实体保存，并新增 `StructureDiskData.upsideDown`；Plasticraft 当前依赖远程 AnvilCraft `1.6.0+snapshot.2151` 与 AnvilLib `2.0.0+snapshot.507`
- 本体 HEAD 的炼药锅配方 API 改为 `FluidStackPredicate`/`FluidStack` 转换列表，Plasticraft 已完成迁移（`refactor(recipe)` 672aa44）；等离子喷流虚拟气体独立为 `gas` 字段
- 并发开发注意：两个智能体同时跑 gradle/GameTest 会互抢 `session.lock`、互杀 Java 进程、互相覆盖 datagen 产物与哈希缓存；若再出现 datagen 结果与代码不符，先删 `src/generated/resources/.cache` 强制全量重写
- GameTest 世界级施工 SavedData 会留在 `run/gameTestServer/world/data/`。下次跑全量前若蓝图生命周期测试报“index should contain exactly one job”或“no leftover jobs after reimport”，只删这两份文件再跑，不要删整个 `run/gameTestServer/world`：
  - `anvilcraftplasticraft_construction_jobs.dat`
  - `anvilcraftplasticraft_construction_job_progress.dat`

### 已通过的蓝图 / 无休息室施工验收（2026-08-14，载体已改为悦灵）

1. 无休息室建设悦灵从所有者背包取料，在 128 格内把普通方块交付为可碰撞施工投影；创造模式不扣物品；玩家可站在已交付格子上
2. 已交付木头等实心方块看起来就是世界上的不透明方块，不是绿色半透明幽灵；世界格仍是空气，Jade 显示「空气」是预期
3. 菜单停止施工时，已取料的悦灵飞回玩家身边再把物品塞进背包，然后交还给原版游荡；物品不会老远瞬移进包
4. 全部交付并安静提交后，悦灵先飞离蓝图包围盒再交还给原版游荡；所有者在工地外时飞回其身边
5. 中途取消只把已交付格子写成真实方块，未交付保持世界原状；缺料默认暂停，改为跳过则其余继续并残缺提交
6. 实心方块上部署蓝图：先拆后建；掉落、经验与不可破坏判定与铁砧砸切石机一致；拆除不吸物品
7. 基岩格被跳过，其余继续。只有建设能力时，PAUSE 进入等待拆除，SKIP 保留障碍并残缺继续；没有绑定休息室的世界悦灵固定暂停
8. 水池 / 无限水 / 区外流水：有限填充 + 1 格壳后拆除区内填充再建造，不追海洋；壳留到提交后再砸
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

### 休息室待玩家游戏内验收

1. 放置休息室，断电时显示 `allay_lounge_off`；接入电网后切换有电外观。没有内部 FE，拆下再放回不保留电量
2. 休息室始终请求 16 功率；创造发电机接上后 `POWERED==true`
3. 在室旁摆多只戴帽悦灵，点界面召回：依次飞向室顶，严格一秒一架入库；队首之外在室顶上方 3 格列 1 格间距 4×4 方阵等待
4. 满员后拒绝覆盖；破坏休息室把托管和入库中的悦灵生成回世界，并掉出磁盘
5. 入库进行中切断电力：模型变无电、进度冻结、数据不丢；恢复后从原进度继续
6. 休息室界面切换暂停/跳过，室内放出的悦灵都跟这份设置；世界悦灵没有设置界面

## 2. 代码地图

### 公共玩法层 `allay/`

| 类 | 职责 |
| --- | --- |
| `AllayWorkRecord` | 世界实体与休息室托管往返的完整数据。字段序冻结：`entity_id, hard_hat, held_tool, owner, shortage_strategy, collection_inventory, assigned_job_id, hosted_carry, custom_name`。工种 ID 不存盘，由主手解析。后续任务字段**只能追加** |
| `AllayShortageStrategy` | 缺料/缺拆除策略 enum（PAUSE/SKIP）。权限拒绝不读取该配置 |
| `AllayFlightState` | 飞行状态机 enum：`HOVERING, FLYING, DOCKING`；同步字节值按序冻结，只能追加。没有 `LANDED` |
| `AllayFlightPlanner` | 单机三维寻路：自身碰撞箱直线扫掠，受阻后窗口 12 / 预算 4096 的 A*；失败则抬升绕行。目标或自身嵌进已交付假方块时先 `snapToFree`。不要复用树脂牵引规划器，也不做多机预约 |
| `AllayFlightNavigator` | 沿路点移动；到达后悬停等执行器 |
| `AllayWorkMotions` | `flyTo` / `holdStation` / `releaseToVanilla` / `arrived`。完工用 `leaveSiteThenIdle` 一类路径，不要再写降落 |
| `AllayDefaultHardHat` | 从 jar 资源 `assets/anvilcraftplasticraft/allay/default_hard_hat.json` 加载默认安全帽 |
| `AllayHardHats` | 通用塑料且成型类型为 `allay_hard_hat` |
| `AllayHardHatTraits` | 安全帽材料能力；当前只有 `FIRE_RESISTANT` 判定边界，耐热塑料尚未加入游戏 |
| `allay/tool/AllayToolDefinition(+s)` | 工具定义注册边界。`none` / `construction` / `demolition` / `collection` / `observation`。无能量报价。`CONSTRUCTION` 触及 `4`，其余工作工具 `1`，观察 `0`，收集九格容量未改 |
| `GeneralAllayToolBehavior` | 空手与加强工具共用的调度器：按能力叠加建设、拆除、收集；没有任务时把飞行交还给原版悦灵大脑 |
| `ConstructionAllayToolBehavior` 等 | 无休息室建设 / 拆除 / 收集执行器。完工 `leaveSiteThenIdle`，不要再写降落 |

### 实体与物品

- `entity/allay/WorkingAllayEntity`：唯一施工悦灵。继承原版 `Allay`；`0.35 × 0.6`；`canBeCollidedWith()==false`；`isPushable()==true`；可拴绳。`convertFrom` / `toWorkRecord` / `applyWorkRecord` / `spawnFromRecord`。`prepareAction` 每 4 gt 且对准后才允许动手。`shortageStrategy()` 只在已加载的 `homeLoungePos` 上读休息室策略，否则固定 `PAUSE`。收集库存：`tryInsertCollection` / `canAcceptCollection` / `isCollectionFull` / `unloadCollectionTo`；装入前剥任务标记，卸货用 `Inventory.add`。潜行右击摘帽，不是打开设置。死亡掉落安全帽、托管物和收集库存
- 没有 `DroneItem`、`DroneAssemblyRecipe`、`DroneData` 或工人创造变体

### 悦灵休息室

- `block/AllayLoungeBlock`：`FACING` + `POWERED`；本体始终完整方块碰撞；破坏调用 `releaseAllToWorld()`
- `block/entity/AllayLoungeBlockEntity`：16 条托管、1 磁盘槽、固定 `getInputPower()==16`、`isPowered()` 看电网是否工作、`20 gt` 单通道入库、召回扫描 16 格、室顶上方 3 格 `4×4` 方阵。NBT：`Hosted`、`DockingAllay`、`DockingProgress`、`DockingRunning`、`ShortageStrategy`。**出库接口尚未实现，留给任务协调器（TODO 11）**；下方容器取料留给 TODO 11
- 菜单：`PlasticraftMenuTypes.ALLAY_LOUNGE`；界面 `AllayLoungeScreen` 画 4×4 卡片、召回、暂停/跳过。没有工人单机菜单

### 施工任务 `blueprint/`（投影部署已接到单机施工）

| 类 | 职责 |
| --- | --- |
| `ConstructionJob` | 服务端任务条目。`state` 字节：`0` INACTIVE、`1` ACTIVE（冻结兼容值）、`2` PLANNING … `16` FAILED。`isActive()` = 非 INACTIVE 且非终态。`activate()` 写入 `PLANNING` 而不是 `1`。新状态只能追加 |
| `ConstructionJobIndex` | 主世界 SavedData 任务索引；`activeJobOf` 只返回进行中任务 |
| `ConstructionJobStore` / `ConstructionJobProgress` | 与索引分开的世界级进度库：操作、台账、已交付集合。文件名 `anvilcraftplasticraft_construction_job_progress`。进度 NBT 在既有字段后追加 `Debris`，再追加 `CommitLog`，不改既有字段序。运行时租约 `entityUUID → allayUUID` 不落盘，实体合并消失则作废重领 |
| `ConstructionBuildOp` | 单格规范操作：`PLACE` / `ATTACHED` / `UNSUPPORTED` / `SEAL` / `DEMOLISH` / `CONTENT` / `FLUID` / `ENTITY`（按名存盘）。NBT 在 `Shell` 之后追加 `ParentId` / `BlockEntity` / `Slot` / `Fluid` / `EntityNbt` / `Return`。租约字段是 `LeaseAllay`，不要读 `LeaseDrone`。`writesProjection()` 只有 PLACE/ATTACHED 为真 |
| `OrdinaryBlockAdapter` | 普通方块映射：有放置物品则 `PLACE` 扣一份；门上半/床头/活塞头 `ATTACHED` 不重复扣料；流体源改由 `FluidBuildAdapter` 规划 |
| `ConstructionLedgerEntry` | 托管台账：`CARRIED` / `DELIVERED` / `RETURNED`。执行者字段是 `AllayId`，不要读 `DroneId` |
| `ConstructionAssembler` | `peelOrder` 抽出共用：拆除用正向（由外向内，砸开后重算表面），建造顺序仍用逆序 |
| `FluidSealPlanner` / `FluidSealFill` | 只在声明格内洪泛可替换纯流体，区外一格壳切断流入，不追海洋。含水固体不替换。填充料先预留 PLACE 数量，再从剩余物品选最多的合法稳定实心块 |
| `DemolitionPlanner` | 声明格固体 + 封堵位 → `DEMOLISH`；`destroySpeed < 0` 永久障碍跳过对应 PLACE。多方块/门/床/活塞头折到核心。壳 DEMOLISH 标 `shell=true`，拆除阶段不派发 |
| `StonecutterSmashAdapter` | 对齐普通铁砧砸切石机：`BreakBlockUtil` + `spawnAfterBreak(..., false)` + `IHasMultiBlock.onRemove` + 标记掉落 + 按实际堆叠累加 `Debris` 已生成 + `AnvilUtil.dropItems` + 置空气。禁止 `destroyBlock` / 假玩家。爆炸抗性 ≥ 1200 只伤铁砧，悦灵忽略，黑曜石可拆 |
| `ConstructionDebris` | 数据组件 `construction_debris`（job UUID + op id）。原版合并看组件，任务掉落不与玩家掉落合堆。玩家捡起剥组件并记外部结算；收集吸入也剥组件再入库存 |
| `ConstructionDebrisAccount` | 按拆除 op 对账：已生成 / 已收集 / 外部。盒内标记 + 已收集 + 外部 < 已生成的差额记外部，不复制补发 |
| `ConstructionPermission` | 窄接口，本项恒为允许。拒绝时必须 `WAITING_PERMISSION` 且忽略 SKIP。领地/FTB 留给 TODO 13 |
| `ConstructionProjectionIndex` | 已交付假方块的区块段索引。世界格保持空气，不放占位方块实体。`connect()` 对红石导线/二极管/拉杆/按钮/活塞跳过 `updateShape`。`isOccupied` 忽略悦灵、掉落物和经验球 |
| `ConstructionEntityProjectionIndex` | 已交付实体投影。不改 `ConstructionProjectionSectionPacket` 字段序，用独立包 `ConstructionEntityProjectionPacket` 同步 |
| `ConstructionOverlayView` | 只读覆盖视图，已交付/规划目标优先于世界，用于栅栏等邻接形状 |
| `BlockEntityContentAdapter` | 方块实体库存按槽拆成 CONTENT；提交时 `loadWithComponents` 后再按槽插入 |
| `MultiblockBuildAdapter` | AnvilCraft 大型多方块折到核心 PLACE，其余 part 为 ATTACHED；提交阶段 `restore` 写回 part 状态 |
| `FluidBuildAdapter` | 源液体满桶、分层锅/储罐精确 mB；不改 `OrdinaryBlockAdapter`，含水固体仍是 PLACE 的状态属性 |
| `EntityBuildAdapter` / `EntityBuildAdapters` | 实体适配接口与注册表。瞬态直接跳过。注册项在 `init/PlasticraftEntityBuildAdapters` |
| `AnvilCraftRedstoneWirePorts` | 提交后先 `topologyChanged`，再用本体公开 `editConnection` 按蓝图关掉多余端口、补回缺失端口。不要再写红石导线 mixin，也不要把类放进 `dev.dubhe.anvilcraft.block`（JPMS 裂包） |
| `ConstructionCommitLog` | 分区提交相位：`NONE/STATES/BLOCK_ENTITIES/MULTIBLOCK/BOUNDARY/ENTITIES/PUBLISH/DONE`。进度 NBT 在 `Debris` 之后追加 `CommitLog` |
| `ConstructionCommitService` | 安静提交：已交付投影整区静默写入区块段，不跑 `onPlace`/`neighborChanged`。BOUNDARY/PUBLISH 再写一遍蓝图状态后 `restoreWirePorts`。通用路径禁止假玩家 |
| `ConstructionJobController` | 规划、阶段恢复、封堵/拆除/收集窄接口、缺拆除策略与权限桩。`fitsWorker` 使用 `0.35 × 0.6`。`setLeaseAllay` / `AllayId`。`reach(worker)` 读工具定义，不要写死 1 格。`extractMaterial` 对创造模式走 `creativeSupply`。`nextPhase()` 按剩余 SEAL/DEMOLISH/PLACE 回到对应阶段，收集不是必经阶段。`allDemolishResolved()` 后仅当工地盒内仍有本任务标记掉落、且存在所有者本维度到最近标记物 ≤ 128、库存未满的已加载收集悦灵时进入 `COLLECTING_DEBRIS`，否则直接 `BUILDING`。`reconcileDebris` 只扫 `worldBox`，禁止 `inflate(128)`。`ensureIndex` 只恢复 `writesProjection()` 的已交付格。`chooseApproach` 在 `writesProjection()==false` 且六向都是未交付 PLACE 时可用目标格。`finish`/`complete` 在砸剩余壳后再 `restoreWirePorts`。缺收集**不**走 PAUSE/SKIP，不追加 `WaitReason`。工人无能量资格，不要再写 `ENERGY` 拒派 |
| `ConstructionBlueprintService` | 导入/部署/启动停止/取消仍走这里；启动第二份会 `pause` 旧任务。取消仍删除任务并安静提交已交付投影，已放真实填充块不删 |
| `ConstructionWaitReason` | `NONE/MATERIAL/OCCUPIED/WORLD/SOURCE/ENERGY/DEMOLITION/PERMISSION`；`ENERGY` 保留占位，不要删除或重排。`byId` 按序，只能追加 |
| `network/ConstructionProjectionSectionPacket` | 按区块段同步已交付格子；字段顺序冻结，只能追加 |

### 建设、拆除与收集执行器

- `ConstructionAllayToolBehavior`：无休息室建设闭环，并在 `SEALING_FLUID` 下按同一取料路径真实 `setBlock` 填充（不是投影）。`isActiveBuildCarry` 含封堵，取消时在途填充料飞回返还而不是就地落地。非 `BUILDING` / `SEALING_FLUID` 不领 PLACE。`COLLECTING_DEBRIS` 期间不领建造。`writesProjection()==false` 的 ENTITY/FLUID 允许格内交付，回退点必须落在 `inflate(REACH)` 内
- `DemolitionAllayToolBehavior`：无携带物。自领非壳、表面可达的 `DEMOLISH`，128 发现、工具触及，到位砸击。不吸取掉落物
- `CollectionAllayToolBehavior`：磁铁（`inventorySize()>0`）按 16 格吸入并装入九格临时库存；空手走近捡 1 个走托管携带物。自由模式每次吸入后重扫当前 16 格任意 `ItemEntity`，不吸经验，不预锁 16 格外目标。任务模式（`DEMOLISHING` / `COLLECTING_DEBRIS`）只领本任务标记掉落。满载或清场结束后按建设同一套离场再交还给原版游荡。设计上卸货目标是所有者背包或休息室下方容器；空手和磁铁都要尽力腾空，两边都塞不下就扔旁边地上，不能留在身上。当前代码仍只向所有者 `Inventory.add`，塞不下的留在托管物或九格里。休息室下表面与满载丢地留给 TODO 11 一并落地。直到 TODO 11「磁盘入室后排除无休息室工人」生效前，所有者的无休息室收集悦灵在拆除/清场阶段自领
- 接近点每 tick 用 `isUsableApproach` 刷新；已预约的 PLACE/SEAL 格不能当落脚点；悦灵 AABB 若还插在目标格里，先飞到接近点再执行
- 缺拆除：无本主人、本维度、到最近拆除目标 ≤ 128 的已加载拆除能力时，读休息室策略（无室则 PAUSE）。PAUSE → `WAITING_DEMOLITION`；SKIP → 跳过剩余可拆与仍被占的 PLACE，残缺进入建造。基岩类永久障碍不走该策略
- 缺收集不阻塞：不暂停、不跳过、不加 `WaitReason`；掉落留世界，任务进建造

### 碰撞 Mixin

- `mixin/BlockCollisionsMixin` + `mixin/BlockGetterMixin`：把 `ConstructionProjectionIndex` 的世界 `VoxelShape` 注入方块碰撞。`onlySuffocatingBlocks` 查询跳过假方块，避免把空气格当成窒息方块。不要再为施工投影放占位 BE

### 客户端渲染

- `client/renderer/blueprint/BlueprintProjectionRenderer`：未交付仍走全息层（不写深度、半透明）。已交付格跳过全息，改用 `RenderType.solid()` / `cutoutMipped()` 按真实方块烘焙，写深度、不染色、不加绿色遮罩。已交付流体单独画半透明水面。实体投影读 `ConstructionEntityProjectionIndex`，施工期塑料实体只显示、提交后才有硬碰撞。`DeliveredRenderView` 用索引状态做邻接剔除，光照读真实世界。拆除不得往索引里写格子
- 设计文档的 `construction_projection.png` 遮罩仍留在资源里，但验收要求已交付必须看起来像世界方块，因此不再叠绿色遮罩。后续 TODO 若要给「活动/受阻/跳过」加状态色，不要把已交付实心块重新洗成全息
- `WorkingAllayRenderer` / `WorkingAllayModel` / `WorkingAllayHeldItemLayer` / `AllayHardHatLayer` / `AllayHostedCarryLayer`：原版悦灵贴图与光照，再叠加安全帽、主手和托管携带物。蟹钳建设时把托管方块夹在朝下钳口。不要恢复无人机机身或四种机械附件

### 菜单/网络注册

- 菜单：`PlasticraftMenuTypes.ALLAY_LOUNGE`。没有 `working_allay` 工人菜单
- 网络包：`allay_lounge_recall`、`allay_lounge_release`、`allay_lounge_settings`，以及蓝图投影包 `ConstructionProjectionSectionPacket`、`ConstructionEntityProjectionPacket`，由 AnvilLib `NetworkRegistrar` 按包扫描自动注册。实体投影包字段序冻结，只能追加
- 不要恢复 `allay_settings` / `allay_unequip_hat` 工人包；摘帽走实体交互

## 3. 稳定契约（不得破坏）

- 注册名：实体 `working_allay`；方块 `allay_lounge`；菜单 `allay_lounge`；成型类型 `allay_hard_hat`；组件 `molded_plastic, station_energy, blueprint_task, construction_debris`。不要恢复 `drone` / `drone_station` / `drone_data` / `propeller`
- `AllayWorkRecord` 字段顺序、`AllayFlightState` 字节值、`ConstructionJob.state` 既有字节值（`0`/`1` 冻结，`2`–`16` 已占用，其中 `5/6/7/8` 为封堵/拆除/收集清场/等待拆除）
- NBT：`LeaseAllay`、`AllayId`、`DockingAllay`、`AllayWork`。不要读 `LeaseDrone` / `DroneId` / `DroneData`
- `ConstructionProjectionSectionPacket` 与 `ConstructionEntityProjectionPacket` 字段顺序冻结
- `AllayToolDefinitions.CONSTRUCTION` / `DEMOLITION` / `COLLECTION` 只允许换 `behavior`，不要改注册名、触及距离或收集九格容量
- 只追加过：`Kind.SEAL/DEMOLISH/CONTENT/FLUID/ENTITY`、`WaitReason.DEMOLITION/PERMISSION`（`ENERGY` 占位保留）、组件 `construction_debris`、操作 NBT `Shell/ParentId/BlockEntity/Slot/Fluid/EntityNbt/Return`、进度 NBT `Debris/CommitLog`。不要改既有字段序或重排 enum
- 休息室**没有合成配方**：设计文档未定义配方，未擅自发明
- 工人不使用 FE。休息室固定请求 16 功率，不写内部电量或电容器槽
- 不考虑旧存档兼容，也不添加迁移代码或旧 NBT 读取逻辑

## 4. 资产

休息室方块贴图、GUI 背景和 `pause/skip/return_home` 按钮已在 `textures/`。蓝图按钮已在 `textures/gui/button/blueprint/`。默认安全帽在 `assets/anvilcraftplasticraft/allay/default_hard_hat.json`。

`textures/gui/background/working_allay.png` 是遗留资源，施工悦灵没有设置界面，不要据此恢复工人菜单。

玩家重做美术时：替换 PNG 即可；GUI 布局改动需同步 `AllayLoungeScreen` / `AllayLoungeMenu` 中的坐标常量。

## 5. 重要经验（GameTest 与运行时陷阱）

1. `@EmptyTemplate(floor = true)` 的铁块地板占据 helper 相对坐标 **y=1**，可用地面顶面在 **y=2**——实体 spawn、setBlock 都要从 y=2 起算
2. vanilla `GameTestInfo.succeed()` 会丢弃结构 bounds 外扩 1 格内的所有实体；测试断言不要写“实体必须存活”，要断言位置/数据语义，或精确引用自己的实体
3. GameTest 结构间距仅 5 格：128 格发现、召回扫描会波及邻居测试。施工测试只调用窄接口，禁止 `inflate(128)` 扫实体。`level.getAllEntities()` 只用于暂停时认领已加载的本任务悦灵，不要在测试里再扫一遍
4. 不要对「起点到终点」做一次巨大 `noCollision(AABB)`：会把地面整段落进检测，也会在 GameTest 里对远距假玩家扫出超大 AABB，并触发 `BondedPlasticShapeIndex` 卡死。寻路必须按自身碰撞箱步进扫掠
5. `findOwner` 先 `PlayerList.getPlayer`，再扫 `level.players()`；不要用 `player.level() != level` 引用比较，GameTest 假玩家会对不上
6. `isOccupied` / `fitsWorker` 必须跳过 `WorkingAllayEntity`、`ItemEntity` 和 `ExperienceOrb`。占用 GameTest 用猪，不用悦灵自己。砸击掉落若算占格，拆除后 PLACE 会永远不可分配，接近点也会被堵死
7. 接近点不能落在仍预约的 PLACE 格上；交付前若悦灵 AABB 还与目标格相交，先飞到接近点
8. A* 失败禁止 `List.of(goal)` 对着墙直线冲。先抬升绕行，再不行返回空路径并悬停/爬升
9. 水平碰撞才强制重规划；`verticalCollision` 在贴地滑行时很常见，不能当撞墙
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
21. 休息室 GameTest 用创造发电机接电网，不要写 FE-on-item 或内部电量
22. 高粘性树脂与树脂铁砧不得捕获 `WorkingAllayEntity`
23. `ConstructionJobGameTests` 部分方法描述仍写 drone，以实体类型 `WorkingAllayEntity` 为准，不要据此恢复无人机
24. 空手通用工不是“只悬停”；未知主手物品也走 `none`
25. 蟹钳触及是 4 格，不要把建设写成一律 1 格

## 6. TODO 11 切入点提示

- 依赖：休息室本体、TODO 06 至 TODO 10 对应的无休息室闭环。不要顺带做分层 A*（TODO 12）、领地/FTB（TODO 13）、观察九区块加载（TODO 15）
- 结构磁盘放入休息室后自动认领任务并排除未托管的世界工人；建设/拆除/收集都改从休息室下方物流取料和卸货，不要继续让无休息室工人从玩家背包自领已入室任务
- 顶部严格一秒一架；出库/回库走休息室统一策略。不要给悦灵加 FE
- 磁盘移除、任务暂停、取消、缺料及来源不可用都按台账返还；来源整体不可用时相关悦灵立即悬停并保留租约
- 先写 TODO 11 实施 Plan，再改代码。GameTest 继续放现有类能归入的窄接口，不要 `inflate(128)` 扫实体，也不要把休息室物流失败条件合成进拆除或收集 live batch

## 7. 已知待办与注意点

- 望远镜观察仍是 `AllayToolBehavior.NONE`，TODO 15 接入真实覆盖状态
- 向休息室下表面容器卸货、出库/回库、磁盘入室后排除无休息室工人属 TODO 11
- 休息室 `recallNearbyWorkers` 是管理/测试入口；任务化的入出库调度属 TODO 11
- DOCKING 飞行仍是无寻路直线 + 遇阻爬升的基础版，TODO 12 升级为分层规划器时替换；单机三维寻路不要提前做成 64 机预约或走廊 A*
- 权限桩恒为允许，领地/FTB Teams 属 TODO 13；`WAITING_PERMISSION` 已占位且忽略 SKIP。自由收集物的团队共享也属 TODO 13，本项只对所有者卸货
- 已交付投影的世界格是空气：指向时 Jade 显示空气是当前契约，不是渲染 bug。若后续要让准星识别假方块，应走独立查询，不要改 `Level#getBlockState`
- 施工悦灵本身没有设置界面；缺料/缺拆除只在休息室切换
- 不要恢复无人机物品、装配配方、螺旋桨类型、工人内部 FE、硬碰撞或落地状态

## 8. GameTest 分册

- `AllayGameTests`：戴帽转换、空手通用工、手持加强、原版游荡、推挤、拴绳与无硬碰撞
- `AllayLoungeGameTests`：16 功率、召回节流、满员方阵、满员拒绝、断电暂停、破坏放出
- `CollectionAllayGameTests`：九格、16 格重扫、向所有者卸货、短时序吸入。不要启动施工任务
- `ConstructionJobGameTests`：无休息室建设 / 拆除 / 收集闭环。不要再写电量拒派
- `MoldingProductGameTests` / `PlasticMoldingChamberGameTests`：安全帽占地/高度包络与高精度打印组件
