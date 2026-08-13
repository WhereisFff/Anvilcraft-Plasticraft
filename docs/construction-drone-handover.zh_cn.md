# 无人机系统交接(TODO 01 ~ 05)

> 交接时间:2026-08-13;设计规格见 `docs/construction-drone-design.zh_cn.md`(以下简称"设计文档")。
> 接手后续任务前,必须完整阅读设计文档与仓库 `AGENTS.md`,并先写该 TODO 的实施 Plan 再动手。

## 1. 当前进度

| TODO | 状态 |
| --- | --- |
| TODO 01 四种无人机基础实体、物品与完整外观 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 02 无人机能源、基础飞行与单机设置 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 03 无人机站本体、能源、库存与停泊 | 已实现;召回验收发现的多机互挤抬升已改为站顶 4x4 方阵排队(`fix(drone)` b8134d4),**游戏内验收待玩家执行** |
| TODO 04 原版结构蓝图、世界部署与结构磁盘交互 | 已实现(`feat(blueprint)` 79c6c23),自动化验证绿,**游戏内验收待玩家执行** |
| TODO 05 Create 与 Litematica 蓝图导入兼容 | 已实现(`feat(blueprint)` f960eb6),自动化验证绿,**游戏内验收待玩家执行** |
| TODO 06+ | 未开始 |

### 本轮联调与环境备注(2026-08-13 晚)

- 本体扫描器已在本地仓库 `C:\Users\a1810\IdeaProjects\AnvilCraft`(分支 `cr`,提交 `dc10bbd0f`)补齐方块实体 NBT 与实体保存,并新增 `StructureDiskData.upsideDown`;Plasticraft 暂以 `gradle.properties` 的 `anvilcraft_jar` 指向本地构建的 `1.6.0+snapshot.9999` jar 联调,本体发版后需移除该属性并同步版本号
- 本体 HEAD 的炼药锅配方 API 改为 `FluidStackPredicate`/`FluidStack` 转换列表,Plasticraft 已完成迁移(`refactor(recipe)` 672aa44);等离子喷流虚拟气体独立为 `gas` 字段
- 另一智能体(grok cli)在同一工作树修复其他 bug,其未提交改动包含胶粘拉杆活塞推动等内容;其 `BondedPistonReactions.claimDestroyBlock` 未检查推动反应,把黑曜石等不可推方块收进 `toPush`,导致既有测试 `immovableBondedMemberBlocksPiston` 失败——这是它在制品的回归,与 TODO 04/05 无关,当前全套件 385 项仅此一项失败
- 并发开发注意:两个智能体同时跑 gradle/GameTest 会互抢 `session.lock`、互杀 Java 进程、互相覆盖 datagen 产物与哈希缓存;若再出现 datagen 结果与代码不符,先删 `src/generated/resources/.cache` 强制全量重写

工作流程约定(与玩家确认过):每个 TODO 完成"范围 + 自动化验证"后按 conventional-commits(中文 subject,scope 用 `drone`)提交一次,然后暂停等待玩家游戏内验收,验收通过才继续下一个;验收通过后勾选设计文档中的复选框。玩家已声明后续会自制全新贴图模型并配套改代码,当前程序生成资产只是可用占位。

### TODO 03 游戏内验收清单(待玩家执行)

1. 放置无人机站,断电时显示 `drone_station_off` 外观;接入电网或塞入充电电容器后切换有电外观,拆下再放回内部 FE 保留
2. 电容器槽行为与塑料成型舱一致:整颗消耗、空壳留槽;接近满容量时不消耗
3. 站内放入低电无人机物品,观察其以 800 FE/gt(默认效率)从站点内部充电
4. 在站旁摆多架无人机,点界面召回按钮:依次飞向站顶,严格一秒一架下沉入库;多机同时召回按登记顺序排队,队首之外在站顶上方 3 格列 1 格间距 4x4 方阵等待,不再互相垫高抬升;玩家站在站顶不会掉入舱门
5. 入库动画进行中切断电力:模型变无电、下沉画面冻结、数据不丢;恢复后从原进度继续

### TODO 04/05 游戏内验收清单(待玩家执行)

1. 用原版结构方块保存一个含箱子内容与展示框的结构,手持结构磁盘右击该结构方块导入;再用结构扫描器扫同一建筑并右击磁盘转换,两者磁盘 Tooltip 摘要一致
2. 手持已导入磁盘右击进入部署会话:投影跟随准星,滚轮切换七工具、右击执行、Shift+滚轮调层;移动、旋转、镜像、分层显示与确认后的投影位置准确,多份蓝图共存
3. 磁盘放进背包、箱子等任意容器,槽底黄色(已放置未启动)/绿色(进行中)正确跟随;菜单中空手右击已部署磁盘切换启动/停止,启动第二份时第一份自动变回黄色
4. 潜行右击打开文件导入界面,分别放入原版 .nbt、Create .nbt 与多区域 .litematic 同一测试建筑,三者部署投影一致;损坏文件与缺模组文件给出可理解错误,不产生残缺蓝图
5. 会话取消已部署蓝图后投影消失、槽底恢复无色;取消未部署的仅退出会话

## 2. 代码地图

### 公共玩法层 `drone/`

| 类 | 职责 |
| --- | --- |
| `DroneData` | 物品/实体/站内槽位共用的完整数据组件(record),字段顺序冻结:`tool_id, left_propeller, right_propeller, energy, owner, shortage_strategy, collection_inventory`;后续任务字段**只能追加** |
| `DroneShortageStrategy` | 缺料/缺拆除策略 enum(PAUSE/SKIP) |
| `DroneFlightState` | 飞行状态机 enum:`LANDED, TAKING_OFF, HOVERING, LANDING, DOCKING`;同步字节值按序冻结,只能追加 |
| `DroneEnergyModel` | 能耗常量与换算:256 FE/空中 gt、256 FE/格、8 kW 充电、20,480 FE 安全降落储备、40,960 FE 起飞门槛、`Quote` 任务报价 record |
| `DroneDefaultPropeller` | 从 jar 资源 `assets/anvilcraftplasticraft/drone/default_propeller.json` 加载默认白色螺旋桨(创造条目与配方展示用),与世界蓝图库无关 |
| `DronePropellerTraits` | 螺旋桨材料能力并集注册表;当前只有 `FIRE_RESISTANT` 判定边界,耐热塑料尚未加入游戏 |
| `drone/tool/DroneToolDefinition(+s)` | 工具定义注册边界:`none/construction/demolition/collection/observation` 五项;声明资源 ID、工具物品、能力集合、触及距离、库存布局、2,560 FE 瞬时费、显示状态名、执行器占位 `DroneToolBehavior.NONE`(后续 TODO 替换实现) |

### 实体与物品

- `entity/drone/DroneEntity`:唯一无人机实体。0.5³ 硬碰撞/可站立/推动;电网充电经 AnvilCraft `DynamicPowerComponent`(未满电申报 8 kW);状态机与能耗计费在 `serverFlightTick`/`serverEnergyCostTick`;`startDockingTo(BlockPos)` 进入 DOCKING 并飞向站顶,由站点 `tryDock` 原子接收;Shift+铁砧锤回收;`canAcceptQuote` 是任务分配的能量资格入口
- `item/DroneItem`:五个物品注册共用一类(`drone` 空无人机 + 四工具变体);右击放置(点击点自由放置,不对齐网格)、潜行右击打开设置菜单;实现 `CreativeVariantPickerItem` 提供创造物品栏 4x4 工具选择;`byToolId` 供回收/入库找回物品变体
- `recipe/DroneAssemblyRecipe` + `DronePropellerIngredient`:装配配方把两格螺旋桨完整 ItemStack 写入成品;螺旋桨格按 `molded_plastic` 组件的 `finalType == propeller` 匹配

### 无人机站

- `block/DroneStationBlock`:FACING + POWERED,完整方块碰撞,`onRemove` 掉出全部槽位与入库托管数据
- `block/entity/DroneStationBlockEntity`:18 槽(0..15 无人机 / 16 磁盘 / 17 电容器)`ItemStackHandler`;容量 `SuperCapacitorItem.ENERGY`;`IPowerConsumer` 未满请求 256 kW;电容器整颗消耗/空壳返还(`viewers` 列表追踪打开菜单的玩家);站内每架 8 kW 充电;顶部泊位单通道(`dockingData` 托管 + 20 gt 动画 + 断电冻结);拆下经隐式组件 `station_energy`(`PlasticraftDataComponents.STATION_ENERGY`)保留 FE + loot `CopyComponentsFunction`;**出库接口尚未实现,留给任务协调器(TODO 11)**;下方容器取料留给 TODO 11(下表面唯一物流接口的语义已写进手册与 Tooltip)
- `inventory/DroneStationMenu` + `client/gui/screen/DroneStationScreen` + `network/DroneStationRecallPacket`:18 槽 + 背包 + 能量条 + 召回按钮

### 客户端渲染

- `client/renderer/entity/drone/DroneModel`:主体模型,部件名严格按设计文档 §0 冻结;`DroneToolAttachmentModel` 四个附件独立模型层与贴图;附件关节动画(蟹钳开合等)是**占位**,由后续任务 TODO 驱动
- `DroneRenderDispatcher`:实体/物品/站点 BER 三方共用渲染管线(工具附件选层 + 双桨 `MoldedPlasticMeshRenderer` 渲染 + 反向旋转角);附件注册表 `registerAttachment` 供未来工具扩展
- `DroneItemRenderer`(BEWLR):物品三维显示;`client/renderer/blockentity/DroneStationRenderer`:停泊下沉显示对象(无电不播)
- `client/gui/CreativeVariantPickerOverlay` + `mixin/CreativeModeInventoryScreenMixin`:通用 4x4 创造变体叠加层,十六色塑料与无人机工具选择共用;物品侧接口 `item/CreativeVariantPickerItem`

### 菜单/网络注册

- 菜单:`PlasticraftMenuTypes.DRONE / DRONE_STATION`;网络包:`DroneSettingsPacket`(策略切换)、`DroneStationRecallPacket`(召回),由 AnvilLib `NetworkRegistrar` 按包扫描自动注册

### 蓝图系统 `blueprint/`(TODO 04/05 新增)

| 类 | 职责 |
| --- | --- |
| `StructureSnapshot` / `StructureSnapshotCodec` | 规范快照与原版结构 NBT 互转:YZX 排序、调色板首现序、SHA-256 内容哈希、显式校验报错 |
| `ConstructionStructureLibrary` | 世界结构库 `<world>/anvilcraftplasticraft/structures/<hash>.nbt`,原子写入、内容寻址去重 |
| `ConstructionBlueprintData` | 磁盘组件 `blueprint_task`(哈希/名称/尺寸/来源/内容标记/任务 UUID),字段顺序冻结 |
| `ConstructionJob` / `ConstructionJobIndex` | 服务端任务条目与主世界 SavedData 索引,单玩家单活动在 `activate` 内保证 |
| `ConstructionBlueprintService` | 导入写盘、部署/锚点移动、启动/停止切换、取消,全部所有者校验 |
| `ScannerDiskImporter` | 扫描器磁盘世界文件读取与预览空间到世界对齐归一化(四朝向+上下翻转) |
| `LitematicaImporter` | .litematic 多区域/负轴/位压缩转换为稠密原版结构 NBT,重叠与扩展字段显式报告 |
| `BlueprintUploadTracker` / `BlueprintJobSync` | 分块上传会话(8 MiB 上限)与任务索引全量/增量同步 |
| `client/blueprint/*` | 部署会话状态机、任务/快照客户端缓存、文件目录与导入界面、HUD 工具条 |
| `client/renderer/blueprint/BlueprintProjectionRenderer` | 投影网格烘焙缓存(哈希+旋转+镜像+分层),纯 BE 方块粒子贴图占位盒 |
| `mixin/DiskSlotClickMixin` / `DiskSlotTintMixin` | 菜单内右击启动/停止覆盖与槽底状态色程序绘制 |
| `event/ConstructionBlueprintEvents` | 结构方块复制、扫描器转换、导入界面入口与登录全量同步 |

## 3. 稳定契约(不得破坏)

- 注册名:实体 `drone`;物品 `drone, construction_drone, demolition_drone, collection_drone, observation_drone`;方块 `drone_station`;配方 ID 与物品同名;组件 `molded_plastic, drone_data, station_energy`
- `DroneData` 字段顺序、`DroneFlightState` 字节值、模型部件名、贴图/GUI 资源路径(设计文档 §0 冻结清单)
- 无人机站**没有合成配方**:设计文档未定义站的配方,未擅自发明;若需要生存获取方式请先补设计文档
- 能量数值全部引用 AnvilCraft 常量(`CapacitorItem.ENERGY`、`SuperCapacitorItem.ENERGY`、`powerConverterEfficiency`),不复制魔法数

## 4. 资产工具

`art/tools/DroneAssetGenerator.java`(单文件,仓库根目录运行 `java art/tools/DroneAssetGenerator.java`)一次性生成:实体主体与四附件贴图、`.bbmodel` 源文件(base64 内嵌贴图)、无人机/站点 GUI 背景、`pause/skip/return_home` 按钮(四帧竖排:正常/悬停/按下(选中)/禁用)、站体有电/无电方块贴图。

**几何数值必须与 `DroneModel`/`DroneToolAttachmentModel` 手工同步**(文件头有注释)。玩家重做美术时:替换 PNG 即可,UV 布局变化才需要同步改 Java 模型 `texOffs`;GUI 布局改动需同步 `DroneScreen`/`DroneStationScreen`/`DroneStationMenu` 中的坐标常量。

## 5. 重要经验(GameTest 陷阱)

1. `@EmptyTemplate(floor = true)` 的铁块地板占据 helper 相对坐标 **y=1**,可用地面顶面在 **y=2**——实体 spawn、setBlock 都要从 y=2 起算(历史上曾因此把实体生成进夹缝)
2. vanilla `GameTestInfo.succeed()` 会丢弃结构 bounds 外扩 1 格内的所有实体;测试断言不要写"实体必须存活",要断言位置/数据语义,或精确引用自己的实体
3. GameTest 结构间距仅 5 格:任何大范围世界操作(如召回的 16 格扫描)会波及邻居测试,测试中应直接调用窄接口(如 `startDockingTo`),把大范围行为留给游戏内验收
4. `succeedOnTickWhen` 要求条件在目标 tick 前必须失败;"持续不变式"用 `onEachTick` + `runAfterDelay(n, helper::succeed)`
5. 全量验证命令:`./gradlew runData`(资产变化时,检查 `src/generated` 差异)→ `./gradlew build` → `./gradlew runGameTestServer`;严禁 UI 自动化操作 Minecraft 客户端

## 6. TODO 04 切入点提示

- TODO 04(原版结构蓝图、世界部署与结构磁盘交互)与站点无依赖冲突,依赖仅为"无"(可并行 TODO 01-03 的产物)
- 结构磁盘物品是 AnvilCraft `DiskItem`(站内 `DISK_SLOT` 已按它过滤);成型舱侧已有磁盘读写先例:`molding/blueprint/MoldingBlueprintDisk`、`MoldingBlueprintCodec`(结构磁盘嵌入数据 + SHA-256 哈希),TODO 04 的任务 UUID/哈希关联可参考该模式
- 设计文档 §2 强调:蓝图规范快照最终使用原版结构 NBT 语义;"现有 AnvilCraft 结构保存流程若仍省略方块实体 NBT 或固定写出空 entities,必须先补齐"是 TODO 04 的前置检查项
- 施工投影的虚拟碰撞(TODO 06)应抽取 `entity/collision/BondedPlasticShapeIndex` + `mixin/BlockCollisionsMixin` 的索引注入边界,设计文档 §3 已指明

## 7. 已知待办与注意点

- 无人机附件的动作动画(蟹钳张开/抓取、锯片切割、磁铁吸附、望远镜伸缩)只有静态姿态与 `DATA_ACTION_STATE` 同步字节占位,等任务系统 TODO 驱动
- 无人机 GUI 的观察类"区块加载状态"是占位文案,TODO 15 接入真实覆盖状态
- 收集无人机九格库存在菜单中只读展示,取放语义由 TODO 08 决定
- 站点 `recallNearbyDrones` 是管理/测试入口;任务化的入出库调度(站属无人机、低电自动返站)属 TODO 05+ 的任务系统范畴
- DOCKING 飞行是无寻路直线 + 遇阻爬升的基础版,TODO 12 升级为分层规划器时替换
- JEI 中装配配方的成品无人机显示为无桨机体(配方 result 不带组件);如需展示默认桨,改 `PlasticraftRecipeData.generateDroneRecipe` 的 result 构造(注意配方 JSON 体积)
