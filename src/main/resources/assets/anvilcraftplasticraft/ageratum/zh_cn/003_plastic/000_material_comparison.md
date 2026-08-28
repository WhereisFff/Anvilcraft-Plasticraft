---
navigation:
  title: "材料比较"
  icon: "anvilcraftplasticraft:universal_plastic"
items:
  - anvilcraftplasticraft:universal_plastic
  - anvilcraftplasticraft:universal_plastic_granule
  - anvilcraftplasticraft:engineering_plastic
  - anvilcraftplasticraft:engineering_plastic_granule
  - anvilcraftplasticraft:clear_plastic
  - anvilcraftplasticraft:clear_plastic_granule
  - anvilcraftplasticraft:heat_resistant_plastic
  - anvilcraftplasticraft:heat_resistant_plastic_granule
---

# 材料比较

<row halign="center">
<item id="anvilcraftplasticraft:universal_plastic"/>
<item id="anvilcraftplasticraft:engineering_plastic"/>
<item id="anvilcraftplasticraft:clear_plastic"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic"/>
</row>

四种塑料都能染 16 色，也都能拿来做可移动制品。熔体、熔体桶和塑料粒各用自己材料的色板，透明塑料看起来就像染色玻璃。下表的硬度和爆炸抗性是对应塑料方块的数值：

| 材料 | 染色 | 硬度 / 爆炸抗性 | 独有能力 |
| --- | --- | ---: | --- |
| 通用塑料 | 16 色 | 1.5 / 3.0 | 无额外能力 |
| 工程塑料 | 16 色 | 2.5 / 6.0 | 更坚固；专用制品可以提供精准采集拆除 |
| 透明塑料 | 16 色（染色玻璃外观） | 1.5 / 3.0 | 幻灵铁砧与激光可以穿过，各色制品可为信标光柱着色 |
| 耐热塑料 | 16 色 | 3.5 / 10.0 | 防火；专用制品可以提供熔炼拆除 |

激光那一项值得单独说说。通用、工程和耐热塑料都会挡住激光；激光从 5 级起能伤到实体，也会破坏命中的通用或工程塑料。透明塑料干脆让激光穿过去，自己免疫激光伤害；耐热塑料挡得住激光，同样免疫激光伤害。另外各色透明塑料制品会像对应的染色玻璃一样，为穿过它们的信标光柱着色

四种熔体分别要什么催化剂、多少热量、要不要寒冷，都在[塑料熔体](../002_refining/010_plastic_melts.md)。要注意材料一旦进了成型舱批次，批次清空前就不能再混别的；颜色也会跟着熔体一路留到制品上

## 冷却与塑料粒

世界里的一格熔体源被上方的雨淋到，或者旁边有水、冰、雪，就会当场凝固成对应材料的 16x16x14px 特殊塑料制品，不会同时掉塑料粒。注意它和创造物品栏里那个 16x16x16px 的标准塑料块不是一回事

想要塑料粒就得配冷却物。每消耗 1000mB 熔体加上下面任意一项，都会得到 16 个同材料塑料粒，并保留熔体颜色：

- 1000mB 水
- 1000mB 细雪
- 配方接受的一个冷却物品

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_water"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_powder_snow"/>
<recipe id="anvilcraftplasticraft:solid_liquid/cool_universal_plastic_melt"/>
</row>

上面这三个配方对四种材料各有一份，[催化压盖](../002_refining/020_catalytic_press_lid.md)也能把熔体压成塑料粒。四种熔体随便用什么染料都能改色，流体种类和数量不变；透明熔体凝固之后依然保持染色玻璃外观

## 珠宝商交易

铁砧工艺的一级珠宝商会随机挑一种颜色，用 2 个绿宝石收 8 个该颜色的<ref item="anvilcraftplasticraft:universal_plastic_granule"/>，每项报价最多做 16 次。工程、透明和耐热塑料粒他一概不收
