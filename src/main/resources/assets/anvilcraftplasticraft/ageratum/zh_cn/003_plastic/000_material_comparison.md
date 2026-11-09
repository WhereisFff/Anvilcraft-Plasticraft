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
  - anvilcraftplasticraft:elastic_plastic
  - anvilcraftplasticraft:elastic_plastic_granule
---

# 材料比较

<row halign="center">
<item id="anvilcraftplasticraft:universal_plastic"/>
<item id="anvilcraftplasticraft:engineering_plastic"/>
<item id="anvilcraftplasticraft:clear_plastic"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic"/>
<item id="anvilcraftplasticraft:elastic_plastic"/>
</row>

五种塑料都能染 16 色，也都能拿来做可移动制品。熔体、熔体桶和塑料粒各用自己材料的色板，透明塑料看起来就像染色玻璃，弹性塑料则有树脂块一样的半透明外壳与内芯。下表的硬度和爆炸抗性是对应塑料方块的数值：

| 材料 | 染色 | 硬度 / 爆炸抗性 | 独有能力 |
| --- | --- | ---: | --- |
| 通用塑料 | 16 色 | 1.5 / 3.0 | 无额外能力 |
| 工程塑料 | 16 色 | 2.5 / 6.0 | 更坚固；塑料铁砧落在切石机上时提供精准采集 |
| 透明塑料 | 16 色（染色玻璃外观） | 1.5 / 3.0 | 幻灵铁砧与激光可以穿过，各色制品可为信标光柱着色 |
| 耐热塑料 | 16 色 | 3.5 / 10.0 | 防火；塑料铁砧落在切石机上时提供熔炼效果 |
| 弹性塑料 | 16 色（半透明双层外观） | 1.5 / 3.0 | 所有放置后的制品都像树脂铁砧一样回弹，也能弹起落在表面的实体 |

激光那一项值得单独说说。通用、工程、耐热和弹性塑料都会挡住激光；激光从 5 级起能伤到实体，也会破坏命中的通用、工程或弹性塑料。透明塑料干脆让激光穿过去，自己免疫激光伤害；耐热塑料挡得住激光，同样免疫激光伤害。另外各色透明塑料制品会像对应的染色玻璃一样，为穿过它们的信标光柱着色

五种熔体分别要什么催化剂、多少热量、要不要寒冷，都在[塑料熔体](../002_refining/010_plastic_melts.md)。要注意材料一旦进了成型舱批次，批次清空前就不能再混别的；颜色也会跟着熔体一路留到制品上

## 弹性塑料的回弹

弹性塑料块、箱子、储罐、铁砧、炼药锅、大型炼药锅、支架都能回弹；表面光滑，走路时不会施加树脂的踩踏减速，也不会因贴地重力反复小幅弹起。碰撞按模型的实际形状判断，模型空隙不会成为弹跳面

- 制品撞到方块时，碰撞方向的回弹速度为入射速度的 80%；撞到可碰撞实体时为 72%，不额外削减与碰撞面平行的速度
- 滑轨上的重力方向碰撞保留全部沿轨速度；低于 0.06 格/gt 的碰撞不再反弹，同一碰撞面有 3gt 的回弹间隔
- 撞到实体时，传给对方的速度增量最多为 0.45 格/gt
- 落到制品表面的实体以碰撞前法向速度的 80% 回弹，同时清除本次下落距离；生物不超过 0.1 格/gt、其他实体不超过 0.06 格/gt 的碰撞直接落稳，玩家潜行可抑制这种回弹
- 请求位移中的位置补偿不计入回弹速度；起跳后立即离开接地状态，连续回弹会损失能量并最终落稳

弹性塑料不会传导红石，也不会获得透明塑料的激光穿透或信标染色能力

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

上面这三个配方对五种材料各有一份，[催化压盖](../002_refining/020_catalytic_press_lid.md)也能把熔体压成塑料粒。五种熔体随便用什么染料都能改色，流体种类和数量不变；透明熔体凝固之后依然保持染色玻璃外观，弹性熔体凝固后保留半透明双层外观

## 珠宝商交易

铁砧工艺的一级珠宝商会随机挑一种颜色，用 2 个绿宝石收 8 个该颜色的<ref item="anvilcraftplasticraft:universal_plastic_granule"/>，每项报价最多做 16 次。工程、透明、耐热和弹性塑料粒他一概不收
