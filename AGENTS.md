# 仓库协作规范

## 版本与兼容性

- 当前仅维护 Minecraft 1.21.1，同时为未来迁移到 26.1.2 保持清晰的代码边界。
- 除非用户明确要求，否则不考虑旧存档兼容，也不添加迁移代码或旧 NBT 读取逻辑。
- 保持注册名、资源路径、配方 ID 和网络载荷字段顺序稳定。

## 代码约定

- 将通用玩法逻辑放在小型公共类中；NeoForge 注册和客户端逻辑分别隔离在 `init` 与 `client` 包中。
- 注册代码沿用 AnvilCraft 的包结构：方块放在 `init.block`，实体放在 `init.entity`，物品组与标签放在 `init.item`，菜单放在 `init` 根包。
- 优先使用原版和 AnvilCraft 的公开 API、`ResourceLocation.fromNamespaceAndPath` 及注册表 Holder；避免反射、映射专属辅助方法和大范围 Mixin。
- 实体行为不得依赖渲染代码；渲染器只读取实体已同步的显示状态。
- JEI、Jade、Ageratum 等可选集成必须放在独立类中，公共入口不得直接初始化其客户端 API。
- 新增或修改的代码注释使用中文；API 标识符、类名、方法名、资源 ID 和协议关键字可保留原文。

## 资源与数据生成

- `neoforge.mods.toml` 保持在 `src/main/resources/META-INF`，由 `processResources` 展开属性；不要新增独立的模板源码目录。
- `src/generated/resources` 是生成结果。修改 Registrum/DataGen 源码后运行 `./gradlew runData` 并检查差异，不要直接编辑生成的 JSON。

## 验证限制

- 严禁使用 Computer Use、桌面自动化或其他 UI 控制工具启动、聚焦、重载、检查、输入或关闭 Minecraft 客户端，包括用户已启动的客户端。
- 渲染与资源改动仅做静态检查和非客户端构建验证，游戏内视觉效果由用户验收。
- 使用 JDK 21。按改动范围运行 `./gradlew build`、`./gradlew runData` 或 `./gradlew runGameTestServer`，并遵循 `CONTRIBUTING.md`。
