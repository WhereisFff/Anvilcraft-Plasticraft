---
navigation:
  title: "Shared Movable-Product Rules"
  icon: "anvilcraftplasticraft:universal_plastic"
---

# Shared Movable-Product Rules

The special 16x16x14px products cooled directly from world melt and ordinary products made by the molding chamber are movable plastic products. Chests, tanks, anvils, trays and Allay Hard Hats add a function to the same material and shape rules

## Creative inventory

Each plastic material has its own labeled section in the Plasticraft creative tab. Its banner starts on a new row, shows a material tooltip on hover, and contains that material's melt bucket, granules, standard 16x16x16px block, chest, tank, anvil, tray and Allay Hard Hat product slots. By default, 16-color plastic items are folded into one representative slot; right-click it to open the 16-color picker. Disable folding in the client configuration to show all sixteen colors directly

## Placement and recovery

- Products are entities affected by gravity, buoyancy, pushing and sliding rails, and they can be magnetized
- Clicking one of the six block faces selects one of four in-face turns, for 24 placement orientations; hold an Anvil Hammer use to adjust the orientation wheel
- Sneak-use any Anvil Hammer to retrieve a product; its model, material, color, magnetism and container contents are preserved, while its orientation is chosen again when placing it
- High-Viscosity Resin can bond products to blocks or other products; pistons and sliding rails move a bonded group as one
- Products follow the movement rules of Resin Blocks, Slime Blocks and Honey Blocks; one bonded group counts as one position against the piston push limit

The real shape is used for standing, pushing, selection and collision. Zero-thickness planes can be selected and retrieved, but do not support or block entities

## Redstone

Only a non-transparent plastic block or product whose model fills exactly 16px along X, Y and Z receives and conducts redstone like a vanilla solid block. The ordinary 16x16x16px plastic block qualifies; transparent plastic remains non-conductive like glass even when it fills one block, and the 16x16x14px world-cooled product or any other model with a non-16px axis do not conduct. Redstone components mounted on a tray keep their own vanilla behavior; see [Trays and Allay Hard Hats](040_plastic_components.md)

Material abilities are listed in [Material Comparison](000_material_comparison.md); melt use and casting or printing are covered by [Casting and 3D Printing](../004_molding/020_casting_and_printing.md)
