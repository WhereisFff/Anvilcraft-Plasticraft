# 无人机系统交接(TODO 01 ~ 03)

> 交接时间:2026-08-13;设计规格见 `docs/construction-drone-design.zh_cn.md`(以下简称"设计文档")。
> 接手 TODO 04 及后续任务前,必须完整阅读设计文档与仓库 `AGENTS.md`,并先写该 TODO 的实施 Plan 再动手。

## 1. 当前进度

| TODO | 状态 |
| --- | --- |
| TODO 01 四种无人机基础实体、物品与完整外观 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 02 无人机能源、基础飞行与单机设置 | 已实现,游戏内验收通过,复选框已勾选 |
| TODO 03 无人机站本体、能源、库存与停泊 | 已实现,自动化验证全绿(363 项 GameTest),**游戏内验收待玩家执行**,复选框未勾选 |
| TODO 04+ | 未开始 |

工作流程约定(与玩家确认过):每个 TODO 完成"范围 + 自动化验证"后按 conventional-commits(中文 subject,scope 用 `drone`)提交一次,然后暂停等待玩家游戏内验收,验收通过才继续下一个;验收通过后勾选设计文档中的复选框。玩家已声明后续会自制全新贴图模型并配套改代码,当前程序生成资产只是可用占位。

### TODO 03 游戏内验收清单(待玩家执行)

1. 放置无人机站,断电时显示 `drone_station_off` 外观;接入电网或塞入充电电容器后切换有电外观,拆下再放回内部 FE 保留
2. 电容器槽行为与塑料成型舱一致:整颗消耗、空壳留槽;接近满容量时不消耗
3. 站内放入低电无人机物品,观察其以 800 FE/gt(默认效率)从站点内部充电
4. 在站旁摆多架无人机,点界面召回按钮:依次飞向站顶,严格一秒一架下沉入库,站满后剩余无人机悬停等待、不覆盖槽位;玩家站在站顶不会掉入舱门
5. 入库动画进行中切断电力:模型变无电、下沉画面冻结、数据不丢;恢复后从原进度继续

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
