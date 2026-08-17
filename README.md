# 铁砧工艺：塑料工艺

[![Build](https://github.com/WhereisFff/Anvilcraft-Plasticraft/actions/workflows/ci.yml/badge.svg)](https://github.com/WhereisFff/Anvilcraft-Plasticraft/actions/workflows/ci.yml)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1-E78A3A)
[![License: LGPL-3.0-or-later](https://img.shields.io/badge/License-LGPL--3.0--or--later-4C7EAF)](LICENSE)

**铁砧工艺：塑料工艺（Anvilcraft: Plasticraft）** 是一个正在开发中的
[铁砧工艺（AnvilCraft）](https://github.com/Anvil-Dev/AnvilCraft) NeoForge 附属模组。
模组以塑料为媒介，横向扩展铁砧工艺的灵活性与可玩性。
每一种物品和方块都会尽可能回应玩家的小巧思，让推动、碰撞、粘附、包裹、加热和落砧等交互都有明确反馈。

## 当前内容

- **树脂与粘接**：弹性树脂铁砧、硬化树脂铁砧、高粘性树脂及其移动、捕获和粘接机制
- **原油分馏**：冷凝塔将气态原油分离为高热燃料、塑料油和原油精华
- **四种塑料**：通用、工程、透明、耐热塑料均支持 16 色，分别提供精准采集、信标着色、耐火等材料特性
- **塑料成型**：在成型舱编辑或导入模型，通过铸造或 3D 打印生产塑料实体与制品
- **悦灵施工**：为悦灵戴上安全帽后，可由休息室使用结构磁盘完成蓝图施工、拆除与收集；安装 FTB Teams 时同队成员可协作管理

配方、数值和操作说明见游戏内 Ageratum 手册；JEI 与 Jade 可提供配方和状态查询。项目仍在开发中，内容可能调整。

## 运行要求

| 项目 | 版本 / 说明 |
| --- | --- |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1+ |
| 前置模组 | AnvilCraft 1.6.0+、AnvilLib 2.0.0+ |
| 可选模组 | JEI、Jade、Ageratum、FTB Teams |

将构建产物和所需前置模组放入游戏实例的 `mods` 目录。

## 构建与开发

项目使用 JDK 21 和 Gradle Wrapper：

```powershell
.\gradlew.bat build
.\gradlew.bat runData
.\gradlew.bat runGameTestServer
```

Linux 或 macOS 使用 `./gradlew build`。构建产物位于 `build/libs`。

## 贡献者

[WhereisFff](https://github.com/WhereisFff)（策划、程序、美术）、[Leaden-TP](https://github.com/Leaden-TP)（美术）、[XeKr](https://github.com/XeKr)（美术）

通过 [Issue](https://github.com/WhereisFff/Anvilcraft-Plasticraft/issues) 反馈问题或提交 Pull Request 前，请阅读[贡献指南](CONTRIBUTING.md)。

## 赞助者

<table>
  <tr>
    <td align="center">
      <img src="docs/assets/sponsors/yanqiu-lumia.jpg" width="96" height="96" alt="言秋Lumia 的头像"><br>
      <strong>言秋Lumia</strong>
    </td>
    <td align="center">
      <img src="docs/assets/sponsors/pi.jpg" width="96" height="96" alt="Π 的头像"><br>
      <strong>Π</strong>
    </td>
  </tr>
</table>

## 许可

代码采用 [LGPL-3.0-or-later](LICENSE)；美术等非代码资源采用 [ASSETS_LICENSE](ASSETS_LICENSE) 中的条款。
