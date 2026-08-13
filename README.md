# 铁砧工艺：塑料工艺

[English](README_en.md)

[![Build](https://github.com/WhereisFff/Anvilcraft-Plasticraft/actions/workflows/ci.yml/badge.svg)](https://github.com/WhereisFff/Anvilcraft-Plasticraft/actions/workflows/ci.yml)
![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1-E78A3A)
[![License: LGPL-3.0-or-later](https://img.shields.io/badge/License-LGPL--3.0--or--later-4C7EAF)](LICENSE)

**铁砧工艺：塑料工艺（Anvilcraft: Plasticraft）** 是一个正在开发中的
[铁砧工艺（AnvilCraft）](https://github.com/Anvil-Dev/AnvilCraft) NeoForge 附属模组。
模组以塑料为媒介，横向扩展铁砧工艺的灵活性与可玩性。
每一种物品和方块都会尽可能回应玩家的小巧思，让推动、碰撞、粘附、包裹、加热和落砧等交互都有明确反馈。

塑料的材料特性会直接成为玩法：树脂铁砧可以弹来弹去，高粘性树脂能把几乎任何东西粘在方块或其他实体上，
未来的塑料外壳则可以包裹不同物品、方块与实体。材料层级用于逐步解锁这些能力，而不是单纯增加一条更长的加工链。

## 当前内容

目前的首个可玩阶段围绕树脂制品展开：

- 树脂铁砧与硬化树脂铁砧：以可移动实体存在，能够受重力、推动、碰撞、浮力与磁力影响。
- 六向放置与连续推动：铁砧可依附不同表面，并能在碰撞中推动其他塑料制品。
- 原版铁砧流程：硬化树脂铁砧支持修复、附魔合并与重命名；仅重命名时不消耗经验。
- 树脂捕获玩法：树脂铁砧可保存生物，并与铁砧工艺已有的树脂、怨念和时移机制联动。
- 硬化树脂锅：可配合移动铁砧，从不同方向触发铁砧工艺的世界内配方。
- 液态高粘性树脂：可在水锅或鱼缸中快速烹饪获得，缓慢流动两格并粘住接触的实体。
- 高粘性树脂桶：可牵引实体并将其粘在方块或其他实体上；未选择实体时可以放胶。
- 裸露树脂胶：接触胶面的实体和随后放在胶面上的方块都会被粘住，形成的方块组可由活塞或滑轨整体推动；粘在滑轨上的塑料实体方块化后会无视该滑轨；支持 3×3×3 的下落巨型铁砧，实体粘合组会平滑移动。
- 下落方块粘附：除本模组自带的持久化树脂锅与树脂砧外，普通下落方块粘到实体后不再于 30 秒后掉落；接近可着陆位置时仍会正常方块化，并保留与实体的粘合。
- 高粘性树脂块：保留本体树脂机制且不限制捕获体型，提供双向活塞粘连与分组推动预算。
- JEI、Jade 与 Ageratum 集成，便于查询配方、状态和模组手册。

项目仍处于早期开发阶段，现有玩法、配方和美术资源均可能继续调整。

## 设计方向

塑料工艺使用三级材料层级组织内容，并在最终与超限材料结合制作终极工具：

```text
树脂与硬化树脂
  -> 通用及功能塑料
      -> 与铁砧工艺材料结合的复合塑料
```

这些层级只负责组织材料与能力的解锁，不代表模组要把本体主线向后延伸。后续内容会围绕原油分馏、
塑料加工与回收，以及耐热、低温、光学和电气等材料特性，为已有的落砧、搬运、自动化、容纳与环境交互增加横向分支。
详细方案见[材料与内容设计文档](docs/plastic-materials-and-process-design.zh_cn.md)。

## 运行要求

| 项目 | 版本 / 说明 |
| --- | --- |
| Minecraft | 1.21.1 |
| 模组加载器 | NeoForge 21.1 或更高的 1.21.1 版本 |
| 前置模组 | AnvilCraft 1.6.0+、AnvilLib 2.0.0+ |
| 可选模组 | JEI 19.32.0+、Jade 15.3.4+、Ageratum 0.0.1+ |

将构建得到的模组文件与所需前置模组一同放入游戏实例的 `mods` 目录即可。

## 构建与开发

项目使用 JDK 21 和 Gradle Wrapper。克隆仓库后运行：

```powershell
.\gradlew.bat build
```

Linux 或 macOS：

```bash
./gradlew build
```

构建产物位于 `build/libs`。修改注册项或资源后，应先运行 `runData`；修改玩法逻辑后，还应运行 GameTest：

```powershell
.\gradlew.bat runData
.\gradlew.bat runGameTestServer
```

AnvilCraft 本体通过 `gradle/libs.versions.toml` 中的远程 Maven 构件提供，构建时无需准备本地 AnvilCraft JAR。

## 贡献者

| 贡献者 | 分工       |
| --- |----------|
| [WhereisFff](https://github.com/WhereisFff) | 策划、程序、美术 |
| [Leaden-TP](https://github.com/Leaden-TP) | 美术       |

欢迎通过 [Issue](https://github.com/WhereisFff/Anvilcraft-Plasticraft/issues) 反馈问题或提出建议，也欢迎提交 Pull Request。
参与开发前请先阅读[贡献指南](CONTRIBUTING.md)。

## 赞助者

感谢以下赞助者对塑料工艺开发的支持：

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

除非另有说明，项目代码采用 [LGPL-3.0-or-later](LICENSE) 许可；美术等非代码资源采用
[ASSETS_LICENSE](ASSETS_LICENSE) 中的条款，目前为保留所有权利。
