---
navigation:
  title: "Material Comparison"
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

# Material Comparison

<row halign="center">
<item id="anvilcraftplasticraft:universal_plastic"/>
<item id="anvilcraftplasticraft:engineering_plastic"/>
<item id="anvilcraftplasticraft:clear_plastic"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic"/>
</row>

All four plastics support all 16 dye colors and can be used for movable products. Melts, melt buckets and granules use their material's own palette. Clear Plastic uses a stained-glass appearance; hardness and blast resistance below are the values of the corresponding plastic blocks

| Material | Dyeing | Hardness / blast resistance | Distinct ability |
| --- | --- | ---: | --- |
| Universal Plastic | 16 colors | 1.5 / 3.0 | None |
| Engineering Plastic | 16 colors | 2.5 / 6.0 | Stronger material; its dedicated products can provide Silk Touch demolition |
| Clear Plastic | 16 colors (stained glass) | 1.5 / 3.0 | Spectral Anvils and lasers pass through it; each color tints beacon beams |
| Heat-Resistant Plastic | 16 colors | 3.5 / 10.0 | Fire-resistant material; its dedicated products can provide Smelting demolition |

Universal, Engineering and Heat-Resistant Plastic all block lasers. Lasers can damage entities from level 5 onward and also destroy the Universal or Engineering Plastic they hit. Clear Plastic lets lasers pass and is immune to laser damage; Heat-Resistant Plastic blocks lasers but is also immune to laser damage. Each Clear Plastic color tints beacon beams passing through it like the matching stained glass

Catalysts, heat and cold conditions for every melt are listed in [Plastic Melts](../002_refining/010_plastic_melts.md). Once a molding batch receives its first melt, its material and color are fixed until that batch is emptied

## Cooling and granules

A one-block world melt source solidifies in place when it meets rain above, adjacent water, ice or snow. It becomes a special 16x16x14px product of the matching material and does not drop granules. This differs from the standard 16x16x16px block in the creative inventory

For every 1000mB of melt, any one of these coolants produces 16 granules of the same material and color:

- 1000mB water
- 1000mB powder snow
- one cold item accepted by the recipe

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_water"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_powder_snow"/>
<recipe id="anvilcraftplasticraft:solid_liquid/cool_universal_plastic_melt"/>
</row>

Equivalent recipes exist for all four materials. The [Catalytic Press Lid](../002_refining/020_catalytic_press_lid.md) can also turn melt into granules. All four melts accept any dye, and fluid type and amount are unchanged; Clear Plastic Melt keeps its stained-glass appearance after solidification

## Jeweler trade

An AnvilCraft novice Jeweler randomly chooses one color and buys 8 <ref item="anvilcraftplasticraft:universal_plastic_granule"/> of that exact color for 2 emeralds. Each offer has 16 uses. Engineering, Clear and Heat-Resistant Granules do not match this trade
