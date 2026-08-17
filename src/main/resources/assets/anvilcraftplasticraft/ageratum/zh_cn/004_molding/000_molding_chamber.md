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

放置 <ref item="anvilcraftplasticraft:plastic_molding_chamber"/> 后，方块背面会保留一个 3x3x3 成型区域。模型坐标以像素为单位：普通工作空间是 48x48x48px，每个世界方块对应 16px。模型可以包含实体积 Cube 和零厚度平面

铸造成型要求模型每个轴的外接尺寸不超过 48px。3D 打印要求模型位于 32x32x32px 的打印区域内，X/Z 四周各留 8px，底部留出 1px 空间。超过限制的模型仍可载入和编辑，但不能锁定加工

## 建立周期

在编辑状态中加入至少一个 Cube，然后锁定模型。锁定后，本周期内模型和成型方式不再改变，并根据模型形状和成型舱上方的结构确定成型方法。模型、材料和结构不满足条件时，周期不会消耗粘土、熔体或电力

普通模型没有 3D 打印组件时使用铸造成型；在成型舱正上方一格安装 <ref item="anvilcraftplasticraft:plastic_3d_printing_component"/> 后，所有模型改用 3D 打印。成型方式在锁定时确定，一个周期内不会切换

模型不满足所选制品类型时，可以在资源槽放入 <ref item="anvilcraft:multiphase_transcendium"/>，强制按所选类型加工而不是拒绝本周期。它不能突破铸造或打印的尺寸限制，并且只在模型确实不符合该类型时每周期消耗 1 个

## 相关

- [编辑、导入与保存模型](010_model_editor_and_import.md)
- [铸造与 3D 打印](020_casting_and_printing.md)
- [生产模式、平台与物流](030_production_modes.md)
