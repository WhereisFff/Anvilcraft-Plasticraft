# GameTest 规范

编写或修改 GameTest 前必须先读完本文，并按本文执行。`AGENTS.md` 的 GameTest 小节指向本文。

本文是长期维护的**约束与说明**，不是测试目录、覆盖矩阵或变更记录。不要在本文逐条罗列 `@GameTest` 方法名。

## 怎么跑

使用 JDK 21。不要用 `runClient`，也不要用 Computer Use 或其它 UI 自动化启动 Minecraft 客户端。

Windows：

```powershell
.\gradlew.bat compileGameTestJava
.\gradlew.bat runGameTestServer
```

Unix：`./gradlew compileGameTestJava` 与 `./gradlew runGameTestServer`。`CONTRIBUTING.md` 与 `README.md` 使用同一任务。玩法改动以 `runGameTestServer` 为准；只需确认测试源码能编译时用 `compileGameTestJava`。

`compileGameTestJava` 依赖主源集已编译。主源集因 AnvilCraft API 迁移失败时，不要在 GameTest 里回滚那些 import。冷凝塔蒸汽能力使用本模组 `dev.anvilcraft.plasticraft.vapor`，不要把 yukkuri 加回来。

入口点是 `src/gameTest/java/dev/anvilcraft/plasticraft/gametest/PlasticraftGameTestEntrypoint.java`，测试模组 ID 为 `anvilcraftplasticraft_tests`。运行目录是 `run/gameTestServer`，日志在 `run/gameTestServer/logs`。测试专用配方放在 `src/gameTest/resources/data/anvilcraftplasticraft_tests/`。

两个智能体不要同时跑 Gradle / GameTest：会互抢 `session.lock`、互杀 Java 进程。

涉及区块加载、卸载或服务器生命周期的改动，除了检查测试汇总，还必须确认测试服务器正常结束且 Gradle 返回成功。测试全部通过后仍卡在保存或区块卸载阶段，不算生命周期验证通过。

## 测什么

GameTest 只验证**服务器运行时**的玩法契约：玩家或实体实际执行操作后，世界状态、碰撞、移动、持久化、资源事务和机器状态必须保持正确。

同一公开契约、不同载体，可以放在同一场景里分别断言。失败条件不同的契约必须分开，失败时才能定位。

## 不测什么

不要把下列内容当成独立的 GameTest 回归目标，也不要为它们新开测试类：

- 配方 JSON 的数量、时长、权重、燃烧寿命、交易随机性
- 注册名、资源路径、语言键、手册 Markdown 排版
- 客户端渲染、模型、纹理字节、光影、F3+B、编辑器控件与 CPU 命中测试
- JEI / Jade / Ageratum 等集成 UI
- 某个内部算法的中间值（除非它会直接变成玩家可观察的世界状态）
- 真实联机带宽、延迟、分块卸载竞态（存档往返可用单机 NBT 重载测）
- 真实跨维度传送（传送门测试只验证 AnvilCraft `EntityThroughPortalEvent` 后实体身份）

已删除、不要加回来的同类测试：纹理生成、成型编辑器数学、油气催化公式曲线、熔体静态默认色、冷凝塔产量/燃料寿命/Royal Preference 配方定义、铁砧配方选择与旧 NBT 迁移、凸碰撞调试轮廓与 broad phase。这些运行时契约分别由通用塑料、成型舱/凸碰撞、冷凝塔背压与点燃、生产事务、铁砧/锅交互覆盖。

## 源码组织

全部测试类放在 `src/gameTest/java/dev/anvilcraft/plasticraft/gametest/`，按玩法子系统分文件。没有 `@GameTestGenerator`；参数化矩阵写成同一方法内的循环或私有断言，不要复制 `@GameTest`。

全部使用 NeoForge Test Framework 的 `@EmptyTemplate`，没有独立结构 NBT。需要地板时写 `@EmptyTemplate(..., floor = true)`。`@TestHolder(description = ...)` 用一句话说明该条证明的契约。

## 归类与合并

新增或改测试时，先找到负责同一契约的测试类，优先在已有方法里加断言。

- **归入已有类**：同一状态机、同一机器、同一实体族的服务器行为。
- **可以合成一条**：同一前置、同一结构、只差载体或只差一个断言；失败信息必须写清是哪一载体、哪一相位失败。
- **必须分开**：失败条件不同、时序语义不同、或「解析器认为可以」与「世界里真的发生了」不是同一契约。
- **不要塞进同一条**：无关玩法、不同机器、或需要完全不同超时/模板尺寸的场景。
- **不要删除**：仍无其它测试覆盖的场景。

本文只在约束或子系统职责变化时改。不要为增删方法名而把本文改成清单。

## 禁止再复制

下列场景已经按「同契约多载体」或「失败条件不同必须分开」处理过，禁止再复制一套几乎相同的 `@GameTest`：

- 同一胶粘/隐身/运输契约的方块贴片、拉伸实体胶、锚点实体胶；裸胶与占胶。方块障碍与实体障碍时序不同则分开，不要再为第三种相同障碍复制。
- 活塞：结构解析器收集 ≠ 活塞真的伸出/收回。黏合前与黏合后的杠杆已同场景。
- 锤子、弹跳、头顶产品：同一交互在实体形态与方块化形态上的结果，优先同场景断言。
- 冷却固化的雨/邻水/冷却剂标签；传送门的末地门与下界门。
- 成型打印的斜面像素、占用、碎片面积、旋转二面角——它们共同证明「几何封闭且与占用一致」。
- 托盘红石：比较器、中继器、脉冲发生器是不同 tick 契约，不要合成一条长时序，也不要为每个元件再复制六向端口测试。
- 通用塑料生产：预检失败保资源 ≠ 执行中失败回滚。
- 冷凝塔油气点燃：打火石、火焰弹、扔火把、扔热方块失败条件不同，不要为了少方法而合成参数化。
- 高热燃料：分层锅 / 鱼缸 / 塑料锅 / 大型锅的共享伤害已同场景。成型储罐与成型炼药锅的四材质熔岩储存在 `MoldingProductGameTests` 一条场景里，不要按材质、按容器类型或按 tier 再拆一套。

## 模板、超时、失败信息

- `@EmptyTemplate(floor = true)` 的铁块地板占据 helper 相对坐标 **y=1**，可用地面顶面在 **y=2**。实体 spawn、`setBlock` 都要从 y=2 起算。
- 原版 `GameTestInfo.succeed()` 会丢弃结构 bounds 外扩 1 格内的所有实体。不要断言「实体必须仍存活」；断言位置、数据或精确引用自己的实体。
- GameTest 结构间距仅 5 格。大范围世界操作会波及邻居测试；测试里调用窄接口，把大范围行为留给游戏内验收。
- `timeoutTicks` 必须让 `succeedWhen` / `startSequence` / `runAfterDelay` 在超时前完成。不要靠拉长超时掩盖不确定等待。
- 重启恢复类回归无法在 `runGameTestServer` 里真的重启进程。只能按「世界级 SavedData 存档往返 + 清运行时缓存」模拟，并断言恢复次数与恢复结论，不要断言进程行为。
- `succeedOnTickWhen` 要求条件在目标 tick **之前必须失败**。持续不变式用 `onEachTick` + `runAfterDelay(n, helper::succeed)`。
- 失败用 `GameTestAssertException`，消息写清失败的契约、载体和相位，避免只写 `expected true`。
- 注释只写设计意图，用中文。禁止 `*` import 和正文全限定名。

## 子系统职责

每个类只证明自己这一块的服务器契约，跨系统行为不要依靠复制测试覆盖

- **`AdhesiveBondingGameTests`**：选面、建胶、解胶、运输绕障、活塞/滑轨/磁力带动黏合组、锤子旋转黏合件。证明胶键能建立、保持、在障碍前停下，并被合法外力带动或拆开。
- **`PlasticAnvilGameTests`**：塑料铁砧/锅/树脂冲击、锤子、滑轨、承载与推动。证明这些实体在重力、流体、玩家推动和 AnvilCraft 装置下的世界行为。
- **`PlasticConvexCollisionGameTests`**：凸形 SAT 对玩家、生物、掉落物和塑料实体的可观察结果：齐平碰撞、斜面穿越/站稳、倒置锅安全脱离、窄缝承载。不测调试轮廓或 broad phase 计数。
- **`UniversalPlasticGameTests`**：通用塑料放置、世界冷却固化、锤子旋转对齐碰撞、观察者触发与重载不重复触发、绑定保色与公共物理（含静止实体休眠、方块/邻居/活塞/冲量唤醒与静止不广播运动包）。
- **`PlasticMoldingChamberGameTests`**：成型舱四向结构、会话与历史、黏土/熔体事务、打印几何与蓝图磁盘。证明机器占用、资源与打印结果在服务器上正确，不测编辑器 UI。
- **`PlasticMoldingProductionGameTests`**：成型产品产出事务、活塞中继、整块红石导电。证明生产预检/执行/中断重载，以及产出实体能被活塞和红石按契约对待。
- **`MoldingProductGameTests`**：成型产品在服务器物品上的类型边界、容量、能力编解码与堆叠，以及板条箱的本体仓储存取、合成解锁、相邻搜索、溢出销毁和访问校验。炼药锅的开口面积/最矮壁高/开口过大升级为大型锅，以及成型储罐与成型锅的四材质熔岩储存矩阵都在本类，不要再按材质或按 tier 拆一套。不是客户端编辑器数学。
- **`MoldedPlasticTrayGameTests`**：托盘外形、元件白名单、红石端口与方块化交接后的网络。比较器/中继/脉冲的短 tick 契约在此覆盖；不要再写 20 秒以上的随机红石钟。
- **`MoldedPlasticAnvilGameTests`**：成型铁砧谓词与菜单、落地朝向、巨型多方块与冲击。落地时底面覆盖到的每一格各发布一次落砧事件的契约与落地朝向同场景，不要为覆盖格数另开测试。成型锅单次撞击的落砧加工预算（普通 1 个通道、大型最多 9 组普通堆叠预算），以及大型锅的分区交互、分层流体事务和环境行为也在本类：断言产出、剩余输入与世界状态，不要断言配方管线的中间值。
- **`CondenserTowerGameTests`**：背压、层级连通、经验蒸汽、出口熄火/点燃。蒸汽 API 用本模组 `vapor` 包。
- **`IgnitedFuelGameTests`**：高热燃料容器伤害、手持点火、增强等离子喷口，以及实体锅和粘合方块锅的普通油模拟/执行燃料事务。
- **`UniversalPlasticProductionGameTests`**：锅内催化的温度条件、催化剂保留与流体数量。
- **`PlasticEntityPortalGameTests`**：塑料实体经传送门事件后保持身份；普通下落方块仍转末地尘作为对照。
- **`PlasticEntityItemTransferGameTests`**：塑料锅与 Hopper / 漏斗矿车 / Chute 的物品传输。
- **`HardenedResinCauldronFluidHazardGameTests`**：硬化树脂锅的熔岩储存、世界熔岩伤害、移动后位置与溢出清理。只管这一个固定形状的实体锅；成型锅的四材质熔岩耐受在 `MoldingProductGameTests`，不要搬进来。
- **`CreativeColorVariantGameTests`**：创造十六色条目的公共物品数据。

## 新增或修改时

1. 先在对应子系统类里找同类场景。已有方法能加断言就加，不要新开 `@EmptyTemplate`。
2. 三个问题都为「是、是、否」才新增：是否玩家可观察的服务器行为；改代码后旧行为是否会回归；现有板块是否已有同一状态机场景。
3. 能循环的矩阵不要手写 N 份拷贝。
4. 不要把无关玩法塞进同一个测试。
5. 不要删除仍无其它覆盖的场景。
6. 测试名、超时、`succeedWhen` / `startSequence` / `runAfterDelay` 必须仍能在超时前完成。
7. 若本次改的是玩法，同步 Tooltip / 手册。本文只在约束或子系统职责变化时改。
