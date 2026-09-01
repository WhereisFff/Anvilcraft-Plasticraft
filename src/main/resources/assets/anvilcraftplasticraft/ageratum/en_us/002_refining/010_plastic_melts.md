---
navigation:
  title: "Plastic Melts"
  icon: "anvilcraftplasticraft:universal_plastic_melt_bucket"
items:
  - anvilcraftplasticraft:plastic_oil_bucket
  - anvilcraftplasticraft:universal_plastic_melt_bucket
  - anvilcraftplasticraft:clear_plastic_melt_bucket
  - anvilcraftplasticraft:engineering_plastic_melt_bucket
  - anvilcraftplasticraft:heat_resistant_plastic_melt_bucket
---

# Plastic Melts

<row halign="center">
<item id="anvilcraftplasticraft:plastic_oil_bucket"/>
<item id="anvilcraftplasticraft:universal_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:clear_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:engineering_plastic_melt_bucket"/>
<item id="anvilcraftplasticraft:heat_resistant_plastic_melt_bucket"/>
</row>

Melts do not come from a recipe, they come from environmental catalysis: drop a catalyst into the fluid, give it the right surroundings, and it changes on its own. There are four routes:

| Input | Catalyst | Environment | Output |
| --- | --- | --- | --- |
| Plastic Oil | Royal steel or frost metal | Heated from directly below | Universal Plastic Melt |
| Plastic Oil | Royal glass or frost glass | Heated from directly below | Clear Plastic Melt |
| Universal Plastic Melt | Royal steel or frost metal | Royal steel needs cold directly below | Engineering Plastic Melt |
| Universal Plastic Melt | Ember metal | A heat source directly below | Heat-Resistant Plastic Melt |

Plenty of vessels support open catalysis: Plastic Oil Cauldrons, Universal Plastic Melt Cauldrons, Fish Tanks, Large Cauldrons, upward-facing Hardened Resin and plastic cauldrons, and even fluid source blocks out in the world

Ordinary block containers and fluid sources read the condition directly beneath their container cell. An upward-facing Hardened Resin or plastic cauldron instead searches from the center and every covered part of its physical bottom face along current gravity for one block. A valid processing block below the center takes priority, then the remaining covered bottom cells are tried. That is why a small cauldron can share a cell with a campfire, while a cauldron taller than one block still reads the block outside its actual bottom

## Catalyst count

Catalysis counts types, not stacks — a whole chest of the same item is no faster. Royal steel, royal glass and ember metal contribute at full efficiency; frost metal and frost glass contribute at half. Adding more types does speed things up, but with diminishing returns. The good news is that catalysts are never consumed

For royal or ember materials, one type gives a 0.25 multiplier and eight types give 0.5, with the ceiling at 0.55. Frost materials use half of the corresponding multiplier

For paired royal and frost materials, including glass, let their distinct type counts be `R` and `F`. Their mixed multiplier is `M(R) + 0.5 * (M(R + F) - M(R))`, where `M(n) = min(0.55, 0.25 + log2(n) / 12)` and `M(0) = 0`

## Plastic Oil catalysis

Plastic Oil must be heated from directly below, and the hotter the source's actual output, the faster it progresses. Royal steel and frost metal turn it into Universal Plastic Melt, but if royal glass or frost glass is also present, the clear branch cuts in front

Clear Plastic Melt supports all 16 dye colors, and that color carries through molding, cooling and buckets. The trade-off is that it stops there — it cannot continue into the metal Engineering Plastic branch

## Engineering Plastic Melt

- Royal steel is picky: it only contributes while a cold block sits directly below the melt. One type takes 400gt, eight take 200gt
- Frost metal is not picky at all, needing neither cooling nor heat, but it runs at half efficiency. One type takes 800gt, eight take 400gt

## Heat-Resistant Plastic Melt

The heat-resistant branch needs at least one ember-metal type plus a heat source below. More heat and more distinct ember-metal types make it faster, again with diminishing returns from extra types

When both the engineering and heat-resistant conditions are met, the heat-resistant branch takes priority

## Batches and dyeing

A Large Cauldron uses the average actual heat output of the nine blocks in the 3x3 area directly beneath its floor and can process a full fluid layer at once. The engineering branch is the exception: it reads cold only from the center block

All four Plastic Melts can be dyed, and catalysis preserves the melt's amount and color. Clear Plastic products look like stained glass, and each color tints beacon beams passing through it exactly like the matching stained glass
