---
navigation:
  title: "通用塑料熔体与皇家钢催化"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:catalytic_press_lid
---

# 通用塑料熔体与皇家钢催化

塑料油转化为通用塑料熔体是一种环境反应，而不是配方。只要塑料油、皇家钢制品和有效热源在同一装置中正确接触，反应就会自动推进，因此 JEI 中没有独立的催化压制配方。

## 开放催化

把带有 `anvilcraftplasticraft:royal_steel_items` 标签的物品放进塑料油，并让热源紧贴载体正下方。下列载体均可反应：

- 装有塑料油的四层炼药锅；
- 鱼缸；
- 大型炼药锅；
- 开口朝上的硬化树脂炼药锅；
- 世界中的塑料油流体源。

皇家钢不会被消耗。催化速度按不同物品 ID 的数量计算，同一种物品放入多件仍只算一种。开放反应倍率为 `0.25 + log2(种类数) / 12`，最高为 0.55：一种皇家钢物品是密封压盖速度的 1/4，八种不同物品恰好为 1/2。

热源沿用集热器可识别的热源，以及工作中的燃烧加热器和电加热器。不同热源保留各自的实际热功率，不会被统一替换成某一种热源。

## 大型炼药锅

大型炼药锅读取锅底正下方 3×3 的九个位置，最终热功率为九格实际功率之和除以 9。九格放满同一种热源时与鱼缸使用该热源的速度相同；只有一格时速度为对应鱼缸的 1/9；混合热源则按每一格各自的功率共同计算。一次完成会转化锅中整层塑料油，因此它可以批量处理最多 64 B。

## 催化压盖

<recipe id="anvilcraftplasticraft:catalytic_press_lid"/>

催化压盖本身提供皇家钢催化面。用高粘性树脂把它粘在兼容容器正上方并从下方加热时，不需要再把皇家钢物品放进油中，反应按完整速度推进。反应完成后用下落铁砧压下压臂；熔体会优先从容器输出口或管网排出，无法排出时会冲破容器并弹起压盖。
