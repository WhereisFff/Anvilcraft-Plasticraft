---
navigation:
  title: "塑料熔体"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:clear_plastic_melt_bucket
  - anvilcraftplasticraft:engineering_plastic_melt_bucket
  - anvilcraftplasticraft:heat_resistant_plastic_melt_bucket
---

# 塑料熔体

<row halign="center">
<item id="anvilcraftplasticraft:plastic_oil_bucket"/>
<item id="anvilcraftplasticraft:universal_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:clear_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:engineering_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic_melt_bucket"/>
</row>

塑料熔体由环境催化获得，共有四条路线：

| 输入 | 催化剂 | 环境 | 输出 |
| --- | --- | --- | --- |
| 塑料油 | 皇家钢或浮霜金属 | 从正下方受热 | 通用塑料熔体 |
| 塑料油 | 皇家玻璃或浮霜玻璃 | 从正下方受热 | 透明塑料熔体 |
| 通用塑料熔体 | 皇家钢或浮霜金属 | 皇家钢需要正下方寒冷 | 工程塑料熔体 |
| 通用塑料熔体 | 余烬金属 | 正下方有热源 | 耐热塑料熔体 |

装有塑料油或通用塑料熔体的炼药锅、鱼缸、大型炼药锅、开口朝上的硬化树脂炼药锅和世界中的流体源都支持开放催化

## 催化剂数量

开放催化只统计不同物品种类，同种物品叠加不会加速。皇家钢、皇家玻璃和余烬金属按完整效率计算，浮霜金属和浮霜玻璃按一半效率计算。增加种类可以加速，但收益会逐渐降低；催化剂不会被消耗

皇家或余烬材料只有一种时倍率为 0.25，八种时为 0.5，最高为 0.55；浮霜材料使用对应倍率的一半

对皇家与浮霜成对的材料，包括两种玻璃，设各自种类数为 `R` 和 `F`。混合倍率为 `M(R) + 0.5 × (M(R + F) - M(R))`，其中 `M(n) = min(0.55, 0.25 + log2(n) / 12)`，`M(0) = 0`

## 塑料油催化

塑料油必须从正下方受热，并按热源的实际热量推进。皇家钢与浮霜金属产出通用塑料熔体；只要同时存在皇家玻璃或浮霜玻璃，透明分支就会优先

透明塑料熔体支持全部 16 色染色，颜色会沿成型、冷却和装桶流程保留；它不能继续进入金属工程塑料分支

## 工程塑料熔体

- 皇家钢只在熔体正下方为寒冷方块时生效；一种需要 400gt，八种需要 200gt
- 浮霜金属不需要冷却或热源，但效率只有一半；一种需要 800gt，八种需要 400gt

## 耐热塑料熔体

耐热分支需要至少一种余烬金属和正下方的热源。热量越高、余烬金属种类越多，反应越快；增加种类的收益会逐渐降低

同时满足工程与耐热分支时，耐热分支优先

## 批量与染色

大型炼药锅使用底部正下方 3x3 九格实际热量的平均值，并可一次处理整层流体。工程分支的寒冷条件只读取中央方块

四种塑料熔体都可以染色。催化会保留熔体的液量和颜色，透明塑料制品呈现染色玻璃外观；各色制品会像对应染色玻璃一样为信标光柱着色
