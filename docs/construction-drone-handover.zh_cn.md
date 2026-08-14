# 无人机系统交接(TODO 01 ~ 06)

> 交接时间:2026-08-14;设计规格见 `docs/construction-drone-design.zh_cn.md`(以下简称"设计文档")。
> 接手后续任务前,必须完整阅读设计文档与仓库 `AGENTS.md`,并先写该 TODO 的实施 Plan 再动手。
> TODO 01 至 TODO 06 均已实现,且玩家已完成游戏内验收并勾选设计文档复选框。下一任务是 TODO 07。

## 1. 当前进度

| TODO | 状态 |
| --- | --- |
| TODO 01 四种无人机基础实体、物品与完整外观 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 02 无人机能源、基础飞行与单机设置 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 03 无人机站本体、能源、库存与停泊 | 已实现,游戏内验收通过,复选框已勾选;召回多机互挤抬升已改为站顶 4x4 方阵排队(`fix(drone)` b8134d4) |
| TODO 04 原版结构蓝图、世界部署与结构磁盘交互 | 已实现,游戏内验收通过,复选框已勾选(`feat(blueprint)` 79c6c23) |
| TODO 05 Create 与 Litematica 蓝图导入兼容 | 已实现,游戏内验收通过,复选框已勾选(`feat(blueprint)` f960eb6) |
| TODO 06 单架建设无人机的完整施工闭环 | 已实现,游戏内验收通过,复选框已勾选(`feat(drone)` 39d7e34 + 本提交的验收修正) |
| TODO 07 流体封堵与真实拆除阶段 | **未开始;下一任务** |
| TODO 08+ | 未开始 |

工作流程约定(与玩家确认过):每个 TODO 完成"范围 + 自动化验证"后按 conventional-commits(中文 subject,scope 用 `drone` 或 `blueprint`)提交一次,然后暂停等待玩家游戏内验收,验收通过才继续下一个;验收通过后勾选设计文档中的复选框。玩家已声明后续会自制全新贴图模型并配套改代码,当前程序生成资产只是可用占位。

### 联调与环境备注

- 本体扫描器已补齐方块实体 NBT 与实体保存,并新增 `StructureDiskData.upsideDown`;Plasticraft 已切回远程 AnvilCraft `1.6.0+snapshot.2145` 与 AnvilLib `2.0.0+snapshot.507`
- 本体 HEAD 的炼药锅配方 API 改为 `FluidStackPredicate`/`FluidStack` 转换列表,Plasticraft 已完成迁移(`refactor(recipe)` 672aa44);等离子喷流虚拟气体独立为 `gas` 字段
- 并发开发注意:两个智能体同时跑 gradle/GameTest 会互抢 `session.lock`、互杀 Java 进程、互相覆盖 datagen 产物与哈希缓存;若再出现 datagen 结果与代码不符,先删 `src/generated/resources/.cache` 强制全量重写
- GameTest 世界级施工 SavedData 会留在 `run/gameTestServer/world/data/`。下次跑全量前若蓝图生命周期测试报"index should contain exactly one job"或"no leftover jobs after reimport",只删这两份文件再跑,不要删整个 `run/gameTestServer/world`:
  - `anvilcraftplasticraft_construction_jobs.dat`
  - `anvilcraftplasticraft_construction_job_progress.dat`

### TODO 03 游戏内验收清单(已通过)

1. 放置无人机站,断电时显示 `drone_station_off` 外观;接入电网或塞入充电电容器后切换有电外观,拆下再放回内部 FE 保留
2. 电容器槽行为与塑料成型舱一致:整颗消耗、空壳留槽;接近满容量时不消耗
3. 站内放入低电无人机物品,观察其以 800 FE/gt(默认效率)从站点内部充电
4. 在站旁摆多架无人机,点界面召回按钮:依次飞向站顶,严格一秒一架下沉入库;多机同时召回按登记顺序排队,队首之外在站顶上方 3 格列 1 格间距 4x4 方阵等待,不再互相垫高抬升;玩家站在站顶不会掉入舱门
5. 入库动画进行中切断电力:模型变无电、下沉画面冻结、数据不丢;恢复后从原进度继续

### TODO 04/05 游戏内验收清单(已通过)

1. 用原版结构方块保存一个含箱子内容与展示框的结构,手持结构磁盘右击该结构方块导入;再用结构扫描器扫同一建筑并右击磁盘转换,两者磁盘 Tooltip 摘要一致
2. 手持已导入磁盘右击进入部署会话:投影跟随准星并显示包围盒与半透明方块;普通滚轮仍切换快捷栏,Ctrl+滚轮切换七工具,Alt+滚轮调整当前工具(高度/位置/旋转/镜像/分层),右击执行;确认后的投影位置准确,多份蓝图共存
3. 磁盘放进背包、箱子等任意容器,槽底黄色(已放置未启动)/绿色(进行中)正确跟随;菜单中空手右击已部署磁盘切换启动/停止,启动第二份时第一份自动变回黄色
4. 潜行右击打开文件导入界面,分别放入原版 .nbt、Create .nbt 与多区域 .litematic 同一测试建筑,三者部署投影一致;损坏文件与缺模组文件给出可理解错误,不产生残缺蓝图
5. 会话取消已部署蓝图后投影消失、槽底恢复无色;取消未部署的仅退出会话

### TODO 06 游戏内验收清单(已通过,2026-08-14)

1. 无站建设无人机从所有者背包取料,在 128 格内把普通方块交付为可碰撞施工投影;玩家可站在已交付格子上
2. 已交付木头等实心方块看起来就是世界上的不透明方块,不是绿色半透明幽灵;世界格仍是空气,Jade 显示「空气」是预期
3. 玩家走动后,无人机仍能飞来取料并绕开已交付碰撞飞回工地,不会顶墙卡住
4. 菜单停止施工时,已取料的无人机飞回玩家身边再把物品塞进背包,然后降落;物品不会老远瞬移进包、无人机原地落地
5. 全部交付并安静提交后,无人机先飞离蓝图包围盒再降落;所有者在工地外时飞回其身边
6. 中途取消只把已交付格子写成真实方块,未交付保持世界原状;缺料默认暂停,改为跳过则其余继续并残缺提交

## 2. 代码地图

### 公共玩法层 `drone/`

| 类 | 职责 |
| --- | --- |
| `DroneData` | 物品/实体/站内槽位共用的完整数据组件(record),字段顺序冻结:`tool_id, left_propeller, right_propeller, energy, owner, shortage_strategy, collection_inventory, assigned_job_id, hosted_carry`;后续任务字段**只能追加** |
| `DroneShortageStrategy` | 缺料/缺拆除策略 enum(PAUSE/SKIP) |
| `DroneFlightState` | 飞行状态机 enum:`LANDED, TAKING_OFF, HOVERING, LANDING, DOCKING, FLYING`;同步字节值按序冻结,只能追加。`FLYING` 是 TODO 06 追加的任务路点飞行,不要复用观察无人机的四格悬停 |
| `DroneEnergyModel` | 能耗常量与换算:256 FE/空中 gt、256 FE/格、8 kW 充电、20,480 FE 安全降落储备、40,960 FE 起飞门槛、`Quote` 任务报价 record |
| `DroneFlightPlanner` | 单架基础三维寻路:0.5 步进直线扫掠,受阻后窗口 12 / 预算 4096 的 A*;失败则抬升绕行,禁止再退回「对着墙直线飞」。目标或自身嵌进已交付假方块时先 `snapToFree`。不要复用树脂牵引规划器,也不做多机预约 |
| `DroneFlightNavigator` | 沿路点移动;到达后悬停等执行器。`clearAssignment` 会清路点,完工撤离途中不要先清再指望旧路径 |
| `DroneDefaultPropeller` | 从 jar 资源 `assets/anvilcraftplasticraft/drone/default_propeller.json` 加载默认白色螺旋桨(创造条目与配方展示用),与世界蓝图库无关 |
| `DronePropellerTraits` | 螺旋桨材料能力并集注册表;当前只有 `FIRE_RESISTANT` 判定边界,耐热塑料尚未加入游戏 |
| `drone/tool/DroneToolDefinition(+s)` | 工具定义注册边界。`CONSTRUCTION` 只把 `behavior` 换成了 `ConstructionDroneToolBehavior.INSTANCE`,注册名和触及距离未改;其余工种仍是 `DroneToolBehavior.NONE` |

### 实体与物品

- `entity/drone/DroneEntity`:唯一无人机实体。0.5³ 硬碰撞/可站立/推动;`canBeCollidedWith()==true`。电网充电经 AnvilCraft `DynamicPowerComponent`(未满电申报 8 kW);状态机与能耗计费在 `serverFlightTick`/`serverEnergyCostTick`;工具执行器在飞行刻之前调用;`startDockingTo(BlockPos)` 进入 DOCKING;`Shift+铁砧锤` 回收时 `DroneData` 带着 `hostedCarry` 走,不会吞托管物;`canAcceptQuote` 是任务分配的能量资格入口
- `item/DroneItem`:五个物品注册共用一类;建设无人机 Tooltip 仍是一句定性用途,数值在手册
- `recipe/DroneAssemblyRecipe` + `DronePropellerIngredient`:装配配方把两格螺旋桨完整 ItemStack 写入成品

### 无人机站

- 与 TODO 03 相同。**出库接口尚未实现,留给任务协调器(TODO 11)**;下方容器取料留给 TODO 11。TODO 06 的建设无人机完全不经过站点

### 施工任务 `blueprint/`(TODO 06 已把投影部署接到单机施工)

| 类 | 职责 |
| --- | --- |
| `ConstructionJob` | 服务端任务条目。`state` 字节:`0` INACTIVE、`1` ACTIVE(冻结兼容值)、`2` PLANNING … `16` FAILED。`isActive()` = 非 INACTIVE 且非终态。`activate()` 写入 `PLANNING` 而不是 `1`。新状态只能追加 |
| `ConstructionJobIndex` | 主世界 SavedData 任务索引;`activeJobOf` 只返回进行中任务 |
| `ConstructionJobStore` / `ConstructionJobProgress` | 与索引分开的世界级进度库:操作、台账、已交付集合。文件名 `anvilcraftplasticraft_construction_job_progress` |
| `ConstructionBuildOp` | 单格规范操作:`PLACE` / `ATTACHED` / `UNSUPPORTED`;状态 `PENDING` / `WAITING_WORLD` / `WAITING_OCCUPIED` / `LEASED` / `DELIVERED` / `SKIPPED` |
| `OrdinaryBlockAdapter` | 普通方块映射:有放置物品则 `PLACE` 扣一份;门上半/床头/活塞头 `ATTACHED` 不重复扣料;流体与无物品状态 `UNSUPPORTED`(本阶段跳过) |
| `ConstructionLedgerEntry` | 托管台账:`CARRIED` / `DELIVERED` / `RETURNED`。取出的资源在交付或物理返还前只存在一份 |
| `ConstructionAssembler` | 反向可拆解装配顺序与撤离校验 |
| `ConstructionProjectionIndex` | 已交付假方块的区块段索引。世界格保持空气,不放占位方块实体;碰撞与客户端渲染读这里的目标状态和世界 `VoxelShape` |
| `ConstructionOverlayView` | 只读覆盖视图,已交付/规划目标优先于世界,用于栅栏等邻接形状 |
| `ConstructionCommitService` | 安静提交:已交付写成真实方块,不引发邻居连锁;取消不是回滚 |
| `ConstructionJobController` | 规划、暂停/取消、台账、接近点、占用、还物与完工撤离的窄接口。无人机自行领取,这里不扫描 128 格实体。`fitsDrone` 忽略 `DroneEntity`(与 `isOccupied` 一致)。`worldBox` 给出蓝图世界包围盒 |
| `ConstructionBlueprintService` | 导入/部署/启动停止/取消仍走这里;启动第二份会 `pause` 旧任务 |
| `ConstructionWaitReason` | `NONE/MATERIAL/OCCUPIED/WORLD/SOURCE/ENERGY`;首次变化向所有者报告一次 |
| `network/ConstructionProjectionSectionPacket` | 按区块段同步已交付格子;字段顺序冻结,只能追加 |

### 建设执行器

- `drone/tool/ConstructionDroneToolBehavior`:无站建设闭环。从所有者背包取料,飞到一格触及处交付,再接下一项。停止/取消时已取料机飞回玩家再 `depositHostedCarry`;没有对应实体的台账条目才当场返还。完工后 `leaveSiteThenLand`:所有者在工地外则飞回其身边,否则飞到包围盒外侧空位,再降落。任务删除后若导航器仍有撤离路点,继续飞完,禁止立刻原地落地
- 接近点每 tick 用 `isUsableApproach` 刷新;已预约的 PLACE 格不能当落脚点;无人机 AABB 若还插在目标格里,先飞到接近点再交付

### 碰撞 Mixin

- `mixin/BlockCollisionsMixin` + `mixin/BlockGetterMixin`:把 `ConstructionProjectionIndex` 的世界 `VoxelShape` 注入方块碰撞。`onlySuffocatingBlocks` 查询跳过假方块,避免把空气格当成窒息方块。不要再为施工投影放占位 BE

### 客户端渲染

- `client/renderer/blueprint/BlueprintProjectionRenderer`:未交付仍走全息层(不写深度、半透明)。已交付格跳过全息,改用 `RenderType.solid()` / `cutoutMipped()` 按真实方块烘焙,写深度、不染色、不加绿色遮罩。`DeliveredRenderView` 用索引状态做邻接剔除,光照读真实世界
- 设计文档 §0 的 `construction_projection.png` 遮罩仍留在资源里,但验收要求已交付必须看起来像世界方块,因此不再叠绿色遮罩。后续 TODO 若要给「活动/受阻/跳过」加状态色,不要把已交付实心块重新洗成全息
- `BlueprintProjectionRenderTypes`:全息方块层与箱子/矿车深度预通道仍在;已删除未再使用的 `constructionMask()` 层

### 菜单/网络注册

- 菜单:`PlasticraftMenuTypes.DRONE / DRONE_STATION`;网络包:`DroneSettingsPacket`、`DroneStationRecallPacket`、`ConstructionProjectionSectionPacket`,由 AnvilLib `NetworkRegistrar` 按包扫描自动注册

## 3. 稳定契约(不得破坏)

- 注册名:实体 `drone`;物品 `drone, construction_drone, demolition_drone, collection_drone, observation_drone`;方块 `drone_station`;配方 ID 与物品同名;组件 `molded_plastic, drone_data, station_energy, blueprint_task`
- `DroneData` 字段顺序、`DroneFlightState` 字节值(`FLYING` 已占用追加位)、`ConstructionJob.state` 既有字节值(`0`/`1` 冻结,`2`–`16` 已占用)、模型部件名、贴图/GUI 资源路径(设计文档 §0 冻结清单)
- `ConstructionProjectionSectionPacket` 字段顺序冻结
- `DroneToolDefinitions.CONSTRUCTION` 只允许换 `behavior`,不要改注册名或触及距离
- 无人机站**没有合成配方**:设计文档未定义站的配方,未擅自发明
- 能量数值全部引用 AnvilCraft 常量(`CapacitorItem.ENERGY`、`SuperCapacitorItem.ENERGY`、`powerConverterEfficiency`),不复制魔法数
- 不考虑旧存档兼容,也不添加迁移代码或旧 NBT 读取逻辑

## 4. 资产工具

`art/tools/DroneAssetGenerator.java`(单文件,仓库根目录运行 `java art/tools/DroneAssetGenerator.java`)一次性生成:实体主体与四附件贴图、`.bbmodel` 源文件(base64 内嵌贴图)、无人机/站点 GUI 背景、`pause/skip/return_home` 按钮(四帧竖排:正常/悬停/按下(选中)/禁用)、站体有电/无电方块贴图、`textures/misc/construction_projection.png`。

**几何数值必须与 `DroneModel`/`DroneToolAttachmentModel` 手工同步**(文件头有注释)。玩家重做美术时:替换 PNG 即可,UV 布局变化才需要同步改 Java 模型 `texOffs`;GUI 布局改动需同步 `DroneScreen`/`DroneStationScreen`/`DroneStationMenu` 中的坐标常量。

蓝图按钮已在 `textures/gui/button/blueprint/`。

## 5. 重要经验(GameTest 与运行时陷阱)

1. `@EmptyTemplate(floor = true)` 的铁块地板占据 helper 相对坐标 **y=1**,可用地面顶面在 **y=2**——实体 spawn、setBlock 都要从 y=2 起算
2. vanilla `GameTestInfo.succeed()` 会丢弃结构 bounds 外扩 1 格内的所有实体;测试断言不要写"实体必须存活",要断言位置/数据语义,或精确引用自己的实体
3. GameTest 结构间距仅 5 格:128 格发现、召回扫描会波及邻居测试。施工测试只调用窄接口,禁止 `inflate(128)` 扫实体。`level.getAllEntities()` 只用于暂停时认领已加载的本任务无人机,不要在测试里再扫一遍
4. 不要对「起点到终点」做一次巨大 `noCollision(AABB)`:会把地面整段落进检测,也会在 GameTest 里对远距假玩家扫出超大 AABB,并触发 `BondedPlasticShapeIndex` 卡死。寻路必须按 0.5 格步进扫掠
5. `findOwner` 先 `PlayerList.getPlayer`,再扫 `level.players()`;不要用 `player.level() != level` 引用比较,GameTest 假玩家会对不上
6. `isOccupied` / `fitsDrone` 必须跳过 `DroneEntity`。占用 GameTest 用猪,不用无人机自己。无人机飞到接近点后若把自己算作占格,会改接近点或原地卡死
7. 接近点不能落在仍预约的 PLACE 格上;交付前若无人机 AABB 还与目标格相交,先飞到接近点
8. A* 失败禁止 `List.of(goal)` 对着墙直线冲。先抬升绕行,再不行返回空路径并悬停/爬升
9. 水平碰撞才强制重规划;`verticalCollision` 在贴地滑行时很常见,不能当撞墙
10. 瞬时断言(`zzz_construction`)与短时序飞行(`zzz_construction_live` / `zzz_construction_return`)必须分 batch,避免和蓝图索引生命周期测试并行抢世界级 SavedData,也避免 20 gt 窗口打死 400 gt 交付
11. 无实体的台账取消仍当场返还(防复制);有加载无人机拿着材料时必须飞回再还,GameTest 用 `pauseReturnsCarryByFlyingToOwner` 锁这条
12. 全量验证命令:`./gradlew runData`(资产变化时,检查 `src/generated` 差异)→ `./gradlew build` → `./gradlew runGameTestServer`;严禁 UI 自动化操作 Minecraft 客户端。渲染与资源改动只做静态检查,游戏内视觉由玩家验收

## 6. TODO 07 切入点提示

- 依赖:TODO 06。不要顺带做站点物流(TODO 11)、分层 A*/多机预约(TODO 12)、观察加载(TODO 15)
- 本项才开始**真实拆除**和**流体封堵**。TODO 06 遇到非空气世界格只标 `WAITING_WORLD`;流体和无物品方块已由 `OrdinaryBlockAdapter` 记为 `UNSUPPORTED` 并跳过。拆除无人机工具执行器仍是 `DroneToolBehavior.NONE`
- 设计顺序:先完成全部拆除再建造;从外向内表面剥离;永久障碍跳过;缺拆除无人机走与建设相同的 PAUSE/SKIP 策略,权限拒绝必须强制暂停,不能被跳过绕过
- 有限流体区域及边界流入扫描后,建设无人机用真实稳定方块整片封堵,拆除无人机再拆这些填充块。取消时立即停止,已放下的填充块原样留在世界,未放置的在途封堵材料按台账返还,不能无掉落删除
- 掉落物先留在世界并带任务来源标记;收集吸入是 TODO 08,本项不要让拆除无人机吸物品
- 切石机附件动作应对齐 AnvilCraft 铁砧砸切石机上方方块的破坏语义(经验、战利品、多方块回调、不可破坏判定)
- `ConstructionJob.state` 里 `STATE_SEALING_FLUID` / `STATE_DEMOLISHING` / `STATE_COLLECTING_DEBRIS` / `STATE_WAITING_DEMOLITION` 字节已占位,直接使用,不要改号
- 先写 TODO 07 实施 Plan,再改代码。GameTest 继续放在现有施工/无人机测试类能归入的地方;拆除与封堵是不同失败条件,不要和 TODO 06 的空气建造短时序合成一条

## 7. 已知待办与注意点

- 无人机附件的动作动画(蟹钳张开/抓取、锯片切割、磁铁吸附、望远镜伸缩)只有静态姿态与 `DATA_ACTION_STATE` 同步字节占位;建设抓取仍是占位,拆除锯片等 TODO 07 再接
- 无人机 GUI 的观察类"区块加载状态"是占位文案,TODO 15 接入真实覆盖状态
- 收集无人机九格库存在菜单中只读展示,取放语义由 TODO 08 决定
- 站点 `recallNearbyDrones` 是管理/测试入口;任务化的入出库调度(站属无人机、低电自动返站)属 TODO 11
- DOCKING 飞行仍是无寻路直线 + 遇阻爬升的基础版,TODO 12 升级为分层规划器时替换;TODO 06 的单机三维寻路不要提前做成 64 机预约或走廊 A*
- 方块实体内容、真实容器、多方块折叠、实体生成属 TODO 09/10;`OrdinaryBlockAdapter` 不要在 TODO 07 里偷偷扩成通用放置器
- JEI 中装配配方的成品无人机显示为无桨机体(配方 result 不带组件);如需展示默认桨,改 `PlasticraftRecipeData.generateDroneRecipe` 的 result 构造(注意配方 JSON 体积)
- 已交付投影的世界格是空气:指向时 Jade 显示空气是当前契约,不是渲染 bug。若后续要让准星识别假方块,应走独立查询,不要改 `Level#getBlockState`
