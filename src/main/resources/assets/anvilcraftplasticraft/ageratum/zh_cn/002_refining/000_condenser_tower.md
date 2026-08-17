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

每个冷凝塔模块占据 3x3x3 格，可以直接堆叠在大型炼药锅上方。每层都有独立的 64B 冷凝液槽、64B 气体缓存和四个只能向外输出的接口

同一塔组最多有 5 层连续生效；原油分离使用前三层，水和经验只使用第一层

## 气化速度

- 大型炼药锅底部的普通喷流每条每 gt 气化 5mB 顶层流体，多条喷流的速度相加
- 使用高热燃料可以产生强化喷流，每条每 gt 气化 50mB，同时消耗 10mB 高热燃料
- 高热燃料炼药锅每次消耗一层 250mB 高热燃料，并将强化喷流延长 50gt；其他点燃的流体容器会连续供给燃料

高热燃料可以点燃，其着火伤害是普通燃料的两倍

## 原油分离

气态原油会自下而上通过冷凝塔。前三层正好分离每份 50mB 气态原油：

| 层 | 输入 | 输出 |
| --- | ---: | ---: |
| 第一层 | 10mB 气态原油 | 10mB <ref item="anvilcraftplasticraft:high_heat_fuel_bucket"/> |
| 第二层 | 30mB 气态原油 | 30mB <ref item="anvilcraftplasticraft:plastic_oil_bucket"/> |
| 第三层 | 10mB 气态原油 | 10mB <ref item="anvilcraftplasticraft:crude_oil_acid_bucket"/> |

原油精华还可以参与流体混合：1B 原油精华与 1B 高热燃料会得到 3B 高热燃料，换成塑料油时得到 3B 塑料油

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/high_heat_fuel_enrichment"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/plastic_oil_enrichment"/>
</row>

## 水与经验

第一层可以完成以下冷凝：

| 输入 | 输出 |
| ---: | ---: |
| 50mB 气态水 | 50mB 水 |
| 50mB 气态经验 | 50mB 经验流体 |

逸散的气态经验可以被开放出口水平三格、上方三格内的玩家或有职业的成年村民吸收。没有冷凝塔时，每个开放的大型炼药锅出口都可逸散；安装冷凝塔后，只有最高层开放的中心出口会逸散，不能隔着塔从下方吸收。玩家每吸收 10mB 获得 1 点经验，村民累计吸收 64B 可以从新手升至大师；多个目标会均分气体

逸散的水蒸气会熄灭同一范围内的火焰、营火、蜡烛和着火实体。原油蒸气会形成可点燃的临时云团

## 蒸汽背压

冷凝塔顶部开放时，多余蒸汽会直接逸散。用完整方块封住最高层中心出口后，如果任意一层的冷凝液槽与气体缓存都已装满，背压会停止整条产线继续气化

没有冷凝塔时，完整封住大型炼药锅顶部 3x3 的全部出口也会产生背压。背压会移除并熄灭正在工作的喷流；移除阻挡并排空冷凝塔后，还必须重新建立喷流才能恢复运行
