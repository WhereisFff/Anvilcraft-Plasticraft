---
navigation:
  title: "机械无人机"
  icon: "anvilcraftplasticraft:construction_drone"
categories:
  - tools
items:
  - anvilcraftplasticraft:drone
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

创造物品栏只展示一架装好默认白色螺旋桨、未安装工具的空无人机；像十六色塑料一样右击该条目会打开 4x4 选择叠加层，可以直接取出空无人机或四种工具变体。

## 螺旋桨

配方顶行两角的螺旋桨必须是塑料成型舱制作的“螺旋桨”类型制品。装配时两个螺旋桨的完整物品数据分别保存进无人机，各自保留玩家制作的模型、颜色与材质，不会合并成同一外观；实体两侧与物品形态都会按原样渲染这两个螺旋桨。任意一个螺旋桨具备某种材料特性时，整架无人机获得该特性；后续加入耐热塑料后，任意一侧耐热螺旋桨即可使整机防火。

## 放置与回收

- 手持无人机物品右击方块面，会在点击位置放出无人机实体；实体不对齐方块网格，一格空间内最多可以堆放 8 架
- 无人机实体的碰撞箱固定为 0.5 × 0.5 × 0.5 格，玩家可以站立在无人机上，也可以推动它；无人机被推动后不会穿过方块或其他实体
- 潜行并手持任意铁砧锤右击无人机，可将其回收为对应的物品；工具、两个螺旋桨、能量、设置与所有者数据在物品与实体之间完整往返
- 直接攻击无人机会使其掉落自身物品，数据同样保留；创造模式攻击直接移除

## 能源与飞行

- 每架无人机的能量容量跟随本体<ref item="anvilcraft:capacitor"/>当前容量，为 8,000,000 FE
- 能耗模型：总消耗 = 256 FE × 空中 gt + 256 FE × 实际飞行格数 + 2,560 FE × 瞬时操作次数；距离按服务端实际轨迹累计，落地静止不扣悬浮 FE
- 处在任意有效电网范围内时自动充电，每架未满电无人机只请求 8 功率，按当前功率转换效率充入内部 FE（默认 800 FE/gt）；悬浮、移动和瞬时操作先扣内部 FE
- 无任务时普通无人机落地等待；观察无人机有电即起飞，让碰撞箱底面保持在当前位置下方最近稳定、非流体碰撞顶面之上 4 格悬停，头顶受阻则回到地面等待
- 建设无人机发现范围是到最近可执行施工目标的直线距离 128 格，触及距离 1 格；从所有者背包取料飞到接近位交付施工投影，再飞回玩家；全部交付完成后先飞离蓝图范围再降落，不在工地原地落下；所有者在工地外时飞回其身边；取料与返还不计瞬时费，每次交付扣除 2,560 FE
- 已交付的施工投影在世界格仍是空气、没有方块实体，但外观与该方块在世界中一致（木头就是不透明木板），并提供真实碰撞，可以站上去；未交付全息可以穿过；空碰撞目标（如红石粉）不产生假碰撞
- 暂停任务会停止派发；已取料的无人机会飞回所有者身边，把在途材料塞进背包后再降落，已交付假方块保留；取消同样先让在途无人机飞回还物，再按进度安静提交已交付方块，未交付格子保持世界原状
- 观察无人机始终保留 20,480 FE 的安全降落储备，电量降到储备立即降落停耗；低于 40,960 FE 的起飞门槛不会起飞
- 剩余能量不足以覆盖任务报价加安全降落储备的无人机会拒绝任务

## 单机设置界面

- 右击无人机实体，或手持无人机物品潜行右击，打开同一份单机设置界面；两种形态读写同一份数据，回收后设置保持
- 界面显示已安装工具、所有者、飞行状态、等待原因与能量，建设无人机额外显示托管携带物；能量条与塑料成型舱同一规格
- 建设与拆除无人机可切换缺料与缺拆除能力策略：暂停等待（默认）或跳过
- 收集无人机额外显示九格收集库存；观察无人机额外显示区块加载状态
