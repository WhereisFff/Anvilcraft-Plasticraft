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
  - anvilcraftplasticraft:elastic_plastic
  - anvilcraftplasticraft:elastic_plastic_granule
---

# Material Comparison

<row halign="center">
<item id="anvilcraftplasticraft:universal_plastic"/>
<item id="anvilcraftplasticraft:engineering_plastic"/>
<item id="anvilcraftplasticraft:clear_plastic"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic"/>
<item id="anvilcraftplasticraft:elastic_plastic"/>
</row>

All five plastics take all 16 dye colors and work as movable products. Melts, melt buckets and granules use their own material's palette. Clear Plastic looks like stained glass, while Elastic Plastic has a translucent shell and an inner core like a Resin Block. Hardness and blast resistance below are the values of the corresponding plastic blocks:

| Material | Dyeing | Hardness / blast resistance | Distinct ability |
| --- | --- | ---: | --- |
| Universal Plastic | 16 colors | 1.5 / 3.0 | None |
| Engineering Plastic | 16 colors | 2.5 / 6.0 | Stronger material; its anvils apply Silk Touch when they land on a Stonecutter |
| Clear Plastic | 16 colors (stained glass) | 1.5 / 3.0 | Spectral Anvils and lasers pass through it; each color tints beacon beams |
| Heat-Resistant Plastic | 16 colors | 3.5 / 10.0 | Fire-resistant material; its anvils apply Smelting when they land on a Stonecutter |
| Elastic Plastic | 16 colors, translucent shell and core | 1.5 / 3.0 | All placed products bounce like Resin Anvils and launch entities that land on their surfaces |

Lasers deserve their own paragraph. Universal, Engineering, Heat-Resistant and Elastic Plastic all block them; lasers can damage entities from level 5 onward and also destroy the Universal, Engineering or Elastic Plastic they hit. Clear Plastic simply lets lasers through and is immune to laser damage, while Heat-Resistant Plastic blocks lasers and is immune as well. On top of that, each Clear Plastic color tints beacon beams passing through it like the matching stained glass

Catalysts, heat and cold conditions for every melt are listed in [Plastic Melts](../002_refining/010_plastic_melts.md). Keep in mind that once a molding batch receives its first melt, its material and color are fixed until that batch is emptied

## Elastic plastic rebounds

Elastic blocks, chests, tanks, anvils, cauldrons, large cauldrons, and trays bounce while retaining their real model gaps and cavities. Their smooth surface does not apply resin's walking slowdown, and ordinary grounded movement does not trigger small bounces.

- A moving product retains 80% of its incident normal speed against blocks or 72% against collidable entities; the bounce does not reduce its tangential speed
- Product impacts below 0.06 blocks/gt stop bouncing, with a 3gt cooldown per contact face
- The velocity increment imparted to another entity is capped at 0.45 blocks/gt
- Entities landing on the surface rebound at 80% of their incoming normal velocity and clear their fall distance; living impacts up to 0.1 blocks/gt and other impacts up to 0.06 blocks/gt settle directly
- Corrected movement distance does not add rebound energy, and a rebound immediately clears the grounded state; repeated bounces lose energy and settle
- Sneaking suppresses the landing bounce

Elastic plastic does not conduct redstone and does not gain clear plastic's laser transparency or beacon tinting

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

Equivalent recipes exist for all five materials, and the [Catalytic Press Lid](../002_refining/020_catalytic_press_lid.md) can press melt into granules too. All five melts accept any dye, with fluid type and amount unchanged; Clear Plastic Melt keeps its stained-glass appearance after solidification, and Elastic Plastic keeps its translucent shell and core

## Jeweler trade

An AnvilCraft novice Jeweler randomly picks one color and buys 8 <ref item="anvilcraftplasticraft:universal_plastic_granule"/> of that exact color for 2 emeralds, with 16 uses per offer. Engineering, Clear, Heat-Resistant and Elastic Granules are of no interest to them
