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

All four plastics take all 16 dye colors and all four work as movable products. Melts, melt buckets and granules use their own material's palette, and Clear Plastic looks just like stained glass. Hardness and blast resistance below are the values of the corresponding plastic blocks:

| Material | Dyeing | Hardness / blast resistance | Distinct ability |
| --- | --- | ---: | --- |
| Universal Plastic | 16 colors | 1.5 / 3.0 | None |
| Engineering Plastic | 16 colors | 2.5 / 6.0 | Stronger material; its dedicated products can provide Silk Touch demolition |
| Clear Plastic | 16 colors (stained glass) | 1.5 / 3.0 | Spectral Anvils and lasers pass through it; each color tints beacon beams |
| Heat-Resistant Plastic | 16 colors | 3.5 / 10.0 | Fire-resistant material; its dedicated products can provide Smelting demolition |

Lasers deserve their own paragraph. Universal, Engineering and Heat-Resistant Plastic all block them; lasers can damage entities from level 5 onward and also destroy the Universal or Engineering Plastic they hit. Clear Plastic simply lets lasers through and is immune to laser damage, while Heat-Resistant Plastic blocks lasers and is immune as well. On top of that, each Clear Plastic color tints beacon beams passing through it like the matching stained glass

Catalysts, heat and cold conditions for every melt are listed in [Plastic Melts](../002_refining/010_plastic_melts.md). Keep in mind that once a molding batch receives its first melt, its material and color are fixed until that batch is emptied

## Cooling and granules

A one-block world melt source solidifies on the spot when it meets rain above, adjacent water, ice or snow. It becomes a special 16x16x14px product of the matching material and drops no granules. Note that this is not the same thing as the standard 16x16x16px block in the creative inventory

If you want granules, you need a coolant. For every 1000mB of melt, any one of these produces 16 granules of the same material, keeping the melt's color:

- 1000mB water
- 1000mB powder snow
- one cold item accepted by the recipe

<row halign="center">
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_water"/>
<recipe id="anvilcraftplasticraft:fluid_mixing/universal_plastic_melt_with_powder_snow"/>
<recipe id="anvilcraftplasticraft:solid_liquid/cool_universal_plastic_melt"/>
</row>

Equivalent recipes exist for all four materials, and the [Catalytic Press Lid](../002_refining/020_catalytic_press_lid.md) can press melt into granules too. All four melts accept any dye, with fluid type and amount unchanged; Clear Plastic Melt keeps its stained-glass appearance after solidification

## Jeweler trade

An AnvilCraft novice Jeweler randomly picks one color and buys 8 <ref item="anvilcraftplasticraft:universal_plastic_granule"/> of that exact color for 2 emeralds, with 16 uses per offer. Engineering, Clear and Heat-Resistant Granules are of no interest to them
