# 模型选取与描边配置

客户端通过 `assets/anvilcraftplasticraft/anvillib/cube_selection.json` 启用 Cube 模型选取与描边，须配套使用支持资源规则及 `outlineTarget` 的 AnvilLib Cube。

`model` 使用模型精确选取与描边，`outline` 保留原有选取并绘制模型轮廓，`vanilla` 保留原有选取与描边。
单个方块规则优先于命名空间规则，高优先级资源包逐项覆盖同名规则，同一资源包内按配置资源 ID 字典序合并。
整合包也可通过 `kubejs/assets/example/anvillib/cube_selection.json` 设置规则，按 `F3+T` 重载。

```json
{
  "blocks": {
    "anvilcraftplasticraft:condenser_tower": "outline"
  }
}
```

凝聚塔整机描边遵守上述模式；机器与粘附塑料的动态几何仍由原有 provider 提供，放置预览使用描边几何入口；关闭模型选择或模型不可用时显示方块原有形状。
成型区域是无模型交互占位，保留程序排除，资源包无法强制为其启用模型选取。
这些规则只影响客户端方块选取与描边，不改变塑料实体物理或服务端碰撞。
