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

熔体通过环境催化生成：把催化剂丢进液体里，再给对环境，它自己会变。JEI 的“塑料熔体催化”分类会显示下面四条路线；查询流体或熔体桶的配方都能找到，查看催化剂用途也能找到对应路线。皇家材料与浮霜材料分开展示，以区分环境要求和效率；催化剂不会被消耗，页面中的 1000mB 只是等量转换示例，悬停页面底部可查看批量规则和分支优先级

JEI 页面用流体方块模型展示输入与输出，在输入流体正下方紧贴着轮播有效的热源或寒冷方块，催化剂位于模型下方，效率显示在催化剂右侧。热源展示的是实际能够提供热量的状态，悬停可查看方块名称、环境要求和热量；浮霜金属的工程塑料路线不需要下方环境方块

| 输入 | 催化剂 | 环境 | 输出 |
| --- | --- | --- | --- |
| 塑料油 | 皇家钢或浮霜金属 | 从正下方受热 | 通用塑料熔体 |
| 塑料油 | 皇家玻璃或浮霜玻璃 | 从正下方受热 | 透明塑料熔体 |
| 通用塑料熔体 | 皇家钢或浮霜金属 | 皇家钢需要正下方寒冷 | 工程塑料熔体 |
| 通用塑料熔体 | 余烬金属 | 正下方有热源 | 耐热塑料熔体 |

能开放催化的容器不少：装着塑料油或通用塑料熔体的炼药锅、鱼缸、大型炼药锅、开口朝上的硬化树脂炼药锅和塑料炼药锅，连世界里的流体源都算

普通方块容器和世界流体源看的是容器格正下方。开口朝上的硬化树脂炼药锅和塑料炼药锅则从实际锅底的中心和全部覆盖范围沿当前重力方向在一格内查询；中心下方命中的有效加工方块优先，其余锅底覆盖到的加工方块依次作为兜底。所以小锅可以和营火挤在同一格，高过一格的锅也仍然读得到实际锅底外的加工方块

## 催化剂数量

催化只数种类，不数数量——同一种堆一箱也不会更快。皇家钢、皇家玻璃和余烬金属算完整效率，浮霜金属和浮霜玻璃只算一半。多凑几种确实能加速，但边际收益越来越小；好消息是催化剂一个都不会被消耗

皇家或余烬材料只有一种时倍率是 0.25，八种时 0.5，天花板是 0.55；浮霜材料取对应倍率的一半

皇家和浮霜成对的材料（两种玻璃也算），设各自种类数为 `R` 和 `F`，混合倍率就是 `M(R) + 0.5 × (M(R + F) - M(R))`，其中 `M(n) = min(0.55, 0.25 + log2(n) / 12)`，`M(0) = 0`

## 塑料油催化

塑料油必须从正下方受热，热源的实际热量越高推进越快。皇家钢和浮霜金属把它变成通用塑料熔体；但只要锅里同时有皇家玻璃或浮霜玻璃，透明分支就会抢先

透明塑料熔体支持全部 16 色染色，颜色会一路带到成型、冷却和装桶。代价是它到此为止，不能再往金属那条工程塑料分支走

## 工程塑料熔体

- 皇家钢挑食：只有熔体正下方是寒冷方块时才生效。一种要 400gt，八种要 200gt
- 浮霜金属不挑，冷却和热源都不要，但效率只有一半。一种要 800gt，八种要 400gt

## 耐热塑料熔体

耐热分支要至少一种余烬金属，再加正下方的热源。热量越高、余烬金属种类越多就越快，同样是种类越多、收益越小

工程和耐热的条件同时满足时，耐热分支优先

## 批量与染色

大型炼药锅算的是底部正下方 3x3 九格实际热量的平均值，一次能处理一整层流体。工程分支的寒冷条件是个例外，它只读正中间那一格

四种塑料熔体都能染色，催化过程会保留熔体的液量和颜色。透明塑料制品长得像染色玻璃，各色制品也会像对应的染色玻璃一样，为穿过它的信标光柱着色
