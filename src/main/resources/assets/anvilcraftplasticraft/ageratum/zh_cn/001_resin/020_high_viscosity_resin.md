---
navigation:
  title: "高粘性树脂"
  icon: "anvilcraftplasticraft:high_viscosity_resin_block"
items:
  - anvilcraftplasticraft:liquid_high_viscosity_resin_bucket
  - anvilcraftplasticraft:high_viscosity_resin_block
---

# 高粘性树脂

## 液态高粘性树脂

在装有一桶水的炼药锅或鱼缸中进行快速烹饪，每次可以得到 1000mB 液态高粘性树脂

<recipe id="anvilcraftplasticraft:fast_cooking/liquid_high_viscosity_resin"/>

- 流体每 40gt 向外流动一格，最远离开源头两格
- 普通实体会被完全粘住，玩家仍能以蜘蛛网中的速度移动
- 流体不会挥发，可以使用桶、鱼缸、流体管道或硬化树脂炼药锅搬运

## 树脂胶

手持液态高粘性树脂桶右击实体可以选择它；选中的整体也可以包含完整的 3x3x3 下落巨型铁砧。再右击方块或另一个实体即可粘合，路线颜色表示能否确认本次粘合：

| 颜色 | 与目标的直线距离 | 结果 |
| --- | ---: | --- |
| 绿色 | 不超过 12 格 | 可以粘合 |
| 黄色 | 超过 12 格且不超过 16 格 | 可以粘合 |
| 红色 | 无可用路线或超过 16 格 | 无法粘合 |

与所选实体相距超过 20 格时会清除选择

未选择实体时，0.5 秒内松开右键仍会正常使用可交互方块，长按则会留下裸露的树脂胶；不可交互方块会立即涂胶。实体碰到胶面会被粘住，在胶面前放置方块也会把相邻方块粘在一起

- 活塞和滑轨会将粘合方块组整体移动
- 未固定的粘接实体组受击退时会整体移动；固定到方块后，整组会围绕粘接点回弹
- 喷溅型或滞留型隐身药水可以永久隐藏命中点水平 4 格、竖直 2 格范围内的树脂胶，但不会解除粘合

## 高粘性树脂块

对一整桶液态高粘性树脂进行时移，可以得到高粘性树脂块

<recipe id="anvilcraftplasticraft:time_warp/high_viscosity_resin_block"/>

- 保留树脂块的弹跳、减速、生物捕获、刷怪笼与时移能力
- 捕获生物没有体型限制，敌对生物仍需先获得虚弱效果
- 像粘液块一样粘住相邻方块；由高粘性树脂连接的整组方块，在活塞推动上限中只计一个
