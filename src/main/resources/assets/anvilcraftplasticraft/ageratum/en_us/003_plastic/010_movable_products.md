---
navigation:
  title: "Shared Movable-Product Rules"
  icon: "anvilcraftplasticraft:universal_plastic"
---

# Shared Movable-Product Rules

The special 16x16x14px products that cool straight out of world melt and the ordinary products the molding chamber makes are all movable plastic products. Chests, tanks, anvils, trays and Allay Hard Hats have not changed material — they simply grew a function on top of the same material and shape rules

## Creative inventory

Each plastic material gets its own labeled section in the Plasticraft creative tab. Its banner starts on a new row and shows a material tooltip on hover, and the section holds that material's melt bucket, granules, standard 16x16x16px block, chest, tank, anvil, tray and Allay Hard Hat product slots

16-color items are folded into one representative slot by default, so the tab stays readable. Right-click that slot to open the 16-color picker; click a material banner to expand every color-capable slot in the section, and click it again to fold it back. The client configuration controls the initial state, and disabling folding starts with all sixteen colors visible

## Placement and recovery

- Products are entities, so gravity, buoyancy, pushing and sliding rails all apply, and they can be magnetized
- Click any of the six block faces to place one, with four in-face turns per face for 24 orientations; hold an Anvil Hammer use to adjust the orientation wheel
- Sneak-use any Anvil Hammer to retrieve a product; model, material, color, magnetism and container contents are all preserved, while the orientation is chosen again when placing it
- In Creative Mode, middle-click returns an empty initial product that preserves material, color, magnetization and molded shape; Ctrl + middle-click also copies container contents and persistent functional state
- High-Viscosity Resin can bond products to blocks or to other products; pistons and sliding rails move a bonded group as one
- Products follow the movement rules of Resin Blocks, Slime Blocks and Honey Blocks; one bonded group counts as one position against the piston push limit
- When piston movement finishes, a Resin Block, High-Viscosity Resin Block or Slime Block occupying a product's former space launches it in the movement direction at an initial speed of 1 block per game tick; Honey Blocks, ordinary blocks and side adhesion do not add this launch

The real shape is what you stand on, push, select and collide with — what you see is what you touch. Zero-thickness planes are the exception: they can be selected and retrieved, but they neither support nor block entities

The crosshair can pass through gaps between solid parts of the model, and the selection outline preserves slanted edges. Products bonded into blocks use the same selection behavior, while attached interactive tray components remain selectable. Complex models may initially show a simplified outline before their merged edges are ready; exceptionally complex outlines remain simplified without changing physical collisions

If a bonded product extends more than 2 blocks beyond any face of its anchor block, it retains the compatibility selection bounds while its outline still follows the product geometry

Dropped items settle on the real slope just like mobs; entering empty space inside its bounding box does not trigger escape jitter. Arrows, snowballs and other projectiles hit the real surface of products, passing through empty corners and holes inside their bounding boxes. This also applies after products become blocks, including portions extending outside their anchor cells; ordinary blocks behind empty corners still stop projectiles

## Redstone

The redstone rule is blunt: only a non-transparent plastic block or product whose model fills exactly 16px along X, Y and Z receives and conducts redstone like a vanilla solid block. The ordinary 16x16x16px plastic block qualifies; transparent plastic stays non-conductive like glass even when it fills a full block, and neither the 16x16x14px world-cooled product nor any other model with a non-16px axis conducts

Redstone components mounted on a tray are exempt from all of this and keep their own vanilla behavior; see [Trays and Allay Hard Hats](040_plastic_components.md)

Material abilities are listed in [Material Comparison](000_material_comparison.md); how much melt a product costs and how casting differs from printing are covered by [Casting and 3D Printing](../004_molding/020_casting_and_printing.md)
