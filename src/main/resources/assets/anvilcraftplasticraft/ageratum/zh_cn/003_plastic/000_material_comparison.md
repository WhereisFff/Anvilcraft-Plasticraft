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

四种塑料都支持 16 色染色，都能作为可移动制品的材料。熔体、熔体桶和塑料粒均使用各自材料的色板；透明塑料的外观类似染色玻璃；表中的硬度和爆炸抗性是对应塑料方块的数值

| 材料 | 染色 | 硬度 / 爆炸抗性 | 独有能力 |
| --- | --- | ---: | --- |
| 通用塑料 | 16 色 | 1.5 / 3.0 | 无额外能力 |
| 工程塑料 | 16 色 | 2.5 / 6.0 | 更坚固；专用制品可以提供精准采集拆除 |
| 透明塑料 | 16 色（染色玻璃外观） | 1.5 / 3.0 | 幻灵铁砧与激光可以穿过，各色制品可为信标光柱着色 |
| 耐热塑料 | 16 色 | 3.5 / 10.0 | 防火；专用制品可以提供熔炼拆除 |

通用、工程和耐热塑料都会阻挡激光。激光从 5 级起能够伤害实体，也会破坏命中的通用或工程塑料；透明塑料允许激光穿过且免疫激光伤害，耐热塑料则会阻挡激光但同样免疫激光伤害。各色透明塑料制品会像对应染色玻璃一样为穿过它们的信标光柱着色

四种熔体的催化剂、热量和寒冷条件见[塑料熔体](../002_refining/010_plastic_melts.md)。材料一旦进入成型舱批次，批次清空前不能混入其他材料；颜色也会随熔体保留到制品

## 冷却与塑料粒

世界中的一格熔体源遇到上方的雨、相邻水、冰或雪时，会在原地凝固成对应材料的 16x16x14px 特殊塑料制品，不会同时掉落塑料粒。它不同于创造物品栏中的 16x16x16px 标准塑料块

每消耗 1000mB 熔体和以下任一冷却物，都会得到 16 个同材料塑料粒，并保留熔体颜色：

- 1000mB 水
- 1000mB 细雪
- 配方接受的一个冷却物品

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_water"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_powder_snow"/>
<recipe id="anvilcraftplasticraft:solid_liquid/cool_universal_plastic_melt"/>
</row>

上面的配方对四种材料分别存在对应版本；[催化压盖](../002_refining/020_catalytic_press_lid.md)也可以把熔体制成塑料粒。四种熔体都可以用任意染料改变颜色，流体种类和数量不变；透明熔体凝固后保持染色玻璃外观

## 珠宝商交易

铁砧工艺的一级珠宝商会随机选择一种颜色，收购 8 个该颜色的<ref item="anvilcraftplasticraft:universal_plastic_granule"/>并支付 2 个绿宝石；每项报价最多使用 16 次。工程、透明和耐热塑料粒不能用于这项交易
