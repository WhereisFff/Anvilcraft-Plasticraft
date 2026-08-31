---
navigation:
  title: "Plastic Anvils"
  icon: "anvilcraftplasticraft:universal_plastic"
---

# Plastic Anvils

Pick Anvil as the product type and your model has to grow three sections along the world Y axis: bottom, middle and top. Miss one and it is not an anvil

## Ordinary anvil

- The lowest layer needs a continuous, hole-free flat rectangle at least 12x12px
- Total height must be at least 10px; bottom, middle and top sections must be at least 3px, 3px and 4px thick, with the top strictly thicker than the bottom and all three sections connected along Y
- The middle projection must be strictly inset on all four sides, and the top projection may be at most 16px² smaller in area than the bottom
- Only an impact on the local bottom face while it points down can trigger anvil processing; right-clicking or using an Anvil Hammer opens the vanilla anvil interface
- Every cell the bottom face lands on is processed on its own, even one only clipped by the very edge of that face, so a bottom that spans several cells processes all of them in a single landing — a 40x40px bottom covers exactly 3x3 cells

The material decides what falls out when it breaks a block:

- Universal Plastic Anvils use ordinary drops
- Engineering Plastic Anvils use Silk Touch drops when breaking a block above a stonecutter
- Heat-Resistant Plastic Anvils use Smelting drops above a stonecutter and keep their fire resistance
- Clear Plastic Anvils keep Clear Plastic's pass-through properties

## Giant anvil

If that flat rectangle on the lowest layer reaches a complete 40x40px or more, the anvil also unlocks Giant Anvil abilities: AnvilCraft multiblock conversions and crafting, Giant Anvil Ground Pound, and Large Cauldron processing

Close is not enough — a 39x40px bottom still makes a perfectly good ordinary Plastic Anvil, but none of those functions come with it

See [Material Comparison](000_material_comparison.md) and [Plastic Molding](../004_molding/index.md) for material and production rules
