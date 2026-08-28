---
navigation:
  title: "成型舱"
  icon: "anvilcraftplasticraft:plastic_molding_chamber"
items:
  - anvilcraftplasticraft:plastic_molding_chamber
---

# 塑料成型舱

<recipe id="anvilcraftplasticraft:plastic_molding_chamber"/>

## 成型区域

把 <ref item="anvilcraftplasticraft:plastic_molding_chamber"/> 放下之后，方块背面会留出一个 3x3x3 成型区域，成品就在那里出现。模型坐标以像素为单位：普通工作空间是 48x48x48px，一个世界方块折 16px。模型里既能放实体积 Cube，也能放零厚度平面

两种成型方式的尺寸门槛不一样。铸造成型要求模型每个轴的外接尺寸都不超过 48px；3D 打印更严，模型得挤进 32x32x32px 的打印区域，X/Z 四周各留 8px，底部留出 1px 空间。超过限制的模型仍可载入和编辑，只是锁不上、加工不了

## 建立周期

编辑状态下至少加入一个 Cube，然后锁定模型。锁定的那一刻，本周期内的模型和成型方式就定死了，系统会照模型形状和成型舱上方的结构确定成型方法。要是模型、材料或结构有一样不满足条件，这个周期不会白吃粘土、熔体和电力

普通模型在没有 3D 打印组件时使用铸造成型；一旦你在成型舱正上方一格装上 <ref item="anvilcraftplasticraft:plastic_3d_printing_component"/>，所有模型都改用 3D 打印。成型方式在锁定时确定，一个周期内不会切换

模型不满足所选制品类型时，还有一条后路：往资源槽里放 <ref item="anvilcraft:multiphase_transcendium"/>，本周期就按所选类型强制加工，而不是直接拒绝。它突破不了铸造或打印的尺寸限制，而且只在模型确实不符合该类型时才每周期消耗 1 个

## 相关

- [编辑、导入与保存模型](010_model_editor_and_import.md)
- [铸造与 3D 打印](020_casting_and_printing.md)
- [生产模式、平台与物流](030_production_modes.md)
