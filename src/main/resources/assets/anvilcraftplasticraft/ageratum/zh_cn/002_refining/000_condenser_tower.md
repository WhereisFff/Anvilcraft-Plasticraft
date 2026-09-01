---
navigation:
  title: "冷凝塔"
  icon: "anvilcraftplasticraft:condenser_tower"
items:
  - anvilcraftplasticraft:condenser_tower
  - anvilcraftplasticraft:high_heat_fuel_bucket
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:crude_oil_acid_bucket
---

# 冷凝塔

<row halign="center">
<recipe id="anvilcraftplasticraft:multiblock/condenser_tower"/>
<recipe id="anvilcraftplasticraft:multiblock_conversion/condenser_tower"/>
</row>

一个冷凝塔模块占 3x3x3 格，直接摞在大型炼药锅头上就行。每一层都自带 64B 冷凝液槽、64B 气体缓存，还有四个只出不进的接口

同一座塔最多 5 层连续生效。原油分离要用到前三层，水和经验只用第一层，往上再堆也是摆着看

## 气化速度

- 大型炼药锅底下的普通喷流，每条每 gt 气化 5mB 顶层流体，几条喷流的速度直接相加
- 烧高热燃料能升级成强化喷流，每条每 gt 干掉 50mB，代价是同时烧掉 10mB 高热燃料
- 高热燃料炼药锅每次抽走一层 250mB 高热燃料，把强化喷流续上 50gt；换成其他点燃的流体容器就是连续供给燃料

顺带一提，高热燃料自己也能点着，着火伤害是普通燃料的两倍

## 原油分离

气态原油会自下而上穿过整座塔，前三层刚好把每份 50mB 拆干净：

| 层 | 输入 | 输出 |
| --- | ---: | ---: |
| 第一层 | 10mB 气态原油 | 10mB <ref item="anvilcraftplasticraft:high_heat_fuel_bucket"/> |
| 第二层 | 30mB 气态原油 | 30mB <ref item="anvilcraftplasticraft:plastic_oil_bucket"/> |
| 第三层 | 10mB 气态原油 | 10mB <ref item="anvilcraftplasticraft:crude_oil_acid_bucket"/> |

第三层的原油精华别急着丢，它是个放大器：1B 原油精华配 1B 高热燃料，混出 3B 高热燃料；换成塑料油就是 3B 塑料油

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/high_heat_fuel_enrichment"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/plastic_oil_enrichment"/>
</row>

## 水与经验

第一层还能顺手做这两件事：

| 输入 | 输出 |
| ---: | ---: |
| 50mB 气态水 | 50mB 水 |
| 50mB 气态经验 | 50mB 经验流体 |

没被接住的气态经验会往外飘，开放出口水平三格、上方三格内的玩家或有职业的成年村民都能吸。没装冷凝塔时，大型炼药锅每个开放的出口都在冒；装了塔之后只有最高层开放的那个中心出口会冒，站在塔底下隔着塔是吸不到的。你每吸 10mB 换 1 点经验，村民累计吸满 64B 能从新手一路升到大师；同时有多个目标就均分气体

逸散的水蒸气会顺手扑灭同一范围里的火焰、营火、蜡烛和着火的实体。原油蒸气则相反，它会攒成一团能点着的临时云

## 蒸汽背压

塔顶敞着的时候，多余蒸汽直接放跑，什么事都不会发生。可一旦你用完整方块封住最高层的中心出口，只要任意一层的冷凝液槽和气体缓存同时装满，背压就会掐停整条产线继续气化

没有冷凝塔时，把大型炼药锅顶部 3x3 的全部出口封死也是同样效果。背压会顺手移除并熄灭正在工作的喷流，所以清掉阻挡、排空冷凝塔之后，你还得重新建立一次喷流才能恢复运行
