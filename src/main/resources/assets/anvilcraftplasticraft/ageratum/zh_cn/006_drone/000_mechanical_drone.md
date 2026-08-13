---
navigation:
  title: "机械无人机"
  icon: "anvilcraftplasticraft:construction_drone"
categories:
  - tools
items:
  - anvilcraftplasticraft:construction_drone
  - anvilcraftplasticraft:demolition_drone
  - anvilcraftplasticraft:collection_drone
  - anvilcraftplasticraft:observation_drone
---

# 机械无人机

<recipe id="anvilcraftplasticraft:construction_drone"/>
<recipe id="anvilcraftplasticraft:demolition_drone"/>
<recipe id="anvilcraftplasticraft:collection_drone"/>
<recipe id="anvilcraftplasticraft:observation_drone"/>

机械无人机由两个塑料螺旋桨、<ref item="anvilcraft:ionocraft"/>、<ref item="anvilcraft:magnetoelectric_core"/>、<ref item="anvilcraft:processor"/>、<ref item="anvilcraft:capacitor"/>和一件工具装配而成。四种配方只有第三行第一格的工具不同：<ref item="anvilcraft:crab_claw"/>产出建设无人机，<ref item="minecraft:stonecutter"/>产出拆除无人机，<ref item="anvilcraft:magnet"/>产出收集无人机，<ref item="minecraft:spyglass"/>产出观察无人机。四种无人机是同一种机械实体上安装不同工具附件的变体，机身、双螺旋桨与工具附件在物品栏、手持、掉落物和物品展示框中都显示完整的三维外观。

## 螺旋桨

配方顶行两角的螺旋桨必须是塑料成型舱制作的“螺旋桨”类型制品。装配时两个螺旋桨的完整物品数据分别保存进无人机，各自保留玩家制作的模型、颜色与材质，不会合并成同一外观；实体两侧与物品形态都会按原样渲染这两个螺旋桨。任意一个螺旋桨具备某种材料特性时，整架无人机获得该特性；后续加入耐热塑料后，任意一侧耐热螺旋桨即可使整机防火。

## 放置与回收

- 手持无人机物品右击方块面，会在点击位置放出无人机实体；实体不对齐方块网格，一格空间内最多可以堆放 8 架
- 无人机实体的碰撞箱固定为 0.5 × 0.5 × 0.5 格，玩家可以站立在无人机上，也可以推动它；无人机被推动后不会穿过方块或其他实体
- 潜行并手持任意铁砧锤右击无人机，可将其回收为对应的物品；工具、两个螺旋桨、能量、设置与所有者数据在物品与实体之间完整往返
- 直接攻击无人机会使其掉落自身物品，数据同样保留；创造模式攻击直接移除

无人机的能源、飞行与任务能力由后续版本逐步开放。
